package market.foodhome.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
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

        composeRule.onNodeWithTag("foodhome.shell.title")
            .assertIsDisplayed()
            .assertTextEquals("Food&Home")
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
