package market.foodhome.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.mutableStateOf
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodHomeShellSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingSurfaceIsBranded() {
        composeRule.setContent {
            MaterialTheme {
                AppShellSurface(AppShellState.Loading, onRetry = {})
            }
        }

        composeRule.onNodeWithTag("foodhome.shell.loading")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Загрузка Food&Home")
        composeRule.onNodeWithTag("foodhome.shell.logo").assertIsDisplayed()
        repeat(3) { composeRule.onNodeWithTag("foodhome.shell.dot.$it").assertIsDisplayed() }
        val image = composeRule.onNodeWithTag("foodhome.shell.loading").captureToImage().asAndroidBitmap()
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        File(directory, "foodhome-loading.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun loadingDoesNotCoverContentOrRecovery() {
        val state = mutableStateOf<AppShellState>(AppShellState.Loading)
        composeRule.setContent { MaterialTheme { AppShellSurface(state.value, onRetry = {}) } }
        composeRule.onNodeWithTag("foodhome.shell.loading").assertIsDisplayed()
        composeRule.runOnIdle { state.value = AppShellState.Content }
        composeRule.onNodeWithTag("foodhome.shell.loading").assertDoesNotExist()
        composeRule.runOnIdle { state.value = AppShellState.Offline }
        composeRule.onNodeWithTag("foodhome.shell.loading").assertDoesNotExist()
        composeRule.onNodeWithTag("foodhome.shell.retry").assertIsDisplayed()
    }

    @Test
    fun offlineSurfaceOffersControlledRetry() {
        composeRule.setContent {
            MaterialTheme {
                AppShellSurface(AppShellState.Offline, onRetry = {})
            }
        }

        composeRule.onNodeWithTag("foodhome.shell.title")
            .assertTextEquals("Нет подключения")
        composeRule.onNodeWithTag("foodhome.shell.retry").assertIsDisplayed()
    }
}
