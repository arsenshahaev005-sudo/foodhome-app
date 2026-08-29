import XCTest
@testable import FoodHomeApp

@MainActor
final class IOSPaymentReturnRouterTests: XCTestCase {
    func testSceneReturnCreatesOneStableEventWithoutRelaunch() {
        let store = RouterMemoryStore()
        let launcher = RouterLauncher()
        let coordinator = PaymentCoordinator(
            policy: PaymentLaunchPolicy(
                rules: [
                    PaymentProviderRule(
                        flow: .sbpCyclops,
                        exactHost: "sbp.example.invalid",
                        pathPrefix: "/qr"
                    ),
                ]
            ),
            store: store,
            launcher: launcher,
            nowMilliseconds: { 1_000 },
            eventID: { "payment-event-1" }
        )
        _ = coordinator.open(
            OpenPaymentRequest(
                rawURL: "https://sbp.example.invalid/qr",
                recoveryContext: "ctx",
                serverExpiresAtEpochMilliseconds: 10_000,
                userInitiated: true
            )
        )
        let router = IOSPaymentReturnRouter(coordinator: coordinator)
        var notifications = 0
        _ = router.attach { notifications += 1 }

        router.sceneBecameActive()
        router.sceneLeftForeground()
        router.sceneBecameActive()
        router.sceneBecameActive()

        XCTAssertEqual(launcher.launchCount, 1)
        XCTAssertEqual(notifications, 2)
        XCTAssertEqual(coordinator.pendingEvent()?.eventID, "payment-event-1")
    }

    func testColdStartWithoutRecoveryDoesNothing() {
        let coordinator = PaymentCoordinator(
            policy: PaymentLaunchPolicy(rules: []),
            store: RouterMemoryStore(),
            launcher: RouterLauncher()
        )
        let router = IOSPaymentReturnRouter(coordinator: coordinator)
        var notifications = 0
        _ = router.attach { notifications += 1 }

        router.sceneBecameActive()

        XCTAssertEqual(notifications, 0)
        XCTAssertNil(coordinator.pendingEvent())
    }

    func testActiveRecoveryWriteFailureEmitsSanitizedTelemetry() throws {
        let store = RouterMemoryStore()
        let coordinator = PaymentCoordinator(
            policy: PaymentLaunchPolicy(
                rules: [
                    PaymentProviderRule(
                        flow: .sbpCyclops,
                        exactHost: "sbp.example.invalid",
                        pathPrefix: "/qr"
                    ),
                ]
            ),
            store: store,
            launcher: RouterLauncher(),
            nowMilliseconds: { 1_000 },
            eventID: { "payment-event-1" }
        )
        _ = coordinator.open(
            OpenPaymentRequest(
                rawURL: "https://sbp.example.invalid/qr",
                recoveryContext: "ctx",
                serverExpiresAtEpochMilliseconds: 10_000,
                userInitiated: true
            )
        )
        store.rejectWrites = true
        let sink = RouterRecordingTelemetrySink()
        let reporter = TelemetryReporter(
            sanitizer: TelemetrySanitizer(
                trustedOrigin: URL(string: "https://foodhome.market")!
            ),
            sink: sink
        )

        IOSPaymentReturnRouter(coordinator: coordinator, telemetry: reporter)
            .sceneBecameActive()

        let event = try XCTUnwrap(sink.events.first)
        XCTAssertEqual(event.name, .paymentReturnFailed)
        XCTAssertEqual(event.attributes[.errorCode], "PAYMENT_RETURN_RECORD_FAILED")
    }
}

private final class RouterMemoryStore: PaymentRecoveryStoring {
    var current: PaymentRecoverySnapshot?
    var rejectWrites = false
    func read() -> PaymentRecoverySnapshot? { current }
    func write(_ snapshot: PaymentRecoverySnapshot) -> Bool {
        if rejectWrites { return false }
        current = snapshot
        return true
    }
    func clear() -> Bool {
        current = nil
        return true
    }
}

@MainActor
private final class RouterLauncher: PaymentLaunching {
    var launchCount = 0
    func launch(_ destination: ValidatedPaymentDestination) -> Bool {
        launchCount += 1
        return true
    }
}

private final class RouterRecordingTelemetrySink: TelemetrySink {
    private(set) var events: [TelemetryEvent] = []
    func record(_ event: TelemetryEvent) {
        events.append(event)
    }
}
