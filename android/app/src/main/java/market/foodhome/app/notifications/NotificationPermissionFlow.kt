package market.foodhome.app.notifications

sealed interface NotificationPermissionResult {
    data class Status(val value: NotificationAuthorizationStatus) : NotificationPermissionResult
    data object Cancelled : NotificationPermissionResult
}

/** Effective app permission, not an assertion about which button the user pressed. */
object NotificationPermissionPolicy {
    fun status(
        runtimePermissionRequired: Boolean,
        runtimePermissionGranted: Boolean,
        notificationsEnabled: Boolean,
        channelBlocked: Boolean,
        attempted: Boolean,
    ): NotificationAuthorizationStatus = when {
        channelBlocked -> NotificationAuthorizationStatus.Denied
        runtimePermissionRequired && !runtimePermissionGranted ->
            if (attempted) NotificationAuthorizationStatus.Denied else NotificationAuthorizationStatus.NotDetermined
        !notificationsEnabled -> NotificationAuthorizationStatus.Denied
        else -> NotificationAuthorizationStatus.Authorized
    }
}

/** Main-thread owner of one OS dialog. No timer, pre-prompt, bind or token request. */
class NotificationPermissionFlow(
    private val status: () -> NotificationAuthorizationStatus,
    private val canLaunch: () -> Boolean,
    private val markAttempted: () -> Boolean,
    private val launch: () -> Unit,
) {
    private var inFlight = false
    private var pending: ((NotificationPermissionResult) -> Unit)? = null

    fun request(completion: (NotificationPermissionResult) -> Unit) {
        // Never replace the original callback with a newer WebView request.
        if (inFlight) { completion(NotificationPermissionResult.Cancelled); return }
        val current = status()
        if (current != NotificationAuthorizationStatus.NotDetermined) {
            completion(NotificationPermissionResult.Status(current)); return
        }
        if (!canLaunch()) { completion(NotificationPermissionResult.Cancelled); return }
        // Persist BEFORE showing the OS dialog. Dismissal, process death and denial
        // must not cause another automatic prompt on the next login/reload.
        if (!markAttempted()) {
            completion(NotificationPermissionResult.Status(NotificationAuthorizationStatus.Unavailable)); return
        }
        inFlight = true
        pending = completion
        try {
            launch()
        } catch (_: RuntimeException) {
            inFlight = false
            cancel()
        }
    }

    fun onResult() {
        inFlight = false
        val completion = pending
        pending = null
        completion?.invoke(NotificationPermissionResult.Status(status()))
    }

    fun cancel() {
        val completion = pending
        pending = null
        // Retain the in-flight tombstone until the old OS callback arrives.
        completion?.invoke(NotificationPermissionResult.Cancelled)
    }
}
