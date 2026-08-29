import Foundation

@MainActor
final class NativeEventQueue {
    private let manifest: BridgeManifest
    private let trustedOrigin: URL
    private let paymentCoordinator: PaymentCoordinator

    init(
        manifest: BridgeManifest,
        trustedOrigin: URL,
        paymentCoordinator: PaymentCoordinator
    ) {
        self.manifest = manifest
        self.trustedOrigin = trustedOrigin
        self.paymentCoordinator = paymentCoordinator
    }

    /// JavaScript execution is intentionally not treated as delivery acknowledgement.
    func pendingDispatchScript() -> String? {
        guard let event = paymentCoordinator.pendingEvent()?.bridgeValue(
            protocolName: manifest.protocolName,
            version: manifest.bridgeMajor
        ),
        JSONSerialization.isValidJSONObject(event),
        let eventData = try? JSONSerialization.data(withJSONObject: event),
        let eventJSON = String(data: eventData, encoding: .utf8),
        let origin = exactOrigin(trustedOrigin),
        let originData = try? JSONEncoder().encode(origin),
        let originJSON = String(data: originData, encoding: .utf8),
        let eventNameData = try? JSONEncoder().encode(manifest.nativeEvents.eventName),
        let eventNameJSON = String(data: eventNameData, encoding: .utf8)
        else {
            return nil
        }

        return """
        (() => {
          if (window !== window.top || window.location.origin !== \(originJSON)) return;
          window.dispatchEvent(new CustomEvent(\(eventNameJSON),{detail:\(eventJSON)}));
        })();
        """
    }

    private func exactOrigin(_ url: URL) -> String? {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              components.scheme?.lowercased() == "https",
              let host = components.host?.lowercased(),
              components.user == nil,
              components.password == nil,
              components.port == nil || components.port == 443
        else {
            return nil
        }
        return "https://\(host)"
    }
}
