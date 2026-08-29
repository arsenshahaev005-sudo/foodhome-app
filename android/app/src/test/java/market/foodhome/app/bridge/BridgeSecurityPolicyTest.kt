package market.foodhome.app.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class BridgeSecurityPolicyTest {
    private val manifest = BridgeManifest(
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
        maxMessageBytes = 512,
        methods = setOf("share"),
        phase0Capabilities = emptySet(),
    )

    @Test
    fun `bridge requires exact origin and main frame`() {
        val policy = BridgeOriginPolicy(URI("https://foodhome.market"))
        assertTrue(policy.accepts("https://foodhome.market", isMainFrame = true))
        assertFalse(policy.accepts("https://foodhome.market", isMainFrame = false))
        assertFalse(policy.accepts("https://cdn.foodhome.market", isMainFrame = true))
        assertFalse(policy.accepts("http://foodhome.market", isMainFrame = true))
        assertFalse(policy.accepts("https://foodhome.market:444", isMainFrame = true))
    }

    @Test
    fun `unknown method and version return typed rejection`() {
        val validator = BridgeRequestValidator(manifest)
        val unknownMethod = validator.validate(
            """{"protocol":"foodhome.bridge","version":1,"requestId":"r1","method":"eval","payload":{}}""",
        ) as BridgeRequestResult.Rejected
        val unsupportedVersion = validator.validate(
            """{"protocol":"foodhome.bridge","version":2,"requestId":"r2","method":"share","payload":{}}""",
        ) as BridgeRequestResult.Rejected

        assertEquals("METHOD_NOT_SUPPORTED", unknownMethod.code)
        assertEquals("VERSION_NOT_SUPPORTED", unsupportedVersion.code)
    }

    @Test
    fun `message size counts utf8 bytes`() {
        val validator = BridgeRequestValidator(manifest.copy(maxMessageBytes = 4))
        val result = validator.validate("яяя") as BridgeRequestResult.Rejected
        assertEquals("PAYLOAD_TOO_LARGE", result.code)
    }

    @Test
    fun `deep and prototype shaped json fails closed`() {
        val strict = manifest.copy(maxJsonDepth = 4, maxJsonNodes = 64)
        val validator = BridgeRequestValidator(strict)
        val deep = validator.validate(
            """{"protocol":"foodhome.bridge","version":1,"requestId":"r1","method":"share","payload":{"url":"https://foodhome.market","future":{"a":{"b":{"c":1}}}}}""",
        ) as BridgeRequestResult.Rejected
        val prototype = validator.validate(
            """{"protocol":"foodhome.bridge","version":1,"requestId":"r2","method":"share","payload":{"url":"https://foodhome.market","__proto__":{}}}""",
        ) as BridgeRequestResult.Rejected

        assertEquals("INVALID_MESSAGE", deep.code)
        assertEquals("INVALID_MESSAGE", prototype.code)
    }

    @Test
    fun `terminal reply completes once`() {
        val terminal = TerminalReply()
        var calls = 0
        assertTrue(terminal.complete { calls += 1 })
        assertFalse(terminal.complete { calls += 1 })
        assertEquals(1, calls)
    }

    @Test
    fun `phase one handshake uses canonical event and advertises no capability`() {
        val script = BridgeHandshakeScript.create(
            manifest = manifest,
            appVersion = "0.1.0",
            buildNumber = "1",
            platform = "android",
        )

        assertTrue(script.contains("foodhome:bridge-ready"))
        assertTrue(script.contains("\"capabilities\":[]"))
        assertFalse(script.contains("\"capabilities\":[\"share\"]"))
    }
}
