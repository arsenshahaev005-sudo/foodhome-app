import Foundation

@MainActor
final class IOSPaymentReturnRouter {
    private let coordinator: PaymentCoordinator
    private let telemetry: TelemetryReporter?
    private var listener: (() -> Void)?
    private var leftForeground = false
    private var hasActivated = false

    init(coordinator: PaymentCoordinator, telemetry: TelemetryReporter? = nil) {
        self.coordinator = coordinator
        self.telemetry = telemetry
    }

    func attach(_ listener: @escaping () -> Void) -> () -> Void {
        self.listener = listener
        if coordinator.pendingEvent() != nil { listener() }
        return { [weak self] in
            guard let self, self.listener != nil else { return }
            self.listener = nil
        }
    }

    func sceneBecameActive() {
        if !hasActivated {
            hasActivated = true
            record(reason: "coldStart")
        } else if leftForeground {
            leftForeground = false
            record(reason: "appResumed")
        }
    }

    func sceneLeftForeground() {
        leftForeground = true
    }

    func receivedUniversalLink() {
        record(reason: "universalLink")
    }

    func webViewRecovered() {
        record(reason: "webViewRecovered")
    }

    private func record(reason: String) {
        let hadActiveRecovery = coordinator.hasActiveRecovery()
        if coordinator.recordReturn(reason: reason) != nil {
            listener?()
        } else if hadActiveRecovery {
            telemetry?.record(
                .paymentReturnFailed,
                attributes: ["errorCode": "PAYMENT_RETURN_RECORD_FAILED"]
            )
        }
    }
}
