import UIKit
import UserNotifications

extension Notification.Name {
    static let foodHomePushRoute = Notification.Name("market.foodhome.push-route")
    static let foodHomePushStateChanged = Notification.Name("market.foodhome.push-state")
}

@MainActor
final class FoodHomeAppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        IOSPushRegistration.shared.receive(deviceToken)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        IOSPushRegistration.shared.registrationFailed()
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        Task { @MainActor in
            let push = IOSPushRegistration.shared.parsed(notification.request.content.userInfo)
            completionHandler(push == nil ? [] : [.banner, .list, .sound])
        }
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        Task { @MainActor in
            if response.actionIdentifier == UNNotificationDefaultActionIdentifier,
               let push = IOSPushRegistration.shared.parsed(response.notification.request.content.userInfo) {
                IOSPushRegistration.shared.queue(push)
            }
            completionHandler()
        }
    }
}
