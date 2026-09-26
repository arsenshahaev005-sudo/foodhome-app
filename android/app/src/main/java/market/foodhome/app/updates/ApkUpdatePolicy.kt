package market.foodhome.app.updates

import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.MessageDigest

internal data class ApkRelease(val version: String, val url: String, val size: Long, val sha256: String)

/** Only stable, owner-published APKs from the existing public repository. No web/bridge input. */
internal object ApkUpdatePolicy {
    const val REPOSITORY = "arsenshahaev005-sudo/foodhome-app"
    const val LATEST_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"
    const val CERTIFICATE_SHA256 = "b3e8823f71ad073653b0314f40f13fd902f9b5850458674c1b7db59d9f5e2645"
    const val MAX_APK_BYTES = 64L * 1024 * 1024
    private val versionPattern = Regex("(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})")

    fun newer(candidate: String, installed: String): Boolean {
        if (!versionPattern.matches(candidate) || !versionPattern.matches(installed)) return false
        val left = candidate.split('.').map(String::toInt)
        val right = installed.split('.').map(String::toInt)
        for (index in left.indices) {
            if (left[index] != right[index]) return left[index] > right[index]
        }
        return false
    }

    fun parse(json: String, installed: String): ApkRelease? {
        require(json.toByteArray().size <= 256 * 1024)
        val release = JSONObject(json)
        if (release.getBoolean("draft") || release.getBoolean("prerelease")) return null
        val tag = release.getString("tag_name")
        if (!tag.startsWith("v")) return null
        val version = tag.substring(1)
        if (!newer(version, installed)) return null
        val assets = release.getJSONArray("assets")
        val expectedName = "foodhome-$version-android.apk"
        val matches = (0 until assets.length()).map(assets::getJSONObject)
            .filter { it.getString("name") == expectedName }
        require(matches.size == 1) { "Missing or ambiguous APK asset" }
        val asset = matches.single()
        val url = asset.getString("browser_download_url")
        require(url == "https://github.com/$REPOSITORY/releases/download/$tag/$expectedName")
        val digest = asset.getString("digest")
        require(Regex("sha256:[0-9a-f]{64}").matches(digest)) { "Missing APK digest" }
        val size = asset.getLong("size")
        require(size in 1..MAX_APK_BYTES)
        require(asset.getString("state") == "uploaded")
        return ApkRelease(version, url, size, digest.removePrefix("sha256:"))
    }

    fun allowedDownloadUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.userInfo == null && uri.port == -1 && uri.fragment == null &&
            when (uri.host) {
                "github.com" -> uri.path.startsWith("/$REPOSITORY/releases/download/") && uri.query == null
                "release-assets.githubusercontent.com", "objects.githubusercontent.com" -> true
                else -> false
            }
    }.getOrDefault(false)

    fun archiveMatches(
        release: ApkRelease, packageName: String?, versionName: String?, versionCode: Long,
        installedCode: Long, minSdk: Int, deviceSdk: Int, debuggable: Boolean, signers: Set<String>,
    ): Boolean = packageName == "market.foodhome.app" && versionName == release.version &&
        versionCode > installedCode && minSdk <= deviceSdk && !debuggable &&
        signers == setOf(CERTIFICATE_SHA256)

    fun copyVerified(
        input: InputStream, file: File, release: ApkRelease,
        checkCancelled: () -> Unit = {}, progress: (Int) -> Unit = {},
    ) {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            var lastPercent = -1
            file.outputStream().use { output ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    checkCancelled()
                    val count = input.read(buffer)
                    if (count == -1) break
                    total += count
                    require(total <= release.size && total <= MAX_APK_BYTES)
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    val percent = (total * 100 / release.size).toInt()
                    if (percent != lastPercent) { progress(percent); lastPercent = percent }
                }
                output.fd.sync()
            }
            require(total == release.size && hex(digest.digest()) == release.sha256) { "APK integrity check failed" }
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) return output.toByteArray()
            require(output.size() + count <= limit)
            output.write(buffer, 0, count)
        }
    }
}
