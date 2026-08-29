package market.foodhome.app.capabilities

import market.foodhome.app.bridge.BridgeCapabilityDispatcher
import market.foodhome.app.bridge.BridgeDispatchResult
import market.foodhome.app.bridge.BridgeManifest
import market.foodhome.app.bridge.BridgeRequest
import market.foodhome.app.location.LocationRequestResult
import market.foodhome.app.notifications.NotificationAuthorizationStatus
import market.foodhome.app.notifications.NotificationPermissionResult
import org.json.JSONObject
import java.net.URI
import java.time.Instant
import market.foodhome.app.payments.OpenPaymentRequest
import market.foodhome.app.payments.PaymentCoordinator
import market.foodhome.app.payments.PaymentOperationResult
import market.foodhome.app.telemetry.TelemetryEventName
import market.foodhome.app.telemetry.TelemetryReporter

class AndroidCapabilityCoordinator(
    private val manifest: BridgeManifest,
    trustedOrigin: URI,
    private val presentShare: (SharePayload) -> Boolean,
    private val requestLocation: (String, (LocationRequestResult) -> Unit) -> Unit,
    private val notificationStatus: () -> NotificationAuthorizationStatus,
    private val requestNotificationPermission: (String?, (NotificationPermissionResult) -> Unit) -> Unit,
    private val paymentCoordinator: PaymentCoordinator? = null,
    private val hasRecentPaymentUserAction: () -> Boolean = { false },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val telemetry: TelemetryReporter = TelemetryReporter.disabled(trustedOrigin),
) : BridgeCapabilityDispatcher {
    private val sharePolicy = FoodHomeSharePolicy(trustedOrigin)
    private val rateLimiter = CapabilityRateLimiter(nowMillis)

    override fun dispatch(
        request: BridgeRequest,
        completion: (BridgeDispatchResult) -> Unit,
    ) {
        val reportingCompletion: (BridgeDispatchResult) -> Unit = { result ->
            if (result is BridgeDispatchResult.Failure) {
                telemetry.record(
                    TelemetryEventName.BridgeRequestFailed,
                    attributes = mapOf("errorCode" to result.code),
                )
            }
            completion(result)
        }
        val isPaymentControl = request.method == "ackNativeEvent" ||
            request.method == "clearPaymentRecovery"
        val isAvailable = request.method in manifest.advertisedCapabilities ||
            (isPaymentControl && "openPayment" in manifest.advertisedCapabilities)
        if (!isAvailable) {
            reportingCompletion(unavailable())
            return
        }

        when (request.method) {
            "share" -> dispatchShare(request, reportingCompletion)
            "requestLocation" -> dispatchLocation(request, reportingCompletion)
            "getNotificationStatus" -> reportingCompletion(notificationResult(notificationStatus()))
            "requestNotificationPermission" -> dispatchNotificationPermission(request, reportingCompletion)
            "openPayment" -> dispatchPayment(request, reportingCompletion)
            "ackNativeEvent" -> dispatchEventAcknowledgement(request, reportingCompletion)
            "clearPaymentRecovery" -> dispatchPaymentRecoveryClear(request, reportingCompletion)
            else -> reportingCompletion(unavailable())
        }
    }

    private fun dispatchPayment(
        request: BridgeRequest,
        completion: (BridgeDispatchResult) -> Unit,
    ) {
        if (!enforceRateLimit("openPayment", completion)) return
        val coordinator = paymentCoordinator ?: run {
            completion(unavailable())
            return
        }
        val expiresAt = runCatching {
            Instant.parse(request.payload.getString("expiresAt")).toEpochMilli()
        }.getOrNull() ?: run {
            completion(failure("INVALID_PAYLOAD", "Payment expiry is invalid"))
            return
        }
        val result = coordinator.open(
            OpenPaymentRequest(
                rawUrl = request.payload.getString("url"),
                recoveryContext = request.payload.getString("recoveryContext"),
                serverExpiresAtEpochMillis = expiresAt,
                userInitiated = hasRecentPaymentUserAction(),
            ),
        )
        completion(paymentResult(result))
    }

    private fun dispatchEventAcknowledgement(
        request: BridgeRequest,
        completion: (BridgeDispatchResult) -> Unit,
    ) {
        val coordinator = paymentCoordinator ?: run {
            completion(unavailable())
            return
        }
        completion(paymentResult(coordinator.acknowledge(request.payload.getString("eventId"))))
    }

    private fun dispatchPaymentRecoveryClear(
        request: BridgeRequest,
        completion: (BridgeDispatchResult) -> Unit,
    ) {
        val coordinator = paymentCoordinator ?: run {
            completion(unavailable())
            return
        }
        completion(
            paymentResult(
                coordinator.clear(
                    recoveryContext = request.payload.getString("recoveryContext"),
                    reason = request.payload.getString("reason"),
                ),
            ),
        )
    }

    private fun paymentResult(result: PaymentOperationResult): BridgeDispatchResult = when (result) {
        is PaymentOperationResult.Presented -> BridgeDispatchResult.Success(
            JSONObject()
                .put("capability", "openPayment")
                .put("status", "presented")
                .put("flow", result.flow.wireValue),
        )
        is PaymentOperationResult.Acknowledged -> BridgeDispatchResult.Success(
            JSONObject()
                .put("capability", "nativeEvents")
                .put("status", "acknowledged")
                .put("eventId", result.eventId),
        )
        PaymentOperationResult.Cleared -> BridgeDispatchResult.Success(
            JSONObject()
                .put("capability", "paymentRecovery")
                .put("status", "cleared"),
        )
        is PaymentOperationResult.Failure -> failure(
            result.code,
            "Payment operation was rejected",
            result.retryable,
        )
    }

    private fun dispatchShare(
        request: BridgeRequest,
        completion: (BridgeDispatchResult) -> Unit,
    ) {
        val payload = sharePolicy.parse(
            title = request.payload.optString("title").takeIf { request.payload.has("title") },
            text = request.payload.optString("text").takeIf { request.payload.has("text") },
            rawUrl = request.payload.optString("url"),
        )
        if (payload == null) {
            completion(failure("INVALID_PAYLOAD", "Share payload is invalid"))
        } else if (presentShare(payload)) {
            completion(
                BridgeDispatchResult.Success(
                    JSONObject().put("capability", "share").put("status", "presented"),
                ),
            )
        } else {
            completion(unavailable())
        }
    }

    private fun dispatchLocation(
        request: BridgeRequest,
        completion: (BridgeDispatchResult) -> Unit,
    ) {
        if (!enforceRateLimit("requestLocation", completion)) return
        val purpose = request.payload.optString("purpose")
        requestLocation(purpose) { result ->
            completion(
                when (result) {
                    is LocationRequestResult.Granted -> BridgeDispatchResult.Success(
                        JSONObject()
                            .put("capability", "requestLocation")
                            .put("status", "granted")
                            .put("latitude", result.latitude)
                            .put("longitude", result.longitude)
                            .put("accuracyMeters", result.accuracyMeters)
                            .put("precise", result.precise),
                    )
                    is LocationRequestResult.Failed -> failure(
                        result.code,
                        result.message,
                        result.retryable,
                    )
                },
            )
        }
    }

    private fun dispatchNotificationPermission(
        request: BridgeRequest,
        completion: (BridgeDispatchResult) -> Unit,
    ) {
        if (!enforceRateLimit("requestNotificationPermission", completion)) return
        val purpose = request.payload.optString("purpose").takeIf(String::isNotBlank)
        requestNotificationPermission(purpose) { result ->
            completion(
                when (result) {
                    is NotificationPermissionResult.Status -> notificationResult(result.value)
                    NotificationPermissionResult.Cancelled -> failure(
                        "CANCELLED",
                        "Notification permission request was cancelled",
                    )
                },
            )
        }
    }

    private fun notificationResult(status: NotificationAuthorizationStatus) =
        BridgeDispatchResult.Success(
            JSONObject()
                .put("capability", "notifications")
                .put("status", status.wireValue),
        )

    private fun enforceRateLimit(
        method: String,
        completion: (BridgeDispatchResult) -> Unit,
    ): Boolean {
        val rule = manifest.rateLimits[method]?.takeIf { it.isSafe } ?: run {
            completion(unavailable())
            return false
        }
        if (
            !rateLimiter.allow(
                method,
                maxRequests = rule.maxRequests,
                windowMillis = rule.windowSeconds * 1_000L,
            )
        ) {
            completion(failure("RATE_LIMITED", "Capability request rate limit reached", retryable = true))
            return false
        }
        return true
    }

    private fun failure(code: String, message: String, retryable: Boolean = false) =
        BridgeDispatchResult.Failure(code, message, retryable)

    private fun unavailable() = failure("CAPABILITY_UNAVAILABLE", "Capability is unavailable")
}

private class CapabilityRateLimiter(
    private val nowMillis: () -> Long,
) {
    private val requests = mutableMapOf<String, ArrayDeque<Long>>()

    @Synchronized
    fun allow(key: String, maxRequests: Int, windowMillis: Long): Boolean {
        val now = nowMillis()
        val timestamps = requests.getOrPut(key) { ArrayDeque() }
        while (timestamps.firstOrNull()?.let { now - it >= windowMillis } == true) {
            timestamps.removeFirst()
        }
        if (timestamps.size >= maxRequests) return false
        timestamps.addLast(now)
        return true
    }
}
