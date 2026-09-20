package market.foodhome.app.web

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import market.foodhome.app.bridge.BridgeManifest
import market.foodhome.app.bridge.NativeEventQueue
import market.foodhome.app.bridge.UnavailableBridgeCapabilityDispatcher
import market.foodhome.app.config.AppEnvironment
import market.foodhome.app.media.MediaRequestPolicy
import market.foodhome.app.navigation.NavigationCoordinator
import market.foodhome.app.navigation.NavigationPolicy
import market.foodhome.app.payments.PaymentCoordinator
import market.foodhome.app.payments.PaymentLaunchPolicy
import market.foodhome.app.payments.PaymentLauncher
import market.foodhome.app.payments.PaymentRecoverySnapshot
import market.foodhome.app.payments.PaymentRecoveryStore
import market.foodhome.app.recovery.CrashLoopBreaker
import market.foodhome.app.telemetry.TelemetryReporter
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Synthetic local files only. No website requests, accounts, real documents or uploads. */
@RunWith(AndroidJUnit4::class)
class FoodHomeDocumentUploadTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun documentExtensionsBecomeMimeTypesAndSelectedContentIsReadableWithSecureSettings() {
        val activity = composeRule.activity
        val origin = URI("https://upload-test.invalid")
        val manifest = activity.assets.open("manifest.json").use(BridgeManifest::from)
        val events = NativeEventQueue(manifest, origin, PaymentCoordinator(
            PaymentLaunchPolicy(emptyList()), EmptyPaymentStore(), PaymentLauncher { false },
        ))
        val pickerTypes = AtomicReference<List<String>>()
        val chosenUri = AtomicReference<Uri>()
        composeRule.setContent {
            FoodHomeWebView(
                environment = AppEnvironment(origin, origin),
                navigationCoordinator = NavigationCoordinator(NavigationPolicy(origin)),
                manifest = manifest,
                crashLoopBreaker = CrashLoopBreaker(),
                initialUrl = "about:blank",
                onStateChanged = {}, onTrustedUrlCommitted = {}, onRendererGone = {}, onOpenExternal = {},
                capabilityDispatcher = UnavailableBridgeCapabilityDispatcher,
                nativeEventQueue = events, nativeEventRevision = 0,
                telemetry = TelemetryReporter.disabled(origin), onPaymentUserAction = {},
                onFileRequest = { callback, request ->
                    val resolved = MediaRequestPolicy.resolve(request)
                    val intent = ActivityResultContracts.OpenDocument().createIntent(
                        activity, resolved.acceptedTypes.toTypedArray(),
                    )
                    pickerTypes.set(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList())
                    // Simulate the successful user-selected picker result, not an HTTP upload.
                    callback.onReceiveValue(arrayOf(chosenUri.get()))
                    true
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composeRule.waitForIdle()
        val webView = composeRule.runOnIdle { checkNotNull(findWebView(activity.window.decorView)) }
        composeRule.runOnIdle {
            assertFalse(webView.settings.allowFileAccess)
            assertFalse(webView.settings.allowContentAccess)
        }
        val dir = File(activity.cacheDir, "foodhome-capture").apply { mkdirs() }
        val fixture = File(dir, "upload-qa-${System.nanoTime()}.pdf")
        try {
            val contents = "%PDF-1.4\nFoodHome synthetic document - not a certificate\n%%EOF"
            fixture.writeText(contents)
            chosenUri.set(FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", fixture))
            composeRule.runOnIdle {
                webView.loadDataWithBaseURL(null, """
                    <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body>
                    <input id="file" type="file" accept=".jpg,.jpeg,.png,.webp,.pdf,.doc,.docx">
                    <script>
                    window.result = {};
                    document.getElementById('file').onchange = async function() {
                      try { const f = this.files[0];
                        window.result = {name:f.name, type:f.type, text:await f.text()};
                      } catch (e) { window.result = {error:String(e)}; }
                    };
                    </script></body></html>
                """.trimIndent(), "text/html", "UTF-8", null)
            }
            composeRule.waitUntil(10_000) { evaluate(webView, "({ready:!!document.getElementById('file')})").optBoolean("ready") }
            // Chromium requires user activation; a JavaScript-only click is correctly ignored.
            val rect = evaluate(webView, """
                (() => { const r = document.getElementById('file').getBoundingClientRect();
                  return {x:r.left + 20, y:r.top + r.height / 2, width:innerWidth}; })()
            """.trimIndent())
            composeRule.runOnUiThread {
                val scale = webView.width / rect.getDouble("width")
                val x = (rect.getDouble("x") * scale).toFloat()
                val y = (rect.getDouble("y") * scale).toFloat()
                val downTime = SystemClock.uptimeMillis()
                for ((action, time) in listOf(MotionEvent.ACTION_DOWN to downTime, MotionEvent.ACTION_UP to downTime + 100)) {
                    val event = MotionEvent.obtain(downTime, time, action, x, y, 0)
                    try { webView.dispatchTouchEvent(event) } finally { event.recycle() }
                }
            }
            composeRule.waitUntil(10_000) { pickerTypes.get() != null }
            assertEquals(listOf("image/jpeg", "image/png", "image/webp", "application/pdf", "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"), pickerTypes.get())
            composeRule.waitUntil(10_000) { evaluate(webView, "window.result").has("text") }
            val result = evaluate(webView, "window.result")
            assertEquals(contents, result.getString("text"))
            assertEquals("application/pdf", result.getString("type"))
        } finally {
            fixture.delete()
        }
    }

    private fun evaluate(webView: WebView, expression: String): JSONObject {
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        composeRule.runOnUiThread {
            webView.evaluateJavascript("JSON.stringify($expression)") { result.set(it); completed.countDown() }
        }
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        return JSONObject(JSONTokener(result.get()).nextValue() as String)
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private class EmptyPaymentStore : PaymentRecoveryStore {
        override fun read(): PaymentRecoverySnapshot? = null
        override fun write(snapshot: PaymentRecoverySnapshot): Boolean = false
        override fun clear(): Boolean = true
    }
}
