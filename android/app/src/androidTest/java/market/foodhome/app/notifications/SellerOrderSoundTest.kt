package market.foodhome.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SellerOrderSoundTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val coordinator = AndroidNotificationCoordinator(context)
    private val legacyId = "foodhome.test.legacy.${UUID.randomUUID()}"
    private val sellerId = "foodhome.test.seller.${UUID.randomUUID()}"

    @After fun cleanupOwnedChannelsOnly() {
        manager.deleteNotificationChannel(sellerId)
        manager.deleteNotificationChannel(legacyId)
    }

    @Test fun namedSoundResourceIsExactOwnerFileAndDecodes() {
        val uri = AndroidNotificationCoordinator.sellerOrderSoundUri(context)
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals("7fb0a3e173d9cb882b3a95aa55072f34b536bcb9842c3729c10c6a3829559710", hash)
        val reader = MediaMetadataRetriever()
        try {
            reader.setDataSource(context, uri)
            val duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue(duration in 4_000..4_040)
        } finally { reader.release() }
    }

    @Test fun newSellerChannelHasNamedCustomNotificationSound() {
        coordinator.createSellerOrdersChannel(sellerId, legacyId)
        val channel = manager.getNotificationChannel(sellerId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals(AndroidNotificationCoordinator.sellerOrderSoundUri(context), channel.sound)
        assertEquals(AudioAttributes.USAGE_NOTIFICATION, channel.audioAttributes.usage)
        assertFalse(channel.canBypassDnd())
    }

    @Test fun existingSellerChannelIsNeverReset() {
        val original = NotificationChannel(sellerId, "Owned seller test", NotificationManager.IMPORTANCE_LOW)
            .apply { setSound(null, null); setShowBadge(false); enableVibration(false) }
        manager.createNotificationChannel(original)
        val before = manager.getNotificationChannel(sellerId)
        coordinator.createSellerOrdersChannel(sellerId, legacyId)
        assertEquals(before, manager.getNotificationChannel(sellerId))
    }

    @Test fun legacyMuteAndDisabledImportanceAreInheritedWithoutChangingLegacy() {
        val original = NotificationChannel(legacyId, "Owned legacy test", NotificationManager.IMPORTANCE_NONE)
            .apply { setSound(null, null); setShowBadge(false); enableVibration(false) }
        manager.createNotificationChannel(original)
        val before = manager.getNotificationChannel(legacyId)
        coordinator.createSellerOrdersChannel(sellerId, legacyId)
        val created = manager.getNotificationChannel(sellerId)
        assertNull(created.sound)
        assertEquals(NotificationManager.IMPORTANCE_NONE, created.importance)
        assertFalse(created.canShowBadge())
        assertFalse(created.shouldVibrate())
        assertEquals(before, manager.getNotificationChannel(legacyId))
    }

    @Test fun audibleDefaultLegacyImportanceIsNotRaised() {
        val legacy = NotificationChannel(legacyId, "Owned default test", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(legacy)
        coordinator.createSellerOrdersChannel(sellerId, legacyId)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, manager.getNotificationChannel(sellerId).importance)
    }

    @Test fun silentHighImportanceLegacyRemainsSilentInNewCategory() {
        val legacy = NotificationChannel(legacyId, "Owned silent test", NotificationManager.IMPORTANCE_HIGH)
            .apply { setSound(null, null) }
        manager.createNotificationChannel(legacy)
        coordinator.createSellerOrdersChannel(sellerId, legacyId)
        val created = manager.getNotificationChannel(sellerId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, created.importance)
        assertNull(created.sound)
    }

    @Test fun settingsDestinationIsNativeAllowlisted() {
        assertNull(NotificationSettingsIntentFactory.create(context, "arbitrary.channel"))
        val intent = NotificationSettingsIntentFactory.create(context, AndroidNotificationCoordinator.SELLER_ORDERS_CHANNEL_ID)
        assertNotNull(intent)
        if (intent!!.action == Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS) {
            assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
            assertEquals(AndroidNotificationCoordinator.SELLER_ORDERS_CHANNEL_ID, intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
        }
    }
}
