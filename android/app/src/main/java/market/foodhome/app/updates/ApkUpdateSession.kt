package market.foodhome.app.updates

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal enum class UpdateStage { Offer, Download, Ready, Permission, Verify, Error }

/** Small private pending state, never account data, absolute paths or CDN credentials. */
internal data class ApkUpdateSession(
    val release: ApkRelease,
    val stage: UpdateStage,
    val offeredAt: Long,
    val fileName: String? = null,
)

internal object ApkUpdateSessionPolicy {
    const val PREFERENCE_KEY = "pending_session_v1"
    const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    private val cacheName = Regex("update-[A-Za-z0-9-]+\\.apk")

    fun encode(session: ApkUpdateSession): String = JSONObject()
        .put("schema", 1).put("version", session.release.version)
        .put("url", session.release.url).put("size", session.release.size)
        .put("sha256", session.release.sha256).put("stage", session.stage.name)
        .put("offeredAt", session.offeredAt)
        .put("fileName", session.fileName ?: JSONObject.NULL).toString()

    fun restore(json: String?, installedVersion: String, now: Long): ApkUpdateSession? = runCatching {
        if (json == null || json.length > 4096) return null
        val saved = JSONObject(json)
        require(saved.getInt("schema") == 1)
        val offeredAt = saved.getLong("offeredAt")
        require(offeredAt > 0 && offeredAt <= now && now - offeredAt <= MAX_AGE_MS)
        val version = saved.getString("version")
        // Reuse the same strict stable-version, URL, size and digest rules as network metadata.
        val metadata = JSONObject().put("draft", false).put("prerelease", false)
            .put("tag_name", "v$version").put("assets", JSONArray().put(JSONObject()
                .put("name", "foodhome-$version-android.apk")
                .put("browser_download_url", saved.getString("url"))
                .put("size", saved.getLong("size"))
                .put("digest", "sha256:${saved.getString("sha256")}").put("state", "uploaded")))
        val release = ApkUpdatePolicy.parse(metadata.toString(), installedVersion) ?: return null
        val stage = UpdateStage.valueOf(saved.getString("stage"))
        val name = if (saved.isNull("fileName")) null else saved.getString("fileName")
        require(name == null || cacheName.matches(name))
        val hasFile = name != null && stage in setOf(UpdateStage.Ready, UpdateStage.Permission, UpdateStage.Verify)
        ApkUpdateSession(
            release, when {
                hasFile -> UpdateStage.Ready // Resume a button, never installer/settings actions.
                stage == UpdateStage.Error -> UpdateStage.Error
                else -> UpdateStage.Offer // Interrupted download needs another explicit tap.
            }, offeredAt, if (hasFile) name else null,
        )
    }.getOrNull()

    fun cachedFile(directory: File, name: String): File {
        require(cacheName.matches(name))
        return File(directory, name).also {
            require(it.canonicalFile.parentFile == directory.canonicalFile)
        }
    }
}
