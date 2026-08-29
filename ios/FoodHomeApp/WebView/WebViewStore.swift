import Combine
import Foundation
import UIKit
import WebKit

@MainActor
final class WebViewStore: NSObject, ObservableObject, UIGestureRecognizerDelegate {
    @Published private(set) var state: AppShellState
    @Published private(set) var webViewGeneration = 0

    private(set) var webView: WKWebView

    private let environment: AppEnvironment
    private let manifest: BridgeManifest?
    private let navigationCoordinator: NavigationCoordinator
    private let crashLoopBreaker = CrashLoopBreaker()
    private let capabilityCoordinator: IOSCapabilityCoordinator?
    private let paymentCoordinator: PaymentCoordinator?
    private let paymentReturnRouter: IOSPaymentReturnRouter?
    private let nativeEventQueue: NativeEventQueue?
    private let paymentUserActionTracker: PaymentUserActionTracker
    private let mediaPickerCoordinator: IOSMediaPickerCoordinator
    private let telemetry: TelemetryReporter
    private let fixedLaunchState: AppShellState?
    private var messageHandler: BridgeMessageHandler?
    private var navigationAttachment: NavigationAttachment?
    private var documentAttachment: TrustedDocumentAttachment?
    private var lastCommittedURL: URL
    private var started = false
    private var trustedDocumentReady = false
    private var detachPaymentReturnListener: (() -> Void)?

    init(
        environment: AppEnvironment,
        manifest: BridgeManifest?,
        fixedLaunchState: AppShellState? = nil,
        telemetry: TelemetryReporter? = nil
    ) {
        let policy = NavigationPolicy(trustedOrigin: environment.trustedOrigin)
        let telemetryReporter = telemetry ?? .disabled(trustedOrigin: environment.trustedOrigin)
        let paymentUserActionTracker = PaymentUserActionTracker()
        let paymentCoordinator: PaymentCoordinator?
        if manifest != nil, let recoveryStore = FilePaymentRecoveryStore() {
            paymentCoordinator = PaymentCoordinator(
                policy: .production,
                store: recoveryStore,
                launcher: IOSPaymentLauncher()
            )
        } else {
            paymentCoordinator = nil
        }
        let capabilityCoordinator = manifest.map {
            IOSCapabilityCoordinator(
                manifest: $0,
                trustedOrigin: environment.trustedOrigin,
                paymentCoordinator: paymentCoordinator,
                hasRecentPaymentUserAction: { paymentUserActionTracker.isRecent() },
                telemetry: telemetryReporter
            )
        }
        let paymentReturnRouter = paymentCoordinator.map {
            IOSPaymentReturnRouter(coordinator: $0, telemetry: telemetryReporter)
        }
        let nativeEventQueue: NativeEventQueue?
        if let manifest, let paymentCoordinator {
            nativeEventQueue = NativeEventQueue(
                manifest: manifest,
                trustedOrigin: environment.trustedOrigin,
                paymentCoordinator: paymentCoordinator
            )
        } else {
            nativeEventQueue = nil
        }
        let mediaPickerCoordinator = IOSMediaPickerCoordinator()
        let webViewBundle = Self.makeWebView(
            manifest: manifest,
            trustedOrigin: environment.trustedOrigin,
            dispatcher: capabilityCoordinator,
            telemetry: telemetryReporter
        )

        self.environment = environment
        self.manifest = manifest
        self.navigationCoordinator = NavigationCoordinator(navigationPolicy: policy)
        self.capabilityCoordinator = capabilityCoordinator
        self.paymentCoordinator = paymentCoordinator
        self.paymentReturnRouter = paymentReturnRouter
        self.nativeEventQueue = nativeEventQueue
        self.paymentUserActionTracker = paymentUserActionTracker
        self.mediaPickerCoordinator = mediaPickerCoordinator
        self.telemetry = telemetryReporter
        self.fixedLaunchState = fixedLaunchState
        self.state = fixedLaunchState ?? .loading
        self.lastCommittedURL = environment.baseURL
        self.webView = webViewBundle.webView
        self.messageHandler = webViewBundle.messageHandler

        super.init()
        var launchAttributes = [
            "platform": "ios",
            "appVersion": Bundle.main.object(
                forInfoDictionaryKey: "CFBundleShortVersionString"
            ) as? String ?? "0.0.0",
        ]
        if let manifest {
            launchAttributes["bridgeVersion"] = manifest.contractVersion
        } else {
            telemetryReporter.record(
                .mobileConfigFailed,
                attributes: ["errorCode": "BRIDGE_MANIFEST_UNAVAILABLE"]
            )
        }
        telemetryReporter.record(.shellLaunch, attributes: launchAttributes)
        capabilityCoordinator?.presenterProvider = { [weak self] in
            self?.presentationController()
        }
        mediaPickerCoordinator.presenterProvider = { [weak self] in
            self?.presentationController()
        }
        configureCurrentWebView()
        detachPaymentReturnListener = paymentReturnRouter?.attach { [weak self] in
            self?.dispatchPendingNativeEvent()
        }
    }

