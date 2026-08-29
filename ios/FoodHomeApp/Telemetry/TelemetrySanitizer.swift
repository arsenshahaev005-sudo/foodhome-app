import Foundation

struct TelemetrySanitizer: Sendable {
    let trustedOrigin: URL

    func sanitize(
        name: TelemetryEventName,
        rawAttributes: [String: String]
    ) -> TelemetryEvent {
        var attributes: [TelemetryAttributeKey: String] = [:]
        for (rawKey, rawValue) in rawAttributes {
            guard let key = TelemetryAttributeKey(rawValue: rawKey),
                  let value = sanitizeValue(key: key, raw: rawValue)
            else {
                continue
            }
            attributes[key] = value
        }
        return TelemetryEvent(name: name, attributes: attributes)
    }

    func routeTemplate(_ rawURL: String?) -> String? {
        guard let rawURL,
              !rawURL.isEmpty,
              rawURL.utf8.count <= Self.maximumURLBytes,
              rawURL == rawURL.trimmingCharacters(in: .whitespacesAndNewlines),
              !rawURL.unicodeScalars.contains(where: {
                  CharacterSet.whitespacesAndNewlines.contains($0) ||
                      CharacterSet.controlCharacters.contains($0) ||
                      $0.value == 0x5C
              }),
              let components = URLComponents(string: rawURL),
              let trusted = URLComponents(url: trustedOrigin, resolvingAgainstBaseURL: false),
              components.scheme?.lowercased() == "https",
              components.scheme?.lowercased() == trusted.scheme?.lowercased(),
              components.host?.lowercased() == trusted.host?.lowercased(),
              effectivePort(components) == effectivePort(trusted),
              components.user == nil,
              components.password == nil
        else {
            return nil
        }

        let segments = components.percentEncodedPath
            .split(separator: "/", omittingEmptySubsequences: true)
            .prefix(Self.maximumRouteSegments)
        guard !segments.isEmpty else { return "/" }
        let safe = segments.enumerated().map { index, value -> String in
            let normalized = value.lowercased()
            if Self.safeRouteSegments.contains(normalized) { return normalized }
            return index == 0 ? ":route" : ":id"
        }
        return "/\(safe.joined(separator: "/"))"
    }

    private func sanitizeValue(key: TelemetryAttributeKey, raw: String) -> String? {
        guard !raw.isEmpty,
              raw.count <= Self.maximumAttributeLength,
              !raw.unicodeScalars.contains(where: {
                  CharacterSet.controlCharacters.contains($0)
              })
        else {
            return nil
        }
        switch key {
        case .platform:
            return ["android", "ios"].contains(raw) ? raw : nil
        case .appVersion:
            return raw.matches(Self.appVersionPattern) ? raw : nil
        case .bridgeVersion:
            return raw.matches(Self.bridgeVersionPattern) ? raw : nil
        case .webViewVersion:
            return raw.matches(Self.webViewVersionPattern) ? raw : nil
        case .routeTemplate:
            return raw.matches(Self.routeTemplatePattern) ? raw : nil
        case .errorCode:
            return raw.matches(Self.errorCodePattern) ? raw : nil
        case .correlationId:
            return raw.matches(Self.correlationIDPattern) ? raw : nil
        case .networkClass:
            return Self.networkClasses.contains(raw) ? raw : nil
        case .durationBucket:
            return Self.durationBuckets.contains(raw) ? raw : nil
        }
    }

    private func effectivePort(_ components: URLComponents) -> Int {
        components.port ?? (components.scheme?.lowercased() == "https" ? 443 : -1)
    }

    private static let maximumAttributeLength = 160
    private static let maximumURLBytes = 2_048
    private static let maximumRouteSegments = 8
    private static let appVersionPattern = #"^[0-9]+(?:\.[0-9]+){1,3}(?:-[A-Za-z0-9.-]{1,24})?$"#
    private static let bridgeVersionPattern = #"^[0-9]+\.[0-9]+\.[0-9]+$"#
    private static let webViewVersionPattern = #"^[0-9]+(?:\.[0-9]+){0,4}$"#
    private static let routeTemplatePattern =
        #"^/(?:[a-z][a-z0-9-]{0,31}|:id|:route)(?:/(?:[a-z][a-z0-9-]{0,31}|:id|:route)){0,7}$|^/$"#
    private static let errorCodePattern = #"^[A-Z][A-Z0-9_]{0,63}$"#
    private static let correlationIDPattern = #"^[A-Za-z0-9_-]{8,64}$"#
    private static let networkClasses: Set<String> = [
        "offline", "wifi", "cellular", "ethernet", "unknown",
    ]
    private static let durationBuckets: Set<String> = [
        "lt_100ms", "100_499ms", "500_1999ms", "2_9s", "gte_10s",
    ]
    private static let safeRouteSegments: Set<String> = [
        "products", "sellers", "orders", "chat", "cart", "checkout", "profile",
        "support", "login", "register", "favorites", "notifications", "settings",
        "help", "delivery",
    ]
}

private extension String {
    func matches(_ pattern: String) -> Bool {
        range(of: pattern, options: .regularExpression) != nil
    }
}
