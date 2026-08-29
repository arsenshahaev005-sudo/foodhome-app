package market.foodhome.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class NavigationCoordinatorTest {
    private val coordinator = NavigationCoordinator(
        NavigationPolicy(URI("https://foodhome.market")),
    )

    @Test
    fun `trusted deep link waits for ready document and is delivered once`() {
        val navigations = mutableListOf<String>()
        val attachment = coordinator.attach(navigations::add)
        val document = requireNotNull(coordinator.markTrustedDocumentLoading(attachment))

        assertTrue(coordinator.offerDeepLink("https://foodhome.market/orders/123"))
        assertTrue(navigations.isEmpty())

        coordinator.markTrustedDocumentReady(attachment, document)
        coordinator.markTrustedDocumentReady(attachment, document)

        assertEquals(listOf("https://foodhome.market/orders/123"), navigations)

        assertTrue(coordinator.offerDeepLink("https://foodhome.market/cart"))
        assertEquals(1, navigations.size)
        val nextDocument = requireNotNull(coordinator.markTrustedDocumentLoading(attachment))
        coordinator.markTrustedDocumentReady(attachment, nextDocument)
        assertEquals("https://foodhome.market/cart", navigations.last())
    }

    @Test
    fun `new generation resets readiness and external route is rejected`() {
        val firstGeneration = mutableListOf<String>()
        val firstAttachment = coordinator.attach(firstGeneration::add)
        val firstDocument = requireNotNull(
            coordinator.markTrustedDocumentLoading(firstAttachment),
        )
        coordinator.markTrustedDocumentReady(firstAttachment, firstDocument)
        coordinator.detach(firstAttachment)

        val secondGeneration = mutableListOf<String>()
        val secondAttachment = coordinator.attach(secondGeneration::add)

        assertTrue(coordinator.offerDeepLink("https://foodhome.market/cart"))
        assertFalse(coordinator.offerDeepLink("https://evil.example/cart"))
        assertTrue(secondGeneration.isEmpty())

        coordinator.markTrustedDocumentReady(firstAttachment, firstDocument)
        assertTrue(secondGeneration.isEmpty())
        val secondDocument = requireNotNull(
            coordinator.markTrustedDocumentLoading(secondAttachment),
        )
        coordinator.markTrustedDocumentReady(secondAttachment, secondDocument)

        assertEquals(listOf("https://foodhome.market/cart"), secondGeneration)
    }
}
