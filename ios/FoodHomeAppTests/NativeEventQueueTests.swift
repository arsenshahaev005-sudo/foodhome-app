import XCTest
@testable import FoodHomeApp

@MainActor
final class NativeEventQueueTests: XCTestCase {
    func testScriptIsStableUntilAckAndNeverClaimsPaymentStatus() {
        let store = MemoryQueueStore()
        let coordinator = PaymentCoordinator(
            policy: PaymentLaunchPolicy(
                rules: [
                    PaymentProviderRule(
                        flow: .cardAcquiring,
                        exactHost: "pay.example.invalid",
                        pathPrefix: "/checkout"
                    ),
                ]
            ),
            store: store,
            launcher: QueueLauncher(),
            nowMilliseconds: { 10_000 },
            eventID: { "payment-event-1" }
        )
        _ = coordinator.open(
            OpenPaymentRequest(
                rawURL: "https://pay.example.invalid/checkout",
                recoveryContext: "opaque-context",
                serverExpiresAtEpochMilliseconds: 20_000,
                userInitiated: true
            )
        )
        _ = coordinator.recordReturn(reason: "appResumed")
        let queue = NativeEventQueue(
            manifest: manifest(),
            trustedOrigin: URL(string: "https://foodhome.market")!,
            paymentCoordinator: coordinator
        )

        let first = queue.pendingDispatchScript()
        XCTAssertEqual(first, queue.pendingDispatchScript())
        XCTAssertTrue(first?.contains("foodhome:native-event") == true)
        XCTAssertTrue(first?.contains("payment-event-1") == true)
        XCTAssertFalse(first?.contains("paid") == true)
        _ = coordinator.acknowledge(eventID: "payment-event-1")
        XCTAssertNil(queue.pendingDispatchScript())
        XCTAssertNotNil(store.current)
    }

    private func manifest() -> BridgeManifest {
        BridgeManifest(
            protocolName: "foodhome.bridge",
            contractVersion: "1.3.0",
            bridgeMajor: 1,
            supportedVersions: [1],
            globalObjectName: "FoodHomeBridge",
            handshake: .init(transport: "dom-event", eventName: "foodhome:bridge-ready"),
            nativeEvents: .init(transport: "dom-event", eventName: "foodhome:native-event"),
            nativeMode: .init(
                protocolName: "foodhome.native-mode",
                version: 1,
                globalObjectName: "FoodHomeNative",
                userAgentProduct: "FoodHomeNative/1",
                securityBoundary: false,
                documentStart: .init(trustedOriginOnly: true, mainFrameOnly: true)
            ),
            limits: .init(
                maxMessageBytes: 32_768,
                requestTimeoutMs: 10_000,
                maxRequestIdLength: 64,
                maxUrlLength: 2_048
            ),
            methods: ["openPayment", "ackNativeEvent"],
            phase0Capabilities: [],
            compiledCapabilities: [],
            builtInCapabilities: ["openPayment"],
            advertisedCapabilities: []
        )
    }
}

private final class MemoryQueueStore: PaymentRecoveryStoring {
    var current: PaymentRecoverySnapshot?
    func read() -> PaymentRecoverySnapshot? { current }
    func write(_ snapshot: PaymentRecoverySnapshot) -> Bool {
        current = snapshot
        return true
    }
    func clear() -> Bool {
        current = nil
        return true
    }
}

@MainActor
private final class QueueLauncher: PaymentLaunching {
    func launch(_ destination: ValidatedPaymentDestination) -> Bool { true }
}
