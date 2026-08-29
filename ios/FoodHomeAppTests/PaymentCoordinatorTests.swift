import XCTest
@testable import FoodHomeApp

@MainActor
final class PaymentCoordinatorTests: XCTestCase {
    private var now: Int64 = 10_000
    private var store = MemoryPaymentStore()
    private var launcher = TestPaymentLauncher()

    override func setUp() {
        super.setUp()
        now = 10_000
        store = MemoryPaymentStore()
        launcher = TestPaymentLauncher()
    }

    func testPersistencePrecedesLaunchAndExpiryIsCappedAtTwentyFourHours() {
        launcher.beforeLaunch = { [store] in XCTAssertFalse(store.writes.isEmpty) }
        let coordinator = makeCoordinator()

        let result = coordinator.open(request(serverExpiry: .max))

        XCTAssertEqual(result, .presented(.cardAcquiring))
        XCTAssertEqual(launcher.launchCount, 1)
        XCTAssertEqual(store.writes.count, 2)
        XCTAssertEqual(
            store.current?.expiresAtEpochMilliseconds,
            now + PaymentRecoverySnapshot.localMaximumLifetimeMilliseconds
        )
    }

    func testDuplicateContextIsIdempotentAndOtherContextIsBlocked() {
        let coordinator = makeCoordinator()
        _ = coordinator.open(request())

        XCTAssertEqual(coordinator.open(request()), .presented(.cardAcquiring))
        XCTAssertEqual(
            coordinator.open(request(context: "another-context")),
            .failure(code: "PAYMENT_IN_PROGRESS")
        )
        XCTAssertEqual(launcher.launchCount, 1)
    }

    func testReturnEventSurvivesDeliveryUntilIdempotentAckAndRecoveryRemains() {
        let coordinator = makeCoordinator()
        _ = coordinator.open(request())
        let event = coordinator.recordReturn(reason: "appResumed")

        XCTAssertEqual(event, coordinator.pendingEvent())
        XCTAssertEqual(event, coordinator.pendingEvent())
        XCTAssertEqual(
            coordinator.acknowledge(eventID: "payment-event-1"),
            .acknowledged(eventID: "payment-event-1")
        )
        XCTAssertEqual(
            coordinator.acknowledge(eventID: "payment-event-1"),
            .acknowledged(eventID: "payment-event-1")
        )
        XCTAssertNil(coordinator.pendingEvent())
        XCTAssertNil(coordinator.recordReturn(reason: "appResumed"))
        XCTAssertEqual(store.current?.state, .eventAcknowledged)
    }

    func testExplicitClearRequiresMatchingOpaqueContext() {
        let coordinator = makeCoordinator()
        _ = coordinator.open(request())

        XCTAssertEqual(
            coordinator.clear(recoveryContext: "wrong", reason: "terminal"),
            .failure(code: "PAYMENT_RECOVERY_NOT_FOUND")
        )
        XCTAssertEqual(
            coordinator.clear(recoveryContext: "opaque-context", reason: "terminal"),
            .cleared
        )
        XCTAssertNil(store.current)
    }

    func testLaunchFailureIsRecoverableAndNeverAutomaticallyRetried() {
        launcher.shouldLaunch = false
        let coordinator = makeCoordinator()

        XCTAssertEqual(coordinator.open(request()), .failure(code: "LAUNCH_FAILED"))
        XCTAssertEqual(store.current?.state, .launchFailed)
        XCTAssertEqual(coordinator.open(request()), .failure(code: "PAYMENT_IN_PROGRESS"))
        XCTAssertEqual(launcher.launchCount, 1)
    }

    func testOpenPaymentRequiresExplicitUserAction() {
        let coordinator = makeCoordinator()

        XCTAssertEqual(
            coordinator.open(request(userInitiated: false)),
            .failure(code: "PAYMENT_USER_ACTION_REQUIRED")
        )
        XCTAssertEqual(launcher.launchCount, 0)
        XCTAssertTrue(store.writes.isEmpty)
    }

    private func makeCoordinator() -> PaymentCoordinator {
        PaymentCoordinator(
            policy: PaymentLaunchPolicy(
                rules: [
                    PaymentProviderRule(
                        flow: .cardAcquiring,
                        exactHost: "pay.example.invalid",
                        pathPrefix: "/checkout",
                        allowedQueryKeys: ["session"]
                    ),
                ]
            ),
            store: store,
            launcher: launcher,
            nowMilliseconds: { [unowned self] in now },
            eventID: { "payment-event-1" }
        )
    }

    private func request(
        context: String = "opaque-context",
        serverExpiry: Int64? = nil,
        userInitiated: Bool = true
    ) -> OpenPaymentRequest {
        OpenPaymentRequest(
            rawURL: "https://pay.example.invalid/checkout?session=opaque",
            recoveryContext: context,
            serverExpiresAtEpochMilliseconds: serverExpiry ?? now + 60_000,
            userInitiated: userInitiated
        )
    }
}

private final class MemoryPaymentStore: PaymentRecoveryStoring {
    var current: PaymentRecoverySnapshot?
    var writes: [PaymentRecoverySnapshot] = []
    func read() -> PaymentRecoverySnapshot? { current }
    func write(_ snapshot: PaymentRecoverySnapshot) -> Bool {
        writes.append(snapshot)
        current = snapshot
        return true
    }
    func clear() -> Bool {
        current = nil
        return true
    }
}

@MainActor
private final class TestPaymentLauncher: PaymentLaunching {
    var launchCount = 0
    var shouldLaunch = true
    var beforeLaunch: (() -> Void)?
    func launch(_ destination: ValidatedPaymentDestination) -> Bool {
        beforeLaunch?()
        launchCount += 1
        return shouldLaunch
    }
}
