import XCTest
@testable import FoodHomeApp

final class PaymentLaunchPolicyTests: XCTestCase {
    private let policy = PaymentLaunchPolicy(
        rules: [
            PaymentProviderRule(
                flow: .cardAcquiring,
                exactHost: "pay.example.invalid",
                pathPrefix: "/checkout",
                allowedQueryKeys: ["session"]
            ),
            PaymentProviderRule(
                flow: .sbpCyclops,
                exactHost: "sbp.example.invalid",
                pathPrefix: "/qr"
            ),
        ]
    )

    func testAcceptsOnlyExactInjectedHTTPSDestination() {
        let destination = policy.classify(
            "https://pay.example.invalid:443/checkout/start?session=opaque"
        )
        XCTAssertEqual(destination?.flow, .cardAcquiring)
    }

    func testRejectsHostPortCredentialsAndPathMismatches() {
        XCTAssertNil(policy.classify("https://child.pay.example.invalid/checkout"))
        XCTAssertNil(policy.classify("https://pay.example.invalid:444/checkout"))
        XCTAssertNil(policy.classify("https://user@pay.example.invalid/checkout"))
        XCTAssertNil(policy.classify("https://pay.example.invalid/checkout-evil"))
        XCTAssertNil(policy.classify("https://unknown.example.invalid/checkout"))
    }

    func testRejectsEncodedWholeURLControlCharactersAndOverlongValues() {
        XCTAssertNil(policy.classify("https%3A%2F%2Fpay.example.invalid%2Fcheckout"))
        XCTAssertNil(policy.classify("https://pay.example.invalid/checkout?%73ession=opaque"))
        XCTAssertNil(policy.classify("https://pay.example.invalid/checkout\n"))
        XCTAssertNil(policy.classify("https://pay.example.invalid/checkout/\(String(repeating: "x", count: 2_100))"))
    }

    func testRejectsUnexpectedQueryAndFragment() {
        XCTAssertNil(policy.classify("https://pay.example.invalid/checkout?redirect=https://evil.invalid"))
        XCTAssertNil(
            policy.classify(
                "https://pay.example.invalid/checkout?session=https%253A%252F%252Fevil.invalid"
            )
        )
        XCTAssertNil(policy.classify("https://pay.example.invalid/checkout#return"))
        XCTAssertNotNil(policy.classify("https://sbp.example.invalid/qr"))
    }

    func testRejectsAmbiguousHostPathAndUnicodeForms() {
        for url in [
            " https://pay.example.invalid/checkout",
            "https://pay.example.invalid./checkout",
            "https://pay.example.invalid\\@evil.invalid/checkout",
            "https://pay.example.invalid/checkout/%2Fescape",
            "https://pay.example.invalid/checkout/%2e%2e/escape",
            "https://pay.example.invalid/checkout//escape",
            "https://pay.example.invalid/checkout/\u{202E}evil",
        ] {
            XCTAssertNil(policy.classify(url))
        }
    }

    func testProductionPolicyTrustsNoProvisionalDestination() {
        XCTAssertNil(PaymentLaunchPolicy.production.classify("https://pay.example.invalid/checkout"))
    }
}
