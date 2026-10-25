package market.foodhome.app.notifications

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.UUID

class ExpandedPushPolicyTest {
    private val now = Instant.parse("2026-10-25T12:00:00Z").toEpochMilli()
    private val root = File(requireNotNull(System.getProperty("foodhome.contractRoot")))
    private fun wire(version: Int): Map<String, String> {
        val json = JSONObject(File(root, "fixtures/valid/${if (version == 6) "push-broadcast.json" else "push-buyer.json"}").readText())
        return json.keys().asSequence().associateWith { json.get(it).toString() }
    }
    private fun route(data: Map<String, String>, change: (JSONObject) -> Unit): Map<String, String> {
        val json = JSONObject(data.getValue("route")); change(json)
        return data + ("route" to json.toString())
    }
    private fun push(version: Int = 7) = requireNotNull(VisiblePushPolicy.parse(wire(version), now))
    private fun bound(push: VisiblePush) = PushRegistrationState(UUID.randomUUID().toString(), bindingId = push.bindingId)

    @Test fun `notification envelopes fail before Firebase background auto display and data without collapse is valid`() {
        val dataKeys = wire(7).keys + setOf("google.message_id", "google.ttl", "from")
        assertTrue(PushEnvelopePolicy.acceptsKeys(dataKeys))
        assertTrue(PushEnvelopePolicy.acceptsKeys(dataKeys + "collapse_key")) // Legacy provider metadata is not application data.
        for (key in listOf("notification", "gcm.n.e", "gcm.n.title", "gcm.n.body", "gcm.notification.e", "gcm.notification.body")) {
            assertFalse(PushEnvelopePolicy.acceptsKeys(dataKeys + key))
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun `non string FCM values cannot be coerced through map or tap deserialization`() {
        for (key in wire(7).keys) for (wrong in listOf(null, 7, true, emptyList<String>(), emptyMap<String, String>())) {
            val invalid: Map<String, Any?> = wire(7) + (key to wrong)
            assertNull(VisiblePushPolicy.parse(invalid as Map<String, String>, now))
        }
    }

    @Test fun `canonical fixtures resolve to exact fixed-origin inboxes with ordinary sound`() {
        assertEquals("https://foodhome.market/seller?view=CHAT&section=notifications", push(6).destination)
        assertEquals("https://foodhome.market/chat?section=notifications", push(7).destination)
        for (version in listOf(6, 7)) assertEquals(PushNotificationKind.Update, PushNotificationKind.forEvent(push(version).eventType))
        for (role in listOf("ACCOUNT", "CLIENT", "SELLER")) {
            val data = route(wire(6)) { it.getJSONObject("query").put("role", role) }
            assertEquals(if (role == "SELLER") "https://foodhome.market/seller?view=CHAT&section=notifications"
                else "https://foodhome.market/chat?section=notifications", requireNotNull(VisiblePushPolicy.parse(data, now)).destination)
        }
    }

    @Test fun `v7 order uses UUID path while role hints cannot expand buyer navigation`() {
        val id = UUID.randomUUID().toString()
        val data = route(wire(7)) { it.put("name", "order.detail").put("params", JSONObject().put("id", id)).put("query", JSONObject()) }
        assertEquals("https://foodhome.market/orders/$id", requireNotNull(VisiblePushPolicy.parse(data, now)).destination)
        assertNull(VisiblePushPolicy.parse(data + mapOf("version" to "6", "eventType" to "broadcast.notification"), now))
        for (role in listOf("ACCOUNT", "SELLER", "CLIENT ", "client", 1, true, JSONObject.NULL)) {
            assertNull(VisiblePushPolicy.parse(route(wire(7)) { it.getJSONObject("query").put("role", role) }, now))
        }
        for (wrong in listOf("../auth", "https://evil.test", 1, true, JSONObject.NULL)) {
            assertNull(VisiblePushPolicy.parse(route(data) { it.getJSONObject("params").put("id", wrong) }, now))
        }
    }

    @Test fun `version event key identifier binding and TTL rules fail closed`() {
        for (version in listOf(6, 7)) {
            val data = wire(version)
            for (other in listOf("1", "2", "3", "4", "5", "8", "0$version", "$version.0", " $version", "true")) {
                assertNull(VisiblePushPolicy.parse(data + ("version" to other), now))
            }
            assertNull(VisiblePushPolicy.parse(data + ("version" to if (version == 6) "7" else "6"), now))
            for (type in listOf("seller.order.new", "chat.message", "buyer.order.photo_ready", "payment.success")) {
                assertNull(VisiblePushPolicy.parse(data + ("eventType" to type), now))
            }
            for (key in data.keys) assertNull(VisiblePushPolicy.parse(data - key, now))
            for (key in listOf("title", "body", "notification", "url", "address", "amount", "promoCode", "sound")) {
                assertNull(VisiblePushPolicy.parse(data + (key to "private"), now))
            }
            for (bad in listOf("", "A".repeat(64), "a".repeat(63), "a".repeat(65), "a\n".repeat(32))) {
                assertNull(VisiblePushPolicy.parse(data + ("bindingId" to bad), now))
            }
            for (bad in listOf("", "../auth", "FFFFFFFF-FFFF-4FFF-8FFF-FFFFFFFFFFFF", "123")) {
                assertNull(VisiblePushPolicy.parse(data + ("eventId" to bad), now))
            }
            for (bad in listOf("never", "123", "2026-10-25T12:00:00Z", "2026-10-26T12:00:00.001Z")) {
                assertNull(VisiblePushPolicy.parse(data + ("expiresAt" to bad), now))
            }
            assertNotNull(VisiblePushPolicy.parse(data + ("expiresAt" to "2026-10-26T12:00:00Z"), now))
        }
    }

    @Test fun `nested fields are not coerced and arbitrary URLs are rejected`() {
        for (version in listOf(6, 7)) {
            val data = wire(version)
            for (mutate in listOf<(JSONObject) -> Unit>(
                { it.put("version", "1") }, { it.put("version", true) }, { it.put("version", 1.5) },
                { it.put("name", "https://evil.test") }, { it.put("protocol", "https") },
                { it.put("params", "{}") }, { it.put("query", "{}") }, { it.put("extra", "private") },
                { it.getJSONObject("params").put("id", UUID.randomUUID().toString()) },
                { it.getJSONObject("query").put("url", "https://evil.test") },
                { it.getJSONObject("query").put("role", true) }, { it.getJSONObject("query").remove("role") },
            )) assertNull(VisiblePushPolicy.parse(route(data, mutate), now))
            for (raw in listOf("https://evil.test", "[]", "null", "{", "{}", data.getValue("route") + " false")) {
                assertNull(VisiblePushPolicy.parse(data + ("route" to raw), now))
            }
        }
    }

    @Test fun `FCM UTF8 budget includes keys and values with exact 2048 boundary`() {
        val data = wire(7)
        val bytes = data.entries.sumOf { it.key.toByteArray(Charsets.UTF_8).size + it.value.toByteArray(Charsets.UTF_8).size }
        val padded = data + ("route" to data.getValue("route") + " ".repeat(2048 - bytes))
        assertNotNull(VisiblePushPolicy.parse(padded, now))
        assertNull(VisiblePushPolicy.parse(padded + ("route" to padded.getValue("route") + " "), now))
        assertNull(VisiblePushPolicy.parse(data + ("eventId" to "я".repeat(1100)), now))
    }

    @Test fun `receipts survive process restart and repeated taps logout and rotation stay isolated`() {
        val push = push()
        val initial = bound(push)
        val shown = requireNotNull(initial.record(push, now))
        val restored = requireNotNull(PushStateCodec.decode(PushStateCodec.encode(shown), now))
        assertFalse(restored.accepts(push, now))
        val opened = requireNotNull(restored.opened(push, now))
        assertNull(opened.opened(push, now))
        assertNull(requireNotNull(PushStateCodec.decode(PushStateCodec.encode(opened), now)).opened(push, now))
        assertNull(shown.clear().opened(push, now))
        assertNull(shown.tokenChanged("b".repeat(64)).opened(push, now))
        val pending = shown.beginBinding()
        val rebound = requireNotNull(pending.bound(pending.revision, "c".repeat(64), "d".repeat(64)))
        assertNull(rebound.opened(push, now))
        assertNull(shown.opened(push, push.expiresAtMillis))
        assertNull(initial.opened(push, now))
        val second = VisiblePush(UUID.randomUUID().toString(), push.eventType, push.bindingId, push.expiresAtMillis, push.destination)
        val two = requireNotNull(shown.record(second, now))
        assertEquals(2, two.seenEvents.size)
        assertNotNull(two.opened(push, now)); assertNotNull(two.opened(second, now))
    }

    @Test fun `live dedupe receipts are never evicted and capacity recovers at expiry`() {
        val push = push()
        val full = (1..PushRegistrationState.MAX_EVENTS).fold(bound(push)) { state, _ ->
            requireNotNull(state.record(VisiblePush(UUID.randomUUID().toString(), push.eventType, push.bindingId, push.expiresAtMillis, push.destination), now))
        }
        val first = VisiblePush(full.seenEvents.first(), push.eventType, push.bindingId, push.expiresAtMillis, push.destination)
        assertFalse(full.accepts(first, now)); assertNull(full.record(push, now))
        val later = push.expiresAtMillis + 1
        val future = VisiblePush(UUID.randomUUID().toString(), push.eventType, push.bindingId, later + 10000, push.destination)
        assertEquals(1, requireNotNull(full.record(future, later)).seenEvents.size)
    }

    @Test fun `legacy receipt migration keeps consent and deleted-message sync survives process restart`() {
        val push = push()
        val old = JSONObject(PushStateCodec.encode(requireNotNull(bound(push).record(push, now))))
        old.remove("eventExpiries"); old.remove("openedEvents"); old.remove("syncRequired")
        val migrated = requireNotNull(PushStateCodec.decode(old.toString(), now))
        assertFalse(migrated.accepts(push, now)); assertEquals(push.bindingId, migrated.bindingId)
        assertTrue(migrated.optedIn); assertFalse(migrated.needsBinding); assertFalse(migrated.syncRequired)
        val deleted = migrated.copy(syncRequired = true)
        assertTrue(requireNotNull(PushStateCodec.decode(PushStateCodec.encode(deleted), now)).syncRequired)
        assertFalse(deleted.clear().syncRequired)
        assertFalse(deleted.clear().tokenChanged("c".repeat(64)).needsBinding)
        assertNull(PushStateCodec.decode(JSONObject(PushStateCodec.encode(deleted)).put("eventExpiries", "wrong").toString(), now))
    }
}