    deinit {
        if let navigationAttachment {
            navigationCoordinator.detach(navigationAttachment)
        }
        MainActor.assumeIsolated {
            removeMessageHandler(from: webView)
        }
        detachPaymentReturnListener?()
    }

    func startIfNeeded() {
        guard !started else { return }
        started = true
        guard fixedLaunchState == nil else { return }
        load(environment.baseURL)
    }

    func retry() {
        guard fixedLaunchState == nil else { return }
        state = .loading
        load(lastCommittedURL)
    }

    @discardableResult
    func offerDeepLink(_ url: URL) -> Bool {
        let accepted = navigationCoordinator.offer(url.absoluteString)
        if !accepted {
            telemetry.record(
                .deepLinkOpenFailed,
                attributes: ["errorCode": "DEEPLINK_REJECTED"],
                routeURL: url.absoluteString
            )
        }
        return accepted
    }

    @discardableResult
    func offerPushRoute(_ url: URL) -> Bool {
        let accepted = navigationCoordinator.offer(url.absoluteString)
        if !accepted {
            telemetry.record(
                .pushOpenFailed,
                attributes: ["errorCode": "PUSH_ROUTE_REJECTED"],
                routeURL: url.absoluteString
            )
        }
        return accepted
    }

    func receivedUniversalLink(_ url: URL) -> Bool {
        paymentReturnRouter?.receivedUniversalLink()
        return offerDeepLink(url)
    }

    func sceneBecameActive() {
        paymentReturnRouter?.sceneBecameActive()
    }

    func sceneLeftForeground() {
        paymentReturnRouter?.sceneLeftForeground()
    }

