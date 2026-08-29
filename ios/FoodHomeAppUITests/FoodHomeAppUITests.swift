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
}
