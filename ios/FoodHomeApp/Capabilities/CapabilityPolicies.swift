import Foundation

struct SharePayload: Equatable {
    let title: String?
    let text: String?
    let url: URL
}

struct FoodHomeSharePolicy {
    let trustedOrigin: URL
    var maxURLLength = 2_048

    func parse(title: String?, text: String?, rawURL: String?) -> SharePayload? {
        if let title, title.count > 120 { return nil }
        if let text, text.count > 1_000 { return nil }
        guard let rawURL,
              !rawURL.isEmpty,
              rawURL.count <= maxURLLength,
              let candidate = URL(string: rawURL),
              let value = URLComponents(url: candidate, resolvingAgainstBaseURL: false),
              let trusted = URLComponents(url: trustedOrigin, resolvingAgainstBaseURL: false),
              value.scheme?.lowercased() == "https",
              value.scheme?.lowercased() == trusted.scheme?.lowercased(),
              value.host?.lowercased() == trusted.host?.lowercased(),
              effectivePort(value) == effectivePort(trusted),
              value.user == nil,
              value.password == nil
        else {
            return nil
        }
        return SharePayload(title: title, text: text, url: candidate)
    }

    private func effectivePort(_ components: URLComponents) -> Int {
        components.port ?? (components.scheme?.lowercased() == "https" ? 443 : -1)
    }
}

enum CapabilityPurposePolicy {
    static func accepts(_ value: String?) -> Bool {
        guard let value else { return false }
        return !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            value.count <= 160
    }
}

struct BridgePayloadPolicy {
    let trustedOrigin: URL
    let maxMessageBytes: Int

    func accepts(method: String, payload: [String: Any]) -> Bool {
        guard payload.count <= 32,
              JSONSerialization.isValidJSONObject(payload),
              let data = try? JSONSerialization.data(withJSONObject: payload),
              data.count <= maxMessageBytes
        else {
            return false
        }

        switch method {
        case "share":
            guard payload.count <= 3,
                  optionalStringHasValidType(payload, key: "title"),
                  optionalStringHasValidType(payload, key: "text")
            else {
                return false
            }
            return FoodHomeSharePolicy(trustedOrigin: trustedOrigin).parse(
                title: payload["title"] as? String,
                text: payload["text"] as? String,
                rawURL: payload["url"] as? String
            ) != nil
        case "requestLocation":
            return payload.count <= 2 &&
                CapabilityPurposePolicy.accepts(payload["purpose"] as? String)
        case "requestNotificationPermission":
            return payload.count <= 2 && (
                payload["purpose"] == nil ||
                    CapabilityPurposePolicy.accepts(payload["purpose"] as? String)
            )
        case "getNotificationStatus":
            return payload.count <= 1
        case "openExternal":
            return isHTTPSURL(payload["url"] as? String)
        case "openPayment":
            guard isHTTPSURL(payload["url"] as? String),
                  let recovery = payload["recoveryContext"] as? String,
                  PaymentValuePolicy.acceptsRecoveryContext(recovery),
                  let expiresAt = payload["expiresAt"] as? String,
                  acceptsRFC3339(expiresAt),
                  payload.count <= 3
            else {
                return false
            }
            return true
        case "ackNativeEvent":
            guard payload.count == 1, let eventID = payload["eventId"] as? String else {
                return false
            }
            return PaymentValuePolicy.acceptsEventID(eventID)
        case "clearPaymentRecovery":
            guard payload.count == 2,
                  let recovery = payload["recoveryContext"] as? String,
                  let reason = payload["reason"] as? String
            else {
                return false
            }
            return PaymentValuePolicy.acceptsRecoveryContext(recovery) &&
                PaymentValuePolicy.clearReasons.contains(reason)
        default:
            return false
        }
    }

    private func optionalStringHasValidType(_ payload: [String: Any], key: String) -> Bool {
        payload[key] == nil || payload[key] is String || payload[key] is NSNull
    }

    private func isHTTPSURL(_ rawValue: String?) -> Bool {
        guard let rawValue,
              let url = URL(string: rawValue),
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        else {
            return false
        }
        return components.scheme?.lowercased() == "https" &&
            components.host != nil &&
            components.user == nil &&
            components.password == nil
    }

    private func acceptsRFC3339(_ value: String) -> Bool {
        let standard = ISO8601DateFormatter()
        if standard.date(from: value) != nil { return true }
        let fractional = ISO8601DateFormatter()
        fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return fractional.date(from: value) != nil
    }
}

enum BridgeDispatchResult {
    case success([String: Any])
    case failure(code: String, message: String, retryable: Bool = false)
}

@MainActor
protocol BridgeCapabilityDispatching: AnyObject {
    func dispatch(
        _ request: BridgeRequest,
        completion: @escaping (BridgeDispatchResult) -> Void
    )
}

@MainActor
final class UnavailableBridgeCapabilityDispatcher: BridgeCapabilityDispatching {
    func dispatch(
        _ request: BridgeRequest,
        completion: @escaping (BridgeDispatchResult) -> Void
    ) {
        completion(
            .failure(
                code: "CAPABILITY_UNAVAILABLE",
                message: "Capability is unavailable"
            )
        )
    }
}
