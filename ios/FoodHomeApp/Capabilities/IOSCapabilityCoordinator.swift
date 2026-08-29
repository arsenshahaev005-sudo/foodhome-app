import Foundation
import UIKit

@MainActor
final class IOSCapabilityCoordinator: BridgeCapabilityDispatching {
    var presenterProvider: (() -> UIViewController?)?

    private let manifest: BridgeManifest
    private let sharePolicy: FoodHomeSharePolicy
    private let locationProvider: IOSLocationProvider
    private let notificationCoordinator: IOSNotificationCoordinator
    private let rateLimiter: CapabilityRateLimiter
    private let paymentCoordinator: PaymentCoordinator?
    private let hasRecentPaymentUserAction: () -> Bool
    private let telemetry: TelemetryReporter

    init(
        manifest: BridgeManifest,
        trustedOrigin: URL,
        paymentCoordinator: PaymentCoordinator? = nil,
        hasRecentPaymentUserAction: @escaping () -> Bool = { false },
        now: @escaping () -> Date = Date.init,
        telemetry: TelemetryReporter? = nil
    ) {
        self.manifest = manifest
        self.sharePolicy = FoodHomeSharePolicy(trustedOrigin: trustedOrigin)
        self.locationProvider = IOSLocationProvider()
        self.notificationCoordinator = IOSNotificationCoordinator()
        self.paymentCoordinator = paymentCoordinator
        self.hasRecentPaymentUserAction = hasRecentPaymentUserAction
        self.rateLimiter = CapabilityRateLimiter(now: now)
        self.telemetry = telemetry ?? .disabled(trustedOrigin: trustedOrigin)
    }

    func dispatch(
        _ request: BridgeRequest,
        completion: @escaping (BridgeDispatchResult) -> Void
    ) {
        let reportingCompletion: (BridgeDispatchResult) -> Void = { [telemetry] result in
            if case let .failure(code, _, _) = result {
                telemetry.record(.bridgeRequestFailed, attributes: ["errorCode": code])
            }
            completion(result)
        }
        let paymentControl = request.method == "ackNativeEvent" ||
            request.method == "clearPaymentRecovery"
        let available = manifest.advertisedCapabilities.contains(request.method) ||
            (paymentControl && manifest.advertisedCapabilities.contains("openPayment"))
        guard available else {
            reportingCompletion(unavailable())
            return
        }

        switch request.method {
        case "share":
            dispatchShare(request, completion: reportingCompletion)
        case "requestLocation":
            dispatchLocation(request, completion: reportingCompletion)
        case "getNotificationStatus":
            notificationCoordinator.authorizationStatus { status in
                reportingCompletion(self.notificationResult(status))
            }
        case "requestNotificationPermission":
            dispatchNotificationPermission(request, completion: reportingCompletion)
        case "openPayment":
            dispatchPayment(request, completion: reportingCompletion)
        case "ackNativeEvent":
            dispatchEventAcknowledgement(request, completion: reportingCompletion)
        case "clearPaymentRecovery":
            dispatchPaymentRecoveryClear(request, completion: reportingCompletion)
        default:
            reportingCompletion(unavailable())
        }
    }

    private func dispatchPayment(
        _ request: BridgeRequest,
        completion: @escaping (BridgeDispatchResult) -> Void
    ) {
        guard enforceRateLimit(method: "openPayment", completion: completion) else { return }
        guard let paymentCoordinator,
              let rawURL = request.payload["url"] as? String,
              let recoveryContext = request.payload["recoveryContext"] as? String,
              let rawExpiry = request.payload["expiresAt"] as? String,
              let expiresAt = Self.parseRFC3339(rawExpiry)
        else {
            completion(.failure(code: "INVALID_PAYLOAD", message: "Payment payload is invalid"))
            return
        }
        let result = paymentCoordinator.open(
            OpenPaymentRequest(
                rawURL: rawURL,
                recoveryContext: recoveryContext,
                serverExpiresAtEpochMilliseconds: Int64(
                    (expiresAt.timeIntervalSince1970 * 1_000).rounded(.down)
                ),
                userInitiated: hasRecentPaymentUserAction()
            )
        )
        completion(paymentResult(result))
    }

    private func dispatchEventAcknowledgement(
        _ request: BridgeRequest,
        completion: @escaping (BridgeDispatchResult) -> Void
    ) {
        guard let paymentCoordinator, let eventID = request.payload["eventId"] as? String else {
            completion(unavailable())
            return
        }
        completion(paymentResult(paymentCoordinator.acknowledge(eventID: eventID)))
    }

    private func dispatchPaymentRecoveryClear(
        _ request: BridgeRequest,
        completion: @escaping (BridgeDispatchResult) -> Void
    ) {
        guard let paymentCoordinator,
              let recovery = request.payload["recoveryContext"] as? String,
              let reason = request.payload["reason"] as? String
        else {
            completion(unavailable())
            return
        }
        completion(paymentResult(paymentCoordinator.clear(recoveryContext: recovery, reason: reason)))
    }

    private func paymentResult(_ result: PaymentOperationResult) -> BridgeDispatchResult {
        switch result {
        case let .presented(flow):
            return .success([
                "capability": "openPayment",
                "status": "presented",
                "flow": flow.rawValue,
            ])
        case let .acknowledged(eventID):
            return .success([
                "capability": "nativeEvents",
                "status": "acknowledged",
                "eventId": eventID,
            ])
        case .cleared:
            return .success([
                "capability": "paymentRecovery",
                "status": "cleared",
            ])
        case let .failure(code, retryable):
            return .failure(
                code: code,
                message: "Payment operation was rejected",
                retryable: retryable
            )
        }
    }

