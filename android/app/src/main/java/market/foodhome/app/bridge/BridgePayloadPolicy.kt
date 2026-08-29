package market.foodhome.app.bridge

import market.foodhome.app.capabilities.CapabilityPurposePolicy
import market.foodhome.app.capabilities.FoodHomeSharePolicy
import org.json.JSONObject
import java.net.URI
import java.time.Instant

class BridgePayloadPolicy(
    trustedOrigin: URI,
    private val maxMessageBytes: Int,
) {
    private val sharePolicy = FoodHomeSharePolicy(trustedOrigin)

    fun accepts(method: String, payload: JSONObject): Boolean {
        if (payload.length() > 32 || payload.toString().toByteArray(Charsets.UTF_8).size > maxMessageBytes) {
            return false
        }
        return when (method) {
            "share" -> {
                if (
                    payload.length() > 3 ||
                    !payload.hasStringOrNull("title") ||
                    !payload.hasStringOrNull("text")
                ) {
                    false
                } else {
                    sharePolicy.parse(
                        title = payload.optionalString("title"),
                        text = payload.optionalString("text"),
                        rawUrl = payload.optionalString("url"),
                    ) != null
                }
            }
            "requestLocation" -> payload.length() <= 2 &&
                CapabilityPurposePolicy.accepts(payload.optionalString("purpose"))
            "requestNotificationPermission" -> {
                payload.length() <= 2 && (
                    !payload.has("purpose") ||
                        CapabilityPurposePolicy.accepts(payload.optionalString("purpose"))
                    )
            }
            "getNotificationStatus" -> payload.length() <= 1
            "openExternal" -> payload.optionalString("url")?.let(::isHTTPSURL) == true
            "openPayment" -> payload.optionalString("url")?.let(::isHTTPSURL) == true &&
                payload.optionalString("recoveryContext")?.matches(
                    Regex("^[A-Za-z0-9._:-]{1,128}$"),
                ) == true &&
                payload.optionalString("expiresAt")?.let(::isRFC3339Instant) == true &&
                payload.length() <= 3
            "ackNativeEvent" -> payload.length() == 1 &&
                payload.optionalString("eventId")?.matches(
                    Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$"),
                ) == true
            "clearPaymentRecovery" -> payload.length() == 2 &&
                payload.optionalString("recoveryContext")?.matches(
                    Regex("^[A-Za-z0-9._:-]{1,128}$"),
                ) == true &&
                payload.optionalString("reason") in setOf(
                    "terminal",
                    "expired",
                    "logout",
                    "accountChanged",
                    "abandoned",
                )
            else -> false
        }
    }

    private fun isRFC3339Instant(value: String): Boolean = runCatching { Instant.parse(value) }.isSuccess

    private fun isHTTPSURL(value: String): Boolean = runCatching { URI(value) }
        .getOrNull()
        ?.let { it.isAbsolute && !it.isOpaque && it.scheme.equals("https", ignoreCase = true) && it.host != null && it.userInfo == null }
        ?: false

    private fun JSONObject.optionalString(name: String): String? = when {
        !has(name) || isNull(name) -> null
        opt(name) is String -> getString(name)
        else -> null
    }

    private fun JSONObject.hasStringOrNull(name: String): Boolean =
        !has(name) || isNull(name) || opt(name) is String
}
