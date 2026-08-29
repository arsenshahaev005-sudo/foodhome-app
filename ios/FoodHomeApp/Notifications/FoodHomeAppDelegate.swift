import UIKit
import UserNotifications

extension Notification.Name {
    static let foodHomePushRoute = Notification.Name("market.foodhome.push-route")
}

final class FoodHomeAppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    private let tokenSink: PushTokenSink = DisabledPushTokenSink()
    private let pushPolicy = PushPayloadPolicy(
        navigationPolicy: NavigationPolicy(
            trustedOrigin: URL(string: "https://foodhome.market")!
        )
    )

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        guard let token = SensitivePushToken(deviceToken) else { return }
        tokenSink.receive(token)
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        // Provider details are deliberately not logged or bridged.
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        defer { completionHandler() }
        let strings = response.notification.request.content.userInfo.reduce(
            into: [String: String]()
        ) { result, entry in
            guard let key = entry.key as? String,
                  ["eventId", "route", "type"].contains(key),
                  let value = entry.value as? String
            else {
                return
            }
            result[key] = value
        }
        guard let push = pushPolicy.parse(strings) else { return }
        NotificationCenter.default.post(
            name: .foodHomePushRoute,
            object: push.route
        )
    }
}
