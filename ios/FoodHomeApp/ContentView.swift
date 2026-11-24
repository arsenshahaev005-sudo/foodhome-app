import SwiftUI

struct ContentView: View {
    @StateObject private var store: WebViewStore
    @Environment(\.scenePhase) private var scenePhase

    init() {
        let environment = AppEnvironmentResolver.current()
        let fixedLaunchState: AppShellState?
#if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        if arguments.contains("--ui-test-offline") {
            fixedLaunchState = .offline
        } else if arguments.contains("--ui-test-loading") {
            fixedLaunchState = .loading
        } else if arguments.contains("--ui-test-viewport") {
            fixedLaunchState = .content
        } else {
            fixedLaunchState = nil
        }
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
                .ignoresSafeArea(.container, edges: .bottom)

            switch store.state {
            case .content:
                EmptyView()
            case .loading:
                LaunchSurface()
                    .ignoresSafeArea(.container)
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
        .onReceive(NotificationCenter.default.publisher(for: .foodHomePushRoute)) { _ in
            store.deliverPendingPush()
        }
        .onReceive(NotificationCenter.default.publisher(for: .foodHomePushStateChanged)) { _ in
            store.pushStateChanged()
        }
    }
}

private struct LaunchSurface: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geometry in
            VStack(spacing: 16) {
                Image("FoodHomeWordmark")
                    .resizable()
                    .scaledToFit()
                    .frame(width: min(geometry.size.width * 0.76, 280))
                TimelineView(.animation(minimumInterval: 1.0 / 30, paused: reduceMotion)) { timeline in
                    HStack(spacing: 6) {
                        ForEach(0..<3) { index in
                            let elapsed = timeline.date.timeIntervalSinceReferenceDate + Double(index) * 0.12
                            let phase = elapsed.truncatingRemainder(dividingBy: 0.9)
                            let pulse = reduceMotion ? 1.0 : max(0, 1 - abs(phase - 0.36) / 0.36)
                            Circle()
                                .fill(Color(red: 201 / 255, green: 130 / 255, blue: 91 / 255))
                                .frame(width: 6, height: 6)
                                .opacity(0.28 + 0.72 * pulse)
                                .offset(y: -4 * pulse)
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Color(red: 1, green: 247 / 255, blue: 237 / 255))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Загружаем Food&Home")
        .accessibilityIdentifier("foodhome.shell.loading")
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
