import XCTest
@testable import FoodHomeApp

final class PushLifecyclePolicyTests: XCTestCase {
    private let policy = PushPayloadPolicy(
        navigationPolicy: NavigationPolicy(
            trustedOrigin: URL(string: "https://foodhome.market")!
        )
    )

    func testMinimalPushPayloadResolvesOnlyTrustedRoute() {
        let parsed = policy.parse([
            "eventId": "order-123",
            "type": "order.updated",
            "route": "https://foodhome.market/orders/123",
        ])
        XCTAssertEqual(parsed?.route.absoluteString, "https://foodhome.market/orders/123")
        XCTAssertNil(
            policy.parse([
                "eventId": "x",
                "route": "https://evil.example/orders/1",
            ])
        )
        XCTAssertNil(
            policy.parse([
                "eventId": "x",
                "route": "https://foodhome.market/orders/1",
                "message": "sensitive",
            ])
        )
    }

    func testTokenDescriptionNeverContainsRawValue() throws {
        let raw = Data("raw-provider-token".utf8)
        let token = try XCTUnwrap(SensitivePushToken(raw))

        XCTAssertFalse(token.description.contains("raw-provider-token"))
        XCTAssertEqual(token.fingerprint.count, 12)
        XCTAssertEqual(token.use { $0 }, raw)
    }
}
