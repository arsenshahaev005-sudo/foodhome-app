import Foundation

final class PendingDeepLinkStore {
    private let navigationPolicy: NavigationPolicy
    private var pendingURL: URL?

    init(navigationPolicy: NavigationPolicy) {
        self.navigationPolicy = navigationPolicy
    }

    @discardableResult
    func offer(_ rawURL: String) -> Bool {
        guard case let .internal(url) = navigationPolicy.classify(rawURL) else { return false }
        pendingURL = url
        return true
    }

    func consumeWhenReady(isBridgeReady: Bool) -> URL? {
        guard isBridgeReady else { return nil }
        defer { pendingURL = nil }
        return pendingURL
    }
}
