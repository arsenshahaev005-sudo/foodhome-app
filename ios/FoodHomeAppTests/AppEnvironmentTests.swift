import XCTest
@testable import FoodHomeApp

final class AppEnvironmentTests: XCTestCase {
    func testProductionAndStagingRequireTheirOwnBundleAndHost() throws {
        for deployment in FoodHomeDeployment.allCases {
            let other: FoodHomeDeployment = deployment == .production ? .staging : .production
            for bundleID in [deployment.appBundleID, deployment.appBundleID + ".OrderStatus"] {
                XCTAssertEqual(FoodHomeDeployment.resolve(bundleID: bundleID, webHost: deployment.host), deployment)
                XCTAssertNil(FoodHomeDeployment.resolve(bundleID: bundleID, webHost: other.host))
                XCTAssertNil(FoodHomeDeployment.resolve(bundleID: bundleID, webHost: deployment.host + ".invalid"))
            }
        }
        XCTAssertNil(FoodHomeDeployment.resolve(bundleID: "market.foodhome.app.preview", webHost: "foodhome.market"))
        XCTAssertNil(FoodHomeDeployment.resolve(bundleID: nil, webHost: nil))
        for debug in [false, true] {
            for override in ["https://foodhome.market", "https://localhost:8443", "https://other.example"] {
                let site = AppEnvironmentResolver.resolve(isDebug: debug, debugBaseURL: override, deployment: .staging)
                XCTAssertEqual(site.baseURL.absoluteString, "https://staging.foodhome.market/")
                XCTAssertEqual(site.trustedOrigin, FoodHomeDeployment.staging.origin)
            }
        }
    }

    func testBuiltApplicationAndEmbeddedWidgetShareThisDeployment() throws {
        let app = Bundle.main
        let deployment = FoodHomeDeployment.current
        XCTAssertEqual(app.bundleIdentifier, deployment.appBundleID)
        XCTAssertEqual(AppEnvironmentResolver.current().trustedOrigin, deployment.origin)
        XCTAssertEqual(app.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String,
                       deployment == .staging ? "Food&Home Staging" : "Food&Home")
        let types = try XCTUnwrap(app.object(forInfoDictionaryKey: "CFBundleURLTypes") as? [[String: Any]])
        XCTAssertEqual(types.count, 1)
        XCTAssertEqual(types.first?["CFBundleURLSchemes"] as? [String], [deployment.callbackScheme])
        let plugins = try XCTUnwrap(app.builtInPlugInsURL)
        let widget = try XCTUnwrap(Bundle(url: plugins.appendingPathComponent("FoodHomeOrderStatus.appex")))
        XCTAssertEqual(widget.bundleIdentifier, deployment.appBundleID + ".OrderStatus")
        XCTAssertEqual(FoodHomeDeployment.resolve(bundleID: widget.bundleIdentifier,
                                                webHost: widget.object(forInfoDictionaryKey: "FoodHomeWebHost") as? String),
                       deployment)
    }

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
