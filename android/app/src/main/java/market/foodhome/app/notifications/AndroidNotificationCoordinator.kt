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

class AndroidNotificationCoordinator(
    private val context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun createChannels() {
        createUpdatesChannel(UPDATES_CHANNEL_ID)
    }

    internal fun createUpdatesChannel(channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        // Existing channels belong to the user. Do not raise their importance,
        // delete/recreate them or replace their ID to override an earlier choice.
        if (manager.getNotificationChannel(channelId) != null) return
        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.notification_channel_updates_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_updates_description)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun authorizationStatus(): NotificationAuthorizationStatus {
        val runtimeRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val runtimeGranted = !runtimeRequired || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        return NotificationPermissionPolicy.status(
            runtimePermissionRequired = runtimeRequired,
            runtimePermissionGranted = runtimeGranted,
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            channelBlocked = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(UPDATES_CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE,
            attempted = permissionWasRequested(),
        )
    }

    // A single small durable write; fail closed if it cannot be persisted.
    fun markPermissionRequested(): Boolean =
        preferences.edit().putBoolean(KEY_PERMISSION_REQUESTED, true).commit()

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
