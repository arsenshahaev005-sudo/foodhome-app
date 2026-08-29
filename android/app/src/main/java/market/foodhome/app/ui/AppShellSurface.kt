package market.foodhome.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal val FoodHomeBackground = Color(0xFFFFF8F1)
private val FoodHomeAccent = Color(0xFF9B3A22)

@Composable
fun AppShellSurface(
    state: AppShellState,
    onRetry: () -> Unit,
) {
    when (state) {
        AppShellState.Content -> Unit
        AppShellState.Loading -> StatusPanel("Food&Home", "Загружаем домашнюю еду…")
        AppShellState.Offline -> RecoveryPanel(
            title = "Нет подключения",
            message = "Проверьте интернет и попробуйте снова.",
            onRetry = onRetry,
        )
        AppShellState.ServerError -> RecoveryPanel(
            title = "Сервис временно недоступен",
            message = "Мы не смогли загрузить Food&Home.",
            onRetry = onRetry,
        )
        AppShellState.TlsError -> StatusPanel(
            title = "Безопасное соединение не установлено",
            message = "Соединение отменено. Повторите позже.",
        )
        AppShellState.Maintenance -> StatusPanel(
            title = "Технические работы",
            message = "Food&Home временно недоступен. Попробуйте позже.",
        )
        AppShellState.RequiredUpdate -> StatusPanel(
            title = "Требуется обновление",
            message = "Установите актуальную версию Food&Home, чтобы продолжить.",
        )
        is AppShellState.RendererUnavailable -> {
            if (state.loopBlocked) {
                StatusPanel(
                    title = "Экран временно остановлен",
                    message = "Повторные сбои остановлены. Данные входа не очищались.",
                )
            } else {
                RecoveryPanel(
                    title = "Экран нужно перезапустить",
                    message = "Веб-процесс завершился. Данные входа не очищались.",
                    onRetry = onRetry,
                )
            }
        }
    }
}
@Composable
private fun StatusPanel(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FoodHomeBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = FoodHomeAccent,
            modifier = Modifier.testTag("foodhome.shell.title"),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun RecoveryPanel(title: String, message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FoodHomeBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = FoodHomeAccent,
            modifier = Modifier.testTag("foodhome.shell.title"),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        Button(
            onClick = onRetry,
            modifier = Modifier
                .padding(top = 20.dp)
                .testTag("foodhome.shell.retry"),
        ) {
            Text("Повторить")
        }
    }
}
