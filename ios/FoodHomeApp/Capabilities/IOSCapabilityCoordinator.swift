import Foundation
import UIKit

@MainActor
final class IOSCapabilityCoordinator: BridgeCapabilityDispatching {
    var presenterProvider: (() -> UIViewController?)?
    var onSberReturn: ((URL) -> Void)?
    private let sberSession: IOSSberAuthSession
    private let hasRecentAuthUserAction: () -> Bool

    private let trustedOrigin: URL
    private let manifest: BridgeManifest
    private let sharePolicy: FoodHomeSharePolicy
    private let locationProvider: IOSLocationProvider
    private let notificationCoordinator: IOSNotificationPermissions
    private let rateLimiter: CapabilityRateLimiter
    private let paymentCoordinator: PaymentCoordinator?
    private let hasRecentPaymentUserAction: () -> Bool
    private let telemetry: TelemetryReporter
    private var documentGeneration: UInt = 0
    private var requestingNotificationPermission = false

    init(
        manifest: BridgeManifest,
        trustedOrigin: URL,
        paymentCoordinator: PaymentCoordinator? = nil,
        hasRecentPaymentUserAction: @escaping () -> Bool = { false },
        hasRecentAuthUserAction: @escaping () -> Bool = { false },
        now: @escaping () -> Date = Date.init,
        telemetry: TelemetryReporter? = nil,
        notificationCoordinator: IOSNotificationPermissions? = nil
    ) {
        self.manifest = manifest
        self.trustedOrigin = trustedOrigin
        self.sberSession = IOSSberAuthSession(trustedOrigin: trustedOrigin)
        self.hasRecentAuthUserAction = hasRecentAuthUserAction
        self.sharePolicy = FoodHomeSharePolicy(trustedOrigin: trustedOrigin)
        self.locationProvider = IOSLocationProvider()
        self.notificationCoordinator = notificationCoordinator ?? IOSNotificationCoordinator()
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
        case "startSberAuth":
            guard let window = presenterProvider?()?.viewIfLoaded?.window, hasRecentAuthUserAction(),
                  let attempt = request.payload["attemptId"] as? String, let url = request.payload["url"] as? String,
                  let onSberReturn else {
                reportingCompletion(.failure(code: "CANCELLED", message: "Authentication requires a foreground user action"))
                return
            }
            guard enforceRateLimit(method: request.method, completion: reportingCompletion) else { return }
            if sberSession.start(attemptID: attempt, rawURL: url, window: window, onReturn: onSberReturn) {
                reportingCompletion(.success(["capability": request.method, "status": "presented"]))
            } else {
                reportingCompletion(.failure(code: "LAUNCH_FAILED", message: "Authentication browser could not be opened"))
            }
        case "managePush":
            if request.payload["action"] as? String == "bind",
               !enforceRateLimit(method: request.method, completion: reportingCompletion) { return }
            IOSPushRegistration.shared.dispatch(request.payload, origin: trustedOrigin,
                                                bridgeVersion: manifest.contractVersion, completion: reportingCompletion)
        case "manageOrderActivity":
            if request.payload["action"] as? String == "sync", !AppEnvironmentResolver.productionPushAvailable {
                reportingCompletion(.success(["capability": request.method, "status": "unavailable"]))
                return
            }
            LiveOrderCoordinator.shared.dispatch(payload: request.payload, origin: trustedOrigin, completion: reportingCompletion)
        case "openNotificationSettings":
            guard presenterProvider?()?.viewIfLoaded?.window != nil,
                  UIApplication.shared.applicationState == .active else { reportingCompletion(unavailable()); return }
            guard hasForegroundNotificationPresenter, hasRecentAuthUserAction() else {
                reportingCompletion(.failure(code: "CANCELLED", message: "Notification settings require a foreground user action"))
                return
            }
            guard enforceRateLimit(method: request.method, completion: reportingCompletion) else { return }
            let destination: String
            if #available(iOS 16.0, *) { destination = UIApplication.openNotificationSettingsURLString }
            else { destination = UIApplication.openSettingsURLString }
            guard let url = URL(string: destination) else { reportingCompletion(unavailable()); return }
            UIApplication.shared.open(url) { opened in
                reportingCompletion(opened ? .success(["capability": "openNotificationSettings", "status": "presented"])
                    : .failure(code: "LAUNCH_FAILED", message: "Settings could not be opened"))
            }
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
        documentGeneration &+= 1
        sberSession.cancel()
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
        guard presenterProvider?()?.viewIfLoaded?.window != nil else {
            completion(unavailable())
            return
        }
        locationProvider.requestCurrentLocation { result in
            switch result {
            case let .granted(latitude, longitude, accuracyMeters, precise):
                completion(.success([
                    "capability": "requestLocation", "status": "granted",
                    "latitude": latitude, "longitude": longitude,
                    "accuracyMeters": accuracyMeters, "precise": precise,
                ]))
            case let .failed(code, message, retryable):
                completion(.failure(code: code, message: message, retryable: retryable))
            }
        }
    }

    private func dispatchNotificationPermission(
        _ request: BridgeRequest,
        completion: @escaping (BridgeDispatchResult) -> Void
    ) {
        guard !requestingNotificationPermission else {
            completion(.failure(code: "RATE_LIMITED", message: "Notification permission request is pending", retryable: true))
            return
        }
        guard enforceRateLimit(
            method: "requestNotificationPermission",
            completion: completion
        ) else {
            return
        }
        guard presenterProvider?()?.viewIfLoaded?.window != nil else {
            completion(unavailable())
            return
        }
        let generation = documentGeneration
        requestingNotificationPermission = true
        notificationCoordinator.requestAuthorizationIfNeeded(canPresent: { [weak self] in
            guard let self, self.documentGeneration == generation else { return false }
            return self.hasForegroundNotificationPresenter && !self.locationProvider.awaitingAuthorization
        }) { result in
            self.requestingNotificationPermission = false
            switch result {
            case let .status(status): completion(self.notificationResult(status))
            case .cancelled:
                completion(.failure(code: "CANCELLED", message: "The document is not available for notification permission"))
            }
        }
    }

    private var hasForegroundNotificationPresenter: Bool {
        guard let presenter = presenterProvider?(), let window = presenter.viewIfLoaded?.window else { return false }
        return !window.isHidden && window.windowScene?.activationState == .foregroundActive
            && presenter.presentedViewController == nil
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
