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
}
