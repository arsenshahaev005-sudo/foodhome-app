import Foundation

enum NavigationBlockReason: Equatable {
    case empty
    case tooLong
    case malformed
    case forbiddenScheme
    case userInfo
    case invalidHost
    case unexpectedPort
    case nestedURL
}

enum NavigationDecision: Equatable {
    case `internal`(URL)
    case external(URL)
    case blocked(NavigationBlockReason)
}

struct NavigationPolicy {
    let trustedOrigin: URL
    var maxURLLength = 2_048

    func classify(_ rawURL: String?) -> NavigationDecision {
        let raw = rawURL ?? ""
        guard !raw.isEmpty else { return .blocked(.empty) }
        guard raw.utf8.count <= maxURLLength else { return .blocked(.tooLong) }
        guard raw == raw.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.unicodeScalars.contains(where: Self.isAmbiguousCharacter)
        else {
            return .blocked(.malformed)
        }
        guard let url = URL(string: raw),
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        else {
            return .blocked(.malformed)
        }
        guard components.scheme?.lowercased() == "https" else {
            return .blocked(.forbiddenScheme)
        }
        guard components.user == nil, components.password == nil else {
            return .blocked(.userInfo)
        }
        guard let host = components.host?.lowercased() else {
            return .blocked(.invalidHost)
        }
        guard Self.isUnambiguousHost(host) else {
            return .blocked(.invalidHost)
        }
        guard !containsNestedURL(components.percentEncodedQuery),
              !containsNestedURL(components.percentEncodedFragment)
        else {
            return .blocked(.nestedURL)
        }
        guard let trusted = URLComponents(url: trustedOrigin, resolvingAgainstBaseURL: false),
              let trustedHost = trusted.host?.lowercased()
        else {
            return .blocked(.malformed)
        }

        if host == trustedHost && effectivePort(components) != effectivePort(trusted) {
            return .blocked(.unexpectedPort)
        }
        if components.scheme?.lowercased() == trusted.scheme?.lowercased(),
           host == trustedHost,
           effectivePort(components) == effectivePort(trusted) {
            return .internal(url)
        }
        return .external(url)
    }

    private func containsNestedURL(_ rawQuery: String?) -> Bool {
        guard var decoded = rawQuery, !decoded.isEmpty else { return false }
        for pass in 0...Self.maximumDecodePasses {
            if Self.containsForbiddenDestination(decoded) { return true }
            if pass == Self.maximumDecodePasses { return false }
            guard let next = Self.percentDecodeOnce(decoded) else { return true }
            decoded = next
        }
        return true
    }

    private static func containsForbiddenDestination(_ value: String) -> Bool {
        let normalized = value.lowercased()
        return forbiddenNestedSchemes.contains { normalized.contains($0) }
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

    private static func isAmbiguousCharacter(_ scalar: Unicode.Scalar) -> Bool {
        CharacterSet.whitespacesAndNewlines.contains(scalar) ||
            CharacterSet.controlCharacters.contains(scalar) ||
            scalar.value == 0x5C ||
            bidiControls.contains(scalar.value)
    }

    private static func isUnambiguousHost(_ host: String) -> Bool {
        !host.hasSuffix(".") && host.unicodeScalars.allSatisfy {
            $0.value > 0x20 && $0.value <= 0x7F
        }
    }

    private func effectivePort(_ components: URLComponents) -> Int {
        components.port ?? (components.scheme?.lowercased() == "https" ? 443 : -1)
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
}
