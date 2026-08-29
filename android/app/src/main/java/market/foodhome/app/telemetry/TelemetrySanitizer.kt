package market.foodhome.app.telemetry

import java.net.URI
import java.nio.charset.StandardCharsets

class TelemetrySanitizer(
    private val trustedOrigin: URI,
) {
    fun sanitize(
        name: TelemetryEventName,
        rawAttributes: Map<String, String>,
    ): TelemetryEvent {
        val sanitized = buildMap {
            for ((rawKey, rawValue) in rawAttributes) {
                val key = ATTRIBUTE_KEYS[rawKey] ?: continue
                val value = sanitizeValue(key, rawValue) ?: continue
                put(key, value)
            }
        }
        return TelemetryEvent(name, sanitized)
    }

    fun routeTemplate(rawUrl: String?): String? {
        val raw = rawUrl ?: return null
        if (
            raw.isEmpty() ||
            raw.toByteArray(StandardCharsets.UTF_8).size > MAX_URL_BYTES ||
            raw != raw.trim() ||
            raw.any { it.isWhitespace() || it.isISOControl() || it == '\\' }
        ) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (
            !uri.scheme.equals("https", ignoreCase = true) ||
            uri.userInfo != null ||
            uri.host?.equals(trustedOrigin.host, ignoreCase = true) != true ||
            effectivePort(uri) != effectivePort(trustedOrigin)
        ) return null

        val segments = uri.rawPath.orEmpty()
            .split('/')
            .filter(String::isNotEmpty)
            .take(MAX_ROUTE_SEGMENTS)
        if (segments.isEmpty()) return "/"
        val safe = segments.mapIndexed { index, segment ->
            val normalized = segment.lowercase()
            when {
                normalized in SAFE_ROUTE_SEGMENTS -> normalized
                index == 0 -> ":route"
                else -> ":id"
            }
        }
        return "/${safe.joinToString("/")}"
    }

    private fun sanitizeValue(key: TelemetryAttributeKey, raw: String): String? {
        if (raw.isEmpty() || raw.length > MAX_ATTRIBUTE_LENGTH || raw.any(Char::isISOControl)) {
            return null
        }
        return when (key) {
            TelemetryAttributeKey.Platform -> raw.takeIf { it in setOf("android", "ios") }
            TelemetryAttributeKey.AppVersion -> raw.takeIf(APP_VERSION::matches)
            TelemetryAttributeKey.BridgeVersion -> raw.takeIf(BRIDGE_VERSION::matches)
            TelemetryAttributeKey.WebViewVersion -> raw.takeIf(WEBVIEW_VERSION::matches)
            TelemetryAttributeKey.RouteTemplate -> raw.takeIf(ROUTE_TEMPLATE::matches)
            TelemetryAttributeKey.ErrorCode -> raw.takeIf(ERROR_CODE::matches)
            TelemetryAttributeKey.CorrelationId -> raw.takeIf(CORRELATION_ID::matches)
            TelemetryAttributeKey.NetworkClass -> raw.takeIf { it in NETWORK_CLASSES }
            TelemetryAttributeKey.DurationBucket -> raw.takeIf { it in DURATION_BUCKETS }
        }
    }

    private fun effectivePort(uri: URI): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> -1
    }

    private companion object {
        const val MAX_ATTRIBUTE_LENGTH = 160
        const val MAX_URL_BYTES = 2_048
        const val MAX_ROUTE_SEGMENTS = 8
        val ATTRIBUTE_KEYS = TelemetryAttributeKey.entries.associateBy(TelemetryAttributeKey::wireValue)
        val APP_VERSION = Regex("^[0-9]+(?:\\.[0-9]+){1,3}(?:-[A-Za-z0-9.-]{1,24})?$")
        val BRIDGE_VERSION = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+$")
        val WEBVIEW_VERSION = Regex("^[0-9]+(?:\\.[0-9]+){0,4}$")
        val ROUTE_TEMPLATE = Regex(
            "^/(?:[a-z][a-z0-9-]{0,31}|:id|:route)(?:/(?:[a-z][a-z0-9-]{0,31}|:id|:route)){0,7}$|^/$",
        )
        val ERROR_CODE = Regex("^[A-Z][A-Z0-9_]{0,63}$")
        val CORRELATION_ID = Regex("^[A-Za-z0-9_-]{8,64}$")
        val NETWORK_CLASSES = setOf("offline", "wifi", "cellular", "ethernet", "unknown")
        val DURATION_BUCKETS = setOf("lt_100ms", "100_499ms", "500_1999ms", "2_9s", "gte_10s")
        val SAFE_ROUTE_SEGMENTS = setOf(
            "products", "sellers", "orders", "chat", "cart", "checkout", "profile",
            "support", "login", "register", "favorites", "notifications", "settings",
            "help", "delivery",
        )
    }
}
