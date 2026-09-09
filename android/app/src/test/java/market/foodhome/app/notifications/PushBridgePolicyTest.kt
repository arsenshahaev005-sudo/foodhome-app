package market.foodhome.app.notifications

import market.foodhome.app.bridge.BridgeManifest
import market.foodhome.app.bridge.BridgeRequest
import market.foodhome.app.bridge.BridgeRequestValidator
import market.foodhome.app.bridge.BridgeRequestResult
import market.foodhome.app.bridge.BridgeDispatchResult
import market.foodhome.app.capabilities.AndroidCapabilityCoordinator
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

class PushBridgePolicyTest {
    private val root = File(requireNotNull(System.getProperty("foodhome.contractRoot")))
    private fun manifest() = File(root, "manifest.json").inputStream().use(BridgeManifest::from)

    @Test fun `disabled manifest cannot advertise or dispatch push activation`() {
        val raw = manifest()
        assertFalse("managePush" in raw.advertisedCapabilities)
        assertFalse("managePush" in raw.forAndroidPush(false).advertisedCapabilities)
        assertTrue("managePush" in raw.forAndroidPush(false).builtInCapabilities)
        val enabled = raw.forAndroidPush(true)
        assertTrue("managePush" in enabled.advertisedCapabilities)
        assertFalse("openPayment" in enabled.advertisedCapabilities)
    }

    @Test fun `canonical nonce fixtures and token leak fixtures match the Android validator`() {
        val validator = BridgeRequestValidator(manifest())
        for (file in listOf("request-push-bind.json", "request-push-clear.json")) {
            assertTrue(validator.validate(File(root, "fixtures/valid/$file").readText()) is BridgeRequestResult.Accepted)
        }
        assertTrue(validator.validate(File(root, "fixtures/invalid/request-push-token-leak.json").readText()) is BridgeRequestResult.Rejected)
        val old = manifest().copy(methods = manifest().methods - "managePush")
        assertEquals("METHOD_NOT_SUPPORTED", (BridgeRequestValidator(old).validate(
            File(root, "fixtures/valid/request-push-bind.json").readText(),
        ) as BridgeRequestResult.Rejected).code)
    }

    @Test fun `canonical provider payload fixtures are accepted without exposing raw provider content`() {
        val now = Instant.parse("2026-09-09T10:00:00Z").toEpochMilli()
        fun serialized(file: File): Map<String, String> {
            val json = JSONObject(file.readText())
            return json.keys().asSequence().associateWith { json.get(it).toString() }
        }
        for (name in listOf("push-order.json", "push-chat.json")) {
            assertNotNull(VisiblePushPolicy.parse(serialized(File(root, "fixtures/valid/$name")), now))
        }
        assertNull(VisiblePushPolicy.parse(serialized(File(root, "fixtures/invalid/push-sensitive-copy.json")), now))
    }

    @Test fun `binding rate limits never prevent local clear or status`() {
        val manifest = manifest().forAndroidPush(true)
        var calls = 0
        val coordinator = AndroidCapabilityCoordinator(manifest, manifest.trustedProductionOrigin,
            presentShare = { false }, requestLocation = { _, _ -> },
            notificationStatus = { NotificationAuthorizationStatus.Authorized },
            requestNotificationPermission = { _, _ -> }, nowMillis = { 0L },
            managePush = { _, completion ->
                calls++
                completion(BridgeDispatchResult.Success(JSONObject().put("capability", "push").put("status", "disabled")))
            })
        val responses = mutableListOf<BridgeDispatchResult>()
        repeat(6) { coordinator.dispatch(BridgeRequest("bind-$it", "managePush", JSONObject().put("action", "bind")), responses::add) }
        assertEquals("RATE_LIMITED", (responses.last() as BridgeDispatchResult.Failure).code)
        for (action in listOf("clear", "status", "revoke")) {
            coordinator.dispatch(BridgeRequest(action, "managePush", JSONObject().put("action", action)), responses::add)
            assertTrue(responses.last() is BridgeDispatchResult.Success)
        }
        assertEquals(8, calls)
    }
}
