package market.foodhome.app

import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import market.foodhome.app.bridge.BridgeManifest
import market.foodhome.app.config.AppEnvironmentResolver
import market.foodhome.app.navigation.NavigationPolicy
import market.foodhome.app.navigation.NavigationCoordinator
import market.foodhome.app.notifications.PushPayloadPolicy
import market.foodhome.app.payments.AndroidPaymentLauncher
import market.foodhome.app.payments.AndroidPaymentReturnRouter
import market.foodhome.app.payments.PaymentCoordinator
import market.foodhome.app.payments.PaymentLaunchPolicy
import market.foodhome.app.payments.SharedPreferencesPaymentRecoveryStore
import market.foodhome.app.telemetry.TelemetryEventName
import market.foodhome.app.telemetry.TelemetryReporter
import market.foodhome.app.ui.FoodHomeAppShell

class MainActivity : ComponentActivity() {
    private val environment by lazy {
        AppEnvironmentResolver.resolve(
            isDebug = BuildConfig.DEBUG,
            debugBaseUrl = BuildConfig.DEBUG_BASE_URL,
        )
    }
    private val navigationPolicy by lazy { NavigationPolicy(environment.trustedOrigin) }
    private val navigationCoordinator by lazy { NavigationCoordinator(navigationPolicy) }
    private val pushPayloadPolicy by lazy { PushPayloadPolicy(navigationPolicy) }
    private val paymentCoordinator by lazy {
        PaymentCoordinator(
            policy = PaymentLaunchPolicy.production(),
            store = SharedPreferencesPaymentRecoveryStore.create(this),
            launcher = AndroidPaymentLauncher(this),
        )
    }
    private val telemetry by lazy { TelemetryReporter.disabled(environment.trustedOrigin) }
    private val paymentReturnRouter by lazy {
        AndroidPaymentReturnRouter(paymentCoordinator, telemetry)
    }
    private var hasResumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intent.dataString?.let(::offerDeepLink)
        offerPushRoute(intent)

        val manifest = assets.open("manifest.json").use(BridgeManifest::from)
        telemetry.record(
            TelemetryEventName.ShellLaunch,
            mapOf(
                "platform" to "android",
                "appVersion" to BuildConfig.VERSION_NAME,
                "bridgeVersion" to manifest.contractVersion,
            ),
        )
        setContent {
            FoodHomeAppShell(
                environment = environment,
                navigationCoordinator = navigationCoordinator,
                manifest = manifest,
                paymentCoordinator = paymentCoordinator,
                paymentReturnRouter = paymentReturnRouter,
                telemetry = telemetry,
                onOpenExternal = { uri ->
                    val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                    }
                    runCatching { startActivity(browserIntent) }
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let {
            offerDeepLink(it)
            paymentReturnRouter.onAppLink()
        }
        offerPushRoute(intent)
    }

    override fun onStop() {
        paymentReturnRouter.onStop()
        CookieManager.getInstance().flush()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (hasResumed) paymentReturnRouter.onResume()
        else {
            hasResumed = true
            paymentReturnRouter.onColdStart()
        }
    }

    private fun offerPushRoute(intent: Intent) {
        val payload = buildMap {
            for (key in listOf("eventId", "route", "type")) {
                intent.getStringExtra(key)?.let { put(key, it) }
            }
        }
        if (payload.isEmpty()) return
        val route = pushPayloadPolicy.parse(payload)?.route
        if (route == null || !navigationCoordinator.offerDeepLink(route)) {
            telemetry.record(
                TelemetryEventName.PushOpenFailed,
                attributes = mapOf("errorCode" to "PUSH_ROUTE_REJECTED"),
            )
        }
    }

    private fun offerDeepLink(rawUrl: String) {
        if (!navigationCoordinator.offerDeepLink(rawUrl)) {
            telemetry.record(
                TelemetryEventName.DeepLinkOpenFailed,
                attributes = mapOf("errorCode" to "DEEPLINK_REJECTED"),
                routeUrl = rawUrl,
            )
        }
    }
}
