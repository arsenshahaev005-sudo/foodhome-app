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
    private let manager = CLLocationManager()
    private let timeout: TimeInterval
    private var completion: ((LocationRequestResult) -> Void)?
    private var timer: Timer?
    private var acquiring = false
    private var bestFix: CLLocation?

    init(timeout: TimeInterval = 22) {
        self.timeout = timeout
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
            guard !acquiring else { return }
            acquiring = true
            scheduleDeadline(after: timeout)
            manager.startUpdatingLocation()
        case .notDetermined:
            break
        default:
            finish(.failed(code: "CAPABILITY_UNAVAILABLE", message: "Location permission is unavailable"))
        }
    }

    static func bestRecentFix(in locations: [CLLocation], now: Date) -> CLLocation? {
        locations.filter {
            let age = now.timeIntervalSince($0.timestamp)
            return (-5...60).contains(age) && (0...100_000).contains($0.horizontalAccuracy)
                && CLLocationCoordinate2DIsValid($0.coordinate)
        }.min { $0.horizontalAccuracy < $1.horizontalAccuracy }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard acquiring,
              let location = Self.bestRecentFix(in: locations + [bestFix].compactMap { $0 }, now: Date())
        else { return }
        bestFix = location
        if location.horizontalAccuracy <= 100 || manager.accuracyAuthorization == .reducedAccuracy {
            grant(location)
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        guard (error as? CLError)?.code != .locationUnknown else { return }
        finish(.failed(code: "CAPABILITY_UNAVAILABLE", message: "Location is unavailable"))
    }

    private func scheduleDeadline(after interval: TimeInterval) {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: interval, repeats: false) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                if let bestFix = self.bestFix {
                    self.grant(bestFix)
                } else {
                    self.finish(.failed(code: "TIMEOUT", message: "Location request timed out", retryable: true))
                }
            }
        }
    }

    private func grant(_ location: CLLocation) {
        finish(.granted(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude,
                        accuracyMeters: location.horizontalAccuracy, precise: manager.accuracyAuthorization == .fullAccuracy))
    }

    private func finish(_ result: LocationRequestResult) {
        guard let completion else { return }
        self.completion = nil
        acquiring = false
        bestFix = nil
        timer?.invalidate()
        timer = nil
        manager.stopUpdatingLocation()
        completion(result)
    }
}