    private static func makeWebView(
        manifest: BridgeManifest?,
        trustedOrigin: URL,
        dispatcher: BridgeCapabilityDispatching?,
        telemetry: TelemetryReporter
    ) -> (webView: WKWebView, messageHandler: BridgeMessageHandler?) {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        configuration.preferences.javaScriptCanOpenWindowsAutomatically = false
        configuration.preferences.isFraudulentWebsiteWarningEnabled = true
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true

        let messageHandler: BridgeMessageHandler?
        if let manifest {
            configuration.applicationNameForUserAgent = NativeModeBootstrap.userAgentProduct(
                manifest: manifest
            )
            if let bootstrapScript = NativeModeBootstrap.script(
                manifest: manifest,
                trustedOrigin: trustedOrigin,
                platform: "ios"
            ) {
                configuration.userContentController.addUserScript(
                    WKUserScript(
                        source: bootstrapScript,
                        injectionTime: .atDocumentStart,
                        forMainFrameOnly: true
                    )
                )
            }
            let handler = BridgeMessageHandler(
                manifest: manifest,
                trustedOrigin: trustedOrigin,
                dispatcher: dispatcher,
                telemetry: telemetry
            )
            messageHandler = handler
            configuration.userContentController.addScriptMessageHandler(
                handler,
                contentWorld: .page,
                name: manifest.globalObjectName
            )
        } else {
            messageHandler = nil
        }

        let webView = WKWebView(frame: .zero, configuration: configuration)
        if #available(iOS 16.4, *) {
#if DEBUG
            webView.isInspectable = true
#else
            webView.isInspectable = false
#endif
        }
        return (webView, messageHandler)
    }

    private func configureCurrentWebView() {
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.allowsBackForwardNavigationGestures = true
        webView.allowsLinkPreview = false
        webView.scrollView.contentInsetAdjustmentBehavior = .automatic
        let paymentGesture = UILongPressGestureRecognizer(
            target: self,
            action: #selector(recordPaymentUserAction)
        )
        paymentGesture.minimumPressDuration = 0
        paymentGesture.cancelsTouchesInView = false
        paymentGesture.delaysTouchesBegan = false
        paymentGesture.delaysTouchesEnded = false
        paymentGesture.delegate = self
        webView.addGestureRecognizer(paymentGesture)

        let attachedWebView = webView
        navigationAttachment = navigationCoordinator.attach {
            [weak self, weak attachedWebView] url in
            guard let self,
                  let attachedWebView,
                  self.webView === attachedWebView
            else {
                return
            }
            self.load(url)
        }
    }

    private func replaceWebView(restoring url: URL) {
        retireCurrentWebView()

        let replacement = Self.makeWebView(
            manifest: manifest,
            trustedOrigin: environment.trustedOrigin,
            dispatcher: capabilityCoordinator,
            telemetry: telemetry
        )
        webView = replacement.webView
        messageHandler = replacement.messageHandler
        configureCurrentWebView()
        webViewGeneration += 1
        state = .loading
        load(url)
    }

    private func retireCurrentWebView() {
        trustedDocumentReady = false
        capabilityCoordinator?.cancelPending()
        mediaPickerCoordinator.cancelPending()
        if let navigationAttachment {
            navigationCoordinator.detach(navigationAttachment)
            self.navigationAttachment = nil
            documentAttachment = nil
        }
        webView.stopLoading()
        webView.navigationDelegate = nil
        webView.uiDelegate = nil
        removeMessageHandler(from: webView)
        messageHandler = nil
    }

    private func removeMessageHandler(from webView: WKWebView) {
        guard let manifest else { return }
        webView.configuration.userContentController.removeScriptMessageHandler(
            forName: manifest.globalObjectName,
            contentWorld: .page
        )
    }

    private func load(_ url: URL) {
        guard case .internal = navigationCoordinator.classify(url.absoluteString) else {
            state = .serverError
            return
        }
        webView.load(URLRequest(url: url))
    }

    private func openExternal(_ url: URL) {
        UIApplication.shared.open(url, options: [:], completionHandler: nil)
    }

    private func presentationController() -> UIViewController? {
        webView.window?.rootViewController
    }

    @objc private func recordPaymentUserAction(_ gesture: UILongPressGestureRecognizer) {
        guard gesture.state == .began else { return }
        paymentUserActionTracker.record()
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    private func dispatchPendingNativeEvent() {
        guard trustedDocumentReady,
              case .internal = navigationCoordinator.classify(webView.url?.absoluteString),
              let script = nativeEventQueue?.pendingDispatchScript()
        else {
            return
        }
        // Script execution is not delivery acknowledgement; only ackNativeEvent removes it.
        webView.evaluateJavaScript(script, completionHandler: nil)
    }

    private func applyFailure(_ error: Error) {
        guard let code = (error as? URLError)?.code else {
            state = .serverError
            telemetry.record(
                .webViewLoadFailed,
                attributes: ["errorCode": "WEBVIEW_LOAD_FAILED"],
                routeURL: lastCommittedURL.absoluteString
            )
            return
        }
        let errorCode: String?
        switch code {
        case .cancelled:
            errorCode = nil
        case .notConnectedToInternet, .cannotFindHost, .cannotConnectToHost,
             .networkConnectionLost, .timedOut:
            state = .offline
            errorCode = "NETWORK_FAILURE"
        case .serverCertificateUntrusted, .serverCertificateHasBadDate,
             .serverCertificateHasUnknownRoot, .secureConnectionFailed:
            state = .tlsError
            errorCode = "TLS_ERROR"
        default:
            state = .serverError
            errorCode = "WEBVIEW_LOAD_FAILED"
        }
        if let errorCode {
            telemetry.record(
                .webViewLoadFailed,
                attributes: ["errorCode": errorCode],
                routeURL: lastCommittedURL.absoluteString
            )
        }
    }

    private func recoveryRouteKey(_ url: URL?) -> String {
        guard case let .internal(trustedURL) = navigationCoordinator.classify(
            url?.absoluteString
        ), var components = URLComponents(
            url: trustedURL,
            resolvingAgainstBaseURL: false
        ) else {
            return environment.trustedOrigin.absoluteString
        }
        components.user = nil
        components.password = nil
        components.query = nil
        components.fragment = nil
        if components.path.isEmpty { components.path = "/" }
        return components.url?.absoluteString ?? environment.trustedOrigin.absoluteString
    }
}

