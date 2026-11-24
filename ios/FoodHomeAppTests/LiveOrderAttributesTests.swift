import ActivityKit
import XCTest
@testable import FoodHomeApp

@available(iOS 16.2, *)
final class LiveOrderAttributesTests: XCTestCase {
    func testDismissalPersistsForThisOrderUntilExpiryAndClearsOnLogout() throws {
        let suite = "test.live-order-dismissal." + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let order = UUID()
        let expiration = Date().addingTimeInterval(300)
        let store = LiveOrderDismissals(defaults: defaults)
        store.remember(order, until: expiration)
        XCTAssertTrue(LiveOrderDismissals(defaults: defaults).contains(order))
        XCTAssertFalse(store.contains(UUID()))
        XCTAssertFalse(store.contains(order, at: expiration))
        store.remember(order, until: expiration)
        store.clear()
        XCTAssertFalse(store.contains(order))
    }

    func testLockScreenHasOnlyAuthoritativeStageAndOpensSpecificOrder() throws {
        let id = UUID()
        let attributes = LiveOrderAttributes(orderID: id, bindingID: UUID())
        XCTAssertEqual(attributes.orderURL.absoluteString, "https://foodhome.market/orders/\(id.uuidString.lowercased())")
        let stage = try JSONDecoder().decode(LiveOrderAttributes.ContentState.self, from: Data(#"{"stage":"READY_FOR_PICKUP"}"#.utf8))
        XCTAssertEqual(stage.title, "Можно забирать")
        XCTAssertFalse(stage.isFinal)
        let result = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(stage)) as? [String: String])
        XCTAssertEqual(result, ["stage": "READY_FOR_PICKUP"])
        XCTAssertTrue(LiveOrderAttributes.ContentState(stage: "CANCELLED").isFinal)
    }
}
