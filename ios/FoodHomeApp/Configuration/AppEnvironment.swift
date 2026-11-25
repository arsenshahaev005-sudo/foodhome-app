import Foundation

enum FoodHomeDeployment: String, CaseIterable, Sendable {
    case production
    case staging

    var appBundleID: String {
        self == .production ? "market.foodhome.app" : "market.foodhome.app.staging"
    }

    var origin: URL {
        switch self {
        case .production: return URL(string: "https://foodhome.market")!
        case .staging: return URL(string: "https://staging.foodhome.market")!
        }
    }

    var host: String { origin.host! }
    var callbackScheme: String { appBundleID + ".auth" }

    static func resolve(bundleID: String?, webHost: String?) -> Self? {
        allCases.first {
            ($0.appBundleID == bundleID || $0.appBundleID + ".OrderStatus" == bundleID) && $0.host == webHost
        }
    }

    static let current: Self = {
        let bundle = Bundle.main
        guard let deployment = resolve(
            bundleID: bundle.bundleIdentifier,
            webHost: bundle.object(forInfoDictionaryKey: "FoodHomeWebHost") as? String
        ) else {
            preconditionFailure("Unrecognized Food&Home bundle and website configuration")
        }
        return deployment
    }()
}

struct AppEnvironment: Equatable, Sendable {
    let baseURL: URL
    let trustedOrigin: URL
}

enum AppEnvironmentResolver {
    static let productionOrigin = FoodHomeDeployment.production.origin
    static var distributionPushAvailable: Bool {
        Bundle.main.object(forInfoDictionaryKey: "FoodHomeAPSEnvironment") as? String == "production"
    }

    private static let localDebugHosts: Set<String> = ["localhost", "127.0.0.1", "::1"]

    static func current() -> AppEnvironment {
        #if DEBUG
        return resolve(
            isDebug: true,
            debugBaseURL: ProcessInfo.processInfo.environment["FOODHOME_DEBUG_BASE_URL"],
            deployment: .current
        )
        #else
        return resolve(isDebug: false, debugBaseURL: nil, deployment: .current)
        #endif
    }

    static func resolve(isDebug: Bool, debugBaseURL: String?, deployment: FoodHomeDeployment = .production) -> AppEnvironment {
        guard isDebug, deployment == .production,
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
            return environment(for: deployment.origin)
        }

        var origin = URLComponents()
        origin.scheme = "https"
        origin.host = host
        origin.port = components.port
        return environment(for: origin.url!)
    }

    private static func environment(for trustedOrigin: URL) -> AppEnvironment {
        return AppEnvironment(
            baseURL: URL(string: trustedOrigin.absoluteString + "/")!,
            trustedOrigin: trustedOrigin
        )
    }
}
