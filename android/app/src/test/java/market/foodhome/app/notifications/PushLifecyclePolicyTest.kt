package market.foodhome.app.notifications

import market.foodhome.app.navigation.NavigationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URI

class PushLifecyclePolicyTest {
    private val policy = PushPayloadPolicy(NavigationPolicy(URI("https://foodhome.market")))

    @Test
    fun `minimal push payload resolves only trusted route`() {
        val parsed = policy.parse(
            mapOf(
                "eventId" to "order-123",
                "type" to "order.updated",
                "route" to "https://foodhome.market/orders/123",
            ),
        )
        assertEquals("https://foodhome.market/orders/123", parsed?.route)
        assertNull(policy.parse(mapOf("eventId" to "x", "route" to "https://evil.example/orders/1")))
        assertNull(
            policy.parse(
                mapOf(
                    "eventId" to "x",
                    "route" to "https://foodhome.market/orders/1",
                    "message" to "sensitive",
                ),
            ),
        )
    }

    @Test
    fun `token wrapper never renders raw token`() {
        val token = SensitivePushToken.from("raw-provider-token")
        assertNotNull(token)
        val requiredToken = requireNotNull(token)
        assertFalse(requiredToken.toString().contains("raw-provider-token"))
        assertEquals(12, requiredToken.fingerprint.length)
        assertEquals("raw-provider-token", requiredToken.use { it })
    }
}
