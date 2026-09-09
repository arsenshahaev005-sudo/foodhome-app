package market.foodhome.app.notifications

import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant

/** Provider data is a routing hint, never proof of authentication or order state. */
class VisiblePush(
    val eventId: String,
    val eventType: String,
    val bindingId: String,
    val expiresAtMillis: Long,
    val destination: String,
) {
    override fun toString() = "VisiblePush(<redacted>)"
}

object VisiblePushPolicy {
    private val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    private val binding = Regex("^[0-9a-f]{64}$")
    private val keys = setOf("version", "eventType", "eventId", "bindingId", "expiresAt", "route")
    const val MAX_LIFETIME_MILLIS = 86_400_000L

    fun parse(data: Map<String, String>, nowMillis: Long): VisiblePush? = runCatching {
        if (data.keys != keys || data.entries.sumOf { it.key.toByteArray(Charsets.UTF_8).size + it.value.toByteArray(Charsets.UTF_8).size } > 2_048) return null
        // Never promote the pre-existing silent v1 contract into a visible alert.
        if (data["version"] != "2") return null
        val eventId = data.getValue("eventId").takeIf(uuid::matches) ?: return null
        val bindingId = data.getValue("bindingId").takeIf(binding::matches) ?: return null
        val expiry = Instant.parse(data.getValue("expiresAt")).toEpochMilli()
        if (expiry <= nowMillis || expiry > nowMillis + MAX_LIFETIME_MILLIS) return null
        val eventType = data.getValue("eventType")
        val route = JSONObject(data.getValue("route"))
        if (route.keySet() != setOf("protocol", "version", "name", "params", "query")) return null
        if (route.opt("protocol") != "foodhome.logical-route" || route.opt("version") != 1) return null
        val params = route.getJSONObject("params")
        val query = route.getJSONObject("query")
        val path = when (eventType) {
            "order.updated" -> {
                if (route.opt("name") != "order.detail" || params.keySet() != setOf("id") || query.length() != 0) return null
                val id = (params.opt("id") as? String)?.takeIf(uuid::matches) ?: return null
                "/orders/$id"
            }
            "chat.message" -> {
                if (route.opt("name") != "chat.inbox" || params.length() != 0 || query.length() > 1) return null
                if (query.length() == 0) "/chat" else {
                    val name = query.keys().next()
                    if (name !in setOf("orderId", "producerId")) return null
                    val id = (query.opt(name) as? String)?.takeIf(uuid::matches) ?: return null
                    "/chat?$name=$id"
                }
            }
            else -> return null
        }
        VisiblePush(eventId, eventType, bindingId, expiry, "https://foodhome.market$path")
    }.getOrNull()
}

object PushBindingPolicy {
    private val noncePattern = Regex("^[A-Za-z0-9_-]{43}$")

    fun accepts(payload: JSONObject): Boolean = when (payload.opt("action")) {
        "status", "clear" -> payload.keySet() == setOf("action")
        "bind", "revoke" -> payload.keySet() == setOf("action", "nonce", "expiresAt") &&
            (payload.opt("nonce") as? String)?.matches(noncePattern) == true &&
            (payload.opt("expiresAt") as? String)?.let { runCatching { Instant.parse(it) }.isSuccess } == true
        else -> false
    }

    fun isFresh(expiresAt: String, nowMillis: Long): Boolean = runCatching {
        val expiry = Instant.parse(expiresAt).toEpochMilli()
        expiry > nowMillis && expiry <= nowMillis + 300_000
    }.getOrDefault(false)

    fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

    fun acceptsReceipt(response: JSONObject, installationId: String, bindingId: String): Boolean =
        response.opt("installationId") == installationId && response.opt("bindingId") == bindingId &&
            response.opt("environment") == "production" && response.opt("platform") == "android" &&
            response.opt("provider") == "fcm" && response.opt("notificationStatus") == "authorized" &&
            response.has("revokedAt") && response.isNull("revokedAt") &&
            response.has("invalidatedAt") && response.isNull("invalidatedAt")
}

internal fun JSONObject.keySet(): Set<String> = keys().asSequence().toSet()
