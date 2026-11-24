import Foundation
import CoreFoundation
import UIKit
import UserNotifications

private final class PushRedirectPolicy: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask,
                    willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
                    completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}

@MainActor
final class IOSPushRegistration {
    static let shared = IOSPushRegistration()

    private struct Installation: Codable {
        let id: UUID
        var bindingID: String?
        var tokenDigest: String?
        // A POST can reach the server even if its response is lost or the app quits.
        var bindingAttempted: Bool?
    }

    private let file: URL
    private let network: URLSession
    private var state: Installation
    private var registration = APNsDeviceRegistration()
    private var generation = UUID()
    private var previousRequest: Task<Void, Never>?
    private var queuedRequests = 0
    private var inbox = PushNavigationInbox()

    private init() {
        let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("FoodHomePush", isDirectory: true)
        file = directory.appendingPathComponent("installation.json")
        state = (try? Data(contentsOf: file)).flatMap { try? JSONDecoder().decode(Installation.self, from: $0) }
            ?? Installation(id: UUID())
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = nil
        configuration.httpCookieStorage = nil
        configuration.urlCredentialStorage = nil
        configuration.httpShouldSetCookies = false
        configuration.timeoutIntervalForRequest = 5
        configuration.timeoutIntervalForResource = 6
        network = URLSession(configuration: configuration, delegate: PushRedirectPolicy(), delegateQueue: nil)
    }

    private func persist() -> Bool {
        do {
            var directory = file.deletingLastPathComponent()
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            try directory.setResourceValues(values)
            try JSONEncoder().encode(state).write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            return true
        } catch { return false }
    }

    func receive(_ value: Data) {
        guard AppEnvironmentResolver.productionPushAvailable, registration.receive(value) else { return }
        if let old = state.tokenDigest, old != PushPayloadPolicy.digest(value) {
            state.bindingID = nil
            state.tokenDigest = nil
            state.bindingAttempted = true
            inbox = PushNavigationInbox()
            _ = persist()
        }
        NotificationCenter.default.post(name: .foodHomePushStateChanged, object: nil)
    }

    func registrationFailed() {
        if registration.fail() {
            NotificationCenter.default.post(name: .foodHomePushStateChanged, object: nil)
        }
    }

    private func requestDeviceToken() {
        if registration.begin() { UIApplication.shared.registerForRemoteNotifications() }
    }

    func suspend() { inbox.sessionConfirmed = false }

    func resume() {
        inbox.sessionConfirmed = false
        guard state.bindingID != nil else { return }
        let current = generation
        Task {
            let permission = await IOSNotificationCoordinator().status()
            guard current == generation else { return }
            if permission.canReceivePush { requestDeviceToken() }
            else if permission == .denied { _ = clear(stopRegistration: true) }
        }
    }

    func parsed(_ info: [AnyHashable: Any]) -> PushOpen? {
        guard AppEnvironmentResolver.productionPushAvailable else { return nil }
        return PushPayloadPolicy.parse(info, bindingID: state.bindingID)
    }

    func queue(_ open: PushOpen) {
        guard inbox.enqueue(open) else { return }
        NotificationCenter.default.post(name: .foodHomePushRoute, object: nil)
    }

    func takeRoute() -> URL? {
        inbox.take(bindingID: state.bindingID)
    }

    @discardableResult
    private func clear(stopRegistration: Bool = false) -> Bool {
        generation = UUID()
        inbox = PushNavigationInbox()
        UNUserNotificationCenter.current().removeAllDeliveredNotifications()
        let couldReceiveForOldOwner = state.bindingID != nil || state.bindingAttempted == true
        if registration.clear(couldReceiveForOldOwner: couldReceiveForOldOwner, stopRegistration: stopRegistration) {
            UIApplication.shared.unregisterForRemoteNotifications()
        }
        state.bindingID = nil
        state.tokenDigest = nil
        state.bindingAttempted = nil
        if persist() { return true }
        // A stale generation must not be restored on relaunch if storage is full.
        try? FileManager.default.removeItem(at: file)
        return false
    }

    private func result(_ status: String) -> BridgeDispatchResult {
        .success(["capability": "push", "status": status])
    }

