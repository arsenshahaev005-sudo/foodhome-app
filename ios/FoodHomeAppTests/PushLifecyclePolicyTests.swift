import XCTest
@testable import FoodHomeApp

final class PushLifecyclePolicyTests: XCTestCase {
    private let id = "bd33e461-0841-4be4-81e7-6df818edc57c"
    private let binding = String(repeating: "a", count: 64)
    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    private func parse(_ overrides: [String: Any] = [:], current: String? = nil) -> PushOpen? {
        var envelope: [String: Any] = [
            "version": 1, "eventId": id, "bindingId": binding, "expiresAt": "2027-01-15T08:05:00.123Z",
            "route": "/chat?threadId=" + id, "recipientRole": "CLIENT"
        ]
        envelope.merge(overrides) { _, new in new }
        return PushPayloadPolicy.parse(["foodhome": envelope], bindingID: current ?? binding, now: now)
    }

    func testCurrentBindingMayOpenExactConversationAndOrder() {
        XCTAssertEqual(parse()?.route.absoluteString, "https://foodhome.market/chat?threadId=" + id)
        XCTAssertNotNil(parse(["route": "/orders/" + id]))
        XCTAssertNotNil(parse(["route": "/seller?view=CHAT&supportThreadId=" + id, "recipientRole": "SELLER"]))
        XCTAssertNotNil(parse(["route": "/seller?view=ORDERS&orderId=" + id, "recipientRole": "SELLER"]))
    }

    func testStaleGenerationExpiryAndUnexpectedRoutesAreIgnored() {
        XCTAssertNil(parse(current: String(repeating: "b", count: 64)))
        XCTAssertNil(parse(["expiresAt": "2027-01-15T07:59:59Z"]))
        XCTAssertNil(parse(["version": true]))
        XCTAssertNil(parse(["eventId": "anything"]))
        XCTAssertNil(parse(["message": "additional content"]))
        for route in ["/seller?view=CHAT", "//example.org", "https://foodhome.market/chat", "/chat?orderId=x",
                      "/chat?threadId=" + id + "&next=elsewhere", "/auth/logout", "/%2f/chat"] {
            XCTAssertNil(parse(["route": route]), route)
        }
    }

    func testColdTapWaitsForOwnerAndTrustedDocumentAndDeduplicates() throws {
        let open = try XCTUnwrap(parse())
        var inbox = PushNavigationInbox()
        XCTAssertTrue(inbox.enqueue(open))
        XCTAssertNil(inbox.take(bindingID: binding, now: now))
        XCTAssertTrue(inbox.hasPending)
        inbox.sessionConfirmed = true
        XCTAssertEqual(inbox.take(bindingID: binding, now: now), open.route)
        XCTAssertFalse(inbox.enqueue(open))
    }

    func testAQueuedTapIsDiscardedAfterTokenRotationLogoutOrExpiry() throws {
        let open = try XCTUnwrap(parse())
        var inbox = PushNavigationInbox()
        for current in [nil, String(repeating: "b", count: 64)] as [String?] {
            XCTAssertTrue(inbox.enqueue(open))
            inbox.sessionConfirmed = true
            XCTAssertNil(inbox.take(bindingID: current, now: now))
            XCTAssertFalse(inbox.hasPending)
        }
        XCTAssertTrue(inbox.enqueue(open))
        XCTAssertNil(inbox.take(bindingID: binding, now: open.expiresAt))
    }

    func testCredentialIsPurposeBoundAndExpires() {
        let grant: [String: Any] = ["action": "bind", "nonce": String(repeating: "a", count: 43),
                                    "expiresAt": "2027-01-15T08:05:00Z"]
        XCTAssertTrue(PushPayloadPolicy.validAction(grant, now: now))
        XCTAssertFalse(PushPayloadPolicy.validAction(grant, now: now.addingTimeInterval(301)))
        XCTAssertFalse(PushPayloadPolicy.validAction(grant.merging(["action": "status"]) { _, v in v }, now: now))
        XCTAssertFalse(PushPayloadPolicy.validAction(["action": "bind"], now: now))
        XCTAssertTrue(PushPayloadPolicy.validAction(["action": "clear"], now: now))
    }

    func testProductionNonceDateAndInstallationURLs() {
        let grant: [String: Any] = ["action": "bind", "nonce": String(repeating: "n", count: 43),
                                    "expiresAt": "2027-01-15T08:02:00.123456Z"]
        XCTAssertTrue(PushPayloadPolicy.validAction(grant, now: now))
        XCTAssertEqual(PushPayloadPolicy.origin, AppEnvironmentResolver.resolve(isDebug: false, debugBaseURL: nil).trustedOrigin)
        for action in ["bind", "revoke"] {
            XCTAssertEqual(PushPayloadPolicy.installationEndpoint(for: action)?.absoluteString,
                           "https://foodhome.market/api/v1/mobile/installations/\(action)/")
        }
        XCTAssertNil(PushPayloadPolicy.installationEndpoint(for: "../"))
    }

