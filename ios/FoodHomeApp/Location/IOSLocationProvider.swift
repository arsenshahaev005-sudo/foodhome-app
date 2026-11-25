import CoreLocation
import Foundation

enum LocationRequestResult {
    case granted(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        precise: Bool
    )
    case failed(code: String, message: String, retryable: Bool = false)
}

@MainActor
final class IOSLocationProvider: NSObject, @preconcurrency CLLocationManagerDelegate {
    private let manager: CLLocationManager
    private let timeout: TimeInterval
    private let now: () -> Date
    private var completion: ((LocationRequestResult) -> Void)?
    private var deadline: Task<Void, Never>?
    private var acquiring = false
    private var bestFix: CLLocation?

    var awaitingAuthorization: Bool { completion != nil && manager.authorizationStatus == .notDetermined }

    init(manager: CLLocationManager = CLLocationManager(), timeout: TimeInterval = 22,
         now: @escaping () -> Date = Date.init) {
        self.manager = manager
        self.timeout = timeout
        self.now = now
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
    }

    func requestCurrentLocation(completion: @escaping (LocationRequestResult) -> Void) {
        guard self.completion == nil else {
            completion(.failed(code: "RATE_LIMITED", message: "Location request already pending", retryable: true))
            return
        }
        self.completion = completion
        if manager.authorizationStatus == .notDetermined {
            scheduleDeadline(after: 60)
            manager.requestWhenInUseAuthorization()
        } else {
            locationManagerDidChangeAuthorization(manager)
        }
    }

    func cancel() {
        finish(.failed(code: "CANCELLED", message: "Location request was cancelled"))
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard completion != nil else { return }
        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            if manager.accuracyAuthorization == .reducedAccuracy { bestFix = nil }
            guard !acquiring else { return }
            // A just-observed fix can serve a new request without waiting for another radio update.
            if manager.accuracyAuthorization == .fullAccuracy, let cached = manager.location,
               cached.horizontalAccuracy <= 100,
               let fix = Self.bestRecentFix(in: [cached], now: now(), maximumAge: 10) {
                grant(fix)
                return
            }
            acquiring = true
            scheduleDeadline(after: timeout)
            manager.startUpdatingLocation()
        case .notDetermined:
            break
        default:
            finish(.failed(code: "CAPABILITY_UNAVAILABLE", message: "Location permission is unavailable"))
        }
    }

    static func bestRecentFix(in locations: [CLLocation], now: Date, maximumAge: TimeInterval = 60) -> CLLocation? {
        locations.filter {
            let age = now.timeIntervalSince($0.timestamp)
            return (-5...maximumAge).contains(age) && (0...100_000).contains($0.horizontalAccuracy)
                && CLLocationCoordinate2DIsValid($0.coordinate)
        }.min { $0.horizontalAccuracy < $1.horizontalAccuracy }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard acquiring else { return }
        let reduced = manager.accuracyAuthorization == .reducedAccuracy
        let candidates = reduced ? locations : locations + [bestFix].compactMap { $0 }
        guard let location = Self.bestRecentFix(in: candidates, now: now()) else { return }
        bestFix = location
        if location.horizontalAccuracy <= 100 || reduced {
            grant(location)
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        guard (error as? CLError)?.code != .locationUnknown else { return }
        finish(.failed(code: "CAPABILITY_UNAVAILABLE", message: "Location is unavailable"))
    }

    private func scheduleDeadline(after interval: TimeInterval) {
        deadline?.cancel()
        deadline = Task { [weak self] in
            do { try await Task.sleep(for: .seconds(interval)) } catch { return }
            guard !Task.isCancelled, let self else { return }
            if self.manager.accuracyAuthorization == .fullAccuracy, let bestFix = self.bestFix,
               let fresh = Self.bestRecentFix(in: [bestFix], now: self.now()) {
                self.grant(fresh)
            } else {
                self.finish(.failed(code: "TIMEOUT", message: "Location request timed out", retryable: true))
            }
        }
    }

    private func grant(_ location: CLLocation) {
        guard manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways else {
            finish(.failed(code: "CAPABILITY_UNAVAILABLE", message: "Location permission is unavailable"))
            return
        }
        finish(.granted(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude,
                        accuracyMeters: location.horizontalAccuracy, precise: manager.accuracyAuthorization == .fullAccuracy))
    }

    private func finish(_ result: LocationRequestResult) {
        guard let completion else { return }
        self.completion = nil
        acquiring = false
        bestFix = nil
        deadline?.cancel()
        deadline = nil
        manager.stopUpdatingLocation()
        completion(result)
    }
}