    private static func parseRFC3339(_ value: String) -> Date? {
        let standard = ISO8601DateFormatter()
        if let date = standard.date(from: value) { return date }
        let fractional = ISO8601DateFormatter()
        fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return fractional.date(from: value)
    }

    func cancelPending() {
        locationProvider.cancel()
    }

    private func dispatchShare(
        _ request: BridgeRequest,
        completion: @escaping (BridgeDispatchResult) -> Void
    ) {
        guard let payload = sharePolicy.parse(
            title: request.payload["title"] as? String,
            text: request.payload["text"] as? String,
            rawURL: request.payload["url"] as? String
        ) else {
            completion(.failure(code: "INVALID_PAYLOAD", message: "Share payload is invalid"))
            return
        }
        guard let presenter = presenterProvider?(), presenter.presentedViewController == nil else {
            completion(unavailable())
            return
        }

        var items: [Any] = []
        if let title = payload.title, !title.isEmpty { items.append(title) }
        if let text = payload.text, !text.isEmpty { items.append(text) }
        items.append(payload.url)
        let activity = UIActivityViewController(
            activityItems: items,
            applicationActivities: nil
        )
        if let popover = activity.popoverPresentationController {
            popover.sourceView = presenter.view
            popover.sourceRect = CGRect(
                x: presenter.view.bounds.midX,
                y: presenter.view.bounds.midY,
                width: 1,
                height: 1
            )
        }
        presenter.present(activity, animated: true)
        completion(
            .success([
                "capability": "share",
                "status": "presented",
            ])
        )
    }

    private func dispatchLocation(
        _ request: BridgeRequest,
        completion: @escaping (BridgeDispatchResult) -> Void
    ) {
        guard enforceRateLimit(method: "requestLocation", completion: completion) else { return }
        guard let purpose = request.payload["purpose"] as? String,
              let presenter = presenterProvider?(),
              presenter.presentedViewController == nil
        else {
            completion(unavailable())
            return
        }

        let alert = UIAlertController(
            title: "Разрешить геолокацию?",
            message: purpose,
            preferredStyle: .alert
        )
        alert.addAction(
            UIAlertAction(title: "Не сейчас", style: .cancel) { _ in
                completion(
                    .failure(
                        code: "CANCELLED",
                        message: "Location request was cancelled"
                    )
                )
            }
        )
        alert.addAction(
            UIAlertAction(title: "Продолжить", style: .default) { [weak self] _ in
                self?.locationProvider.requestCurrentLocation { result in
                    switch result {
                    case let .granted(latitude, longitude, accuracyMeters, precise):
                        completion(
                            .success([
                                "capability": "requestLocation",
                                "status": "granted",
                                "latitude": latitude,
                                "longitude": longitude,
                                "accuracyMeters": accuracyMeters,
                                "precise": precise,
                            ])
                        )
                    case let .failed(code, message, retryable):
                        completion(
                            .failure(
                                code: code,
                                message: message,
                                retryable: retryable
                            )
                        )
                    }
                }
            }
        )
        presenter.present(alert, animated: true)
    }

    private func dispatchNotificationPermission(
        _ request: BridgeRequest,
        completion: @escaping (BridgeDispatchResult) -> Void
    ) {
        guard enforceRateLimit(
            method: "requestNotificationPermission",
            completion: completion
        ) else {
            return
        }
        guard let presenter = presenterProvider?(), presenter.presentedViewController == nil else {
            completion(unavailable())
            return
        }

        let purpose = request.payload["purpose"] as? String
        let alert = UIAlertController(
            title: "Включить уведомления?",
            message: purpose ?? "Узнавать об актуальных событиях Food&Home",
            preferredStyle: .alert
        )
        alert.addAction(
            UIAlertAction(title: "Не сейчас", style: .cancel) { _ in
                completion(
                    .failure(
                        code: "CANCELLED",
                        message: "Notification permission request was cancelled"
                    )
                )
            }
        )
        alert.addAction(
            UIAlertAction(title: "Продолжить", style: .default) { [weak self] _ in
                guard let self else {
                    completion(
                        .failure(
                        code: "INTERNAL_ERROR",
                        message: "Notification permission request failed"
                        )
                    )
                    return
                }
                self.notificationCoordinator.requestAuthorization { status in
                    completion(self.notificationResult(status))
                }
            }
        )
        presenter.present(alert, animated: true)
    }

    private func notificationResult(_ status: NotificationAuthorizationStatus) -> BridgeDispatchResult {
        .success([
            "capability": "notifications",
            "status": status.rawValue,
        ])
    }

    private func enforceRateLimit(
        method: String,
        completion: (BridgeDispatchResult) -> Void
    ) -> Bool {
        guard let rule = manifest.rateLimits[method], rule.isSafe else {
            completion(unavailable())
            return false
        }
        guard rateLimiter.allow(
            key: method,
            maxRequests: rule.maxRequests,
            window: TimeInterval(rule.windowSeconds)
        ) else {
            completion(
                .failure(
                    code: "RATE_LIMITED",
                    message: "Capability request rate limit reached",
                    retryable: true
                )
            )
            return false
        }
        return true
    }

    private func unavailable() -> BridgeDispatchResult {
        .failure(code: "CAPABILITY_UNAVAILABLE", message: "Capability is unavailable")
    }
}

private final class CapabilityRateLimiter {
    private let now: () -> Date
    private var requests: [String: [Date]] = [:]

    init(now: @escaping () -> Date) {
        self.now = now
    }

    func allow(key: String, maxRequests: Int, window: TimeInterval) -> Bool {
        let current = now()
        let cutoff = current.addingTimeInterval(-window)
        var values = requests[key, default: []].filter { $0 > cutoff }
        guard values.count < maxRequests else {
            requests[key] = values
            return false
        }
        values.append(current)
        requests[key] = values
        return true
    }
}
