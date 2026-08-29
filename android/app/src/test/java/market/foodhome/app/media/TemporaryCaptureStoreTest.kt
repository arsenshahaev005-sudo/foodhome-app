package market.foodhome.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TemporaryCaptureStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `cleanup deletes only stale owned captures`() {
        val now = 2 * TemporaryCaptureStore.DEFAULT_MAX_AGE_MILLIS
        val root = temporaryFolder.root
        val store = TemporaryCaptureStore(root) { now }
        val stale = store.createImageFile().apply { setLastModified(0) }
        val current = store.createImageFile().apply { setLastModified(now) }
        val unrelated = File(root, "keep.txt").apply { writeText("keep") }

        assertEquals(1, store.cleanupStale())
        assertFalse(stale.exists())
        assertTrue(current.exists())
        assertTrue(unrelated.exists())
    }
}
