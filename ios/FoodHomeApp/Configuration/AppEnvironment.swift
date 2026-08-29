import Foundation

struct AppEnvironment: Equatable, Sendable {
    let baseURL: URL
    let trustedOrigin: URL
}

enum AppEnvironmentResolver {
    static let productionOrigin = URL(string: "https://foodhome.market")!
    private static let localDebugHosts: Set<String> = ["localhost", "127.0.0.1", "::1"]

    static func current() -> AppEnvironment {
        #if DEBUG
        return resolve(
            isDebug: true,
            debugBaseURL: ProcessInfo.processInfo.environment["FOODHOME_DEBUG_BASE_URL"]
        )
        #else
        return production()
        #endif
    }

    static func resolve(isDebug: Bool, debugBaseURL: String?) -> AppEnvironment {
        guard isDebug,
              let rawValue = debugBaseURL?.trimmingCharacters(in: .whitespacesAndNewlines),
              !rawValue.isEmpty,
              let candidate = URL(string: rawValue),
              let components = URLComponents(url: candidate, resolvingAgainstBaseURL: false),
              components.scheme?.lowercased() == "https",
              let host = components.host?.lowercased(),
              localDebugHosts.contains(host),
              components.user == nil,
              components.password == nil,
              components.query == nil,
              components.fragment == nil,
              components.path.isEmpty || components.path == "/",
              components.port == nil || (1...65_535).contains(components.port!)
        else {
            return production()
        }

        var origin = URLComponents()
        origin.scheme = "https"
        origin.host = host
        origin.port = components.port
        let trustedOrigin = origin.url!
        return AppEnvironment(
            baseURL: URL(string: trustedOrigin.absoluteString + "/")!,
            trustedOrigin: trustedOrigin
        )
    }

    private static func production() -> AppEnvironment {
        AppEnvironment(
            baseURL: URL(string: "https://foodhome.market/")!,
            trustedOrigin: productionOrigin
        )
    }
}
