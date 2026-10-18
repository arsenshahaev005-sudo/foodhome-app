package market.foodhome.app.notifications

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class VisiblePushPolicyTest {
    private val now = Instant.parse("2026-09-09T10:00:00Z").toEpochMilli()
    private val id = "7d85d3b1-6392-4e1e-81a1-673ea6e23509"
    private val bindingId = "a".repeat(64)
    private fun data() = mapOf(
        "version" to "2", "eventType" to "order.updated", "eventId" to id,
        "bindingId" to bindingId, "expiresAt" to "2026-09-09T10:10:00Z",
        "route" to """{"protocol":"foodhome.logical-route","version":1,"name":"order.detail","params":{"id":"$id"},"query":{}}""",
    )

    @Test fun `order resolves only to the confirmed internal route`() {
        assertEquals("https://foodhome.market/orders/$id", VisiblePushPolicy.parse(data(), now)?.destination)
    }

    @Test fun `open order events are bounded to their role and never use paid order sound`() {
        for (event in listOf("seller.open_order.available", "seller.open_order.updated", "buyer.open_order.updated")) {
            val buyer = event == "buyer.open_order.updated"
            val route = if (buyer) "open-order.buyer" else "open-order.seller"
            val input = data() + mapOf("version" to "5", "eventType" to event, "route" to
                """{"protocol":"foodhome.logical-route","version":1,"name":"$route","params":{"id":"$id"},"query":{}}""")
            val push = requireNotNull(VisiblePushPolicy.parse(input, now))
            assertEquals("https://foodhome.market/open-orders/$id" + if (buyer) "" else "?role=seller", push.destination)
            assertEquals(PushNotificationKind.Update, PushNotificationKind.forEvent(event))
            val state = PushRegistrationState(id, bindingId = bindingId)
            assertTrue(state.accepts(push, now))
            assertFalse(state.record(push.eventId).accepts(push, now))
            assertFalse(state.clear().accepts(push, now))
            assertFalse(PushRegistrationState(id, bindingId = "b".repeat(64)).accepts(push, now))
            for (version in listOf("1", "2", "3", "4")) assertNull(VisiblePushPolicy.parse(input + ("version" to version), now))
            assertNull(VisiblePushPolicy.parse(input + ("expiresAt" to "2026-09-09T10:00:00Z"), now))
            assertNull(VisiblePushPolicy.parse(input + ("body" to "private"), now))
            assertNull(VisiblePushPolicy.parse(input + ("eventType" to "seller.order.new"), now))
            assertNull(VisiblePushPolicy.parse(input + ("route" to input.getValue("route").replace(route, if (buyer) "open-order.seller" else "open-order.buyer")), now))
            assertNull(VisiblePushPolicy.parse(input + ("route" to input.getValue("route").replace(id, "../auth")), now))
        }
    }

    @Test fun `moderation v3 opens only the seller product and preserves generation expiry and dedupe`() {
        val input = data() + mapOf("version" to "3", "eventType" to "seller.product.moderated", "route" to
            """{"protocol":"foodhome.logical-route","version":1,"name":"seller.product","params":{"id":"$id"},"query":{}}""")
        val push = requireNotNull(VisiblePushPolicy.parse(input, now))
        assertEquals("https://foodhome.market/seller?view=PRODUCTS&productId=$id", push.destination)
        assertEquals(PushNotificationKind.Update, PushNotificationKind.forEvent(push.eventType))
        val state = PushRegistrationState(id, bindingId = bindingId)
        assertTrue(state.accepts(push, now))
        assertFalse(state.record(push.eventId).accepts(push, now))
        assertFalse(state.clear().accepts(push, now))
        assertNull(VisiblePushPolicy.parse(input + ("version" to "2"), now))
        assertNull(VisiblePushPolicy.parse(data() + ("version" to "3"), now))
        assertNull(VisiblePushPolicy.parse(input + ("expiresAt" to "2026-09-09T10:00:00Z"), now))
        assertNull(VisiblePushPolicy.parse(input + ("body" to "private"), now))
        assertNull(VisiblePushPolicy.parse(input + ("route" to input.getValue("route").replace(id, "../auth")), now))
    }

    @Test fun `buyer photo v4 opens the order with ordinary sound and existing lifecycle guards`() {
        val input = data() + mapOf("version" to "4", "eventType" to "buyer.order.photo_ready")
        val push = requireNotNull(VisiblePushPolicy.parse(input, now))
        assertEquals("https://foodhome.market/orders/$id", push.destination)
        assertEquals(PushNotificationKind.Update, PushNotificationKind.forEvent(push.eventType))
        val state = PushRegistrationState(id, bindingId = bindingId)
        assertTrue(state.accepts(push, now))
        assertFalse(state.record(push.eventId).accepts(push, now))
        assertFalse(state.clear().accepts(push, now))
        assertFalse(PushRegistrationState(id, bindingId = "b".repeat(64)).accepts(push, now))
        for (version in listOf("1", "2", "3")) assertNull(VisiblePushPolicy.parse(input + ("version" to version), now))
        assertNull(VisiblePushPolicy.parse(data() + ("version" to "4"), now))
        assertNull(VisiblePushPolicy.parse(input + ("expiresAt" to "2026-09-09T10:00:00Z"), now))
        assertNull(VisiblePushPolicy.parse(input + ("photoUrl" to "https://example.test/private.jpg"), now))
        assertNull(VisiblePushPolicy.parse(input + ("route" to input.getValue("route").replace("order.detail", "seller.product")), now))
        assertNull(VisiblePushPolicy.parse(input + ("route" to input.getValue("route").replace(id, "../auth")), now))
    }

    @Test fun `seller new order is an explicit bounded event with existing audience guards`() {
        val input = data() + ("eventType" to "seller.order.new")
        val push = requireNotNull(VisiblePushPolicy.parse(input, now))
        assertEquals("https://foodhome.market/orders/$id", push.destination)
        val state = PushRegistrationState(id, bindingId = bindingId)
        assertTrue(state.accepts(push, now))
        assertFalse(state.record(push.eventId).accepts(push, now))
        assertFalse(state.clear().accepts(push, now))
        assertFalse(PushRegistrationState(id, bindingId = "b".repeat(64)).accepts(push, now))
        assertNull(VisiblePushPolicy.parse(input + ("recipientRole" to "seller"), now))
        assertNull(VisiblePushPolicy.parse(input + ("sound" to "https://attacker.example/sound.wav"), now))
        assertNull(VisiblePushPolicy.parse(input + ("expiresAt" to "2026-09-09T10:00:00Z"), now))
        assertNull(VisiblePushPolicy.parse(input + ("route" to
            """{"protocol":"foodhome.logical-route","version":1,"name":"chat.inbox","params":{},"query":{}}"""), now))
    }

    @Test fun `chat supports only inbox and one existing identifier filter`() {
        for (query in listOf("{}", """{"orderId":"$id"}""", """{"producerId":"$id"}""")) {
            val input = data() + mapOf("eventType" to "chat.message", "route" to
                """{"protocol":"foodhome.logical-route","version":1,"name":"chat.inbox","params":{},"query":$query}""")
            assertTrue(requireNotNull(VisiblePushPolicy.parse(input, now)).destination.startsWith("https://foodhome.market/chat"))
        }
    }

    @Test fun `silent unknown expired excessive and mismatched payloads fail closed`() {
        val rejected = listOf(
            data() + ("version" to "1"), data() + ("version" to "3"),
            data() + ("eventType" to "payment.success"), data() + ("eventType" to "chat.message"),
            data() + ("expiresAt" to "2026-09-09T10:00:00Z"),
            data() + ("expiresAt" to "2026-09-11T10:00:00Z"),
            data() + ("expiresAt" to "never"), data() + ("eventId" to "../../auth"),
            data() + ("bindingId" to "a"), data() - "bindingId",
            data() + ("body" to "Private message"), data() + ("title" to "secret"),
            data() + ("pushToken" to "token"), data() + ("route" to "x".repeat(3_000)),
            data() + ("route" to "https://attacker.example/orders/$id"),
        )
        rejected.forEach { assertNull(VisiblePushPolicy.parse(it, now)) }
    }

    @Test fun `nested route fields and ambiguous query are rejected`() {
        val route = JSONObject(data().getValue("route"))
        route.put("extra", "no")
        assertNull(VisiblePushPolicy.parse(data() + ("route" to route.toString()), now))
        route.remove("extra")
        route.put("version", "1")
        assertNull(VisiblePushPolicy.parse(data() + ("route" to route.toString()), now))
        val chat = """{"protocol":"foodhome.logical-route","version":1,"name":"chat.inbox","params":{},"query":{"orderId":"$id","producerId":"$id"}}"""
        assertNull(VisiblePushPolicy.parse(data() + mapOf("eventType" to "chat.message", "route" to chat), now))
    }

    @Test fun `nonce operations have exact bounded shape and short expiry`() {
        val payload = JSONObject().put("action", "bind").put("nonce", "a".repeat(43)).put("expiresAt", "2026-09-09T10:02:00Z")
        assertTrue(PushBindingPolicy.accepts(payload))
        assertTrue(PushBindingPolicy.isFresh(payload.getString("expiresAt"), now))
        assertFalse(PushBindingPolicy.isFresh("2026-09-09T10:06:00Z", now))
        assertFalse(PushBindingPolicy.isFresh("2026-09-09T10:00:00Z", now))
        payload.put("pushToken", "forbidden")
        assertFalse(PushBindingPolicy.accepts(payload))
        payload.remove("pushToken")
        payload.put("nonce", "a\r\n".repeat(15))
        assertFalse(PushBindingPolicy.accepts(payload))
        assertTrue(PushBindingPolicy.accepts(JSONObject("""{"action":"clear"}""")))
        assertFalse(PushBindingPolicy.accepts(JSONObject("""{"action":"clear","nonce":"no"}""")))
    }

    @Test fun `digest is standard sha256 and debug representations redact identifiers`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", PushBindingPolicy.digest("abc"))
        assertFalse(requireNotNull(VisiblePushPolicy.parse(data(), now)).toString().contains(id))
    }

    @Test fun `scope clear rejects old messages and late bind completion`() {
        val initial = PushRegistrationState(id, bindingId = bindingId, tokenDigest = "b".repeat(64))
        val push = requireNotNull(VisiblePushPolicy.parse(data(), now))
        assertTrue(initial.accepts(push, now))
        val cleared = initial.clear()
        assertFalse(cleared.accepts(push, now))
        assertNull(cleared.bound(initial.revision, bindingId, "b".repeat(64)))
        val nextBinding = cleared.beginBinding()
        val switched = requireNotNull(nextBinding.bound(nextBinding.revision, "c".repeat(64), "b".repeat(64)))
        assertFalse(switched.accepts(push, now))
    }

    @Test fun `token rotation disables old binding and asks for authenticated rebind`() {
        val initial = PushRegistrationState(id, bindingId = bindingId, tokenDigest = "b".repeat(64))
        assertEquals(initial, initial.tokenChanged("b".repeat(64)))
        val rotated = initial.tokenChanged("c".repeat(64))
        assertNull(rotated.bindingId)
        assertTrue(rotated.needsBinding)
        assertTrue(rotated.revision > initial.revision)
        val pending = initial.beginBinding()
        assertNull(pending.tokenChanged("c".repeat(64)).bound(pending.revision, bindingId, "b".repeat(64)))
    }

    @Test fun `duplicates are suppressed with bounded persistent history`() {
        val initial = PushRegistrationState(id, bindingId = bindingId)
        val push = requireNotNull(VisiblePushPolicy.parse(data(), now))
        assertFalse(initial.record(push.eventId).accepts(push, now))
        val many = (1..1_000).fold(initial) { state, n -> state.record(n.toString()) }
        assertEquals(128, many.seenEvents.size)
        assertFalse(many.toString().contains(id))
    }

    @Test fun `token refresh after user opt out never asks web to reenable push`() {
        val initial = PushRegistrationState(id, bindingId = bindingId, tokenDigest = "b".repeat(64))
        val optedOut = initial.clear().tokenChanged("c".repeat(64))
        assertFalse(optedOut.optedIn)
        assertFalse(optedOut.needsBinding)
        assertNull(optedOut.bound(optedOut.revision, bindingId, "c".repeat(64)))
    }
}