    func testLateDeviceCallbackSurvivesOlderWebClearUntilFirstConfirmedBinding() {
        var registration = APNsDeviceRegistration()
        let token = Data([1, 2, 3])
        let digest = PushPayloadPolicy.digest(token)
        XCTAssertTrue(registration.begin(now: now))
        XCTAssertFalse(registration.begin(now: now.addingTimeInterval(3)))
        XCTAssertEqual(registration.status(bindingID: binding, tokenDigest: digest), "unavailable")
        XCTAssertTrue(registration.receive(token))
        // The first website release clears on every read while it has no remembered owner.
        XCTAssertFalse(registration.clear(couldReceiveForOldOwner: false))
        XCTAssertFalse(registration.begin(now: now.addingTimeInterval(4)))
        XCTAssertEqual(registration.status(bindingID: nil, tokenDigest: nil), "needsBinding")
        XCTAssertEqual(registration.status(bindingID: binding, tokenDigest: digest), "enabled")
        XCTAssertFalse(registration.receive(token))
        XCTAssertTrue(registration.receive(Data([4, 5, 6])))
        XCTAssertEqual(registration.status(bindingID: binding, tokenDigest: digest), "needsBinding")
    }

    func testRegistrationFailureIsRetriedWithoutRepeatedRegistrationOrFalseSuccess() {
        var registration = APNsDeviceRegistration()
        XCTAssertTrue(registration.begin(now: now))
        XCTAssertTrue(registration.fail(now: now))
        XCTAssertFalse(registration.fail(now: now))
        XCTAssertFalse(registration.begin(now: now.addingTimeInterval(2)))
        XCTAssertEqual(registration.status(bindingID: binding, tokenDigest: nil), "unavailable")
        XCTAssertTrue(registration.begin(now: now.addingTimeInterval(30)))
        XCTAssertFalse(registration.receive(Data()))
        XCTAssertTrue(registration.receive(Data([1])))
        XCTAssertFalse(registration.fail(now: now))
        XCTAssertEqual(registration.status(bindingID: nil, tokenDigest: nil), "needsBinding")
    }

    func testChangingOwnerOrRevokingDiscardsTheTokenAndLateCallbacks() {
        var registration = APNsDeviceRegistration()
        XCTAssertTrue(registration.begin(now: now))
        XCTAssertTrue(registration.receive(Data([1])))
        XCTAssertTrue(registration.clear(couldReceiveForOldOwner: true))
        XCTAssertFalse(registration.receive(Data([1])))
        XCTAssertFalse(registration.clear(couldReceiveForOldOwner: false))
        XCTAssertTrue(registration.begin(now: now))
        XCTAssertTrue(registration.clear(couldReceiveForOldOwner: false, stopRegistration: true))
        XCTAssertFalse(registration.receive(Data([2])))
        // A persisted receipt or potentially successful POST also needs unregister after relaunch.
        XCTAssertTrue(registration.clear(couldReceiveForOldOwner: true))
    }

    func testEnabledRequiresTheCurrentProductionReceipt() {
        let receipt: [String: Any] = ["bindingId": binding, "platform": "ios", "provider": "apns",
                                      "environment": "production", "notificationStatus": "authorized",
                                      "revokedAt": NSNull(), "invalidatedAt": NSNull()]
        func accepts(_ value: [String: Any]?) -> Bool {
            PushPayloadPolicy.acceptsBindingReceipt(value, bindingID: binding, permission: .authorized)
        }
        XCTAssertTrue(accepts(receipt))
        XCTAssertFalse(accepts(nil))
        for (key, value) in ["bindingId": "old", "environment": "sandbox", "notificationStatus": "denied",
                            "revokedAt": "now", "invalidatedAt": "now"] {
            XCTAssertFalse(accepts(receipt.merging([key: value]) { _, replacement in replacement }))
        }
    }

    @MainActor
    func testOnlyAnExplicitForegroundActionShowsOneSystemPrompt() {
        let permissions = PermissionFixture()
        var result: NotificationPermissionResult?
        var active = false
        func request() {
            permissions.requestAuthorizationIfNeeded(canPresent: { active }) { result = $0 }
        }
        request()
        guard case .cancelled = result else { return XCTFail("Expected an explicit user action") }
        XCTAssertEqual(permissions.prompts, 0)

        for existing in [NotificationAuthorizationStatus.denied, .provisional, .authorized] {
            active = true
            permissions.current = existing
            request()
            guard case let .status(status) = result else { return XCTFail("Expected OS status") }
            XCTAssertEqual(status, existing)
            XCTAssertEqual(permissions.prompts, 0)
        }
        permissions.current = .notDetermined
        permissions.beforeStatus = { active = false }
        request()
        guard case .cancelled = result else { return XCTFail("The window left the foreground") }
        XCTAssertEqual(permissions.prompts, 0)

        permissions.beforeStatus = {}
        active = true
        request()
        guard case .status(.denied) = result else { return XCTFail("A system refusal must be preserved") }
        request()
        XCTAssertEqual(permissions.prompts, 1)

        permissions.current = .notDetermined
        permissions.answer = .authorized
        request()
        guard case .status(.authorized) = result else { return XCTFail("Expected the granted OS status") }
        request()
        XCTAssertEqual(permissions.prompts, 2)
    }
}

@MainActor
private final class PermissionFixture: IOSNotificationPermissions {
    var current: NotificationAuthorizationStatus = .notDetermined
    var answer: NotificationAuthorizationStatus = .denied
    var beforeStatus: () -> Void = {}
    private(set) var prompts = 0

    func authorizationStatus(completion: @escaping (NotificationAuthorizationStatus) -> Void) {
        beforeStatus()
        completion(current)
    }

    func requestAuthorization(completion: @escaping (NotificationAuthorizationStatus) -> Void) {
        prompts += 1
        current = answer
        completion(current)
    }
}
