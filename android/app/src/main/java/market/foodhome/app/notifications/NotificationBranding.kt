package market.foodhome.app.notifications

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import market.foodhome.app.R

/** Local brand assets only; shared by normal and privacy-redacted notifications. */
internal object NotificationBranding {
    fun builder(context: Context, channelId: String): NotificationCompat.Builder {
        // The pinned source is 512px. Decode at 128px rather than attaching a full
        // launcher bitmap or fetching an image while handling a push.
        val logo = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.pwa_icon_maskable,
            BitmapFactory.Options().apply { inSampleSize = 4; inScaled = false },
        )
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(logo)
            .setColor(ContextCompat.getColor(context, R.color.foodhome_launch_accent))
    }
}
