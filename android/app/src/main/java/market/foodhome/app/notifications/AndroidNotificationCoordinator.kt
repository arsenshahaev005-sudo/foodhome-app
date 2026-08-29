package market.foodhome.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import market.foodhome.app.R

sealed interface NotificationPermissionResult {
    data class Status(val value: NotificationAuthorizationStatus) : NotificationPermissionResult
    data object Cancelled : NotificationPermissionResult
}

class AndroidNotificationCoordinator(
    private val context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            UPDATES_CHANNEL_ID,
            context.getString(R.string.notification_channel_updates_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_updates_description)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun authorizationStatus(): NotificationAuthorizationStatus {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return if (permissionWasRequested()) {
                NotificationAuthorizationStatus.Denied
            } else {
                NotificationAuthorizationStatus.NotDetermined
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                return if (permissionWasRequested()) {
                    NotificationAuthorizationStatus.Denied
                } else {
                    NotificationAuthorizationStatus.NotDetermined
                }
            }
        }
        return NotificationAuthorizationStatus.Authorized
    }

    fun markPermissionRequested() {
        preferences.edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply()
    }

    private fun permissionWasRequested(): Boolean = preferences.getBoolean(
        KEY_PERMISSION_REQUESTED,
        false,
    )

    companion object {
        const val UPDATES_CHANNEL_ID = "foodhome_updates"
        private const val PREFERENCES_NAME = "foodhome_notification_state"
        private const val KEY_PERMISSION_REQUESTED = "permission_requested"
    }
}
