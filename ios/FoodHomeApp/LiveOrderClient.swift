import CryptoKit
import Foundation
import Security

struct LiveOrderBinding: Codable {
    let id: UUID
    let orderID: UUID
    let origin: URL
    let secret: String
    var expiresAt: Date
    var revoking = false
    var wasPresented: Bool?
    var digest: String?
    var sequence = 0
    var confirmed = 0

    init(orderID: UUID, origin: URL) {
        id = UUID()
        self.orderID = orderID
        self.origin = origin
        secret = SymmetricKey(size: .bits256).withUnsafeBytes { bytes in
            bytes.map { String(format: "%02x", $0) }.joined()
        }
        expiresAt = Date().addingTimeInterval(8 * 3600)
    }
}

struct LiveOrderBindingStore {
    private let base: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: "market.foodhome.order-activity",
        kSecAttrAccount as String: "registrations-v1"
    ]

    func read() throws -> [LiveOrderBinding] {
        var query = base
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return [] }
        guard status == errSecSuccess, let data = item as? Data else { throw LiveOrderError.storage }
        return try JSONDecoder().decode([LiveOrderBinding].self, from: data)
    }

    func write(_ bindings: [LiveOrderBinding]) throws {
        let data = try JSONEncoder().encode(bindings)
        let updates = [kSecValueData as String: data]
        let status = SecItemUpdate(base as CFDictionary, updates as CFDictionary)
        if status == errSecSuccess { return }
        guard status == errSecItemNotFound else { throw LiveOrderError.storage }
        var newItem = base
        newItem[kSecValueData as String] = data
        newItem[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        guard SecItemAdd(newItem as CFDictionary, nil) == errSecSuccess else { throw LiveOrderError.storage }
    }
}

enum LiveOrderError: Error {
    case storage, invalidResponse, invalidOrigin
    case http(Int)
}

private final class LiveOrderNoRedirect: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask,
                    willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}

struct LiveOrderSnapshot: Decodable {
    let orderId: UUID
    let stage: String
    let expiresAt: TimeInterval
}

final class LiveOrderClient {
    private let session: URLSession

    init() {
        let config = URLSessionConfiguration.ephemeral
        config.httpShouldSetCookies = false
        config.httpCookieStorage = nil
        config.urlCredentialStorage = nil
        config.urlCache = nil
        config.timeoutIntervalForRequest = 7
        config.timeoutIntervalForResource = 10
        session = URLSession(configuration: config, delegate: LiveOrderNoRedirect(), delegateQueue: nil)
    }

    func register(_ binding: LiveOrderBinding, grant: String) async throws -> LiveOrderSnapshot {
        let data = try await send("register", binding, ["orderId": binding.orderID.uuidString.lowercased(), "grant": grant])
        let snapshot = try JSONDecoder().decode(LiveOrderSnapshot.self, from: data)
        guard snapshot.orderId == binding.orderID else { throw LiveOrderError.invalidResponse }
        return snapshot
    }

    func setToken(_ token: Data, for binding: LiveOrderBinding) async throws {
        let hex = token.map { String(format: "%02x", $0) }.joined()
        _ = try await send("token", binding, ["pushToken": hex, "sequence": binding.sequence])
    }

    func revoke(_ binding: LiveOrderBinding) async throws {
        _ = try await send("revoke", binding, [:])
    }

    private func send(_ action: String, _ binding: LiveOrderBinding, _ fields: [String: Any]) async throws -> Data {
        guard binding.origin == AppEnvironmentResolver.current().trustedOrigin,
              let url = URL(string: "/api/v1/mobile/order-activities/\(action)/", relativeTo: binding.origin)?.absoluteURL
        else { throw LiveOrderError.invalidOrigin }
        var body = fields
        body["bindingId"] = binding.id.uuidString.lowercased()
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("FoodHomeActivity \(binding.secret)", forHTTPHeaderField: "Authorization")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw LiveOrderError.invalidResponse }
        guard (200..<300).contains(http.statusCode) else { throw LiveOrderError.http(http.statusCode) }
        guard data.count <= 4_096 else { throw LiveOrderError.invalidResponse }
        return data
    }
}
