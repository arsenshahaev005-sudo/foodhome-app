import Foundation

protocol TelemetrySink {
    func record(_ event: TelemetryEvent)
}

struct NoOpTelemetrySink: TelemetrySink {
    func record(_ event: TelemetryEvent) {}
}

final class TelemetryReporter {
    private let sanitizer: TelemetrySanitizer
    private let sink: TelemetrySink

    init(sanitizer: TelemetrySanitizer, sink: TelemetrySink = NoOpTelemetrySink()) {
        self.sanitizer = sanitizer
        self.sink = sink
    }

    func record(
        _ name: TelemetryEventName,
        attributes: [String: String] = [:],
        routeURL: String? = nil
    ) {
        var values = attributes
        if let route = sanitizer.routeTemplate(routeURL) {
            values[TelemetryAttributeKey.routeTemplate.rawValue] = route
        }
        sink.record(sanitizer.sanitize(name: name, rawAttributes: values))
    }

    static func disabled(trustedOrigin: URL) -> TelemetryReporter {
        TelemetryReporter(
            sanitizer: TelemetrySanitizer(trustedOrigin: trustedOrigin),
            sink: NoOpTelemetrySink()
        )
    }
}
