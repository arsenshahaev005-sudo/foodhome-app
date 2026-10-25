package market.foodhome.app.notifications

import org.json.JSONObject
import org.json.JSONTokener
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
        val version = data["version"]
        if (version !in setOf("2", "3", "4", "5", "6", "7")) return null
        val eventId = data.getValue("eventId").takeIf(uuid::matches) ?: return null
        val bindingId = data.getValue("bindingId").takeIf(binding::matches) ?: return null
        val expiry = Instant.parse(data.getValue("expiresAt")).toEpochMilli()
        if (expiry <= nowMillis || expiry > nowMillis + MAX_LIFETIME_MILLIS) return null
        val eventType = data.getValue("eventType")
        val routeTokens = JSONTokener(data.getValue("route"))
        val route = routeTokens.nextValue() as? JSONObject ?: return null
        if (routeTokens.nextClean() != '\u0000') return null
        if (route.keySet() != setOf("protocol", "version", "name", "params", "query")) return null
        if (route.opt("protocol") != "foodhome.logical-route" || route.opt("version") != 1) return null
        val params = route.getJSONObject("params")
        val query = route.getJSONObject("query")
        val path = when (eventType) {
            "order.updated", "seller.order.new" -> {
                if (version != "2") return null
                if (route.opt("name") != "order.detail" || params.keySet() != setOf("id") || query.length() != 0) return null
                val id = (params.opt("id") as? String)?.takeIf(uuid::matches) ?: return null
                "/orders/$id"
            }
            "chat.message" -> {
                if (version != "2") return null
                if (route.opt("name") != "chat.inbox" || params.length() != 0 || query.length() > 1) return null
                if (query.length() == 0) "/chat" else {
                    val name = query.keys().next()
                    if (name !in setOf("orderId", "producerId")) return null
                    val id = (query.opt(name) as? String)?.takeIf(uuid::matches) ?: return null
                    "/chat?$name=$id"
                }
            }
            "seller.product.moderated" -> {
                if (version != "3" || route.opt("name") != "seller.product" || params.keySet() != setOf("id") || query.length() != 0) return null
                val id = (params.opt("id") as? String)?.takeIf(uuid::matches) ?: return null
                "/seller?view=PRODUCTS&productId=$id"
            }
            "buyer.order.photo_ready" -> {
                if (version != "4" || route.opt("name") != "order.detail" || params.keySet() != setOf("id") || query.length() != 0) return null
                val id = (params.opt("id") as? String)?.takeIf(uuid::matches) ?: return null
                "/orders/$id"
            }
            "seller.open_order.available", "seller.open_order.updated", "buyer.open_order.updated" -> {
                val buyer = eventType == "buyer.open_order.updated"
                val routeName = if (buyer) "open-order.buyer" else "open-order.seller"
                if (version != "5" || route.opt("name") != routeName || params.keySet() != setOf("id") || query.length() != 0) return null
                val id = (params.opt("id") as? String)?.takeIf(uuid::matches) ?: return null
                "/open-orders/$id" + if (buyer) "" else "?role=seller"
            }
            "broadcast.notification", "buyer.notification" -> {
                if (version != if (eventType == "broadcast.notification") "6" else "7") return null
                when (route.opt("name")) {
                    "notifications.inbox" -> {
                        if (params.length() != 0 || query.keySet() != setOf("role")) return null
                        val role = query.opt("role") as? String ?: return null
                        val roles = if (version == "6") setOf("ACCOUNT", "CLIENT", "SELLER") else setOf("CLIENT")
                        if (role !in roles) return null
                        if (role == "SELLER") "/seller?view=CHAT&section=notifications" else "/chat?section=notifications"
                    }
                    "order.detail" -> {
                        if (version != "7" || params.keySet() != setOf("id") || query.length() != 0) return null
                        val id = (params.opt("id") as? String)?.takeIf(uuid::matches) ?: return null
                        "/orders/$id"
                    }
                    else -> return null
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
