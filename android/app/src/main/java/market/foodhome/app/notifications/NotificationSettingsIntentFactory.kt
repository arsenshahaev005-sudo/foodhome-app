package market.foodhome.app.notifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/** Native-only settings navigation: no URLs, channel IDs or tokens from JavaScript. */
internal object NotificationSettingsIntentFactory {
    fun create(context: Context): Intent? {
        val candidates = listOf(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, AndroidNotificationCoordinator.UPDATES_CHANNEL_ID),
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null)),
        )
        for (intent in candidates) {
            val activity = context.packageManager.resolveActivity(
                intent, PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_SYSTEM_ONLY,
            )?.activityInfo ?: continue
            if (!activity.exported) continue
            // Pin the PendingIntent to the resolved system component, not an implicit handler.
            return intent.setComponent(ComponentName(activity.packageName, activity.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return null
    }
}
