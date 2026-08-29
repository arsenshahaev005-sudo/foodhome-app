package market.foodhome.app.bridge

import market.foodhome.app.capabilities.CapabilityPurposePolicy
import market.foodhome.app.capabilities.FoodHomeSharePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class BridgeCapabilityPolicyTest {
    private val origin = URI("https://foodhome.market")

    @Test
    fun `share accepts only bounded exact FoodHome https urls`() {
        val policy = FoodHomeSharePolicy(origin)

        assertNotNull(policy.parse("Food&Home", "Домашняя еда", "https://foodhome.market/dishes/1"))
        assertNull(policy.parse(null, null, "http://foodhome.market/dishes/1"))
        assertNull(policy.parse(null, null, "https://cdn.foodhome.market/dishes/1"))
        assertNull(policy.parse(null, null, "https://user@foodhome.market/dishes/1"))
        assertNull(policy.parse("x".repeat(121), null, "https://foodhome.market/"))
    }

    @Test
    fun `context purpose is non blank and bounded`() {
        assertTrue(CapabilityPurposePolicy.accepts("Показать доставку рядом"))
        assertFalse(CapabilityPurposePolicy.accepts(" "))
        assertFalse(CapabilityPurposePolicy.accepts("x".repeat(161)))
    }

    @Test
    fun `rate limit rules are positive and conservatively bounded`() {
        assertTrue(RateLimitRule(maxRequests = 3, windowSeconds = 60).isSafe)
        assertFalse(RateLimitRule(maxRequests = 0, windowSeconds = 60).isSafe)
        assertFalse(RateLimitRule(maxRequests = 11, windowSeconds = 60).isSafe)
        assertFalse(RateLimitRule(maxRequests = 3, windowSeconds = 3_601).isSafe)
    }

    @Test
    fun `validator preserves validated payload for dispatch`() {
        val manifest = BridgeManifest(
            protocol = "foodhome.bridge",
            contractVersion = "1.2.0",
            bridgeMajor = 1,
            supportedVersions = setOf(1),
            globalObjectName = "FoodHomeBridge",
            handshakeEventName = "foodhome:bridge-ready",
            nativeMode = NativeModeContract(
                protocol = "foodhome.native-mode",
                version = 1,
                globalObjectName = "FoodHomeNative",
                userAgentProduct = "FoodHomeNative/1",
                securityBoundary = false,
                trustedOriginOnly = true,
                mainFrameOnly = true,
            ),
            maxMessageBytes = 32_768,
            methods = setOf("share", "requestLocation"),
            phase0Capabilities = emptySet(),
            compiledCapabilities = setOf("share", "requestLocation"),
        )
        val result = BridgeRequestValidator(manifest).validate(
            """{"protocol":"foodhome.bridge","version":1,"requestId":"share-1","method":"share","payload":{"url":"https://foodhome.market/cart"}}""",
        ) as BridgeRequestResult.Accepted

        assertEquals("share-1", result.request.requestId)
        assertEquals("share", result.request.method)
        assertEquals("https://foodhome.market/cart", result.request.payload.getString("url"))
    }

    @Test
    fun `typed success response contains one result and no error`() {
        val manifest = testManifest()
        val json = org.json.JSONObject(
            BridgeResponses.success(
                manifest,
                "share-1",
                org.json.JSONObject().put("capability", "share").put("status", "presented"),
            ),
        )

        assertTrue(json.getBoolean("ok"))
        assertNotNull(json.getJSONObject("result"))
        assertFalse(json.has("error"))
    }

    @Test
    fun `phase four payment control payloads fail closed`() {
        val policy = BridgePayloadPolicy(origin, 32_768)

        assertTrue(
            policy.accepts(
                "openPayment",
                org.json.JSONObject()
                    .put("url", "https://pay.example.invalid/checkout")
                    .put("recoveryContext", "opaque-context")
                    .put("expiresAt", "2026-08-29T12:00:00Z"),
            ),
        )
        assertTrue(
            policy.accepts(
                "ackNativeEvent",
                org.json.JSONObject().put("eventId", "payment-event-1"),
            ),
        )
        assertTrue(
            policy.accepts(
                "clearPaymentRecovery",
                org.json.JSONObject()
                    .put("recoveryContext", "opaque-context")
                    .put("reason", "terminal"),
            ),
        )
        assertFalse(
            policy.accepts(
                "openPayment",
                org.json.JSONObject()
                    .put("url", "https://pay.example.invalid/checkout")
                    .put("recoveryContext", "opaque-context"),
            ),
        )
        assertFalse(
            policy.accepts(
                "clearPaymentRecovery",
                org.json.JSONObject()
                    .put("recoveryContext", "opaque-context")
                    .put("reason", "paid"),
            ),
        )
    }

    private fun testManifest() = BridgeManifest(
        protocol = "foodhome.bridge",
        contractVersion = "1.2.0",
        bridgeMajor = 1,
        supportedVersions = setOf(1),
        globalObjectName = "FoodHomeBridge",
        handshakeEventName = "foodhome:bridge-ready",
        nativeMode = NativeModeContract(
            "foodhome.native-mode", 1, "FoodHomeNative", "FoodHomeNative/1", false, true, true,
        ),
        maxMessageBytes = 32_768,
        methods = setOf("share"),
        phase0Capabilities = emptySet(),
    )
}
