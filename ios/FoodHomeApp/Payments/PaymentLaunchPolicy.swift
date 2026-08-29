import Foundation

struct PaymentProviderRule: Equatable, Sendable {
    let flow: PaymentFlow
    let exactHost: String
    let pathPrefix: String
    let allowedQueryKeys: Set<String>

    init(
        flow: PaymentFlow,
        exactHost: String,
        pathPrefix: String,
        allowedQueryKeys: Set<String> = []
    ) {
        self.flow = flow
        self.exactHost = exactHost
        self.pathPrefix = pathPrefix
        self.allowedQueryKeys = allowedQueryKeys
    }
}

struct ValidatedPaymentDestination: Equatable, Sendable {
    let url: URL
    let flow: PaymentFlow
}

struct PaymentLaunchPolicy: Sendable {
    private let rules: [PaymentProviderRule]
    private let maximumURLLength: Int

    init(rules: [PaymentProviderRule], maximumURLLength: Int = 2_048) {
        self.rules = rules
        self.maximumURLLength = maximumURLLength
    }

    func classify(_ rawURL: String) -> ValidatedPaymentDestination? {
        guard !rawURL.isEmpty,
              rawURL == rawURL.trimmingCharacters(in: .whitespacesAndNewlines),
              rawURL.utf8.count <= maximumURLLength,
              !rawURL.unicodeScalars.contains(where: Self.isAmbiguousCharacter),
              let components = URLComponents(string: rawURL),
              components.scheme?.lowercased() == "https",
              let host = components.host?.lowercased(),
              Self.isUnambiguousHost(host),
              components.user == nil,
              components.password == nil,
              components.port == nil || components.port == 443,
              components.fragment == nil,
              let queryKeys = queryKeys(components.percentEncodedQuery),
              let url = components.url
        else {
            return nil
        }

        let path = components.percentEncodedPath.isEmpty ? "/" : components.percentEncodedPath
        guard Self.isUnambiguousPath(path) else { return nil }
        guard let rule = rules.first(where: {
            host == $0.exactHost.lowercased() &&
                pathMatches(path, prefix: $0.pathPrefix) &&
                queryKeys.isSubset(of: $0.allowedQueryKeys)
        }) else {
            return nil
        }
        return ValidatedPaymentDestination(url: url, flow: rule.flow)
    }

    private func pathMatches(_ path: String, prefix: String) -> Bool {
        guard path.hasPrefix("/"), prefix.hasPrefix("/") else { return false }
        var trimmed = prefix
        while trimmed.count > 1 && trimmed.hasSuffix("/") { trimmed.removeLast() }
        return trimmed == "/" || path == trimmed || path.hasPrefix("\(trimmed)/")
    }

    private func queryKeys(_ rawQuery: String?) -> Set<String>? {
        guard let rawQuery, !rawQuery.isEmpty else { return [] }
        var keys: Set<String> = []
        for part in rawQuery.split(separator: "&", omittingEmptySubsequences: false) {
            let key = part.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)[0]
            guard !key.isEmpty,
                  !key.contains("%"),
                  key.allSatisfy({ $0.isLetter || $0.isNumber || $0 == "_" || $0 == "-" })
            else {
                return nil
            }
            let value = part.split(
                separator: "=",
                maxSplits: 1,
                omittingEmptySubsequences: false
            ).dropFirst().first.map(String.init) ?? ""
            guard !Self.containsNestedDestination(value) else { return nil }
            keys.insert(String(key))
        }
        return keys
    }

    private static func containsNestedDestination(_ rawValue: String) -> Bool {
        var decoded = rawValue
        for pass in 0...maximumDecodePasses {
            let normalized = decoded.lowercased()
            if forbiddenNestedSchemes.contains(where: normalized.contains) { return true }
            if pass == maximumDecodePasses { return false }
            guard let next = percentDecodeOnce(decoded) else { return true }
            decoded = next
        }
        return true
    }

    private static func isAmbiguousCharacter(_ scalar: Unicode.Scalar) -> Bool {
        CharacterSet.whitespacesAndNewlines.contains(scalar) ||
            CharacterSet.controlCharacters.contains(scalar) ||
            scalar.value == 0x5C ||
            bidiControls.contains(scalar.value)
    }

    private static func percentDecodeOnce(_ value: String) -> String? {
        let input = Array(value.utf8)
        var output: [UInt8] = []
        output.reserveCapacity(input.count)
        var index = 0
        while index < input.count {
            guard input[index] == 0x25 else {
                output.append(input[index])
                index += 1
                continue
            }
            guard index + 2 < input.count,
                  let high = hexValue(input[index + 1]),
                  let low = hexValue(input[index + 2])
            else {
                return nil
            }
            output.append((high << 4) | low)
            index += 3
        }
        return String(bytes: output, encoding: .utf8)
    }

    private static func hexValue(_ byte: UInt8) -> UInt8? {
        switch byte {
        case 0x30...0x39: return byte - 0x30
        case 0x41...0x46: return byte - 0x41 + 10
        case 0x61...0x66: return byte - 0x61 + 10
        default: return nil
        }
    }

    private static func isUnambiguousHost(_ host: String) -> Bool {
        !host.hasSuffix(".") && host.unicodeScalars.allSatisfy {
            $0.value > 0x20 && $0.value <= 0x7F
        }
    }

    private static func isUnambiguousPath(_ path: String) -> Bool {
        let normalized = path.lowercased()
        guard !encodedPathSeparators.contains(where: normalized.contains),
              !path.contains("//"),
              !path.contains("\\")
        else {
            return false
        }
        return !path.split(separator: "/", omittingEmptySubsequences: false).contains {
            $0 == "." || $0 == ".."
        }
    }

    private static let maximumDecodePasses = 4
    private static let bidiControls: Set<UInt32> = [
        0x200E, 0x200F,
        0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
        0x2066, 0x2067, 0x2068, 0x2069,
    ]
    private static let forbiddenNestedSchemes = [
        "http://", "https://", "javascript:", "data:", "file:", "intent:",
    ]
    private static let encodedPathSeparators = ["%2f", "%5c", "%2e"]

    /// Provider trust remains empty until the real Tochka mobile PoC is approved.
    static let production = PaymentLaunchPolicy(rules: [])
}
