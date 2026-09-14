package market.foodhome.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import market.foodhome.app.MainActivity
import market.foodhome.app.R
import org.json.JSONObject

internal class PushNotificationPresenter(private val context: Context) {
    fun show(push: VisiblePush, data: Map<String, String>) {
        val kind = PushNotificationKind.forEvent(push.eventType) ?: return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED) return
        AndroidNotificationCoordinator(context).createChannels()
        if (kind == PushNotificationKind.SellerNewOrder) {
            // Don't snapshot an OEM's pre-permission state during initial app launch.
            AndroidNotificationCoordinator(context).createSellerOrdersChannel(
                AndroidNotificationCoordinator.SELLER_ORDERS_CHANNEL_ID,
            )
        }
        val intent = Intent(context, MainActivity::class.java)
            .setAction("market.foodhome.app.PUSH.${push.eventId}")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_PAYLOAD, JSONObject(data).toString())
        val pending = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = when (push.eventType) {
            "seller.order.new" -> R.string.notification_seller_new_order
            "chat.message" -> R.string.notification_new_message
            else -> R.string.notification_order_updated
        }
        val publicVersion = NotificationCompat.Builder(context, kind.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_generic_update)).build()
        val notification = NotificationCompat.Builder(context, kind.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setCategory(if (push.eventType == "chat.message") NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter((push.expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(1))
            .apply {
                NotificationSettingsIntentFactory.create(context, kind.channelId)?.let { settingsIntent ->
                    val settingsPendingIntent = PendingIntent.getActivity(
                        // Intent extras do not participate in PendingIntent identity.
                        context, if (kind == PushNotificationKind.SellerNewOrder) 2 else 1, settingsIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    addAction(
                        NotificationCompat.Action.Builder(
                            android.R.drawable.ic_menu_manage,
                            context.getString(R.string.notification_settings_action),
                            settingsPendingIntent,
                        ).setAuthenticationRequired(true).build(),
                    )
                }
            }
            .build()
        // Permission can be revoked between the check and notify.
        try { manager.notify(push.eventId, 0, notification) } catch (_: SecurityException) { }
    }

    fun cancelAll() = NotificationManagerCompat.from(context).cancelAll()

    companion object { const val EXTRA_PAYLOAD = "market.foodhome.app.VISIBLE_PUSH" }
}
