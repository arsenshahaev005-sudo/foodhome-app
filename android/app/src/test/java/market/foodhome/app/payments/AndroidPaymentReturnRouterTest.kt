package market.foodhome.app.payments

import java.net.URI
import market.foodhome.app.telemetry.TelemetryEvent
import market.foodhome.app.telemetry.TelemetryEventName
import market.foodhome.app.telemetry.TelemetryReporter
import market.foodhome.app.telemetry.TelemetrySanitizer
import market.foodhome.app.telemetry.TelemetrySink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidPaymentReturnRouterTest {
    @Test
    fun `resume creates one stable event and never relaunches payment`() {
        val store = RouterStore()
        var launches = 0
        val coordinator = PaymentCoordinator(
            policy = PaymentLaunchPolicy(
                listOf(PaymentProviderRule(PaymentFlow.SbpCyclops, "sbp.example.invalid", "/qr")),
            ),
            store = store,
            launcher = PaymentLauncher { launches += 1; true },
            nowMillis = { 1_000 },
            eventId = { "payment-event-1" },
        )
        coordinator.open(
            OpenPaymentRequest("https://sbp.example.invalid/qr", "ctx", 10_000, true),
        )
        val router = AndroidPaymentReturnRouter(coordinator)
        var notifications = 0
        router.attach { notifications += 1 }

        router.onStop()
        router.onResume()
        router.onResume()

        assertEquals(1, launches)
        assertEquals(1, notifications)
        assertEquals("payment-event-1", coordinator.pendingEvent()?.eventId)
    }

    @Test
    fun `cold start without recovery does nothing`() {
        val coordinator = PaymentCoordinator(
            PaymentLaunchPolicy(emptyList()),
            RouterStore(),
            PaymentLauncher { error("must not launch") },
        )
        val router = AndroidPaymentReturnRouter(coordinator)

        router.onColdStart()

        assertNull(coordinator.pendingEvent())
    }

    @Test
    fun `active recovery write failure emits sanitized telemetry`() {
        val store = RouterStore()
        val coordinator = PaymentCoordinator(
            policy = PaymentLaunchPolicy(
                listOf(PaymentProviderRule(PaymentFlow.SbpCyclops, "sbp.example.invalid", "/qr")),
            ),
            store = store,
            launcher = PaymentLauncher { true },
            nowMillis = { 1_000 },
            eventId = { "payment-event-1" },
        )
        coordinator.open(
            OpenPaymentRequest("https://sbp.example.invalid/qr", "ctx", 10_000, true),
        )
        store.rejectWrites = true
        val sink = RouterRecordingTelemetrySink()
        val reporter = TelemetryReporter(
            TelemetrySanitizer(URI("https://foodhome.market")),
            sink,
        )

        AndroidPaymentReturnRouter(coordinator, reporter).onColdStart()

        assertEquals(TelemetryEventName.PaymentReturnFailed, sink.events.single().name)
        assertEquals(
            "PAYMENT_RETURN_RECORD_FAILED",
            sink.events.single().attributes.values.single(),
        )
    }
}

private class RouterStore : PaymentRecoveryStore {
    var current: PaymentRecoverySnapshot? = null
    var rejectWrites = false
    override fun read(): PaymentRecoverySnapshot? = current
    override fun write(snapshot: PaymentRecoverySnapshot): Boolean {
        if (rejectWrites) return false
        current = snapshot
        return true
    }
    override fun clear(): Boolean {
        current = null
        return true
    }
}

private class RouterRecordingTelemetrySink : TelemetrySink {
    val events = mutableListOf<TelemetryEvent>()
    override fun record(event: TelemetryEvent) {
        events += event
    }
}
