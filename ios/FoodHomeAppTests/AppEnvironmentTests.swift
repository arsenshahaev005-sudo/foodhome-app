import XCTest
@testable import FoodHomeApp

final class AppEnvironmentTests: XCTestCase {
    func testReleaseIgnoresDebugOverride() {
        let environment = AppEnvironmentResolver.resolve(
            isDebug: false,
            debugBaseURL: "https://localhost:8443"
        )

        XCTAssertEqual(environment.baseURL.absoluteString, "https://foodhome.market/")
        XCTAssertEqual(environment.trustedOrigin.absoluteString, "https://foodhome.market")
    }

    func testDebugAcceptsOnlyLocalHTTPSOrigin() {
        let local = AppEnvironmentResolver.resolve(
            isDebug: true,
            debugBaseURL: "https://localhost:8443"
        )
        let remote = AppEnvironmentResolver.resolve(
            isDebug: true,
            debugBaseURL: "https://evil.example"
        )
        let cleartext = AppEnvironmentResolver.resolve(
            isDebug: true,
            debugBaseURL: "http://localhost:3000"
        )

        XCTAssertEqual(local.trustedOrigin.absoluteString, "https://localhost:8443")
        XCTAssertEqual(remote.trustedOrigin, AppEnvironmentResolver.productionOrigin)
        XCTAssertEqual(cleartext.trustedOrigin, AppEnvironmentResolver.productionOrigin)
    }
}
