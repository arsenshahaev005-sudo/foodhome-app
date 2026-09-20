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

    @Test
    fun `seller document accept extensions become document picker mime types`() {
        val result = resolve(".jpg,.jpeg,.png,.webp,.pdf,.doc,.docx")
        assertEquals(VisualMediaKind.Documents, result.kind)
        assertEquals(listOf(
            "image/jpeg", "image/png", "image/webp", "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        ), result.acceptedTypes)
    }

    @Test
    fun `passport mixed extensions and mime types are deduplicated`() {
        val result = resolve("image/jpeg,image/png,image/webp,image/heif,image/heic,.heif,.heic")
        assertEquals(VisualMediaKind.Images, result.kind)
        assertEquals(listOf("image/jpeg", "image/png", "image/webp", "image/heif", "image/heic"), result.acceptedTypes)
        assertTrue(result.offerCamera)
    }

    @Test
    fun `split arrays whitespace and uppercase are normalized`() {
        val result = MediaRequestPolicy.resolve(MediaRequest(listOf(" .PDF ", " IMAGE/JPEG,.JPG "), false, true))
        assertEquals(listOf("application/pdf", "image/jpeg"), result.acceptedTypes)
        assertTrue(result.allowMultiple)
        assertFalse(result.offerCamera)
    }

    @Test
    fun `empty and unrestricted accept allow documents as well as media`() {
        for (accept in listOf("", " , ", "*/*", "image/png,*/*")) {
            val result = resolve(accept)
            assertEquals(listOf("*/*"), result.acceptedTypes)
            assertEquals(VisualMediaKind.Documents, result.kind)
        }
    }

    @Test
    fun `unresolved extensions do not silently hide otherwise selectable documents`() {
        for (accept in listOf(".unknownextension", "image/png,.unknownextension", "not-a-mime-type")) {
            val result = resolve(accept)
            assertEquals(listOf("*/*"), result.acceptedTypes)
            assertEquals(VisualMediaKind.Documents, result.kind)
        }
    }

    @Test
    fun `platform resolver supplements known extensions`() {
        val result = MediaRequestPolicy.resolve(MediaRequest(listOf(".custom"), false, false)) {
            if (it == "custom") "application/x-custom" else null
        }
        assertEquals(listOf("application/x-custom"), result.acceptedTypes)
    }

    @Test
    fun `valid mime types including vendor types survive unchanged`() {
        assertEquals(listOf("application/vnd.custom+json", "audio/*"),
            resolve("application/vnd.custom+json,audio/*").acceptedTypes)
    }

    @Test
    fun `later document formats are not truncated`() {
        val types = (1..10).map { "application/x-test$it" } + "application/pdf"
        assertEquals(types, MediaRequestPolicy.resolve(MediaRequest(types, false, false)).acceptedTypes)
    }

    private fun resolve(accept: String) = MediaRequestPolicy.resolve(MediaRequest(listOf(accept), false, false))
}
