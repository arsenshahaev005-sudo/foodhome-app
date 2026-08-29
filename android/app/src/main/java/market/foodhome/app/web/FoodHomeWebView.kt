package market.foodhome.app.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import market.foodhome.app.BuildConfig
import market.foodhome.app.bridge.BridgeHandshakeScript
import market.foodhome.app.bridge.BridgeCapabilityDispatcher
import market.foodhome.app.bridge.BridgeDispatchResult
import market.foodhome.app.bridge.BridgeManifest
import market.foodhome.app.bridge.NativeModeBootstrap
import market.foodhome.app.bridge.NativeEventQueue
import market.foodhome.app.bridge.BridgeOriginPolicy
import market.foodhome.app.bridge.BridgeRequestResult
import market.foodhome.app.bridge.BridgeRequestValidator
import market.foodhome.app.bridge.BridgeResponses
import market.foodhome.app.bridge.TerminalReply
import market.foodhome.app.config.AppEnvironment
import market.foodhome.app.navigation.BackNavigationAction
import market.foodhome.app.navigation.BackNavigationPolicy
import market.foodhome.app.navigation.NavigationDecision
import market.foodhome.app.navigation.NavigationAttachment
import market.foodhome.app.navigation.NavigationCoordinator
import market.foodhome.app.navigation.TrustedDocumentAttachment
import market.foodhome.app.recovery.CrashLoopBreaker
import market.foodhome.app.media.MediaRequest
import market.foodhome.app.ui.AppShellState
import market.foodhome.app.telemetry.TelemetryEventName
import market.foodhome.app.telemetry.TelemetryReporter
import java.time.Instant

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FoodHomeWebView(
    environment: AppEnvironment,
    navigationCoordinator: NavigationCoordinator,
    manifest: BridgeManifest,
    crashLoopBreaker: CrashLoopBreaker,
    initialUrl: String,
    onStateChanged: (AppShellState) -> Unit,
    onTrustedUrlCommitted: (String) -> Unit,
    onRendererGone: (Boolean) -> Unit,
    onOpenExternal: (Uri) -> Unit,
    capabilityDispatcher: BridgeCapabilityDispatcher,
    nativeEventQueue: NativeEventQueue,
    nativeEventRevision: Int,
    telemetry: TelemetryReporter,
    onPaymentUserAction: () -> Unit,
    onFileRequest: (ValueCallback<Array<Uri>>, MediaRequest) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var ownedWebView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var trustedDocumentReady by remember { mutableStateOf(false) }

    LaunchedEffect(nativeEventRevision, trustedDocumentReady, ownedWebView) {
        val currentWebView = ownedWebView
        val script = nativeEventQueue.pendingDispatchScript()
        if (trustedDocumentReady && currentWebView != null && script != null) {
            // JavaScript execution is not an ACK; the durable event remains queued.
            currentWebView.evaluateJavascript(script, null)
        }
    }

    BackHandler(
        enabled = BackNavigationPolicy.decide(canGoBack) == BackNavigationAction.WebHistory,
    ) {
        ownedWebView?.let { webView ->
            if (webView.canGoBack()) webView.goBack()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            var navigationAttachment: NavigationAttachment? = null
            var documentAttachment: TrustedDocumentAttachment? = null
            var mainFrameFailed = false
            WebView(context).apply {
                ownedWebView = this
                WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.mediaPlaybackRequiresUserGesture = true
                settings.setGeolocationEnabled(false)
                settings.safeBrowsingEnabled = true
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) onPaymentUserAction()
                    false
                }

                settings.userAgentString = NativeModeBootstrap.userAgent(
                    existing = settings.userAgentString,
                    contract = manifest.nativeMode,
                )

                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    val bootstrapScript = NativeModeBootstrap.documentStartScript(
                        contract = manifest.nativeMode,
                        trustedOrigin = environment.trustedOrigin,
                        platform = "android",
                    )
                    if (bootstrapScript.isNotEmpty()) {
                        WebViewCompat.addDocumentStartJavaScript(
                            this,
                            bootstrapScript,
                            setOf(environment.trustedOrigin.toASCIIString()),
                        )
                    }
                }

                val bridgeAvailable = WebViewFeature.isFeatureSupported(
                    WebViewFeature.WEB_MESSAGE_LISTENER,
                )
                if (bridgeAvailable) {
                    registerBridge(
                        webView = this,
                        environment = environment,
                        manifest = manifest,
                        dispatcher = capabilityDispatcher,
                        telemetry = telemetry,
                    )
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>,
                        fileChooserParams: FileChooserParams,
                    ): Boolean = onFileRequest(
                        filePathCallback,
                        MediaRequest(
                            acceptedTypes = fileChooserParams.acceptTypes.filter(String::isNotBlank),
                            captureEnabled = fileChooserParams.isCaptureEnabled,
                            allowMultiple = fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE,
                        ),
                    )
                }

                setDownloadListener { url, _, _, _, _ ->
                    when (val decision = navigationCoordinator.classify(url)) {
                        is NavigationDecision.Internal -> onOpenExternal(
                            Uri.parse(decision.uri.toString()),
                        )
                        is NavigationDecision.External -> onOpenExternal(
                            Uri.parse(decision.uri.toString()),
                        )
                        is NavigationDecision.Blocked -> Unit
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = when (
                        val decision = navigationCoordinator.classify(request.url.toString())
                    ) {
                        is NavigationDecision.Internal -> false
                        is NavigationDecision.External -> {
                            if (request.isForMainFrame) onOpenExternal(Uri.parse(decision.uri.toString()))
                            true
                        }
                        is NavigationDecision.Blocked -> true
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        trustedDocumentReady = false
                        mainFrameFailed = false
                        onStateChanged(AppShellState.Loading)
                        documentAttachment = navigationAttachment?.let(
                            navigationCoordinator::markTrustedDocumentLoading,
                        )
                        canGoBack = view?.canGoBack() == true
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        if (
                            !mainFrameFailed &&
                            navigationCoordinator.classify(url) is NavigationDecision.Internal
                        ) {
                            val finishedDocument = documentAttachment
                            onStateChanged(AppShellState.Content)
                            url?.let(onTrustedUrlCommitted)
                            canGoBack = view.canGoBack()
                            if (bridgeAvailable) {
                                view.evaluateJavascript(
                                    BridgeHandshakeScript.create(
                                        manifest = manifest,
                                        appVersion = BuildConfig.VERSION_NAME,
                                        buildNumber = BuildConfig.VERSION_CODE.toString(),
                                        platform = "android",
                                    ),
                                ) {
                                    val attachment = navigationAttachment
                                    if (attachment != null && finishedDocument != null) {
                                        navigationCoordinator.markTrustedDocumentReady(
                                            attachment,
                                            finishedDocument,
                                        )
                                    }
                                    trustedDocumentReady = true
                                }
                            } else {
                                val attachment = navigationAttachment
                                if (attachment != null && finishedDocument != null) {
                                    navigationCoordinator.markTrustedDocumentReady(
                                        attachment,
                                        finishedDocument,
                                    )
                                }
                            }
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (!request.isForMainFrame) return
                        mainFrameFailed = true
                        val state = when (error.errorCode) {
                            ERROR_HOST_LOOKUP, ERROR_CONNECT, ERROR_TIMEOUT -> AppShellState.Offline
                            else -> AppShellState.ServerError
                        }
                        telemetry.record(
                            TelemetryEventName.WebViewLoadFailed,
                            attributes = mapOf(
                                "errorCode" to if (state == AppShellState.Offline) {
                                    "NETWORK_FAILURE"
                                } else {
                                    "WEBVIEW_LOAD_FAILED"
                                },
                            ),
                            routeUrl = request.url.toString(),
                        )
                        onStateChanged(state)
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse,
                    ) {
                        if (request.isForMainFrame && errorResponse.statusCode >= 500) {
                            mainFrameFailed = true
                            telemetry.record(
                                TelemetryEventName.WebViewLoadFailed,
                                attributes = mapOf("errorCode" to "HTTP_5XX"),
                                routeUrl = request.url.toString(),
                            )
                            onStateChanged(AppShellState.ServerError)
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler,
                        error: SslError?,
                    ) {
                        handler.cancel()
                        mainFrameFailed = true
                        telemetry.record(
                            TelemetryEventName.WebViewLoadFailed,
                            attributes = mapOf("errorCode" to "TLS_ERROR"),
                            routeUrl = error?.url,
                        )
                        onStateChanged(AppShellState.TlsError)
                    }

                    override fun onRenderProcessGone(
                        view: WebView,
                        detail: RenderProcessGoneDetail,
                    ): Boolean {
                        val routeKey = recoveryRouteKey(
                            navigationCoordinator.classify(view.url?.toString()),
                            environment.trustedOrigin.toASCIIString(),
                        )
                        val loopBlocked = crashLoopBreaker.recordCrash(routeKey, Instant.now())
                        telemetry.record(
                            TelemetryEventName.WebViewRendererTerminated,
                            attributes = mapOf(
                                "errorCode" to if (detail.didCrash()) "RENDERER_CRASHED" else "RENDERER_TERMINATED",
                            ),
                            routeUrl = view.url?.toString(),
                        )
                        navigationAttachment?.let(navigationCoordinator::detach)
                        ownedWebView = null
                        trustedDocumentReady = false
                        view.destroy()
                        onRendererGone(loopBlocked)
                        return true
                    }
                }

                navigationAttachment = navigationCoordinator.attach { targetUrl ->
                    if (navigationCoordinator.classify(targetUrl) is NavigationDecision.Internal) {
                        loadUrl(targetUrl)
                    }
                }
                tag = navigationAttachment
                loadUrl(initialUrl)
            }
        },
        onRelease = {
            (it.tag as? NavigationAttachment)?.let(navigationCoordinator::detach)
            it.tag = null
            if (ownedWebView === it) ownedWebView = null
            it.destroy()
        },
    )
}

