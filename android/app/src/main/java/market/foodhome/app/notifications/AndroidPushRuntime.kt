package market.foodhome.app.notifications

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import market.foodhome.app.BuildConfig
import market.foodhome.app.bridge.BridgeDispatchResult
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** One process-local owner shared by the UI and Firebase service; no Activity references. */
class AndroidPushRuntime private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val store = PushStateStore(appContext)
    private val notifications = AndroidNotificationCoordinator(appContext)
    private val presenter = PushNotificationPresenter(appContext)
    private val bindingClient = PushBindingClient()
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var busy = false
    private val configured get() = BuildConfig.NATIVE_PUSH_ENABLED &&
        FirebaseApp.getApps(appContext).any { it.name == FirebaseApp.DEFAULT_APP_NAME }

    @Synchronized
    fun status(): String = when {
        !configured -> "unavailable"
        notifications.authorizationStatus() != NotificationAuthorizationStatus.Authorized -> "permissionDenied"
        store.get().bindingId != null -> "enabled"
        store.get().needsBinding -> "needsBinding"
        else -> "disabled"
    }

    @Synchronized
    fun clear(): Boolean {
        val saved = store.set(store.get().clear())
        presenter.cancelAll()
        // Auto-init is always disabled; a future bind explicitly asks for the current token.
        return saved
    }

    @Synchronized
    fun tokenChanged(token: String) {
        if (!configured) return
        val state = store.get()
        val updated = state.tokenChanged(PushBindingPolicy.digest(token))
        if (updated != state) {
            store.set(updated)
            if (updated.bindingId == null) presenter.cancelAll()
        }
    }

    @Synchronized
    fun receive(data: Map<String, String>) {
        if (!configured || notifications.authorizationStatus() != NotificationAuthorizationStatus.Authorized) return
        val now = System.currentTimeMillis()
        val push = VisiblePushPolicy.parse(data, now) ?: return
        val state = store.get()
        if (!state.accepts(push, now)) return
        // Persist dedupe before posting; a storage failure suppresses the notification.
        if (store.set(state.record(push.eventId))) presenter.show(push, data)
    }

    @Synchronized
    fun destinationForTap(data: Map<String, String>): String? {
        if (!configured) return null
        val push = VisiblePushPolicy.parse(data, System.currentTimeMillis()) ?: return null
        return push.destination.takeIf { store.get().bindingId == push.bindingId }
    }

    @Synchronized
    fun dispatch(payload: JSONObject, bridgeVersion: String, completion: (BridgeDispatchResult) -> Unit) {
        if (!PushBindingPolicy.accepts(payload)) {
            completion(failure("INVALID_PAYLOAD")); return
        }
        val action = payload.getString("action")
        if (action == "status") { completion(result(status())); return }
        if (action == "clear") {
            completion(if (clear()) result("disabled") else failure("INTERNAL_ERROR")); return
        }
        // Revoke locally even if the network/provider is unavailable.
        if (action == "revoke" && !clear()) { completion(failure("INTERNAL_ERROR")); return }
        if (!configured) { completion(failure("CAPABILITY_UNAVAILABLE")); return }
        if (!PushBindingPolicy.isFresh(payload.getString("expiresAt"), System.currentTimeMillis())) {
            completion(failure("INVALID_PAYLOAD")); return
        }
        if (busy) { completion(failure("RATE_LIMITED", true)); return }
        if (action == "bind" && notifications.authorizationStatus() != NotificationAuthorizationStatus.Authorized) {
            completion(result("permissionDenied")); return
        }
        if (action == "bind") {
            val saved = store.set(store.get().beginBinding())
            presenter.cancelAll()
            if (!saved) { completion(failure("INTERNAL_ERROR")); return }
        }
        val start = store.get()
        busy = true
        executor.execute {
            val outcome = runCatching {
                val nonce = payload.getString("nonce")
                val body = JSONObject().put("installationId", start.installationId).put("environment", "production")
                var tokenDigest: String? = null
                if (action == "bind") {
                    // Keep the existing backend FCM token contract; do not opt into FID migration.
                    @Suppress("DEPRECATION")
                    val token = Tasks.await(FirebaseMessaging.getInstance().token, 3, TimeUnit.SECONDS)
                    tokenDigest = PushBindingPolicy.digest(token)
                    body.put("platform", "android").put("provider", "fcm").put("pushToken", token)
                        .put("appVersion", BuildConfig.VERSION_NAME).put("buildVersion", BuildConfig.VERSION_CODE.toString())
                        .put("bridgeVersion", bridgeVersion).put("locale", Locale.getDefault().toLanguageTag())
                        .put("notificationStatus", "authorized")
                }
                if (!PushBindingPolicy.isFresh(payload.getString("expiresAt"), System.currentTimeMillis())) {
                    return@runCatching failure("INVALID_PAYLOAD")
                }
                val response = bindingClient.redeem(action, nonce, body) ?: return@runCatching failure("CAPABILITY_UNAVAILABLE", true)
                synchronized(this) {
                    if (store.get().revision != start.revision) return@synchronized failure("CANCELLED")
                    if (response.opt("installationId") != start.installationId) return@synchronized failure("INVALID_MESSAGE")
                    if (action == "revoke") {
                        if (response.opt("revoked") !is Boolean) failure("INVALID_MESSAGE") else result("disabled")
                    } else {
                        val receipt = PushBindingPolicy.digest(nonce)
                        if (!PushBindingPolicy.acceptsReceipt(response, start.installationId, receipt)) {
                            return@synchronized failure("CAPABILITY_UNAVAILABLE")
                        }
                        val next = store.get().bound(start.revision, receipt, requireNotNull(tokenDigest))
                            ?: return@synchronized failure("CANCELLED")
                        if (notifications.authorizationStatus() != NotificationAuthorizationStatus.Authorized) {
                            return@synchronized result("permissionDenied")
                        }
                        if (store.set(next)) result("enabled") else failure("INTERNAL_ERROR")
                    }
                }
            }.getOrElse { failure("CAPABILITY_UNAVAILABLE", true) }
            synchronized(this) { busy = false }
            main.post {
                synchronized(this) {
                    completion(if (store.get().revision != start.revision) failure("CANCELLED") else outcome)
                }
            }
        }
    }

    private fun result(status: String) = BridgeDispatchResult.Success(JSONObject().put("capability", "push").put("status", status))
    private fun failure(code: String, retryable: Boolean = false) =
        BridgeDispatchResult.Failure(code, "Push operation could not be completed", retryable)

    companion object {
        @Volatile private var instance: AndroidPushRuntime? = null
        fun get(context: Context): AndroidPushRuntime = instance ?: synchronized(this) {
            instance ?: AndroidPushRuntime(context).also { instance = it }
        }
    }
}
