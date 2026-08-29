import XCTest
@testable import FoodHomeApp

final class NavigationCoordinatorTests: XCTestCase {
    private func makeCoordinator() -> NavigationCoordinator {
        NavigationCoordinator(
            navigationPolicy: NavigationPolicy(
                trustedOrigin: URL(string: "https://foodhome.market")!
            )
        )
    }

    func testTrustedDeepLinkWaitsForReadyDocumentAndIsDeliveredOnce() throws {
        let coordinator = makeCoordinator()
        var navigations: [URL] = []
        let attachment = coordinator.attach { navigations.append($0) }
        let document = try XCTUnwrap(
            coordinator.markTrustedDocumentLoading(attachment)
        )

        XCTAssertTrue(coordinator.offer("https://foodhome.market/orders/123"))
        XCTAssertTrue(navigations.isEmpty)

        coordinator.markTrustedDocumentReady(attachment, document: document)
        coordinator.markTrustedDocumentReady(attachment, document: document)

        XCTAssertEqual(navigations.map(\.absoluteString), ["https://foodhome.market/orders/123"])

        XCTAssertTrue(coordinator.offer("https://foodhome.market/cart"))
        XCTAssertEqual(navigations.count, 1)
        let nextDocument = try XCTUnwrap(
            coordinator.markTrustedDocumentLoading(attachment)
        )
        coordinator.markTrustedDocumentReady(attachment, document: nextDocument)
        XCTAssertEqual(navigations.last?.absoluteString, "https://foodhome.market/cart")
    }

    func testNewGenerationResetsReadinessAndRejectsExternalRoute() throws {
        let coordinator = makeCoordinator()
        let firstAttachment = coordinator.attach { _ in }
        let firstDocument = try XCTUnwrap(
            coordinator.markTrustedDocumentLoading(firstAttachment)
        )
        coordinator.markTrustedDocumentReady(firstAttachment, document: firstDocument)
        coordinator.detach(firstAttachment)

        var navigations: [URL] = []
        let secondAttachment = coordinator.attach { navigations.append($0) }

        XCTAssertTrue(coordinator.offer("https://foodhome.market/cart"))
        XCTAssertFalse(coordinator.offer("https://evil.example/cart"))
        XCTAssertTrue(navigations.isEmpty)

        coordinator.markTrustedDocumentReady(firstAttachment, document: firstDocument)
        XCTAssertTrue(navigations.isEmpty)
        let secondDocument = try XCTUnwrap(
            coordinator.markTrustedDocumentLoading(secondAttachment)
        )
        coordinator.markTrustedDocumentReady(secondAttachment, document: secondDocument)

        XCTAssertEqual(navigations.map(\.absoluteString), ["https://foodhome.market/cart"])
    }
}
