import UIKit
import UserNotifications

enum NotificationPermissionResult {
    case status(NotificationAuthorizationStatus)
    case cancelled
}

@MainActor
protocol IOSNotificationPermissions {
    func authorizationStatus(completion: @escaping (NotificationAuthorizationStatus) -> Void)
    func requestAuthorization(completion: @escaping (NotificationAuthorizationStatus) -> Void)
}

extension IOSNotificationPermissions {
    func requestAuthorizationIfNeeded(
        canPresent: @escaping () -> Bool,
        completion: @escaping (NotificationPermissionResult) -> Void
    ) {
        guard canPresent() else { completion(.cancelled); return }
        authorizationStatus { status in
            guard status == .notDetermined else { completion(.status(status)); return }
            guard canPresent() else { completion(.cancelled); return }
            self.requestAuthorization { completion(.status($0)) }
        }
    }
}

@MainActor
final class IOSNotificationCoordinator: IOSNotificationPermissions {
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
        center.requestAuthorization(options: [.alert, .badge, .sound]) { [self] _, _ in
            DispatchQueue.main.async {
                self.authorizationStatus(completion: completion)
            }
        }
    }
}
