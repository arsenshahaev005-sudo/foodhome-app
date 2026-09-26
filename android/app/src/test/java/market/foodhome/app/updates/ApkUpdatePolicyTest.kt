package market.foodhome.app.updates

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.security.MessageDigest

class ApkUpdatePolicyTest {
    private val data = "signed APK fixture bytes".toByteArray()
    private val digest = ApkUpdatePolicy.hex(MessageDigest.getInstance("SHA-256").digest(data))
    private fun metadata(): JSONObject = JSONObject().put("draft", false).put("prerelease", false)
        .put("tag_name", "v0.2.10").put("assets", JSONArray().put(JSONObject()
            .put("name", "foodhome-0.2.10-android.apk")
            .put("browser_download_url", "https://github.com/${ApkUpdatePolicy.REPOSITORY}/releases/download/v0.2.10/foodhome-0.2.10-android.apk")
            .put("digest", "sha256:$digest").put("size", data.size).put("state", "uploaded")))
    private fun asset(json: JSONObject): JSONObject = json.getJSONArray("assets").getJSONObject(0)
    private fun release(): ApkRelease = requireNotNull(ApkUpdatePolicy.parse(metadata().toString(), "0.2.9"))
    private fun rejects(json: JSONObject) {
        assertTrue(runCatching { ApkUpdatePolicy.parse(json.toString(), "0.2.9") }.isFailure)
    }

    @Test fun `numeric versions not lexical versions`() {
        assertTrue(ApkUpdatePolicy.newer("0.2.10", "0.2.9"))
        assertTrue(ApkUpdatePolicy.newer("1.0.0", "0.99.99"))
        assertFalse(ApkUpdatePolicy.newer("0.2.9", "0.2.9"))
        assertFalse(ApkUpdatePolicy.newer("0.2.8", "0.2.9"))
    }
    @Test fun `only stable unambiguous semantic versions`() {
        for (version in listOf("0.2.10-beta", "01.2.10", "v0.2.10", "9999999.0.0", "0.2", "0.2.10\n")) {
            assertFalse(ApkUpdatePolicy.newer(version, "0.2.9"))
        }
    }
    @Test fun `published exact asset selected`() { assertEquals("0.2.10", release().version) }
    @Test fun `draft and prerelease ignored`() {
        assertNull(ApkUpdatePolicy.parse(metadata().put("draft", true).toString(), "0.2.9"))
        assertNull(ApkUpdatePolicy.parse(metadata().put("prerelease", true).toString(), "0.2.9"))
    }
    @Test fun `old and same releases ignored`() {
        assertNull(ApkUpdatePolicy.parse(metadata().toString(), "0.2.10"))
        assertNull(ApkUpdatePolicy.parse(metadata().toString(), "1.0.0"))
    }
    @Test fun `digest required`() {
        val json = metadata(); asset(json).remove("digest"); rejects(json)
    }
    @Test fun `malformed digest rejected`() {
        val json = metadata(); asset(json).put("digest", "sha1:$digest"); rejects(json)
    }
    @Test fun `external asset URL rejected`() {
        val json = metadata(); asset(json).put("browser_download_url", "https://example.com/app.apk"); rejects(json)
    }
    @Test fun `duplicate asset rejected`() {
        val json = metadata(); json.getJSONArray("assets").put(asset(json)); rejects(json)
    }
    @Test fun `missing asset rejected`() { rejects(metadata().put("assets", JSONArray())) }
    @Test fun `unpublished asset rejected`() {
        val json = metadata(); asset(json).put("state", "new"); rejects(json)
    }
    @Test fun `invalid sizes rejected`() {
        for (size in listOf(0L, -1L, ApkUpdatePolicy.MAX_APK_BYTES + 1)) {
            val json = metadata(); asset(json).put("size", size); rejects(json)
        }
    }
    @Test fun `redirect host scheme and credentials constrained`() {
        assertTrue(ApkUpdatePolicy.allowedDownloadUrl(release().url))
        assertTrue(ApkUpdatePolicy.allowedDownloadUrl("https://release-assets.githubusercontent.com/file?sig=value"))
        for (url in listOf("http://github.com/file", "https://evil.example/a.apk", "https://github.com.evil.example/a",
            "https://user@release-assets.githubusercontent.com/file", "https://github.com/other/repo/releases/download/a/b",
            "https://release-assets.githubusercontent.com:443/file", "https://release-assets.githubusercontent.com/file#fragment")) {
            assertFalse(url, ApkUpdatePolicy.allowedDownloadUrl(url))
        }
    }
    @Test fun `archive must match owner identity and newer build`() {
        fun valid(packageName: String = "market.foodhome.app", version: String = "0.2.10", code: Long = 12,
            minSdk: Int = 26, debuggable: Boolean = false, cert: Set<String> = setOf(ApkUpdatePolicy.CERTIFICATE_SHA256)) =
            ApkUpdatePolicy.archiveMatches(release(), packageName, version, code, 11, minSdk, 34, debuggable, cert)
        assertTrue(valid())
        assertFalse(valid(packageName = "other.app"))
        assertFalse(valid(version = "0.2.11"))
        assertFalse(valid(code = 11))
        assertFalse(valid(minSdk = 35))
        assertFalse(valid(debuggable = true))
        assertFalse(valid(cert = emptySet()))
        assertFalse(valid(cert = setOf("attacker")))
        assertFalse(valid(cert = setOf(ApkUpdatePolicy.CERTIFICATE_SHA256, "attacker")))
    }
    @Test fun `archive sdk compatibility includes Android 13 and newer`() {
        for (sdk in listOf(26, 29, 33, 34, 35, 36)) {
            assertTrue(ApkUpdatePolicy.archiveMatches(release(), "market.foodhome.app", "0.2.10",
                12, 11, 26, sdk, false, setOf(ApkUpdatePolicy.CERTIFICATE_SHA256)))
            assertFalse(ApkUpdatePolicy.archiveMatches(release(), "market.foodhome.app", "0.2.10",
                12, 11, sdk + 1, sdk, false, setOf(ApkUpdatePolicy.CERTIFICATE_SHA256)))
        }
    }
    @Test fun `complete verified download preserved`() {
        val file = Files.createTempFile("foodhome-update-test", ".apk").toFile()
        try {
            val progress = mutableListOf<Int>()
            ApkUpdatePolicy.copyVerified(ByteArrayInputStream(data), file, release(), progress = progress::add)
            assertArrayEquals(data, file.readBytes()); assertEquals(100, progress.last())
        } finally { file.delete() }
    }
    @Test fun `corrupt truncated oversized and cancelled downloads removed`() {
        for (bytes in listOf("corrupt".toByteArray(), data.dropLast(1).toByteArray(), data + byteArrayOf(1))) {
            val file = Files.createTempFile("foodhome-update-test", ".apk").toFile()
            assertTrue(runCatching { ApkUpdatePolicy.copyVerified(ByteArrayInputStream(bytes), file, release()) }.isFailure)
            assertFalse(file.exists())
        }
        val file = Files.createTempFile("foodhome-update-test", ".apk").toFile()
        assertTrue(runCatching {
            ApkUpdatePolicy.copyVerified(ByteArrayInputStream(data), file, release(), { error("Cancelled") })
        }.isFailure)
        assertFalse(file.exists())
    }
    @Test fun `oversized metadata bounded before parsing`() {
        assertTrue(runCatching { ApkUpdatePolicy.readLimited(ByteArrayInputStream(ByteArray(11)), 10) }.isFailure)
        assertEquals(10, ApkUpdatePolicy.readLimited(ByteArrayInputStream(ByteArray(10)), 10).size)
    }
}
