package market.foodhome.app.ui

sealed interface AppShellState {
    data object Loading : AppShellState
    data object Content : AppShellState
    data object Offline : AppShellState
    data object ServerError : AppShellState
    data object TlsError : AppShellState
    data object Maintenance : AppShellState
    data object RequiredUpdate : AppShellState
    data class RendererUnavailable(val loopBlocked: Boolean) : AppShellState
}
