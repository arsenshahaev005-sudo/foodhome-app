package market.foodhome.app.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FoodHomeMessagingService : FirebaseMessagingService() {
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
}
