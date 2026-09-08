package market.foodhome.app.web

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import market.foodhome.app.bridge.BridgeManifest
import market.foodhome.app.bridge.NativeEventQueue
import market.foodhome.app.bridge.UnavailableBridgeCapabilityDispatcher
import market.foodhome.app.config.AppEnvironment
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class FoodHomeWebViewLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cssViewportFollowsComposeBoundsAndKeepsInputsScrollable() {
        val bounds = mutableStateOf(300.dp to 240.dp)
        val origin = URI("https://layout-test.invalid")
        val environment = AppEnvironment(origin, origin)
        val manifest = composeRule.activity.assets.open("manifest.json").use(BridgeManifest::from)
        val navigation = NavigationCoordinator(NavigationPolicy(origin))
        val events = NativeEventQueue(
            manifest,
            origin,
            PaymentCoordinator(
                PaymentLaunchPolicy(emptyList()),
                EmptyPaymentStore(),
                PaymentLauncher { false },
            ),
        )
        val crashLoopBreaker = CrashLoopBreaker()
        val telemetry = TelemetryReporter.disabled(origin)

        composeRule.setContent {
            Box(Modifier.size(bounds.value.first, bounds.value.second)) {
                FoodHomeWebView(
                    environment = environment,
                    navigationCoordinator = navigation,
                    manifest = manifest,
                    crashLoopBreaker = crashLoopBreaker,
                    // Test-only blank document: no network, accounts, bridge requests or providers.
                    initialUrl = "about:blank",
                    onStateChanged = {},
                    onTrustedUrlCommitted = {},
                    onRendererGone = {},
                    onOpenExternal = {},
                    capabilityDispatcher = UnavailableBridgeCapabilityDispatcher,
                    nativeEventQueue = events,
                    nativeEventRevision = 0,
                    telemetry = telemetry,
                    onPaymentUserAction = {},
                    onFileRequest = { _, _ -> false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
        val webView = composeRule.runOnIdle {
            checkNotNull(findWebView(composeRule.activity.window.decorView))
        }
        composeRule.runOnIdle {
            webView.loadDataWithBaseURL(null, FIXTURE, "text/html", "UTF-8", null)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            evaluate(webView, "({ready: !!document.getElementById('field')})").getBoolean("ready")
        }

        assertViewport(webView, 300, 240)
        // Deterministic constraints exercise keyboard-like shrink/restore and width changes.
        // This is not a substitute for a physical keyboard/rotation test.
        for ((width, height) in listOf(300 to 140, 240 to 300, 300 to 240)) {
            composeRule.runOnIdle { bounds.value = width.dp to height.dp }
            assertViewport(webView, width, height)
        }
        composeRule.runOnIdle {
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, webView.layoutParams.width)
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, webView.layoutParams.height)
        }
    }

    private fun assertViewport(webView: WebView, width: Int, height: Int) {
        var metrics = JSONObject()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            metrics = evaluate(webView, METRICS)
            kotlin.math.abs(metrics.getDouble("innerWidth") - width) <= 2 &&
                kotlin.math.abs(metrics.getDouble("innerHeight") - height) <= 2
        }
        for (key in listOf("body", "vh", "dvh")) {
            assertEquals("$key must match the visible WebView height: $metrics", height.toDouble(), metrics.getDouble(key), 2.0)
        }
        evaluate(webView, "(() => { document.getElementById('field').scrollIntoView({block:'center'}); return {}; })()")
        val scroll = evaluate(webView, """
            (() => {
              const r = document.getElementById('field').getBoundingClientRect();
              return {top:r.top, bottom:r.bottom, height:document.body.clientHeight,
                      scrollTop:document.body.scrollTop};
            })()
        """.trimIndent())
        assertTrue("The long form must scroll", scroll.getDouble("scrollTop") > 0)
        assertTrue("Input top must be visible: $scroll", scroll.getDouble("top") >= 0)
        assertTrue("Input bottom must be visible: $scroll", scroll.getDouble("bottom") <= scroll.getDouble("height") + 1)
    }

    private fun evaluate(webView: WebView, expression: String): JSONObject {
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        composeRule.runOnUiThread {
            webView.evaluateJavascript("JSON.stringify($expression)") {
                result.set(it)
                completed.countDown()
            }
        }
        assertTrue("WebView JavaScript callback timed out", completed.await(5, TimeUnit.SECONDS))
        // evaluateJavascript JSON-encodes the string returned by JSON.stringify.
        return JSONObject(org.json.JSONTokener(result.get()).nextValue() as String)
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findWebView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private class EmptyPaymentStore : PaymentRecoveryStore {
        override fun read(): PaymentRecoverySnapshot? = null
        override fun write(snapshot: PaymentRecoverySnapshot): Boolean = false
        override fun clear(): Boolean = true
    }

    private companion object {
        val FIXTURE = """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              html {height:100%;overflow:hidden}
              body {margin:0;height:100vh;height:100dvh;min-height:100%;overflow:auto}
              .probe {position:absolute;visibility:hidden;width:1px}
              #vh {height:100vh} #dvh {height:100vh;height:100dvh}
            </style></head><body>
            <div id="vh" class="probe"></div><div id="dvh" class="probe"></div>
            <div style="height:800px"></div><input id="field" aria-label="Test name">
            <div style="height:400px"></div></body></html>
        """.trimIndent()

        val METRICS = """
            ({innerWidth:innerWidth, innerHeight:innerHeight,
              body:document.body.getBoundingClientRect().height,
              vh:document.getElementById('vh').getBoundingClientRect().height,
              dvh:document.getElementById('dvh').getBoundingClientRect().height})
        """.trimIndent()
    }
}
