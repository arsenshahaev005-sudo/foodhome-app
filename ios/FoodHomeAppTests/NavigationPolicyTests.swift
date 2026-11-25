import XCTest
@testable import FoodHomeApp

final class NavigationPolicyTests: XCTestCase {
    func testStagingAppKeepsProductionOrdersAndPaymentsOutsideItsNavigation() throws {
        let policy = NavigationPolicy(trustedOrigin: FoodHomeDeployment.staging.origin)
        let checkout = try XCTUnwrap(URL(string: "https://staging.foodhome.market/checkout"))
        XCTAssertEqual(policy.classify(checkout.absoluteString), .internal(checkout))
        for url in ["https://foodhome.market/checkout", "https://api.foodhome.market/auth", "https://www.foodhome.market/orders"] {
            XCTAssertEqual(policy.classify(url), .blocked(.productionHostInStaging))
        }
        let provider = try XCTUnwrap(URL(string: "https://id.sber.ru/"))
        XCTAssertEqual(policy.classify(provider.absoluteString), .external(provider))
        let stageBridge = BridgeOriginPolicy(trustedOrigin: FoodHomeDeployment.staging.origin)
        XCTAssertFalse(stageBridge.accepts(frameURL: FoodHomeDeployment.production.origin, isMainFrame: true))
        XCTAssertFalse(stageBridge.accepts(frameURL: checkout, isMainFrame: false))
        XCTAssertTrue(stageBridge.accepts(frameURL: checkout, isMainFrame: true))
        let productionBridge = BridgeOriginPolicy(trustedOrigin: FoodHomeDeployment.production.origin)
        XCTAssertFalse(productionBridge.accepts(frameURL: checkout, isMainFrame: true))
    }

    private let policy = NavigationPolicy(
        trustedOrigin: URL(string: "https://foodhome.market")!
    )

    func testExactOriginIsInternal() {
        guard case .internal = policy.classify("https://foodhome.market/orders/123") else {
            return XCTFail("Expected internal navigation")
        }
    }

    func testOrdinaryExternalHTTPSIsExternal() {
        guard case .external = policy.classify("https://example.com/help") else {
            return XCTFail("Expected external navigation")
        }
    }

    func testLookalikesNeverBecomeInternal() {
        guard case .external = policy.classify("https://cdn.foodhome.market/image") else {
            return XCTFail("Subdomain must be external")
        }
        guard case .external = policy.classify("https://foodhome.market.evil.example/") else {
            return XCTFail("Lookalike must be external")
        }
    }

    func testForbiddenSchemesFailClosed() {
        for url in [
            "javascript:alert(1)",
            "data:text/html,hello",
            "file:///tmp/secret",
            "foodhome://orders/123",
        ] {
            XCTAssertEqual(policy.classify(url), .blocked(.forbiddenScheme))
        }
    }

    func testUserInfoAndUnexpectedPortFailClosed() {
        XCTAssertEqual(
            policy.classify("https://foodhome.market@evil.example/"),
            .blocked(.userInfo)
        )
        XCTAssertEqual(
            policy.classify("https://foodhome.market:444/orders"),
            .blocked(.unexpectedPort)
        )
    }

    func testEncodedNestedDestinationFailsClosed() {
        for url in [
            "https://foodhome.market/?next=https%253A%252F%252Fevil.example",
            "https://foodhome.market/?next=https%25253A%25252F%25252Fevil.example",
            "https://foodhome.market/#next=javascript%253Aalert(1)",
            "https://foodhome.market/?next=intent%253A%252F%252Fbank",
        ] {
            XCTAssertEqual(policy.classify(url), .blocked(.nestedURL))
        }
    }

    func testAmbiguousRawCharactersFailClosed() {
        for url in [
            " https://foodhome.market/orders",
            "https://foodhome.market/orders ",
            "https://foodhome.market\\@evil.example/orders",
            "https://foodhome.market/\u{202E}evil",
            "https://foodhome.market/\u{0000}evil",
        ] {
            XCTAssertEqual(policy.classify(url), .blocked(.malformed))
        }
    }

    func testTrailingDotHostFailsClosed() {
        XCTAssertEqual(
            policy.classify("https://foodhome.market./orders"),
            .blocked(.invalidHost)
        )
    }

    func testIDNALookalikeRemainsExternal() {
        guard case .external = policy.classify("https://xn--foodhme-8fg.example/orders") else {
            return XCTFail("IDNA lookalike must remain external")
        }
    }

    func testCaseNormalizationPreservesValidExactOrigin() {
        guard case .internal = policy.classify("HTTPS://FOODHOME.MARKET/orders/123") else {
            return XCTFail("Expected normalized exact origin")
        }
    }
}
