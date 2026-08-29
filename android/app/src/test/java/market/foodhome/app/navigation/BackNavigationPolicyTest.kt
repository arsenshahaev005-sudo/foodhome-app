package market.foodhome.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class BackNavigationPolicyTest {
    @Test
    fun `web history handles back only when available`() {
        assertEquals(
            BackNavigationAction.WebHistory,
            BackNavigationPolicy.decide(canGoBack = true),
        )
        assertEquals(
            BackNavigationAction.System,
            BackNavigationPolicy.decide(canGoBack = false),
        )
    }
}
