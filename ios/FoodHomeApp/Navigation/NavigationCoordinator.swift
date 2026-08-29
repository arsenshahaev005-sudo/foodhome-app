import Foundation

/// Coordinates one trusted WKWebView generation without becoming an authorization boundary.
final class NavigationCoordinator {
    private let navigationPolicy: NavigationPolicy
    private let pendingDeepLinks: PendingDeepLinkStore
    private var navigate: ((URL) -> Void)?
    private var trustedDocumentReady = false
    private var currentAttachment: NavigationAttachment?
    private var currentDocument: TrustedDocumentAttachment?

    init(navigationPolicy: NavigationPolicy) {
        self.navigationPolicy = navigationPolicy
        self.pendingDeepLinks = PendingDeepLinkStore(navigationPolicy: navigationPolicy)
    }

    func classify(_ rawURL: String?) -> NavigationDecision {
        navigationPolicy.classify(rawURL)
    }

    func attach(navigate: @escaping (URL) -> Void) -> NavigationAttachment {
        let attachment = NavigationAttachment()
        currentAttachment = attachment
        currentDocument = nil
        self.navigate = navigate
        trustedDocumentReady = false
        return attachment
    }

    func detach(_ attachment: NavigationAttachment) {
        guard currentAttachment == attachment else { return }
        currentAttachment = nil
        currentDocument = nil
        navigate = nil
        trustedDocumentReady = false
    }

    @discardableResult
    func offer(_ rawURL: String) -> Bool {
        guard pendingDeepLinks.offer(rawURL) else { return false }
        deliverPendingRouteIfReady()
        return true
    }

    func markTrustedDocumentReady(
        _ attachment: NavigationAttachment,
        document: TrustedDocumentAttachment
    ) {
        guard currentAttachment == attachment, currentDocument == document else { return }
        trustedDocumentReady = true
        deliverPendingRouteIfReady()
    }

    func markTrustedDocumentLoading(
        _ attachment: NavigationAttachment
    ) -> TrustedDocumentAttachment? {
        guard currentAttachment == attachment else { return nil }
        let document = TrustedDocumentAttachment()
        currentDocument = document
        trustedDocumentReady = false
        return document
    }

    private func deliverPendingRouteIfReady() {
        guard let navigate,
              let route = pendingDeepLinks.consumeWhenReady(
                isBridgeReady: trustedDocumentReady
              )
        else {
            return
        }
        trustedDocumentReady = false
        currentDocument = nil
        navigate(route)
    }
}

struct NavigationAttachment: Equatable {
    fileprivate let id = UUID()
}

struct TrustedDocumentAttachment: Equatable {
    fileprivate let id = UUID()
}
