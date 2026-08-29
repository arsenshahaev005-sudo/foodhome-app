package market.foodhome.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRequestPolicyTest {
    @Test
    fun `image input uses visual picker and can offer camera`() {
        val result = MediaRequestPolicy.resolve(
            MediaRequest(listOf(" IMAGE/PNG ", "image/jpeg"), captureEnabled = false, allowMultiple = false),
        )

        assertEquals(VisualMediaKind.Images, result.kind)
        assertEquals(listOf("image/png", "image/jpeg"), result.acceptedTypes)
        assertTrue(result.offerCamera)
    }

    @Test
    fun `multiple input never offers single camera capture`() {
        val result = MediaRequestPolicy.resolve(
            MediaRequest(listOf("image/*"), captureEnabled = true, allowMultiple = true),
        )

        assertEquals(VisualMediaKind.Images, result.kind)
        assertFalse(result.offerCamera)
        assertTrue(result.allowMultiple)
    }

    @Test
    fun `non visual mime type uses document picker`() {
        val result = MediaRequestPolicy.resolve(
            MediaRequest(listOf("application/pdf"), captureEnabled = false, allowMultiple = false),
        )
        assertEquals(VisualMediaKind.Documents, result.kind)
    }
}
