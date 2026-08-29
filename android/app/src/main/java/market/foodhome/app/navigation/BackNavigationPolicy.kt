package market.foodhome.app.navigation

enum class BackNavigationAction {
    WebHistory,
    System,
}
object BackNavigationPolicy {
    fun decide(canGoBack: Boolean): BackNavigationAction = if (canGoBack) {
        BackNavigationAction.WebHistory
    } else {
        BackNavigationAction.System
    }
}
