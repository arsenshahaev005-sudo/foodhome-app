import XCTest
@testable import FoodHomeApp

final class BridgeCapabilityPolicyTests: XCTestCase {
    private let origin = URL(string: "https://foodhome.market")!

    func testShareAcceptsOnlyBoundedExactFoodHomeHTTPSURL() {
        let policy = FoodHomeSharePolicy(trustedOrigin: origin)

        XCTAssertNotNil(
            policy.parse(
                title: "Food&Home",
                text: "Домашняя еда",
                rawURL: "https://foodhome.market/dishes/1"
            )
        )
        XCTAssertNil(policy.parse(title: nil, text: nil, rawURL: "http://foodhome.market/"))
        XCTAssertNil(policy.parse(title: nil, text: nil, rawURL: "https://cdn.foodhome.market/"))
        XCTAssertNil(
            policy.parse(title: nil, text: nil, rawURL: "https://user@foodhome.market/")
        )
        XCTAssertNil(
            policy.parse(
                title: String(repeating: "x", count: 121),
                text: nil,
                rawURL: "https://foodhome.market/"
            )
        )
    }

    func testPurposeIsExplicitAndBounded() {
        XCTAssertTrue(CapabilityPurposePolicy.accepts("Показать доставку рядом"))
        XCTAssertFalse(CapabilityPurposePolicy.accepts(" "))
        XCTAssertFalse(CapabilityPurposePolicy.accepts(String(repeating: "x", count: 161)))
    }

    func testRateLimitRulesArePositiveAndConservativelyBounded() {
        XCTAssertTrue(
            BridgeManifest.RateLimitRule(maxRequests: 3, windowSeconds: 60).isSafe
        )
        XCTAssertFalse(
            BridgeManifest.RateLimitRule(maxRequests: 0, windowSeconds: 60).isSafe
        )
        XCTAssertFalse(
            BridgeManifest.RateLimitRule(maxRequests: 11, windowSeconds: 60).isSafe
        )
        XCTAssertFalse(
            BridgeManifest.RateLimitRule(maxRequests: 3, windowSeconds: 3_601).isSafe
        )
    }

    func testPhaseFourPaymentControlPayloadsFailClosed() {
        let policy = BridgePayloadPolicy(trustedOrigin: origin, maxMessageBytes: 32_768)

        XCTAssertTrue(
            policy.accepts(
                method: "openPayment",
                payload: [
                    "url": "https://pay.example.invalid/checkout",
                    "recoveryContext": "opaque-context",
                    "expiresAt": "2026-08-29T12:00:00Z",
                ]
            )
        )
        XCTAssertTrue(
            policy.accepts(
                method: "ackNativeEvent",
                payload: ["eventId": "payment-event-1"]
            )
        )
        XCTAssertTrue(
            policy.accepts(
                method: "clearPaymentRecovery",
                payload: ["recoveryContext": "opaque-context", "reason": "terminal"]
            )
        )
        XCTAssertFalse(
            policy.accepts(
                method: "openPayment",
                payload: [
                    "url": "https://pay.example.invalid/checkout",
                    "recoveryContext": "opaque-context",
                ]
            )
        )
        XCTAssertFalse(
            policy.accepts(
                method: "clearPaymentRecovery",
                payload: ["recoveryContext": "opaque-context", "reason": "paid"]
            )
        )
    }
}
