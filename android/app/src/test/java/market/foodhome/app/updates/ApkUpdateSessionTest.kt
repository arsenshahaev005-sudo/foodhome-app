package market.foodhome.app.updates

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ApkUpdateSessionTest {
    private val now = 1_800_000_000_000L
    private val release = ApkRelease("0.2.10",
        "https://github.com/${ApkUpdatePolicy.REPOSITORY}/releases/download/v0.2.10/foodhome-0.2.10-android.apk",
        1234, "a".repeat(64))
    private fun session(stage: UpdateStage = UpdateStage.Ready, file: String? = "update-123.apk") =
        ApkUpdateSession(release, stage, now - 1000, file)
    private fun restore(value: String) = ApkUpdateSessionPolicy.restore(value, "0.2.9", now)
    private fun json() = JSONObject(ApkUpdateSessionPolicy.encode(session()))

    @Test fun `ready survives cold recreation`() {
        assertEquals(session(), restore(ApkUpdateSessionPolicy.encode(session())))
    }
    @Test fun `permission and verification restore only explicit install button`() {
        for (stage in listOf(UpdateStage.Permission, UpdateStage.Verify)) {
            assertEquals(session(), restore(ApkUpdateSessionPolicy.encode(session(stage))))
        }
    }
    @Test fun `interrupted download never restarts itself`() {
        val restored = restore(ApkUpdateSessionPolicy.encode(session(UpdateStage.Download)))
        assertEquals(UpdateStage.Offer, restored?.stage)
        assertNull(restored?.fileName)
    }
    @Test fun `error restores retry without trusting a cached file`() {
        val restored = restore(ApkUpdateSessionPolicy.encode(session(UpdateStage.Error)))
        assertEquals(UpdateStage.Error, restored?.stage)
        assertNull(restored?.fileName)
    }
    @Test fun `missing cache name restores download offer`() {
        assertEquals(UpdateStage.Offer, restore(ApkUpdateSessionPolicy.encode(session(file = null)))?.stage)
    }
    @Test fun `already installed or newer app discards session`() {
        val value = ApkUpdateSessionPolicy.encode(session())
        assertNull(ApkUpdateSessionPolicy.restore(value, "0.2.10", now))
        assertNull(ApkUpdateSessionPolicy.restore(value, "0.3.0", now))
    }
    @Test fun `expired future and invalid time discarded`() {
        for (timestamp in listOf(0L, -1L, now + 1, now - ApkUpdateSessionPolicy.MAX_AGE_MS - 1)) {
            assertNull(restore(json().put("offeredAt", timestamp).toString()))
        }
    }
    @Test fun `unknown schema stage malformed or oversized data discarded`() {
        assertNull(restore(json().put("schema", 2).toString()))
        assertNull(restore(json().put("stage", "InstallAutomatically").toString()))
        assertNull(restore("{"))
        assertNull(restore("x".repeat(4097)))
        assertNull(ApkUpdateSessionPolicy.restore(null, "0.2.9", now))
    }
    @Test fun `restored descriptor uses full network policy`() {
        for ((key, value) in listOf("version" to "0.2.10-beta", "url" to "https://evil.example/app.apk",
            "sha256" to "bad", "size" to 0L, "size" to ApkUpdatePolicy.MAX_APK_BYTES + 1)) {
            assertNull(restore(json().put(key, value).toString()))
        }
    }
    @Test fun `absolute relative media and traversal paths rejected`() {
        for (name in listOf("../update-123.apk", "/tmp/update-123.apk", "C:\\update-123.apk",
            "foodhome-capture/image.jpg", "update-123.apk/other", "update-.apk")) {
            assertNull(restore(json().put("fileName", name).toString()))
        }
    }
    @Test fun `cache file resolution stays in updater directory`() {
        val directory = Files.createTempDirectory("foodhome-session-test").toFile()
        try {
            assertEquals(directory.canonicalFile,
                ApkUpdateSessionPolicy.cachedFile(directory, "update-123.apk").canonicalFile.parentFile)
            assertTrue(runCatching { ApkUpdateSessionPolicy.cachedFile(directory, "../update-123.apk") }.isFailure)
        } finally { directory.delete() }
    }
    @Test fun `session payload small with no absolute path`() {
        val value = ApkUpdateSessionPolicy.encode(session())
        assertTrue(value.length < 1024)
        assertEquals("update-123.apk", JSONObject(value).getString("fileName"))
    }
}
