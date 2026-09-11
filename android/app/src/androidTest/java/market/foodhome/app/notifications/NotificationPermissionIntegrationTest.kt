package market.foodhome.app.notifications

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationPermissionIntegrationTest {
    @Test
    @SdkSuppress(maxSdkVersion = 32)
    fun preAndroid13ReturnsRealSystemStateWithoutPromptOrPreferenceWrite() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val coordinator = AndroidNotificationCoordinator(context)
        val expected = coordinator.authorizationStatus()
        val flow = NotificationPermissionFlow(
            status = coordinator::authorizationStatus,
            canLaunch = { error("No runtime notification dialog before Android 13") },
            markAttempted = { error("Do not modify the user's preferences") },
            launch = { error("Do not request an unsupported runtime permission") },
        )
        val results = mutableListOf<NotificationPermissionResult>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            repeat(2) { flow.request(results::add) }
        }
        assertEquals(List(2) { NotificationPermissionResult.Status(expected) }, results)
    }
}