private fun recoveryRouteKey(decision: NavigationDecision, fallback: String): String {
    val internal = decision as? NavigationDecision.Internal ?: return fallback
    return runCatching {
        java.net.URI(
            internal.uri.scheme,
            null,
            internal.uri.host,
            internal.uri.port,
            internal.uri.path.ifBlank { "/" },
            null,
            null,
        ).toASCIIString()
    }.getOrDefault(fallback)
}

private fun registerBridge(
    webView: WebView,
    environment: AppEnvironment,
    manifest: BridgeManifest,
    dispatcher: BridgeCapabilityDispatcher,
    telemetry: TelemetryReporter,
) {
    val originPolicy = BridgeOriginPolicy(environment.trustedOrigin)
    val validator = BridgeRequestValidator(manifest, environment.trustedOrigin)
    val listener = WebViewCompat.WebMessageListener {
            _, message: WebMessageCompat, sourceOrigin, isMainFrame, replyProxy ->
        if (originPolicy.accepts(sourceOrigin.toString(), isMainFrame)) {
            val data = if (message.type == WebMessageCompat.TYPE_STRING) message.data else null
            when (val result = data?.let(validator::validate)) {
                is BridgeRequestResult.Accepted -> {
                    val reply = TerminalReply()
                    dispatcher.dispatch(result.request) { dispatchResult ->
                        reply.complete {
                            replyProxy.postMessage(
                                when (dispatchResult) {
                                    is BridgeDispatchResult.Success -> BridgeResponses.success(
                                        manifest,
                                        result.request.requestId,
                                        dispatchResult.result,
                                    )
                                    is BridgeDispatchResult.Failure -> BridgeResponses.error(
                                        manifest = manifest,
                                        requestId = result.request.requestId,
                                        code = dispatchResult.code,
                                        message = dispatchResult.message,
                                        retryable = dispatchResult.retryable,
                                    )
                                },
                            )
                        }
                    }
                }
                is BridgeRequestResult.Rejected -> {
                    telemetry.record(
                        when (result.code) {
                            "METHOD_NOT_SUPPORTED" -> TelemetryEventName.BridgeMethodUnsupported
                            "VERSION_NOT_SUPPORTED" -> TelemetryEventName.BridgeVersionIncompatible
                            else -> TelemetryEventName.BridgeRequestFailed
                        },
                        attributes = mapOf("errorCode" to result.code),
                    )
                    val requestId = result.requestId
                    if (requestId != null) {
                        val reply = TerminalReply()
                        reply.complete {
                            replyProxy.postMessage(
                                BridgeResponses.rejected(manifest, requestId, result.code),
                            )
                        }
                    }
                }
                null -> telemetry.record(
                    TelemetryEventName.BridgeRequestFailed,
                    attributes = mapOf("errorCode" to "INVALID_MESSAGE"),
                )
            }
        } else {
            telemetry.record(
                TelemetryEventName.BridgeRequestFailed,
                attributes = mapOf("errorCode" to "ORIGIN_OR_FRAME_NOT_ALLOWED"),
            )
        }
    }

    WebViewCompat.addWebMessageListener(
        webView,
        manifest.globalObjectName,
        setOf(environment.trustedOrigin.toASCIIString()),
        listener,
    )
}
