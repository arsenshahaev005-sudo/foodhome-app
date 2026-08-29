package market.foodhome.app.telemetry

import java.net.URI

fun interface TelemetrySink {
    fun record(event: TelemetryEvent)
}

object NoOpTelemetrySink : TelemetrySink {
    override fun record(event: TelemetryEvent) = Unit
}

class TelemetryReporter(
    private val sanitizer: TelemetrySanitizer,
    private val sink: TelemetrySink = NoOpTelemetrySink,
) {
    fun record(
        name: TelemetryEventName,
        attributes: Map<String, String> = emptyMap(),
        routeUrl: String? = null,
    ) {
        val values = attributes.toMutableMap()
        sanitizer.routeTemplate(routeUrl)?.let {
            values[TelemetryAttributeKey.RouteTemplate.wireValue] = it
        }
        sink.record(sanitizer.sanitize(name, values))
    }

    companion object {
        fun disabled(trustedOrigin: URI): TelemetryReporter =
            TelemetryReporter(TelemetrySanitizer(trustedOrigin), NoOpTelemetrySink)
    }
}
