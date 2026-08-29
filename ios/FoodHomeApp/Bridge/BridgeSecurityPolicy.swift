import Foundation

struct BridgeOriginPolicy {
    let trustedOrigin: URL

    func accepts(frameURL: URL?, isMainFrame: Bool) -> Bool {
        guard isMainFrame,
              let frameURL,
              let source = URLComponents(url: frameURL, resolvingAgainstBaseURL: false),
              let trusted = URLComponents(url: trustedOrigin, resolvingAgainstBaseURL: false)
        else {
            return false
        }
        return source.scheme?.lowercased() == "https"
            && source.scheme?.lowercased() == trusted.scheme?.lowercased()
            && source.host?.lowercased() == trusted.host?.lowercased()
            && effectivePort(source) == effectivePort(trusted)
            && source.user == nil
            && source.password == nil
    }

    private func effectivePort(_ components: URLComponents) -> Int {
        components.port ?? (components.scheme?.lowercased() == "https" ? 443 : -1)
    }
}

struct BridgeRequest {
    let requestID: String
    let method: String
    let payload: [String: Any]
}

enum BridgeRequestValidation: Equatable {
    case accepted(BridgeRequest)
    case rejected(requestID: String?, code: String)

    static func == (lhs: BridgeRequestValidation, rhs: BridgeRequestValidation) -> Bool {
        switch (lhs, rhs) {
        case let (.accepted(left), .accepted(right)):
            return left.requestID == right.requestID &&
                left.method == right.method &&
                NSDictionary(dictionary: left.payload).isEqual(to: right.payload)
        case let (.rejected(leftID, leftCode), .rejected(rightID, rightCode)):
            return leftID == rightID && leftCode == rightCode
        default:
            return false
        }
    }
}

struct BridgeRequestValidator {
    let manifest: BridgeManifest
    var trustedOrigin = URL(string: "https://foodhome.market")!

    func validate(_ data: Data) -> BridgeRequestValidation {
        guard data.count <= manifest.limits.maxMessageBytes else {
            return .rejected(requestID: nil, code: "PAYLOAD_TOO_LARGE")
        }
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return .rejected(requestID: nil, code: "INVALID_MESSAGE")
        }
        guard isJSONStructureSafe(json) else {
            return .rejected(requestID: nil, code: "INVALID_MESSAGE")
        }
        let requestID = validRequestID(json["requestId"] as? String)
        guard json["protocol"] as? String == manifest.protocolName else {
            return .rejected(requestID: requestID, code: "INVALID_MESSAGE")
        }
        guard json["version"] as? Int == manifest.bridgeMajor else {
            return .rejected(requestID: requestID, code: "VERSION_NOT_SUPPORTED")
        }
        guard let requestID, let payload = json["payload"] as? [String: Any] else {
            return .rejected(requestID: requestID, code: "INVALID_MESSAGE")
        }
        guard let method = json["method"] as? String, manifest.methods.contains(method) else {
            return .rejected(requestID: requestID, code: "METHOD_NOT_SUPPORTED")
        }
        guard BridgePayloadPolicy(
            trustedOrigin: trustedOrigin,
            maxMessageBytes: manifest.limits.maxMessageBytes
        ).accepts(method: method, payload: payload) else {
            return .rejected(requestID: requestID, code: "INVALID_PAYLOAD")
        }
        return .accepted(
            BridgeRequest(requestID: requestID, method: method, payload: payload)
        )
    }

    private func validRequestID(_ value: String?) -> String? {
        guard let value, value.count <= manifest.limits.maxRequestIdLength else { return nil }
        let pattern = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$"
        return value.range(of: pattern, options: .regularExpression) == nil ? nil : value
    }

    private func isJSONStructureSafe(_ root: [String: Any]) -> Bool {
        var visited = 0
        var pending: [(value: Any, depth: Int)] = [(root, 1)]
        while let current = pending.popLast() {
            visited += 1
            guard visited <= manifest.limits.maxJsonNodes,
                  current.depth <= manifest.limits.maxJsonDepth
            else {
                return false
            }
            if let object = current.value as? [String: Any] {
                for (key, value) in object {
                    guard !Self.forbiddenJSONKeys.contains(key) else { return false }
                    pending.append((value, current.depth + 1))
                }
            } else if let array = current.value as? [Any] {
                for value in array {
                    pending.append((value, current.depth + 1))
                }
            }
        }
        return true
    }

    private static let forbiddenJSONKeys: Set<String> = [
        "__proto__", "constructor", "prototype",
    ]
}
