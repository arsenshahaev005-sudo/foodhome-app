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

    func status() async -> NotificationAuthorizationStatus {
        await withCheckedContinuation { continuation in
            authorizationStatus { continuation.resume(returning: $0) }
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
