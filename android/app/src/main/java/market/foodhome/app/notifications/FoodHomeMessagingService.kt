package market.foodhome.app.notifications

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FoodHomeMessagingService : FirebaseMessagingService() {
    override fun handleIntent(intent: Intent) {
        // Firebase can auto-display a notification envelope in the background
        // before onMessageReceived. Reject it before superclass dispatch too.
        if (!PushEnvelopePolicy.acceptsKeys(intent.extras?.keySet().orEmpty())) return
        super.handleIntent(intent)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        // Tokens stay native. Authenticated rebind needs a fresh web-issued nonce.
        AndroidPushRuntime.get(this).tokenChanged(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Provider contract MUST be data-only, including while the app is backgrounded.
        if (message.notification != null) return
        AndroidPushRuntime.get(this).receive(message.data)
    }

    override fun onDeletedMessages() {
        // Recover through the existing trusted web lifecycle sync. Never invent
        // feed entries or local alerts for messages FCM did not deliver.
        AndroidPushRuntime.get(this).messagesDeleted()
    }
}

internal object PushEnvelopePolicy {
    fun acceptsKeys(keys: Set<String>): Boolean = keys.none {
        it == "notification" || it.startsWith("gcm.n.") || it.startsWith("gcm.notification.")
    }
}
