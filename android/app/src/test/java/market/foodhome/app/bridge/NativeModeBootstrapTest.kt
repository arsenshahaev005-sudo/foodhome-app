package market.foodhome.app.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class NativeModeBootstrapTest {
    private val contract = NativeModeContract(
        protocol = "foodhome.native-mode",
        version = 1,
        globalObjectName = "FoodHomeNative",
        userAgentProduct = "FoodHomeNative/1",
        securityBoundary = false,
        trustedOriginOnly = true,
        mainFrameOnly = true,
    )

    @Test
    fun `user agent product is appended once`() {
        val original = "Mozilla/5.0 Android"
        val augmented = NativeModeBootstrap.userAgent(original, contract)

        assertEquals("Mozilla/5.0 Android FoodHomeNative/1", augmented)
        assertEquals(augmented, NativeModeBootstrap.userAgent(augmented, contract))
    }

    @Test
    fun `document start marker is exact origin main frame and minimal`() {
        val script = NativeModeBootstrap.documentStartScript(
            contract = contract,
            trustedOrigin = URI("https://foodhome.market"),
            platform = "android",
        )

        assertTrue(script.contains("window !== window.top"))
        assertTrue(script.contains("window.location.origin !== \"https://foodhome.market\""))
        assertTrue(script.contains("\"protocol\":\"foodhome.native-mode\""))
        assertTrue(script.contains("\"version\":1"))
        assertTrue(script.contains("\"platform\":\"android\""))
        assertTrue(script.contains("Object.freeze"))
        assertFalse(script.contains("token"))
        assertFalse(script.contains("userId"))
        assertFalse(script.contains("installation"))
        assertFalse(script.contains("*://"))
    }
}
