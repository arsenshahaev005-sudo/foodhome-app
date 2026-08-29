import XCTest
@testable import FoodHomeApp

final class TelemetrySanitizerTests: XCTestCase {
    private let sanitizer = TelemetrySanitizer(
        trustedOrigin: URL(string: "https://foodhome.market")!
    )

    func testOnlyTypedAllowlistedAttributesSurvive() {
        let event = sanitizer.sanitize(
            name: .shellLaunch,
            rawAttributes: [
                "platform": "ios",
                "appVersion": "0.1.0",
                "bridgeVersion": "1.4.0",
                "networkClass": "wifi",
                "unknown": "must-drop",
                "errorCode": "Bearer secret-token",
            ]
        )

        XCTAssertEqual(event.name, .shellLaunch)
        XCTAssertEqual(event.attributes[.platform], "ios")
        XCTAssertEqual(event.attributes[.appVersion], "0.1.0")
        XCTAssertEqual(event.attributes[.bridgeVersion], "1.4.0")
        XCTAssertNil(event.attributes[.errorCode])
        XCTAssertFalse(event.description.contains("secret-token"))
        XCTAssertFalse(event.description.contains("must-drop"))
    }

    func testRouteTemplateStripsQueryFragmentAndEntityIdentifiers() {
        XCTAssertEqual(
            sanitizer.routeTemplate(
                "https://foodhome.market/orders/123/chat/550e8400-e29b-41d4-a716-446655440000?token=secret#phone"
            ),
            "/orders/:id/chat/:id"
        )
        XCTAssertEqual(
            sanitizer.routeTemplate("https://foodhome.market/custom/alice"),
            "/:route/:id"
        )
        XCTAssertNil(sanitizer.routeTemplate("https://evil.example/orders/123?token=secret"))
        XCTAssertNil(sanitizer.routeTemplate("https://user@foodhome.market/orders/123"))
    }

    func testReporterNeverForwardsRawSensitiveSentinels() throws {
        let sink = RecordingTelemetrySink()
        let reporter = TelemetryReporter(sanitizer: sanitizer, sink: sink)

        reporter.record(
            .bridgeRequestFailed,
            attributes: [
                "errorCode": "jwt.eyJhbGciOi.secret",
                "correlationId": "corr_12345",
                "email": "person@example.com",
                "preciseLocation": "51.533,46.034",
            ],
            routeURL: "https://foodhome.market/orders/987?payment_token=secret"
        )

        let event = try XCTUnwrap(sink.events.first)
        XCTAssertEqual(event.attributes[.correlationId], "corr_12345")
        XCTAssertTrue(event.description.contains("/orders/:id"))
        for forbidden in ["jwt", "person@example.com", "51.533", "payment_token", "987"] {
            XCTAssertFalse(event.description.contains(forbidden))
        }
    }

    func testNoOpSinkIsSafe() {
        TelemetryReporter.disabled(
            trustedOrigin: URL(string: "https://foodhome.market")!
        ).record(.mobileConfigFailed, attributes: ["errorCode": "CONFIG_INVALID"])
    }
}

private final class RecordingTelemetrySink: TelemetrySink {
    private(set) var events: [TelemetryEvent] = []

    func record(_ event: TelemetryEvent) {
        events.append(event)
    }
}
