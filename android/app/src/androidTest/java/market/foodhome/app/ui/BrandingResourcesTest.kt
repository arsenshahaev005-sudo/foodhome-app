package market.foodhome.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import market.foodhome.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrandingResourcesTest {
    @Test
    fun launcherAndSplashResourcesInflateWithPwaBackground() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(Color.rgb(255, 247, 237), context.getColor(R.color.foodhome_launch_background))
        assertEquals(R.mipmap.ic_launcher, context.applicationInfo.icon)
        // OEM package managers may wrap launcher icons; verify the bundled resource directly.
        val icon = requireNotNull(context.getDrawable(R.mipmap.ic_launcher))
        assertTrue("Bundled launcher type: ${icon.javaClass.name}", icon is AdaptiveIconDrawable)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 512, 512)
        icon.draw(Canvas(bitmap))
        File(context.getExternalFilesDir(null), "foodhome-launcher.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        for (id in listOf(R.drawable.foodhome_wordmark, R.drawable.foodhome_splash_wordmark)) {
            val drawable = requireNotNull(context.getDrawable(id))
            assertTrue(drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0)
        }
    }
}
