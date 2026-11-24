import Foundation
import CoreFoundation
import CryptoKit

enum NotificationAuthorizationStatus: String, Equatable {
    case notDetermined, denied, authorized, provisional, unavailable

    var canReceivePush: Bool { self == .authorized || self == .provisional }
}

struct PushOpen: Equatable {
    let eventID: UUID
    let bindingID: String
    let expiresAt: Date
    let route: URL
}

struct PushNavigationInbox {
    private var pending: PushOpen?
    private var consumed: [UUID] = []
    var sessionConfirmed = false
    var hasPending: Bool { pending != nil }

    mutating func enqueue(_ open: PushOpen) -> Bool {
        guard !consumed.contains(open.eventID) else { return false }
        pending = open
        return true
    }

    mutating func take(bindingID: String?, now: Date = Date()) -> URL? {
        guard sessionConfirmed, let open = pending else { return nil }
        pending = nil
        guard open.bindingID == bindingID, open.expiresAt > now else { return nil }
        consumed.append(open.eventID)
        if consumed.count > 32 { consumed.removeFirst() }
        return open.route
    }
}

enum PushPayloadPolicy {
    static let origin = URL(string: "https://foodhome.market")!

    static func timestamp(_ text: String) -> Date? {
        guard text.count <= 40 else { return nil }
        let parser = ISO8601DateFormatter()
        parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = parser.date(from: text) { return date }
        parser.formatOptions = [.withInternetDateTime]
        return parser.date(from: text)
    }

    static func matches(_ value: String, _ pattern: String) -> Bool {
        value.range(of: "\\A(?:" + pattern + ")\\z", options: .regularExpression) != nil
    }

    static func acceptsRoute(_ route: String, role: String) -> Bool {
        guard route.utf8.count <= 180 else { return false }
        let uuid = "[a-f0-9A-F]{8}-[a-f0-9A-F]{4}-[a-f0-9A-F]{4}-[a-f0-9A-F]{4}-[a-f0-9A-F]{12}"
        let conversation = "(?:threadId|orderId|supportThreadId)=" + uuid
        switch role {
        case "CLIENT":
            return matches(route, "/(?:orders|open-orders)/" + uuid) ||
                matches(route, "/chat(?:\\?(?:" + conversation + "|section=notifications))?")
        case "SELLER":
            return matches(route, "/seller/open-orders/" + uuid) ||
                matches(route, "/seller\\?view=(?:ORDERS&orderId=|PRODUCTS&productId=)" + uuid) ||
                matches(route, "/seller\\?view=CHAT(?:&(?:" + conversation + "|section=notifications))?")
        case "ACCOUNT":
            return route == "/chat?section=notifications" || route == "/seller?view=CHAT&section=notifications"
        default:
            return false
        }
    }

    static func parse(_ info: [AnyHashable: Any], bindingID: String?, now: Date = Date()) -> PushOpen? {
        guard let bindingID,
              let data = info["foodhome"] as? [String: Any], data.count == 6,
              let version = data["version"] as? NSNumber, CFGetTypeID(version) != CFBooleanGetTypeID(),
              version == 1, let rawID = data["eventId"] as? String,
              let eventID = UUID(uuidString: rawID), rawID.lowercased() == eventID.uuidString.lowercased(),
              data["bindingId"] as? String == bindingID, matches(bindingID, "[a-f0-9]{64}"),
              let expiry = data["expiresAt"] as? String, expiry.hasSuffix("Z"),
              let expiresAt = timestamp(expiry), expiresAt > now,
              let route = data["route"] as? String, let role = data["recipientRole"] as? String,
              acceptsRoute(route, role: role), let url = URL(string: route, relativeTo: origin)?.absoluteURL
        else { return nil }
        return PushOpen(eventID: eventID, bindingID: bindingID, expiresAt: expiresAt, route: url)
    }

    static func digest(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    static func validAction(_ payload: [String: Any], now: Date = Date()) -> Bool {
        guard let action = payload["action"] as? String else { return false }
        if action == "status" || action == "clear" { return payload.count == 1 }
        guard (action == "bind" || action == "revoke"), payload.count == 3,
              let nonce = payload["nonce"] as? String, matches(nonce, "[A-Za-z0-9_-]{43}"),
              let expiry = payload["expiresAt"] as? String, let date = timestamp(expiry)
        else { return false }
        return date > now && date.timeIntervalSince(now) <= 301
    }
}
