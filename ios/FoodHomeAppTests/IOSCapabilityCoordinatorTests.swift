import XCTest
import CoreLocation
import UIKit
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

    func testFirstPermissionGrantUsesFreshPreciseLocationWithoutWaitingForAnotherUpdate() {
        let now = Date()
        let manager = LocationManagerFixture()
        manager.cachedFix = location(at: now, accuracy: 16)
        let provider = IOSLocationProvider(manager: manager, now: { now })
        var result: LocationRequestResult?
        provider.requestCurrentLocation { result = $0 }
        XCTAssertEqual(manager.permissionRequests, 1)
        XCTAssertEqual(manager.cacheReads, 0)
        XCTAssertEqual(manager.starts, 0)
        manager.permission = .authorizedWhenInUse
        provider.locationManagerDidChangeAuthorization(manager)
        guard case let .granted(latitude, longitude, accuracy, precise) = result else {
            return XCTFail("Expected the fresh fix after the system grants access")
        }
        XCTAssertEqual(latitude, 51.5)
        XCTAssertEqual(longitude, 46)
        XCTAssertEqual(accuracy, 16)
        XCTAssertTrue(precise)
        XCTAssertEqual(manager.starts, 0)
    }

    func testStaleOrImpreciseCachedFixWaitsForFreshLocationAndCancelDiscardsLateUpdates() {
        let now = Date()
        let manager = LocationManagerFixture()
        manager.permission = .authorizedWhenInUse
        let provider = IOSLocationProvider(manager: manager, now: { now })
        for fix in [location(at: now.addingTimeInterval(-30), accuracy: 8), location(at: now, accuracy: 750)] {
            manager.cachedFix = fix
            var results: [LocationRequestResult] = []
            let starts = manager.starts
            provider.requestCurrentLocation { results.append($0) }
            provider.locationManagerDidChangeAuthorization(manager)
            XCTAssertEqual(manager.starts, starts + 1)
            XCTAssertTrue(results.isEmpty)
            provider.cancel()
            provider.locationManager(manager, didUpdateLocations: [location(at: now, accuracy: 12)])
            XCTAssertEqual(results.count, 1)
            guard case .failed(code: "CANCELLED", message: _, retryable: _) = results.first else {
                return XCTFail("Cancelled acquisition must not publish a late position")
            }
        }
    }

    func testReducedPermissionNeverReusesAPreciseCachedCoordinate() {
        let now = Date()
        let manager = LocationManagerFixture()
        manager.permission = .authorizedWhenInUse
        manager.precision = .reducedAccuracy
        manager.cachedFix = location(at: now, accuracy: 8)
        let provider = IOSLocationProvider(manager: manager, now: { now })
        var result: LocationRequestResult?
        provider.requestCurrentLocation { result = $0 }
        XCTAssertNil(result)
        XCTAssertEqual(manager.cacheReads, 0)
        XCTAssertEqual(manager.starts, 1)
        provider.locationManager(manager, didUpdateLocations: [location(at: now, accuracy: 2_000)])
        guard case let .granted(_, _, accuracy, precise) = result else {
            return XCTFail("Expected the approximate result for manual selection")
        }
        XCTAssertEqual(accuracy, 2_000)
        XCTAssertFalse(precise)
    }

    func testDeadlineRejectsAPartialFixThatHasAgedOut() async {
        var now = Date()
        let manager = LocationManagerFixture()
        manager.permission = .authorizedWhenInUse
        let provider = IOSLocationProvider(manager: manager, timeout: 0.02, now: { now })
        let finished = expectation(description: "bounded acquisition")
        var result: LocationRequestResult?
        provider.requestCurrentLocation { result = $0; finished.fulfill() }
        provider.locationManager(manager, didUpdateLocations: [location(at: now.addingTimeInterval(-48), accuracy: 700)])
        now = now.addingTimeInterval(15)
        await fulfillment(of: [finished], timeout: 2)
        guard case .failed(code: "TIMEOUT", message: _, retryable: true) = result else {
            return XCTFail("Do not grant a stale location at the deadline")
        }
    }

    private func location(at timestamp: Date, accuracy: CLLocationAccuracy) -> CLLocation {
        CLLocation(coordinate: CLLocationCoordinate2D(latitude: 51.5, longitude: 46), altitude: 0,
                   horizontalAccuracy: accuracy, verticalAccuracy: -1, timestamp: timestamp)
    }

    func testForegroundEntryCanPromptWithoutATouchButCannotOpenSettings() throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive })
        let controller = UIViewController()
        let window = UIWindow(windowScene: scene)
        window.rootViewController = controller
        window.isHidden = false
        defer { window.isHidden = true }
        let permissions = DelayedNotificationPermissions()
        var now = Date()
        let methods: Set<String> = ["requestNotificationPermission", "openNotificationSettings"]
        let coordinator = IOSCapabilityCoordinator(
            manifest: manifest(compiledCapabilities: methods, methods: methods, rateLimits: [
                "requestNotificationPermission": .init(maxRequests: 1, windowSeconds: 30),
                "openNotificationSettings": .init(maxRequests: 1, windowSeconds: 30),
            ]),
            trustedOrigin: try XCTUnwrap(URL(string: "https://foodhome.market")),
            hasRecentAuthUserAction: { false }, now: { now }, notificationCoordinator: permissions
        )
        coordinator.presenterProvider = { controller }
        var responses: [BridgeDispatchResult] = []
        func dispatch(_ method: String) {
            let request = BridgeRequest(requestID: UUID().uuidString, method: method, payload: [:])
            coordinator.dispatch(request) { responses.append($0) }
        }
        dispatch("requestNotificationPermission")
        XCTAssertEqual(permissions.statusReads, 1)
        permissions.returnStatus(.notDetermined)
        XCTAssertEqual(permissions.prompts, 1)
        // A second request arriving after the rate window still cannot start a second system alert.
        now = now.addingTimeInterval(31)
        dispatch("requestNotificationPermission")
        guard case .failure(code: "RATE_LIMITED", message: _, retryable: _) = responses.last else {
            return XCTFail("Expected a single pending authorization")
        }
        permissions.answerPrompt(.denied)
        guard case let .success(payload) = responses.last else { return XCTFail("Expected the OS response") }
        XCTAssertEqual(payload["status"] as? String, "denied")
        now = now.addingTimeInterval(31)
        dispatch("requestNotificationPermission")
        permissions.returnStatus(.denied)
        XCTAssertEqual(permissions.prompts, 1)
        dispatch("openNotificationSettings")
        guard case .failure(code: "CANCELLED", message: _, retryable: _) = responses.last else {
            return XCTFail("Settings still require an explicit foreground action")
        }
        now = now.addingTimeInterval(31)
        dispatch("requestNotificationPermission")
        coordinator.cancelPending()
        permissions.returnStatus(.notDetermined)
        XCTAssertEqual(permissions.prompts, 1, "An obsolete document cannot prompt")
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
        let stage = SberAuthPolicy(trustedOrigin: FoodHomeDeployment.staging.origin)
        let stageAuth = auth.replacingOccurrences(of: "foodhome.market", with: "staging.foodhome.market")
        let stageCallback = try XCTUnwrap(URL(string: "market.foodhome.app.staging.auth://sber/return#\(fragment)"))
        XCTAssertEqual(stage.callbackScheme, "market.foodhome.app.staging.auth")
        XCTAssertNotNil(stage.authorizationURL(attemptID: attempt, raw: stageAuth))
        XCTAssertNil(stage.authorizationURL(attemptID: attempt, raw: auth))
        XCTAssertNil(policy.authorizationURL(attemptID: attempt, raw: stageAuth))
        XCTAssertNil(stage.returnURL(callback: callback, attemptID: attempt))
        XCTAssertNil(policy.returnURL(callback: stageCallback, attemptID: attempt))
        XCTAssertEqual(stage.returnURL(callback: stageCallback, attemptID: attempt)?.absoluteString,
                       "https://staging.foodhome.market/auth/sber/app-return#" + fragment)
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

