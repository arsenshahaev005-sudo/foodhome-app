package market.foodhome.app.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class TelemetrySanitizerTest {
    private val sanitizer = TelemetrySanitizer(URI("https://foodhome.market"))

    @Test
    fun `only typed allowlisted attributes survive`() {
        val event = sanitizer.sanitize(
            TelemetryEventName.ShellLaunch,
            mapOf(
                "platform" to "android",
                "appVersion" to "0.1.0",
                "bridgeVersion" to "1.4.0",
                "networkClass" to "wifi",
                "unknown" to "must-drop",
                "errorCode" to "Bearer secret-token",
            ),
        )

        assertEquals(TelemetryEventName.ShellLaunch, event.name)
        assertEquals("android", event.attributes[TelemetryAttributeKey.Platform])
        assertEquals("0.1.0", event.attributes[TelemetryAttributeKey.AppVersion])
        assertEquals("1.4.0", event.attributes[TelemetryAttributeKey.BridgeVersion])
        assertFalse(event.attributes.containsKey(TelemetryAttributeKey.ErrorCode))
        assertFalse(event.toString().contains("secret-token"))
        assertFalse(event.toString().contains("must-drop"))
    }

    @Test
    fun `route template strips query fragment and entity identifiers`() {
        assertEquals(
            "/orders/:id/chat/:id",
            sanitizer.routeTemplate(
                "https://foodhome.market/orders/123/chat/550e8400-e29b-41d4-a716-446655440000?token=secret#phone",
            ),
        )
        assertEquals("/:route/:id", sanitizer.routeTemplate("https://foodhome.market/custom/alice"))
        assertNull(sanitizer.routeTemplate("https://evil.example/orders/123?token=secret"))
        assertNull(sanitizer.routeTemplate("https://user@foodhome.market/orders/123"))
    }

    @Test
    fun `reporter never forwards raw sensitive sentinels`() {
        val recorded = mutableListOf<TelemetryEvent>()
        val reporter = TelemetryReporter(sanitizer, TelemetrySink { recorded.add(it) })

        reporter.record(
            TelemetryEventName.BridgeRequestFailed,
            attributes = mapOf(
                "errorCode" to "jwt.eyJhbGciOi.secret",
                "correlationId" to "corr_12345",
                "email" to "person@example.com",
                "preciseLocation" to "51.533,46.034",
            ),
            routeUrl = "https://foodhome.market/orders/987?payment_token=secret",
        )

        val text = recorded.single().toString()
        assertEquals("corr_12345", recorded.single().attributes[TelemetryAttributeKey.CorrelationId])
        assertTrue(text.contains("/orders/:id"))
        for (forbidden in listOf("jwt", "person@example.com", "51.533", "payment_token", "987")) {
            assertFalse(text.contains(forbidden))
        }
    }

    @Test
    fun `no-op sink is safe and side effect free`() {
        val reporter = TelemetryReporter.disabled(URI("https://foodhome.market"))
        reporter.record(
            TelemetryEventName.MobileConfigFailed,
            attributes = mapOf("errorCode" to "CONFIG_INVALID"),
        )
    }
}
