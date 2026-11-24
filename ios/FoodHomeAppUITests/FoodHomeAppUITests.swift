import XCTest
import UIKit

final class FoodHomeAppUITests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    func testOfflineShellIsBrandedAndOffersRetry() {
        let app = XCUIApplication()
        app.launchArguments.append("--ui-test-offline")
        app.launch()

        let title = app.staticTexts["foodhome.shell.title"]
        XCTAssertTrue(title.waitForExistence(timeout: 5))
        XCTAssertEqual(title.label, "Нет подключения")
        XCTAssertTrue(app.buttons["foodhome.shell.retry"].exists)
    }

    func testColdLaunchShowsFoodHomeBrand() {
        let app = XCUIApplication()
        app.launchArguments.append("--ui-test-loading")
        app.launch()
        let loading = app.descendants(matching: .any)["foodhome.shell.loading"].firstMatch
        XCTAssertTrue(loading.waitForExistence(timeout: 5))
        XCTAssertEqual(loading.label, "Загружаем Food&Home")
    }

    func testWebViewReachesBottomAndNavigationCanBeTapped() {
        let app = XCUIApplication()
        app.launchArguments.append("--ui-test-viewport")
        app.launch()
        let web = app.webViews.firstMatch
        let tab = web.buttons["Открыть меню"]
        XCTAssertTrue(tab.waitForExistence(timeout: 10))
        XCTAssertEqual(web.frame.maxY, app.windows.firstMatch.frame.maxY, accuracy: 1)
        XCTAssertTrue(tab.isHittable)
        tab.tap()
        XCTAssertTrue(web.buttons["Меню открыто"].exists)
    }
}

final class AppStoreScreenshots: XCTestCase {
    private let openDishPrefix = "Открыть "

    func testGuestCatalogAndDish() throws {
        try XCTSkipUnless(
            ProcessInfo.processInfo.environment["FOODHOME_CAPTURE_STORE_SCREENSHOTS"] == "1",
            "Store screenshots use the public catalog and run on manual dispatch."
        )
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(ru)", "-AppleLocale", "ru_RU"]
        XCUIDevice.shared.orientation = .portrait
        app.launch()

        let web = app.webViews.firstMatch
        let catalogLinks = web.links.matching(NSPredicate(format: "label ==[c] %@", "Каталог"))
        var catalog: XCUIElement?
        XCTAssertTrue(wait(seconds: 45) {
            self.dismissGuestPrompts(in: web)
            catalog = catalogLinks.allElementsBoundByIndex.first(where: { $0.isHittable })
            return catalog != nil
        }, "The app must display the normal guest shopping navigation.")
        try XCTUnwrap(catalog).tap()

        let allDishes = web.descendants(matching: .any)
            .matching(NSPredicate(format: "label ==[c] %@", "Все блюда")).firstMatch
        XCTAssertTrue(wait(seconds: 30) {
            self.dismissGuestPrompts(in: web)
            return allDishes.exists
        }, "The public catalog must load.")
        let cards = web.links.matching(NSPredicate(format: "label BEGINSWITH %@", openDishPrefix))
        XCTAssertTrue(cards.firstMatch.waitForExistence(timeout: 30), "The catalog must contain public dish cards.")

        var selection: (link: XCUIElement, name: String)?
        for attempt in 0..<5 {
            dismissGuestPrompts(in: web)
            for card in cards.allElementsBoundByIndex.prefix(20) where card.isHittable {
                let name = String(card.label.dropFirst(openDishPrefix.count))
                guard !name.isEmpty, web.images[name].firstMatch.exists else { continue }
                let visible = card.frame.intersection(web.frame.insetBy(dx: 0, dy: 60))
                let photoCenter = CGPoint(x: card.frame.midX, y: card.frame.minY + card.frame.height * 0.23)
                if visible.contains(photoCenter) && visible.height > 130 {
                    selection = (card, name)
                    break
                }
            }
            if selection != nil { break }
            if attempt < 4 { web.swipeUp(velocity: .slow) }
        }
        let dish = try XCTUnwrap(selection, "A visible dish with a real catalog photograph is required.")
        try attachScreen("foodhome-store-01-catalog")
        // Tap the dish image, away from its favourite and cart controls.
        dish.link.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.23)).tap()

        let photo = web.buttons["Показать фото товара полностью"].firstMatch
        XCTAssertTrue(photo.waitForExistence(timeout: 25), "The selected dish must open in the product sheet.")
        XCTAssertTrue(web.staticTexts[dish.name].firstMatch.waitForExistence(timeout: 10))
        XCTAssertTrue(web.buttons["Закрыть"].firstMatch.exists)
        XCTAssertTrue(photo.isHittable)
        try attachScreen("foodhome-store-02-dish")
    }

    private func dismissGuestPrompts(in web: XCUIElement) {
        let system = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.firstMatch
        if system.exists {
            let deny = system.buttons.matching(NSPredicate(
                format: "label IN %@", ["Не разрешать", "Don't Allow", "Don’t Allow"] as NSArray
            )).firstMatch
            if deny.exists { deny.tap() }
        }
        for label in ["Закрыть выбор адреса", "Отклонить"] {
            let button = web.buttons[label].firstMatch
            if button.exists && button.isHittable { button.tap() }
        }
    }

    private func wait(seconds: TimeInterval, _ condition: () -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(seconds)
        while Date() < deadline {
            if condition() { return true }
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        }
        return false
    }

    private func attachScreen(_ name: String) throws {
        let screenshot = XCUIScreen.main.screenshot()
        let cgImage = try XCTUnwrap(screenshot.image.cgImage)
        XCTAssertEqual(cgImage.width, 1320)
        XCTAssertEqual(cgImage.height, 2868)
        let attachment = XCTAttachment(data: screenshot.pngRepresentation, uniformTypeIdentifier: "public.png")
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
