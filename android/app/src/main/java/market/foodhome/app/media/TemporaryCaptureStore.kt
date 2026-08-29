package market.foodhome.app.media

import java.io.File
import java.util.UUID

class TemporaryCaptureStore(
    cacheDirectory: File,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val captureDirectory = File(cacheDirectory, DIRECTORY_NAME)

    fun createImageFile(): File {
        check(captureDirectory.exists() || captureDirectory.mkdirs()) {
            "Unable to create capture cache"
        }
        return File(captureDirectory, "capture-${UUID.randomUUID()}.jpg").apply {
            check(createNewFile()) { "Unable to create capture file" }
        }
    }

    fun delete(file: File?) {
        if (file == null || file.parentFile?.canonicalFile != captureDirectory.canonicalFile) return
        if (file.exists()) file.delete()
    }

    fun cleanupStale(maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS): Int {
        if (!captureDirectory.exists()) return 0
        val cutoff = nowMillis() - maxAgeMillis
        return captureDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith("capture-") && it.lastModified() < cutoff }
            .count { it.delete() }
    }

    companion object {
        const val DIRECTORY_NAME = "foodhome-capture"
        const val DEFAULT_MAX_AGE_MILLIS = 24 * 60 * 60 * 1_000L
    }
}