extension WebViewStore: WKNavigationDelegate {
    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationAction: WKNavigationAction,
        decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
    ) {
        guard let url = navigationAction.request.url else {
            decisionHandler(.cancel)
            return
        }

        let decision = navigationCoordinator.classify(url.absoluteString)
        if navigationAction.shouldPerformDownload {
            if navigationAction.targetFrame?.isMainFrame != false {
                switch decision {
                case let .internal(downloadURL):
                    openExternal(downloadURL)
                case let .external(downloadURL):
                    openExternal(downloadURL)
                case .blocked:
                    break
                }
            }
            decisionHandler(.cancel)
            return
        }

        switch decision {
        case .internal:
            decisionHandler(.allow)
        case let .external(externalURL):
            if navigationAction.targetFrame?.isMainFrame != false {
                openExternal(externalURL)
            }
            decisionHandler(.cancel)
        case .blocked:
            decisionHandler(.cancel)
        }
    }

    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationResponse: WKNavigationResponse,
        decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void
    ) {
        guard navigationResponse.isForMainFrame,
              let response = navigationResponse.response as? HTTPURLResponse,
              response.statusCode >= 500
        else {
            decisionHandler(.allow)
            return
        }
        state = .serverError
        telemetry.record(
            .webViewLoadFailed,
            attributes: ["errorCode": "HTTP_5XX"],
            routeURL: response.url?.absoluteString
        )
        decisionHandler(.cancel)
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        trustedDocumentReady = false
        state = .loading
        if let navigationAttachment {
            documentAttachment = navigationCoordinator.markTrustedDocumentLoading(
                navigationAttachment
            )
        }
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        guard case let .internal(committedURL) = navigationCoordinator.classify(
            webView.url?.absoluteString
        ) else {
            return
        }
        lastCommittedURL = committedURL
        state = .content
        let finishedDocument = documentAttachment

        guard let manifest,
              let script = BridgeHandshakeScript.create(
                manifest: manifest,
                appVersion: Bundle.main.object(
                    forInfoDictionaryKey: "CFBundleShortVersionString"
                ) as? String ?? "0",
                buildNumber: Bundle.main.object(
                    forInfoDictionaryKey: "CFBundleVersion"
                ) as? String ?? "0",
                platform: "ios"
              )
        else {
            if let navigationAttachment, let finishedDocument {
                navigationCoordinator.markTrustedDocumentReady(
                    navigationAttachment,
                    document: finishedDocument
                )
            }
            return
        }

        guard let navigationAttachment, let finishedDocument else { return }
        webView.evaluateJavaScript(script) { [weak self, weak webView] _, _ in
            guard let self, let webView, self.webView === webView else { return }
            self.navigationCoordinator.markTrustedDocumentReady(
                navigationAttachment,
                document: finishedDocument
            )
            self.trustedDocumentReady = true
            self.dispatchPendingNativeEvent()
        }
    }

    func webView(
        _ webView: WKWebView,
        didFailProvisionalNavigation navigation: WKNavigation!,
        withError error: Error
    ) {
        applyFailure(error)
    }

    func webView(
        _ webView: WKWebView,
        didFail navigation: WKNavigation!,
        withError error: Error
    ) {
        applyFailure(error)
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        let restoreURL = lastCommittedURL
        let loopBlocked = crashLoopBreaker.recordCrash(
            routeKey: recoveryRouteKey(restoreURL),
            at: Date()
        )
        telemetry.record(
            .webViewRendererTerminated,
            attributes: ["errorCode": "RENDERER_TERMINATED"],
            routeURL: restoreURL.absoluteString
        )
        if loopBlocked {
            state = .rendererUnavailable(loopBlocked: true)
        } else {
            paymentReturnRouter?.webViewRecovered()
            replaceWebView(restoring: restoreURL)
        }
    }

    func webView(
        _ webView: WKWebView,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        // Default platform trust evaluation is mandatory; no bypass credential is created.
        completionHandler(.performDefaultHandling, nil)
    }
}

private final class PaymentUserActionTracker {
    private var lastActionAt: TimeInterval?

    func record() {
        lastActionAt = ProcessInfo.processInfo.systemUptime
    }

    func isRecent() -> Bool {
        guard let lastActionAt else { return false }
        let elapsed = ProcessInfo.processInfo.systemUptime - lastActionAt
        return elapsed >= 0 && elapsed <= 2
    }
}

extension WebViewStore: WKUIDelegate {
    @available(iOS 18.4, *)
    func webView(
        _ webView: WKWebView,
        runOpenPanelWith parameters: WKOpenPanelParameters,
        initiatedByFrame frame: WKFrameInfo,
        completionHandler: @escaping ([URL]?) -> Void
    ) {
        guard !parameters.allowsDirectories,
              BridgeOriginPolicy(trustedOrigin: environment.trustedOrigin).accepts(
                frameURL: frame.request.url,
                isMainFrame: frame.isMainFrame
              )
        else {
            completionHandler(nil)
            return
        }
        mediaPickerCoordinator.present(
            allowsMultipleSelection: parameters.allowsMultipleSelection,
            sourceView: webView,
            completion: completionHandler
        )
    }

    func webView(
        _ webView: WKWebView,
        createWebViewWith configuration: WKWebViewConfiguration,
        for navigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures
    ) -> WKWebView? {
        guard navigationAction.targetFrame == nil,
              let url = navigationAction.request.url
        else {
            return nil
        }
        switch navigationCoordinator.classify(url.absoluteString) {
        case .internal:
            webView.load(navigationAction.request)
        case let .external(externalURL):
            openExternal(externalURL)
        case .blocked:
            break
        }
        return nil
    }
}
