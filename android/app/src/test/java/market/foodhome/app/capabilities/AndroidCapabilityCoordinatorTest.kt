package market.foodhome.app.capabilities

import market.foodhome.app.bridge.BridgeDispatchResult
import market.foodhome.app.bridge.BridgeManifest
import market.foodhome.app.bridge.BridgeRequest
import market.foodhome.app.bridge.NativeModeContract
import market.foodhome.app.bridge.RateLimitRule
import market.foodhome.app.location.LocationRequestResult
import market.foodhome.app.notifications.NotificationAuthorizationStatus
import market.foodhome.app.notifications.NotificationPermissionResult
import market.foodhome.app.telemetry.TelemetryAttributeKey
import market.foodhome.app.telemetry.TelemetryEvent
import market.foodhome.app.telemetry.TelemetryReporter
import market.foodhome.app.telemetry.TelemetrySanitizer
import market.foodhome.app.telemetry.TelemetrySink
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class AndroidCapabilityCoordinatorTest {
    @Test
    fun `share is presented only through validated exact origin payload`() {
        var presented: SharePayload? = null
        val coordinator = coordinator(presentShare = { presented = it; true })
        var response: BridgeDispatchResult? = null

        coordinator.dispatch(
            BridgeRequest(
                requestId = "share-1",
                method = "share",
                payload = JSONObject().put("url", "https://foodhome.market/cart"),
            ),
        ) { response = it }

        assertEquals("https://foodhome.market/cart", presented?.url?.toASCIIString())
        assertEquals(
            "presented",
            (response as BridgeDispatchResult.Success).result.getString("status"),
        )
    }

    @Test
    fun `location returns bounded typed result after explicit request`() {
        var requestedPurpose: String? = null
        val coordinator = coordinator(
            requestLocation = { purpose, completion ->
                requestedPurpose = purpose
                completion(LocationRequestResult.Granted(51.5, 46.0, 100.0, precise = false))
            },
        )
        var response: BridgeDispatchResult? = null

        coordinator.dispatch(
            BridgeRequest(
                requestId = "location-1",
                method = "requestLocation",
                payload = JSONObject().put("purpose", "Показать доставку рядом"),
            ),
        ) { response = it }

        assertEquals("Показать доставку рядом", requestedPurpose)
        val result = (response as BridgeDispatchResult.Success).result
        assertEquals("requestLocation", result.getString("capability"))
        assertEquals(false, result.getBoolean("precise"))
    }

    @Test
    fun `sensitive requests are rate limited`() {
        var now = 0L
        val coordinator = coordinator(nowMillis = { now })
        val request = BridgeRequest(
            "notification-1",
            "requestNotificationPermission",
            JSONObject(),
        )
        val responses = mutableListOf<BridgeDispatchResult>()

        coordinator.dispatch(request, responses::add)
        coordinator.dispatch(request.copy(requestId = "notification-2"), responses::add)

        assertTrue(responses[1] is BridgeDispatchResult.Failure)
        assertEquals("RATE_LIMITED", (responses[1] as BridgeDispatchResult.Failure).code)
        now = 30_000
        coordinator.dispatch(request.copy(requestId = "notification-3"), responses::add)
        assertTrue(responses[2] is BridgeDispatchResult.Success)
    }

    @Test
    fun `missing sensitive rate limit fails closed`() {
        val events = mutableListOf<TelemetryEvent>()
        val telemetry = TelemetryReporter(
            TelemetrySanitizer(URI("https://foodhome.market")),
            TelemetrySink { events.add(it) },
        )
        val coordinator = coordinator(
            manifest = manifest(rateLimits = emptyMap()),
            telemetry = telemetry,
        )
        var response: BridgeDispatchResult? = null

        coordinator.dispatch(
            BridgeRequest(
                "location-missing-limit",
                "requestLocation",
                JSONObject().put("purpose", "test"),
            ),
        ) { response = it }

        assertEquals("CAPABILITY_UNAVAILABLE", (response as BridgeDispatchResult.Failure).code)
        assertEquals(
            "CAPABILITY_UNAVAILABLE",
            events.single().attributes[TelemetryAttributeKey.ErrorCode],
        )
    }

    private fun coordinator(
        presentShare: (SharePayload) -> Boolean = { true },
        requestLocation: (String, (LocationRequestResult) -> Unit) -> Unit = { _, completion ->
            completion(LocationRequestResult.Failed("CANCELLED", "cancelled"))
        },
        nowMillis: () -> Long = { 0L },
        manifest: BridgeManifest = manifest(),
        telemetry: TelemetryReporter = TelemetryReporter.disabled(URI("https://foodhome.market")),
    ) = AndroidCapabilityCoordinator(
        manifest = manifest,
        trustedOrigin = URI("https://foodhome.market"),
        presentShare = presentShare,
        requestLocation = requestLocation,
        notificationStatus = { NotificationAuthorizationStatus.NotDetermined },
        requestNotificationPermission = { _, completion ->
            completion(NotificationPermissionResult.Status(NotificationAuthorizationStatus.Authorized))
        },
        nowMillis = nowMillis,
        telemetry = telemetry,
    )

    private fun manifest(
        rateLimits: Map<String, RateLimitRule> = mapOf(
            "requestLocation" to RateLimitRule(3, 60),
            "requestNotificationPermission" to RateLimitRule(1, 30),
        ),
    ) = BridgeManifest(
        protocol = "foodhome.bridge",
        contractVersion = "1.2.0",
        bridgeMajor = 1,
        supportedVersions = setOf(1),
        globalObjectName = "FoodHomeBridge",
        handshakeEventName = "foodhome:bridge-ready",
        nativeMode = NativeModeContract(
            "foodhome.native-mode", 1, "FoodHomeNative", "FoodHomeNative/1", false, true, true,
        ),
        maxMessageBytes = 32_768,
        methods = setOf(
            "share",
            "requestLocation",
            "getNotificationStatus",
            "requestNotificationPermission",
        ),
        phase0Capabilities = emptySet(),
        compiledCapabilities = setOf(
            "share",
            "requestLocation",
            "getNotificationStatus",
            "requestNotificationPermission",
        ),
        rateLimits = rateLimits,
    )
}
