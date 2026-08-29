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
final class IOSLocationProvider: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private let timeout: TimeInterval
    private var completion: ((LocationRequestResult) -> Void)?
    private var timer: Timer?

    init(timeout: TimeInterval = 10) {
        self.timeout = timeout
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func requestCurrentLocation(completion: @escaping (LocationRequestResult) -> Void) {
        cancel()
        guard CLLocationManager.locationServicesEnabled() else {
            completion(.failed(code: "CAPABILITY_UNAVAILABLE", message: "Location services are disabled"))
            return
        }
        self.completion = completion
        switch manager.authorizationStatus {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .authorizedAlways, .authorizedWhenInUse:
            beginLocationRequest()
        case .denied, .restricted:
            finish(.failed(code: "CAPABILITY_UNAVAILABLE", message: "Location permission is unavailable"))
        @unknown default:
            finish(.failed(code: "CAPABILITY_UNAVAILABLE", message: "Location permission is unavailable"))
        }
    }

    func cancel() {
        manager.stopUpdatingLocation()
        timer?.invalidate()
        timer = nil
        completion = nil
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard completion != nil else { return }
        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            beginLocationRequest()
        case .denied, .restricted:
            finish(.failed(code: "CAPABILITY_UNAVAILABLE", message: "Location permission was denied"))
        case .notDetermined:
            break
        @unknown default:
            finish(.failed(code: "CAPABILITY_UNAVAILABLE", message: "Location permission is unavailable"))
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        finish(
            .granted(
                latitude: min(max(location.coordinate.latitude, -90), 90),
                longitude: min(max(location.coordinate.longitude, -180), 180),
                accuracyMeters: min(max(location.horizontalAccuracy, 0), 100_000),
                precise: manager.accuracyAuthorization == .fullAccuracy
            )
        )
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        let code = (error as? CLError)?.code
        let retryable = code == .locationUnknown
        finish(
            .failed(
                code: retryable ? "TIMEOUT" : "CAPABILITY_UNAVAILABLE",
                message: retryable ? "Location is temporarily unavailable" : "Location is unavailable",
                retryable: retryable
            )
        )
    }

    private func beginLocationRequest() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: timeout, repeats: false) {
            [weak self] _ in
            Task { @MainActor in
                self?.finish(
                    .failed(code: "TIMEOUT", message: "Location request timed out", retryable: true)
                )
            }
        }
        manager.requestLocation()
    }

    private func finish(_ result: LocationRequestResult) {
        guard let completion else { return }
        self.completion = nil
        manager.stopUpdatingLocation()
        timer?.invalidate()
        timer = nil
        completion(result)
    }
}
