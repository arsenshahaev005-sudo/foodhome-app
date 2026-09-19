package market.foodhome.app.notifications

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import market.foodhome.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationBrandingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun brandMarkHasWhiteGeometryAndTransparentBackground() {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val drawable = requireNotNull(context.getDrawable(R.drawable.ic_notification))
        drawable.setBounds(0, 0, 96, 96)
        drawable.draw(Canvas(bitmap))
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        assertEquals(0, Color.alpha(bitmap.getPixel(95, 95)))
        var visible = 0
        for (y in 0 until 96) for (x in 0 until 96) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.alpha(pixel) > 0) {
                visible++
                assertEquals(255, Color.red(pixel))
                assertEquals(255, Color.green(pixel))
                assertEquals(255, Color.blue(pixel))
            }
        }
        assertTrue("Recognizable geometry, not an empty mask or filled square", visible in 1500..6500)
    }

    @Test fun bothNotificationCategoriesHaveLocalBrandIcons() {
        for (channel in listOf(AndroidNotificationCoordinator.UPDATES_CHANNEL_ID,
            AndroidNotificationCoordinator.SELLER_ORDERS_CHANNEL_ID)) {
            // Build only: never post notifications or create/change user channels in CI.
            val notification = NotificationBranding.builder(context, channel).build()
            assertEquals(channel, notification.channelId)
            assertEquals(R.drawable.ic_notification, notification.smallIcon.resId)
            assertEquals(context.getColor(R.color.foodhome_launch_accent), notification.color)
            val logo = requireNotNull(notification.getLargeIcon()).loadDrawable(context)
            assertNotNull(logo)
            assertTrue(logo is BitmapDrawable)
            val bitmap = (logo as BitmapDrawable).bitmap
            assertTrue(bitmap.width in 1..128)
            assertTrue(bitmap.height in 1..128)
        }
    }
}
