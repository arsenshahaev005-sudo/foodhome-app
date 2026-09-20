package market.foodhome.app.bridge

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.time.Instant
import java.util.UUID

/** Main-thread, bounded state hints, NOT durable business events or consent assertions.
 * Keep the latest hint of each kind for replay into a replacement document.
 * Payment events remain in their separate, persistent ACK-controlled queue.
 */
class NotificationLifecycleSignals(
    private val now: () -> Instant = Instant::now,
    private val eventId: () -> String = { UUID.randomUUID().toString() },
) {
    enum class Kind(val wireName: String) {
        Resumed("appResumed"), PermissionChanged("permissionChanged"),
    }

    private val latest = linkedMapOf<Kind, JSONObject>()

    fun record(kind: Kind) {
        latest[kind] = JSONObject()
            .put("protocol", "foodhome.bridge").put("version", 1)
            .put("eventId", eventId()).put("name", kind.wireName)
            .put("payload", JSONObject()).put("occurredAt", now().toString())
    }

    fun dispatchScript(origin: URI, eventName: String, transportName: String): String? {
        if (latest.isEmpty() || origin.scheme != "https" || origin.host == null ||
            origin.userInfo != null || origin.port !in setOf(-1, 443)) return null
        val trusted = JSONObject.quote("https://${origin.host.lowercase()}")
        val events = JSONArray(latest.values.toList())
        return """
            (() => {
              if (window !== window.top || location.origin !== $trusted || document.visibilityState !== 'visible') return false;
              const transport = window[${JSONObject.quote(transportName)}];
              if (!transport || typeof transport.onmessage !== 'function') return false;
              const seen = window.__foodHomeLifecycleSeenV1 || (window.__foodHomeLifecycleSeenV1 = {});
              for (const event of $events) {
                if (seen[event.name] === event.eventId) continue;
                window.dispatchEvent(new CustomEvent(${JSONObject.quote(eventName)}, {detail:event}));
                seen[event.name] = event.eventId;
              }
              return true;
            })();
        """.trimIndent()
    }
}
