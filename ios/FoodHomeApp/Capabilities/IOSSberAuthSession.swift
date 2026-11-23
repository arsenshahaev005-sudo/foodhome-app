import AuthenticationServices
import Foundation
import UIKit

struct SberAuthPolicy {
    let trustedOrigin: URL

    var callbackScheme: String? {
        switch trustedOrigin.absoluteString {
        case "https://foodhome.market": return "market.foodhome.app.auth"
        case "https://staging.foodhome.market": return "market.foodhome.app.staging.auth"
        default: return nil
        }
    }

    func authorizationURL(attemptID: String, raw: String) -> URL? {
        guard callbackScheme != nil, UUID(uuidString: attemptID)?.uuidString.lowercased() == attemptID,
              raw.utf8.count <= 2048, var parts = URLComponents(string: raw),
              parts.scheme == "https", parts.percentEncodedHost == "id.sber.ru",
              parts.port == nil || parts.port == 443, parts.user == nil, parts.password == nil,
              parts.percentEncodedPath == "/CSAFront/oidc/authorize.do", parts.fragment == nil,
              let url = parts.url, let form = parts.percentEncodedQuery
        else { return nil }
        // Sber uses application/x-www-form-urlencoded query parameters.
        parts.percentEncodedQuery = form.replacingOccurrences(of: "+", with: "%20")
        guard let query = fields(parts.queryItems),
              Set(query.keys) == ["client_id", "redirect_uri", "response_type", "scope", "state",
                                  "nonce", "login_hint", "code_challenge", "code_challenge_method"],
              query["redirect_uri"] == trustedOrigin.appendingPathComponent("auth/sber/callback").absoluteString,
              query["response_type"] == "code", query["scope"] == "openid mobile",
              (1...128).contains(query["client_id"]?.count ?? 0), query["code_challenge_method"] == "S256",
              matches(query["state"], #"[A-Za-z0-9_-]{40,96}"#),
              matches(query["nonce"], #"[A-Za-z0-9_-]{40,96}"#),
              matches(query["login_hint"], #"7[0-9]{10}"#),
              matches(query["code_challenge"], #"[A-Za-z0-9_-]{43}"#)
        else { return nil }
        return url
    }

    func returnURL(callback: URL, attemptID: String) -> URL? {
        guard let callbackScheme, callback.absoluteString.utf8.count <= 512,
              let parts = URLComponents(url: callback, resolvingAgainstBaseURL: false),
              parts.scheme == callbackScheme, parts.percentEncodedHost == "sber", parts.port == nil,
              parts.user == nil, parts.password == nil, parts.percentEncodedPath == "/return",
              parts.query == nil, let fragment = parts.percentEncodedFragment,
              let params = URLComponents(string: "?" + fragment),
              let query = fields(params.queryItems), Set(query.keys) == ["attempt_id", "ticket"],
              query["attempt_id"] == attemptID, matches(query["ticket"], #"[A-Za-z0-9_-]{40,100}"#)
        else { return nil }
        var destination = URLComponents(url: trustedOrigin, resolvingAgainstBaseURL: false)
        destination?.path = "/auth/sber/app-return"
        destination?.percentEncodedFragment = fragment
        return destination?.url
    }

    private func fields(_ items: [URLQueryItem]?) -> [String: String]? {
        guard let items else { return nil }
        var fields: [String: String] = [:]
        for item in items {
            guard let value = item.value, fields[item.name] == nil else { return nil }
            fields[item.name] = value
        }
        return fields
    }

    private func matches(_ value: String?, _ pattern: String) -> Bool {
        guard let value else { return false }
        return value.range(of: "\\A" + pattern + "\\z", options: .regularExpression) != nil
    }
}

@MainActor
final class IOSSberAuthSession: NSObject, ASWebAuthenticationPresentationContextProviding {
    private let policy: SberAuthPolicy
    private var session: ASWebAuthenticationSession?
    private var window: UIWindow?
    private var activeAttempt: String?

    init(trustedOrigin: URL) {
        self.policy = SberAuthPolicy(trustedOrigin: trustedOrigin)
    }

    func start(attemptID: String, rawURL: String, window: UIWindow, onReturn: @escaping (URL) -> Void) -> Bool {
        guard session == nil, window.windowScene?.activationState == .foregroundActive,
              let scheme = policy.callbackScheme, let url = policy.authorizationURL(attemptID: attemptID, raw: rawURL)
        else { return false }
        let session = ASWebAuthenticationSession(url: url, callbackURLScheme: scheme) { [weak self] callback, error in
            Task { @MainActor in
                guard let self, self.activeAttempt == attemptID else { return }
                self.session = nil
                self.window = nil
                self.activeAttempt = nil
                if error == nil, let callback, let url = self.policy.returnURL(callback: callback, attemptID: attemptID) {
                    onReturn(url)
                }
            }
        }
        self.window = window
        self.session = session
        activeAttempt = attemptID
        session.presentationContextProvider = self
        guard session.start() else {
            cancel()
            return false
        }
        return true
    }

    func cancel() {
        activeAttempt = nil
        session?.cancel()
        session = nil
        window = nil
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        window ?? ASPresentationAnchor()
    }
}
