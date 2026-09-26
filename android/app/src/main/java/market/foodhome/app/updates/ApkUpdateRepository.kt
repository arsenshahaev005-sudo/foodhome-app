package market.foodhome.app.updates

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Dedicated unauthenticated HTTP client: never sends website cookies, tokens or device identifiers. */
internal class ApkUpdateRepository(
    private val context: Context,
    private val client: OkHttpClient = defaultClient(),
) {
    companion object {
        private fun defaultClient() = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS).build()
    }
    private val directory = File(context.cacheDir, "foodhome-updates")

    fun cancel() { client.dispatcher.cancelAll() }

    fun cachedFile(name: String): File = ApkUpdateSessionPolicy.cachedFile(directory, name)

    suspend fun latest(installedVersion: String): ApkRelease? = withContext(Dispatchers.IO) {
        // Never offer owner-signed updates to a debug or differently signed installation.
        require(signers(installed()) == setOf(ApkUpdatePolicy.CERTIFICATE_SHA256))
        client.newCall(Request.Builder().url(ApkUpdatePolicy.LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "FoodHome-Android-Updater")
            .header("X-GitHub-Api-Version", "2022-11-28").build()).execute().use { response ->
            if (response.code == 404) return@withContext null
            require(response.code == 200)
            val body = requireNotNull(response.body)
            require(body.contentLength() <= 256 * 1024)
            val bytes = body.byteStream().use { ApkUpdatePolicy.readLimited(it, 256 * 1024) }
            currentCoroutineContext().ensureActive()
            ApkUpdatePolicy.parse(bytes.toString(Charsets.UTF_8), installedVersion)
        }
    }

    suspend fun download(release: ApkRelease, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        require(directory.mkdirs() || directory.isDirectory)
        // Only updater-owned cache files, never account data, media or any external storage.
        directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        require(directory.usableSpace > release.size + 8 * 1024 * 1024)
        val file = File.createTempFile("update-", ".apk", directory)
        try {
            downloadResponse(release.url).use { response ->
                require(response.code == 200)
                val body = requireNotNull(response.body)
                require(body.contentLength() == -1L || body.contentLength() == release.size)
                val coroutine = currentCoroutineContext()
                body.byteStream().use { input ->
                    ApkUpdatePolicy.copyVerified(input, file, release, { coroutine.ensureActive() }, progress)
                }
            }
            validate(file, release)
            file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    /** Recheck immediately before giving the URI to Android, including after settings return. */
    suspend fun validate(file: File, release: ApkRelease) = withContext(Dispatchers.IO) {
        require(file.canonicalFile.parentFile == directory.canonicalFile && file.isFile)
        require(file.length() == release.size)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        require(ApkUpdatePolicy.hex(digest.digest()) == release.sha256)
        val installed = installed()
        require(signers(installed) == setOf(ApkUpdatePolicy.CERTIFICATE_SHA256))
        @Suppress("DEPRECATION")
        // Some Android 9/10 package parsers only collect archive certificates when
        // the legacy flag is also present. Still read current signers from SigningInfo
        // on API 28+, never accept the legacy oldest signer as a rotation fallback.
        val archive = requireNotNull(context.packageManager.getPackageArchiveInfo(
            file.path, signingFlags() or PackageManager.GET_SIGNATURES,
        ))
        val app = requireNotNull(archive.applicationInfo)
        require(ApkUpdatePolicy.archiveMatches(
            release, archive.packageName, archive.versionName, code(archive), code(installed),
            app.minSdkVersion, Build.VERSION.SDK_INT,
            app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0, signers(archive),
        )) { "APK identity check failed" }
        // Android's package installer performs final APK signature verification and compatibility checks.
    }

    private fun downloadResponse(initial: String): Response {
        var url = initial
        repeat(5) {
            require(ApkUpdatePolicy.allowedDownloadUrl(url))
            val response = client.newCall(Request.Builder().url(url)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "FoodHome-Android-Updater").build()).execute()
            if (response.code !in setOf(301, 302, 303, 307, 308)) return response
            response.use {
                val location = requireNotNull(it.header("Location"))
                url = requireNotNull(it.request.url.resolve(location)).toString()
            }
        }
        error("Too many APK redirects")
    }

    @Suppress("DEPRECATION")
    private fun installed(): PackageInfo = context.packageManager.getPackageInfo(context.packageName, signingFlags())

    private fun signingFlags(): Int = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners
            else info.signatures
        return signatures.orEmpty().map {
            ApkUpdatePolicy.hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()))
        }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun code(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
        else info.versionCode.toLong()
}
