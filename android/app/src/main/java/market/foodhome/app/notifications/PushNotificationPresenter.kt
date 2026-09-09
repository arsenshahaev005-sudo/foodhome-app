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
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED) return
        AndroidNotificationCoordinator(context).createChannels()
        val intent = Intent(context, MainActivity::class.java)
            .setAction("market.foodhome.app.PUSH.${push.eventId}")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_PAYLOAD, JSONObject(data).toString())
        val pending = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = if (push.eventType == "chat.message") R.string.notification_new_message else R.string.notification_order_updated
        val publicVersion = NotificationCompat.Builder(context, AndroidNotificationCoordinator.UPDATES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_generic_update)).build()
        val notification = NotificationCompat.Builder(context, AndroidNotificationCoordinator.UPDATES_CHANNEL_ID)
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
            .build()
        // Permission can be revoked between the check and notify.
        try { manager.notify(push.eventId, 0, notification) } catch (_: SecurityException) { }
    }

    fun cancelAll() = NotificationManagerCompat.from(context).cancelAll()

    companion object { const val EXTRA_PAYLOAD = "market.foodhome.app.VISIBLE_PUSH" }
}
