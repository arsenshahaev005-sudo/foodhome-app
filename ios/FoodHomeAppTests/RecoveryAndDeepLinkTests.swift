import XCTest
@testable import FoodHomeApp

final class RecoveryAndDeepLinkTests: XCTestCase {
    func testPendingRouteWaitsForBridgeAndIsConsumedOnce() {
        let store = PendingDeepLinkStore(
            navigationPolicy: NavigationPolicy(
                trustedOrigin: URL(string: "https://foodhome.market")!
            )
        )

        XCTAssertTrue(store.offer("https://foodhome.market/orders/123"))
        XCTAssertNil(store.consumeWhenReady(isBridgeReady: false))
        XCTAssertEqual(
            store.consumeWhenReady(isBridgeReady: true)?.absoluteString,
            "https://foodhome.market/orders/123"
        )
        XCTAssertNil(store.consumeWhenReady(isBridgeReady: true))
        XCTAssertFalse(store.offer("https://evil.example/orders/123"))
    }

    func testSecondSameRouteCrashInsideWindowTripsBreaker() {
        let breaker = CrashLoopBreaker(window: 60)
        let start = Date(timeIntervalSince1970: 1_777_377_600)

        XCTAssertFalse(
            breaker.recordCrash(routeKey: "https://foodhome.market/orders/123", at: start)
        )
        XCTAssertTrue(
            breaker.recordCrash(
                routeKey: "https://foodhome.market/orders/123",
                at: start.addingTimeInterval(10)
            )
        )
    }

    func testDifferentRouteOrExpiredCrashAllowsOneRestore() {
        let breaker = CrashLoopBreaker(window: 60)
        let start = Date(timeIntervalSince1970: 1_777_377_600)

        XCTAssertFalse(
            breaker.recordCrash(routeKey: "https://foodhome.market/orders/123", at: start)
        )
        XCTAssertFalse(
            breaker.recordCrash(
                routeKey: "https://foodhome.market/cart",
                at: start.addingTimeInterval(10)
            )
        )
        XCTAssertFalse(
            breaker.recordCrash(
                routeKey: "https://foodhome.market/cart",
                at: start.addingTimeInterval(71)
            )
        )
    }
}
