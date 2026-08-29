import SwiftUI

struct ContentView: View {
    @StateObject private var store: WebViewStore
    @Environment(\.scenePhase) private var scenePhase

    init() {
        let environment = AppEnvironmentResolver.current()
        let fixedLaunchState: AppShellState?
#if DEBUG
        fixedLaunchState = ProcessInfo.processInfo.arguments.contains("--ui-test-offline")
            ? .offline
            : nil
#else
        fixedLaunchState = nil
#endif
        _store = StateObject(
            wrappedValue: WebViewStore(
                environment: environment,
                manifest: BridgeManifest.load(),
                fixedLaunchState: fixedLaunchState
            )
        )
    }

    var body: some View {
        ZStack {
            Color(red: 1.0, green: 0.973, blue: 0.945)
                .ignoresSafeArea()

            WebViewContainer(store: store)
                .id(store.webViewGeneration)

            switch store.state {
            case .content:
                EmptyView()
            case .loading:
                StatusPanel(
                    title: "Food&Home",
                    message: "Загружаем домашнюю еду…"
                )
            case .offline:
                RecoveryPanel(
                    title: "Нет подключения",
                    message: "Проверьте интернет и попробуйте снова.",
                    action: store.retry
                )
            case .serverError:
                RecoveryPanel(
                    title: "Сервис временно недоступен",
                    message: "Мы не смогли загрузить Food&Home.",
                    action: store.retry
                )
            case .tlsError:
                StatusPanel(
                    title: "Безопасное соединение не установлено",
                    message: "Соединение отменено. Повторите позже."
                )
            case .maintenance:
                StatusPanel(
                    title: "Технические работы",
                    message: "Food&Home временно недоступен. Попробуйте позже."
                )
            case .requiredUpdate:
                StatusPanel(
                    title: "Требуется обновление",
                    message: "Установите актуальную версию Food&Home, чтобы продолжить."
                )
            case let .rendererUnavailable(loopBlocked):
                if loopBlocked {
                    StatusPanel(
                        title: "Экран временно остановлен",
                        message: "Повторные сбои остановлены. Данные входа не очищались."
                    )
                } else {
                    RecoveryPanel(
                        title: "Экран нужно перезапустить",
                        message: "Веб-процесс завершился. Данные входа не очищались.",
                        action: store.retry
                    )
                }
            }
        }
        .onAppear { store.sceneBecameActive() }
        .onOpenURL { _ = store.receivedUniversalLink($0) }
        .onChange(of: scenePhase) { phase in
            switch phase {
            case .active:
                store.sceneBecameActive()
            case .background:
                store.sceneLeftForeground()
            case .inactive:
                break
            @unknown default:
                break
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .foodHomePushRoute)) { event in
            guard let route = event.object as? URL else { return }
            store.offerPushRoute(route)
        }
    }
}

private struct StatusPanel: View {
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 12) {
            Text(title)
                .font(.title.bold())
                .foregroundStyle(Color(red: 0.608, green: 0.227, blue: 0.133))
                .accessibilityIdentifier("foodhome.shell.title")
            Text(message)
                .font(.body)
                .multilineTextAlignment(.center)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(red: 1.0, green: 0.973, blue: 0.945))
    }
}

private struct RecoveryPanel: View {
    let title: String
    let message: String
    let action: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Text(title)
                .font(.title2.bold())
                .foregroundStyle(Color(red: 0.608, green: 0.227, blue: 0.133))
                .accessibilityIdentifier("foodhome.shell.title")
            Text(message)
                .font(.body)
                .multilineTextAlignment(.center)
            Button("Повторить", action: action)
                .buttonStyle(.borderedProminent)
                .tint(Color(red: 0.608, green: 0.227, blue: 0.133))
                .accessibilityIdentifier("foodhome.shell.retry")
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(red: 1.0, green: 0.973, blue: 0.945))
    }
}
