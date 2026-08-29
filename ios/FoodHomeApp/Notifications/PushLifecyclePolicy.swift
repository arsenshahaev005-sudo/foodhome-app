import CryptoKit
import Foundation

enum NotificationAuthorizationStatus: String, Equatable {
    case notDetermined
    case denied
    case authorized
    case provisional
    case unavailable
}

struct PushOpen: Equatable {
    let eventID: String
    let route: URL
}

struct PushPayloadPolicy {
    let navigationPolicy: NavigationPolicy

    func parse(_ payload: [String: String]) -> PushOpen? {
        let allowedKeys: Set<String> = ["eventId", "route", "type"]
        guard Set(payload.keys).isSubset(of: allowedKeys),
              let eventID = payload["eventId"],
              eventID.range(
                of: "^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$",
                options: .regularExpression
              ) != nil,
              let route = payload["route"],
              case let .internal(trustedURL) = navigationPolicy.classify(route)
        else {
            return nil
        }
        return PushOpen(eventID: eventID, route: trustedURL)
    }
}

final class SensitivePushToken: CustomStringConvertible {
    private let rawValue: Data
    let fingerprint: String

    init?(_ rawValue: Data) {
        guard !rawValue.isEmpty, rawValue.count <= 4_096 else { return nil }
        self.rawValue = rawValue
        self.fingerprint = SHA256.hash(data: rawValue)
            .prefix(6)
            .map { String(format: "%02x", $0) }
            .joined()
    }

    func use<T>(_ block: (Data) -> T) -> T {
        block(rawValue)
    }

    var description: String {
        "SensitivePushToken(<redacted>:\(fingerprint))"
    }
}
