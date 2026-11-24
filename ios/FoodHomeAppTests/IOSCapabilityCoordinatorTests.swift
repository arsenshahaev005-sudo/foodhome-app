import XCTest
import CoreLocation
@testable import FoodHomeApp

@MainActor
final class IOSCapabilityCoordinatorTests: XCTestCase {
    func testCapabilityNotCompiledFailsClosedWithoutPresentingUI() {
        let coordinator = IOSCapabilityCoordinator(
            manifest: manifest(compiledCapabilities: []),
            trustedOrigin: URL(string: "https://foodhome.market")!
        )
        var result: BridgeDispatchResult?

        coordinator.dispatch(
            BridgeRequest(
                requestID: "share-1",
                method: "share",
                payload: ["url": "https://foodhome.market/cart"]
            )
        ) { result = $0 }

        guard case let .failure(code, _, retryable) = result else {
            return XCTFail("Expected a typed failure")
        }
        XCTAssertEqual(code, "CAPABILITY_UNAVAILABLE")
        XCTAssertFalse(retryable)
    }

    func testBundledManifestBuildsPaymentButDoesNotAdvertiseIt() throws {
        let value = try XCTUnwrap(BridgeManifest.load())
        XCTAssertEqual(value.contractVersion, "1.14.0")
        XCTAssertEqual(value.limits.maxJsonDepth, 12)
        XCTAssertEqual(value.limits.maxJsonNodes, 512)
        XCTAssertEqual(
            value.rateLimits["requestNotificationPermission"],
            .init(maxRequests: 1, windowSeconds: 30)
        )
        XCTAssertEqual(
            value.compiledCapabilities,
            [
                "share",
                "startSberAuth",
                "managePush", "openNotificationSettings", "manageOrderActivity",
                "requestLocation",
                "getNotificationStatus",
                "requestNotificationPermission",
            ]
        )
        XCTAssertFalse(value.compiledCapabilities.contains("openPayment"))
        XCTAssertTrue(value.builtInCapabilities.contains("openPayment"))
        XCTAssertFalse(value.advertisedCapabilities.contains("openPayment"))
        XCTAssertTrue(value.advertisedCapabilities.contains("startSberAuth"))
    }

    func testLocationChoosesFreshAccurateFix() {
        let now = Date()
        func fix(accuracy: Double, age: TimeInterval) -> CLLocation {
            CLLocation(coordinate: CLLocationCoordinate2D(latitude: 51.5, longitude: 46),
                       altitude: 0, horizontalAccuracy: accuracy, verticalAccuracy: -1,
                       timestamp: now.addingTimeInterval(-age))
        }
        let fixes = [fix(accuracy: 3, age: 3600), fix(accuracy: -1, age: 1),
                     fix(accuracy: 12, age: 4), fix(accuracy: 240, age: 1)]
        XCTAssertEqual(IOSLocationProvider.bestRecentFix(in: fixes, now: now)?.horizontalAccuracy, 12)
        XCTAssertNil(IOSLocationProvider.bestRecentFix(in: [fix(accuracy: -1, age: 0)], now: now))
    }

    func testSberReturnsOnlyCurrentAttemptToOriginalWebView() throws {
        let origin = "https://foodhome.market"
        let policy = SberAuthPolicy(trustedOrigin: try XCTUnwrap(URL(string: origin)))
        let attempt = UUID().uuidString.lowercased()
        let token = String(repeating: "a", count: 43)
        let fields = "response_type=code&client_id=test&redirect_uri=https%3A%2F%2Ffoodhome.market%2Fauth%2Fsber%2Fcallback&scope=openid+mobile&state=\(token)&nonce=\(token)&login_hint=70000000000&code_challenge=\(token)&code_challenge_method=S256"
        let auth = "https://id.sber.ru/CSAFront/oidc/authorize.do?\(fields)"
        XCTAssertNotNil(policy.authorizationURL(attemptID: attempt, raw: auth))
        XCTAssertNil(policy.authorizationURL(attemptID: attempt, raw: auth + "&state=another"))
        XCTAssertNil(policy.authorizationURL(attemptID: attempt, raw: auth.replacingOccurrences(of: "id.sber.ru", with: "example.org")))
        let fragment = "attempt_id=\(attempt)&ticket=\(token)"
        let callback = try XCTUnwrap(URL(string: "market.foodhome.app.auth://sber/return#\(fragment)"))
        XCTAssertEqual(policy.returnURL(callback: callback, attemptID: attempt)?.absoluteString,
                       origin + "/auth/sber/app-return#" + fragment)
        XCTAssertNil(policy.returnURL(callback: callback, attemptID: UUID().uuidString.lowercased()))
    }

    func testSensitiveRateLimitComesFromManifestAndExpiresByInjectedClock() {
        var now = Date(timeIntervalSince1970: 0)
        let value = manifest(
            compiledCapabilities: ["requestNotificationPermission"],
            methods: ["requestNotificationPermission"],
            rateLimits: [
                "requestNotificationPermission": .init(maxRequests: 1, windowSeconds: 2),
            ]
        )
        let coordinator = IOSCapabilityCoordinator(
            manifest: value,
            trustedOrigin: URL(string: "https://foodhome.market")!,
            now: { now }
        )
        let request = BridgeRequest(
            requestID: "notification-1",
            method: "requestNotificationPermission",
            payload: [:]
        )
        var results: [BridgeDispatchResult] = []

        coordinator.dispatch(request) { results.append($0) }
        coordinator.dispatch(request) { results.append($0) }
        now = Date(timeIntervalSince1970: 2)
        coordinator.dispatch(request) { results.append($0) }

        guard case let .failure(firstCode, _, _) = results[0],
              case let .failure(secondCode, _, _) = results[1],
              case let .failure(thirdCode, _, _) = results[2]
        else {
            return XCTFail("Expected typed failures without a UI presenter")
        }
        XCTAssertEqual(firstCode, "CAPABILITY_UNAVAILABLE")
        XCTAssertEqual(secondCode, "RATE_LIMITED")
        XCTAssertEqual(thirdCode, "CAPABILITY_UNAVAILABLE")
    }

    func testMissingSensitiveRateLimitFailsClosed() {
        let value = manifest(
            compiledCapabilities: ["requestNotificationPermission"],
            methods: ["requestNotificationPermission"],
            rateLimits: [:]
        )
        let coordinator = IOSCapabilityCoordinator(
            manifest: value,
            trustedOrigin: URL(string: "https://foodhome.market")!
        )
        var result: BridgeDispatchResult?

        coordinator.dispatch(
            BridgeRequest(
                requestID: "notification-missing-limit",
                method: "requestNotificationPermission",
                payload: [:]
            )
        ) { result = $0 }

        guard case let .failure(code, _, _) = result else {
            return XCTFail("Expected typed failure")
        }
        XCTAssertEqual(code, "CAPABILITY_UNAVAILABLE")
    }

    private func manifest(
        compiledCapabilities: Set<String>,
        methods: Set<String> = ["share"],
        rateLimits: [String: BridgeManifest.RateLimitRule] = [:]
    ) -> BridgeManifest {
        BridgeManifest(
            protocolName: "foodhome.bridge",
            contractVersion: "1.2.0",
            bridgeMajor: 1,
            supportedVersions: [1],
            globalObjectName: "FoodHomeBridge",
            handshake: .init(
                transport: "dom-event",
                eventName: "foodhome:bridge-ready"
            ),
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
            methods: methods,
            phase0Capabilities: [],
            compiledCapabilities: compiledCapabilities,
            rateLimits: rateLimits
        )
    }
}