private final class LocationManagerFixture: CLLocationManager {
    var permission: CLAuthorizationStatus = .notDetermined
    var precision: CLAccuracyAuthorization = .fullAccuracy
    var cachedFix: CLLocation?
    private(set) var cacheReads = 0
    private(set) var permissionRequests = 0
    private(set) var starts = 0

    override var authorizationStatus: CLAuthorizationStatus { permission }
    override var accuracyAuthorization: CLAccuracyAuthorization { precision }
    override var location: CLLocation? { cacheReads += 1; return cachedFix }
    override func requestWhenInUseAuthorization() { permissionRequests += 1 }
    override func startUpdatingLocation() { starts += 1 }
    override func stopUpdatingLocation() {}
}

@MainActor
private final class DelayedNotificationPermissions: IOSNotificationPermissions {
    private(set) var statusReads = 0
    private(set) var prompts = 0
    var returnStatus: (NotificationAuthorizationStatus) -> Void = { _ in }
    var answerPrompt: (NotificationAuthorizationStatus) -> Void = { _ in }

    func authorizationStatus(completion: @escaping (NotificationAuthorizationStatus) -> Void) {
        statusReads += 1
        returnStatus = completion
    }

    func requestAuthorization(completion: @escaping (NotificationAuthorizationStatus) -> Void) {
        prompts += 1
        answerPrompt = completion
    }
}
