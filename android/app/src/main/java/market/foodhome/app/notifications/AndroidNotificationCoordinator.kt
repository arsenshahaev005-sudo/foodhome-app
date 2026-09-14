package market.foodhome.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import market.foodhome.app.R

class AndroidNotificationCoordinator(
    private val context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun createChannels() {
        createUpdatesChannel(UPDATES_CHANNEL_ID)
    }

    internal fun createSellerOrdersChannel(
        channelId: String,
        legacyChannelId: String = UPDATES_CHANNEL_ID,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        // Channel settings are user-owned, including a replacement sound or silence.
        if (manager.getNotificationChannel(channelId) != null) return
        val legacy = manager.getNotificationChannel(legacyChannelId)
        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.notification_channel_seller_orders_name),
            minOf(legacy?.importance ?: NotificationManager.IMPORTANCE_HIGH, NotificationManager.IMPORTANCE_HIGH),
        ).apply {
            description = context.getString(R.string.notification_channel_seller_orders_description)
            setShowBadge(legacy?.canShowBadge() ?: true)
            // Do not turn an existing silent updates preference into an audible alert.
            setSound(
                if (legacy != null && legacy.sound == null) null else sellerOrderSoundUri(context),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
            legacy?.let {
                it.vibrationPattern?.let { pattern -> setVibrationPattern(pattern) }
                enableVibration(it.shouldVibrate())
                enableLights(it.shouldShowLights())
                lightColor = it.lightColor
            }
            // No DND bypass, alarm audio usage, forced volume or looping playback.
        }
        manager.createNotificationChannel(channel)
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
        const val SELLER_ORDERS_CHANNEL_ID = "foodhome_seller_new_orders"
        internal fun sellerOrderSoundUri(context: Context): Uri =
            // Stable resource name, not an integer ID that can change in the next APK.
            "android.resource://${context.packageName}/raw/seller_new_order".toUri()
        private const val PREFERENCES_NAME = "foodhome_notification_state"
        private const val KEY_PERMISSION_REQUESTED = "permission_requested"
    }
}
