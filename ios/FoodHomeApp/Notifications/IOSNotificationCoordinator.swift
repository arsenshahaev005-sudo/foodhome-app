import UIKit
import UserNotifications

enum NotificationPermissionResult {
    case status(NotificationAuthorizationStatus)
    case cancelled
}

@MainActor
final class IOSNotificationCoordinator {
    private let center: UNUserNotificationCenter

    init(center: UNUserNotificationCenter = .current()) {
        self.center = center
    }

    func authorizationStatus(
        completion: @escaping (NotificationAuthorizationStatus) -> Void
    ) {
        center.getNotificationSettings { settings in
            let status: NotificationAuthorizationStatus
            switch settings.authorizationStatus {
            case .notDetermined:
                status = .notDetermined
            case .denied:
                status = .denied
            case .authorized, .ephemeral:
                status = .authorized
            case .provisional:
                status = .provisional
            @unknown default:
                status = .unavailable
            }
            DispatchQueue.main.async { completion(status) }
        }
    }

    func requestAuthorization(
        completion: @escaping (NotificationAuthorizationStatus) -> Void
    ) {
        center.requestAuthorization(options: [.alert, .badge, .sound]) { [weak self] granted, _ in
            DispatchQueue.main.async {
                if granted {
                    UIApplication.shared.registerForRemoteNotifications()
                }
                self?.authorizationStatus(completion: completion)
            }
        }
    }
}

protocol PushTokenSink {
    func receive(_ token: SensitivePushToken)
}

struct DisabledPushTokenSink: PushTokenSink {
    func receive(_ token: SensitivePushToken) {
        // Intentionally disabled until the accepted MobileInstallation binding contract exists.
    }
}
