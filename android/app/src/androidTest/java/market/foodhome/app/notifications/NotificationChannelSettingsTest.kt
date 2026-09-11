package market.foodhome.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NotificationChannelSettingsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    // Never modify or remove the real user's foodhome_updates channel in tests.
    private val testId = "foodhome.test.updates.${UUID.randomUUID()}"
    private val coordinator = AndroidNotificationCoordinator(context)

    @After
    fun removeOnlyOwnedTestChannel() {
        manager.deleteNotificationChannel(testId)
    }

    @Test
    fun newChannelUsesHeadsUpImportance() {
        coordinator.createUpdatesChannel(testId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(testId).importance)
    }

    @Test
    fun existingDefaultChannelIsPreservedOnUpgrade() = preserves(NotificationManager.IMPORTANCE_DEFAULT)

    @Test
    fun existingQuietChannelIsPreserved() = preserves(NotificationManager.IMPORTANCE_LOW)

    @Test
    fun existingDisabledChannelIsNotReenabled() = preserves(NotificationManager.IMPORTANCE_NONE)

    private fun preserves(importance: Int) {
        val original = NotificationChannel(testId, "Owned test channel", importance).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(original)
        val before = manager.getNotificationChannel(testId)
        coordinator.createUpdatesChannel(testId)
        assertEquals(before, manager.getNotificationChannel(testId))
    }

    @Test
    fun settingsIntentTargetsOnlyOwnChannelAndSystemComponent() {
        val intent = NotificationSettingsIntentFactory.create(context)
        assertNotNull(intent)
        intent!!
        val component = intent.component
        assertNotNull(component)
        val info = context.packageManager.getApplicationInfo(component!!.packageName, 0)
        assertTrue(info.flags and ApplicationInfo.FLAG_SYSTEM != 0)
        if (intent.action == Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS) {
            assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
            assertEquals(AndroidNotificationCoordinator.UPDATES_CHANNEL_ID, intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
            assertEquals(setOf(Settings.EXTRA_APP_PACKAGE, Settings.EXTRA_CHANNEL_ID), intent.extras!!.keySet())
        } else if (intent.action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
            assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        } else {
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
            assertEquals("package:${context.packageName}", intent.dataString)
        }
    }
}
