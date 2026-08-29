package market.foodhome.app.bridge

import market.foodhome.app.payments.PaymentCoordinator
import org.json.JSONObject
import java.net.URI

class NativeEventQueue(
    private val manifest: BridgeManifest,
    private val trustedOrigin: URI,
    private val paymentCoordinator: PaymentCoordinator,
) {
    /** Returns the same pending event until the trusted web client sends an explicit ACK. */
    fun pendingDispatchScript(): String? {
        val event = paymentCoordinator.pendingEvent()
            ?.toBridgeJson(manifest.protocol, manifest.bridgeMajor)
            ?: return null
        val origin = JSONObject.quote(exactOrigin(trustedOrigin) ?: return null)
        val eventName = JSONObject.quote(manifest.nativeEventName)
        return """
            (() => {
              if (window !== window.top || window.location.origin !== $origin) return;
              window.dispatchEvent(new CustomEvent($eventName,{detail:$event}));
            })();
        """.trimIndent()
    }

    private fun exactOrigin(uri: URI): String? {
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host == null || uri.userInfo != null) {
            return null
        }
        if (uri.port !in setOf(-1, 443)) return null
        return "https://${uri.host.lowercase()}"
    }
}
