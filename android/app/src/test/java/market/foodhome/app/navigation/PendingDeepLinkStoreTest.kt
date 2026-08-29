package market.foodhome.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class PendingDeepLinkStoreTest {
    private val store = PendingDeepLinkStore(NavigationPolicy(URI("https://foodhome.market")))

    @Test
    fun `pending internal route waits for bridge readiness and is consumed once`() {
        assertTrue(store.offer("https://foodhome.market/orders/123"))
        assertNull(store.consumeWhenReady(isBridgeReady = false))
        assertEquals(
            "https://foodhome.market/orders/123",
            store.consumeWhenReady(isBridgeReady = true),
        )
        assertNull(store.consumeWhenReady(isBridgeReady = true))
    }

    @Test
    fun `external route is never stored`() {
        assertFalse(store.offer("https://evil.example/orders/123"))
        assertNull(store.consumeWhenReady(isBridgeReady = true))
    }
}
