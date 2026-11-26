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

    func testLegacyContentStateStillOpensSpecificOrder() throws {
        let id = UUID()
        let attributes = LiveOrderAttributes(orderID: id, bindingID: UUID())
        XCTAssertEqual(attributes.orderURL.absoluteString, FoodHomeDeployment.current.origin.absoluteString + "/orders/\(id.uuidString.lowercased())")
        let stage = try JSONDecoder().decode(LiveOrderAttributes.ContentState.self, from: Data(#"{"stage":"READY_FOR_PICKUP"}"#.utf8))
        XCTAssertEqual(stage.title, "Можно забирать")
        XCTAssertFalse(stage.isFinal)
        XCTAssertNil(stage.pickup)
        XCTAssertEqual(stage.progress?.steps, [.placed, .confirmation, .preparing, .ready, .received])
        let result = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(stage)) as? [String: String])
        XCTAssertEqual(result, ["stage": "READY_FOR_PICKUP"])
        XCTAssertTrue(LiveOrderAttributes.ContentState(stage: "CANCELLED").isFinal)
    }

    func testDeliveryTimelineShowsOrderThroughReceiptInSixSteps() throws {
        let states = [
            "WAITING_FOR_PAYMENT", "WAITING_FOR_ACCEPTANCE", "COOKING",
            "READY_FOR_DELIVERY", "DELIVERING", "COMPLETED",
        ]
        for (index, stage) in states.enumerated() {
            let progress = try XCTUnwrap(LiveOrderAttributes.ContentState(stage: stage, pickup: false).progress)
            XCTAssertEqual(progress.steps, [.placed, .confirmation, .preparing, .ready, .transit, .received])
            XCTAssertEqual(progress.currentIndex, index)
            XCTAssertEqual(progress.stageCountLabel, "\(index + 1) из 6")
            XCTAssertEqual(progress.marker(at: index), index == states.count - 1 ? .completed : .current)
        }

        let cooking = try XCTUnwrap(LiveOrderAttributes.ContentState(stage: "COOKING", pickup: false).progress)
        XCTAssertEqual(cooking.steps.indices.map { cooking.marker(at: $0) }, [
            .completed, .completed, .current, .upcoming, .upcoming, .upcoming,
        ])
        XCTAssertEqual(cooking.accessibilitySummary, "Этап 3 из 6. Далее Готов, В пути, Получен")
    }

    func testPickupTimelineShowsCollectionFromTheFirstSnapshot() throws {
        let waiting = try XCTUnwrap(LiveOrderAttributes.ContentState(stage: "WAITING_FOR_ACCEPTANCE", pickup: true).progress)
        XCTAssertEqual(waiting.steps, [.placed, .confirmation, .preparing, .ready, .received])
        XCTAssertEqual(waiting.title(for: .ready), "К выдаче")
        XCTAssertEqual(waiting.stageCountLabel, "2 из 5")

        let ready = try XCTUnwrap(LiveOrderAttributes.ContentState(stage: "READY_FOR_PICKUP", pickup: true).progress)
        XCTAssertEqual(ready.steps[ready.currentIndex], .ready)
        XCTAssertEqual(ready.accessibilitySummary, "Этап 4 из 5. Далее Получен")
    }

    func testLiveOrderSnapshotDecodesNewAndLegacyPayloads() throws {
        let json = #"{"orderId":"00000000-0000-0000-0000-000000000001","stage":"COOKING","expiresAt":1764144000}"#
        let legacy = try JSONDecoder().decode(LiveOrderSnapshot.self, from: Data(json.utf8))
        XCTAssertNil(legacy.pickup)

        let updatedJSON = json.replacingOccurrences(of: #""stage":"COOKING""#, with: #""stage":"COOKING","pickup":true"#)
        let updated = try JSONDecoder().decode(LiveOrderSnapshot.self, from: Data(updatedJSON.utf8))
        XCTAssertEqual(updated.pickup, true)
        let next = try JSONDecoder().decode(
            LiveOrderAttributes.ContentState.self,
            from: Data(#"{"stage":"READY_FOR_DELIVERY","pickup":false}"#.utf8)
        )
        XCTAssertEqual(next.progress?.stageCountLabel, "4 из 6")
    }

    func testAnInterruptionDoesNotImplyOrderProgress() {
        for stage in ["CANCELLED", "DISPUTE", "BLOCKED_FOR_SURCHARGE_PAYMENT", "UPDATED", "999"] {
            XCTAssertNil(LiveOrderAttributes.ContentState(stage: stage, pickup: false).progress, stage)
        }
        XCTAssertEqual(LiveOrderAttributes.ContentState(stage: "READY_FOR_REVIEW", pickup: false).progress?.currentIndex, 2)
        XCTAssertEqual(LiveOrderAttributes.ContentState(stage: "WAITING_FOR_RECIPIENT", pickup: false).progress?.currentIndex, 0)
    }
}