    func dispatch(_ payload: [String: Any], origin: URL, bridgeVersion: String,
                  completion: @escaping (BridgeDispatchResult) -> Void) {
        guard origin == PushPayloadPolicy.origin, PushPayloadPolicy.validAction(payload),
              let action = payload["action"] as? String else {
            completion(.failure(code: "INVALID_PAYLOAD", message: "Invalid notification request"))
            return
        }
        if action == "clear" {
            completion(result(clear() ? "disabled" : "unavailable"))
            return
        }
        if action == "revoke" { _ = clear(stopRegistration: true) }
        guard AppEnvironmentResolver.productionPushAvailable else {
            if action == "status" { completion(result("unavailable")) }
            else { completion(.failure(code: "CAPABILITY_UNAVAILABLE", message: "Production push registration is unavailable")) }
            return
        }
        guard queuedRequests < 8 else {
            completion(.failure(code: "RATE_LIMITED", message: "Notification request limit reached", retryable: true))
            return
        }
        queuedRequests += 1
        let current = generation
        let previous = previousRequest
        previousRequest = Task {
            defer { queuedRequests -= 1 }
            await previous?.value
            guard current == generation, PushPayloadPolicy.validAction(payload) else {
                completion(.failure(code: "CANCELLED", message: "Notification request expired or was superseded"))
                return
            }
            if action == "revoke", let nonce = payload["nonce"] as? String {
                let receipt = await self.post(action, nonce: nonce, additional: [:])
                guard let revoked = receipt?["revoked"] as? NSNumber, CFGetTypeID(revoked) == CFBooleanGetTypeID() else {
                    completion(.failure(code: "CAPABILITY_UNAVAILABLE", message: "Revocation could not be confirmed", retryable: true))
                    return
                }
                completion(self.result("disabled"))
                return
            }
            let permission = await IOSNotificationCoordinator().status()
            guard current == generation else { completion(self.result("disabled")); return }
            guard permission.canReceivePush else {
                if permission == .denied || permission == .notDetermined { _ = clear(stopRegistration: true) }
                else { inbox.sessionConfirmed = false }
                completion(self.result(permission == .denied ? "permissionDenied" : "disabled"))
                return
            }
            requestDeviceToken()
            if action == "status" {
                inbox.sessionConfirmed = true
                // The website checked the authenticated owner and consent before asking for status.
                if inbox.hasPending { NotificationCenter.default.post(name: .foodHomePushRoute, object: nil) }
                completion(self.result(registration.status(bindingID: state.bindingID, tokenDigest: state.tokenDigest)))
                return
            }
            // The token callback wakes the website to get a fresh grant for the current account.
            guard let deviceToken = registration.token else { completion(self.result("unavailable")); return }
            guard let nonce = payload["nonce"] as? String, PushPayloadPolicy.validAction(payload),
                  let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String,
                  let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String
            else { completion(.failure(code: "INVALID_PAYLOAD", message: "Invalid notification registration")); return }
            state.bindingAttempted = true
            guard persist() else { completion(self.result("unavailable")); return }
            let preferred = Locale.preferredLanguages.first ?? "ru"
            let receipt = await self.post(action, nonce: nonce, additional: [
                "platform": "ios", "provider": "apns", "appVersion": version, "buildVersion": build,
                "bridgeVersion": bridgeVersion, "locale": PushPayloadPolicy.matches(preferred, "[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8}){0,2}") ? preferred : "ru",
                "notificationStatus": permission.rawValue, "pushToken": deviceToken.map { String(format: "%02x", $0) }.joined()
            ])
            let bindingID = PushPayloadPolicy.digest(Data(nonce.utf8))
            guard current == generation, deviceToken == registration.token else { completion(self.result("needsBinding")); return }
            guard PushPayloadPolicy.acceptsBindingReceipt(receipt, bindingID: bindingID, permission: permission) else {
                completion(.failure(code: "CAPABILITY_UNAVAILABLE", message: "Binding could not be confirmed", retryable: true))
                return
            }
            state.bindingID = bindingID
            state.tokenDigest = PushPayloadPolicy.digest(deviceToken)
            state.bindingAttempted = nil
            guard persist() else { _ = clear(); completion(self.result("unavailable")); return }
            inbox.sessionConfirmed = true
            completion(self.result("enabled"))
        }
    }

    private func post(_ action: String, nonce: String, additional: [String: String]) async -> [String: Any]? {
        guard let url = PushPayloadPolicy.installationEndpoint(for: action) else { return nil }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.httpShouldHandleCookies = false
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.setValue("FoodHomeBinding " + nonce, forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        var body = additional
        body["installationId"] = state.id.uuidString.lowercased()
        body["environment"] = "production"
        do {
            request.httpBody = try JSONEncoder().encode(body)
            let (stream, response) = try await network.bytes(for: request)
            guard let http = response as? HTTPURLResponse, [200, 201].contains(http.statusCode),
                  http.url == url, http.expectedContentLength <= 16_384 else { return nil }
            var data = Data()
            for try await byte in stream {
                guard data.count < 16_384 else { return nil }
                data.append(byte)
            }
            guard let result = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let id = result["installationId"] as? String, UUID(uuidString: id) == state.id else { return nil }
            return result
        } catch { return nil }
    }
}
