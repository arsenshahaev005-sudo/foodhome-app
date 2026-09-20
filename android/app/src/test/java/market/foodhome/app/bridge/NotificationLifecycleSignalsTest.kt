package market.foodhome.app.bridge

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.time.Instant

class NotificationLifecycleSignalsTest {
    @Test fun `export actual dispatcher for JavaScript runtime regression tests`() {
        val destination = System.getProperty("foodhome.lifecycleScriptOutput") ?: return
        val queue = signals().apply {
            record(NotificationLifecycleSignals.Kind.Resumed)
            record(NotificationLifecycleSignals.Kind.PermissionChanged)
        }
        java.io.File(destination).apply { parentFile?.mkdirs(); writeText(script(queue)!!) }
    }
    private var sequence = 0
    private fun signals() = NotificationLifecycleSignals(
        now = { Instant.parse("2026-09-20T12:00:00Z") },
        eventId = { "signal-${++sequence}" },
    )
    private fun script(signals: NotificationLifecycleSignals, origin: String = "https://foodhome.market") =
        signals.dispatchScript(URI(origin), "foodhome:native-event", "FoodHomeBridge")
    private fun events(script: String): JSONArray = JSONArray(
        script.substringAfter("for (const event of ").substringBefore(") {"),
    )

    @Test fun `empty queue does not dispatch`() { assertNull(script(signals())) }

    @Test fun `permission result and resume exist without a payment`() {
        val queue = signals()
        queue.record(NotificationLifecycleSignals.Kind.Resumed)
        queue.record(NotificationLifecycleSignals.Kind.PermissionChanged)
        val events = events(script(queue)!!)
        assertEquals(2, events.length())
        assertEquals("appResumed", events.getJSONObject(0).getString("name"))
        assertEquals("permissionChanged", events.getJSONObject(1).getString("name"))
        for (i in 0 until events.length()) {
            val event = events.getJSONObject(i)
            assertEquals("foodhome.bridge", event.getString("protocol"))
            assertEquals(1, event.getInt("version"))
            assertEquals(0, event.getJSONObject("payload").length())
            assertEquals(6, event.length())
            assertEquals(Instant.parse("2026-09-20T12:00:00Z"), Instant.parse(event.getString("occurredAt")))
        }
    }

    @Test fun `coalesces by kind and retains for replacement document rather than acknowledging`() {
        val queue = signals()
        repeat(100) {
            queue.record(NotificationLifecycleSignals.Kind.Resumed)
            queue.record(NotificationLifecycleSignals.Kind.PermissionChanged)
        }
        val first = script(queue)!!
        assertEquals(first, script(queue))
        val events = events(first)
        assertEquals(2, events.length())
        assertEquals("signal-199", events.getJSONObject(0).getString("eventId"))
        assertEquals("signal-200", events.getJSONObject(1).getString("eventId"))
        assertFalse(first.contains("ackNativeEvent"))
        assertFalse(first.contains("paymentReturned"))
    }

    @Test fun `only visible trusted top document with attached web adapter receives hints`() {
        val queue = signals().apply { record(NotificationLifecycleSignals.Kind.Resumed) }
        val js = script(queue)!!
        assertTrue(js.contains("window !== window.top"))
        assertTrue(js.contains("location.origin !== \"https://foodhome.market\""))
        assertTrue(js.contains("document.visibilityState !== 'visible'"))
        assertTrue(js.contains("typeof transport.onmessage !== 'function'"))
        assertTrue(js.contains("seen[event.name] === event.eventId"))
        listOf("http://foodhome.market", "https://user@foodhome.market", "https://foodhome.market:444").forEach {
            assertNull(script(queue, it))
        }
    }
}
