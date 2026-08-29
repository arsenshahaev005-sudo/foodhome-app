package market.foodhome.app.notifications

import market.foodhome.app.navigation.NavigationDecision
import market.foodhome.app.navigation.NavigationPolicy
import java.security.MessageDigest

enum class NotificationAuthorizationStatus(val wireValue: String) {
    NotDetermined("notDetermined"),
    Denied("denied"),
    Authorized("authorized"),
    Provisional("provisional"),
    Unavailable("unavailable"),
}

data class PushOpen(
    val eventId: String,
    val route: String,
)

class PushPayloadPolicy(
    private val navigationPolicy: NavigationPolicy,
) {
    private val eventIdPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$")

    fun parse(payload: Map<String, String>): PushOpen? {
        if (payload.keys.any { it !in ALLOWED_KEYS }) return null
        val eventId = payload["eventId"]?.takeIf(eventIdPattern::matches) ?: return null
        val route = payload["route"] ?: return null
        val decision = navigationPolicy.classify(route) as? NavigationDecision.Internal ?: return null
        return PushOpen(eventId, decision.uri.toASCIIString())
    }

    private companion object {
        val ALLOWED_KEYS = setOf("eventId", "route", "type")
    }
}

class SensitivePushToken private constructor(
    private val rawValue: String,
) {
    val fingerprint: String = MessageDigest.getInstance("SHA-256")
        .digest(rawValue.toByteArray(Charsets.UTF_8))
        .take(6)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun <T> use(block: (String) -> T): T = block(rawValue)

    override fun toString(): String = "SensitivePushToken(<redacted>:$fingerprint)"

    companion object {
        fun from(rawValue: String): SensitivePushToken? = rawValue
            .takeIf { it.isNotBlank() && it.length <= 4_096 }
            ?.let(::SensitivePushToken)
    }
}

fun interface PushTokenSink {
    fun receive(token: SensitivePushToken)
}

object DisabledPushTokenSink : PushTokenSink {
    override fun receive(token: SensitivePushToken) {
        // Intentionally disabled until the accepted MobileInstallation binding contract exists.
    }
}
