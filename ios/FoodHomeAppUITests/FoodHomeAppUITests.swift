import XCTest

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
