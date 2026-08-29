package market.foodhome.app.telemetry

enum class TelemetryEventName(val wireValue: String) {
    ShellLaunch("shell.launch"),
    WebViewLoadFailed("webview.load.failed"),
    WebViewRendererTerminated("webview.renderer.terminated"),
    BridgeRequestFailed("bridge.request.failed"),
    BridgeMethodUnsupported("bridge.method.unsupported"),
    BridgeVersionIncompatible("bridge.version.incompatible"),
    DeepLinkOpenFailed("deeplink.open.failed"),
    PushOpenFailed("push.open.failed"),
    PaymentReturnFailed("payment.return.failed"),
    MobileConfigFailed("mobile.config.failed"),
}

enum class TelemetryAttributeKey(val wireValue: String) {
    Platform("platform"),
    AppVersion("appVersion"),
    BridgeVersion("bridgeVersion"),
    WebViewVersion("webViewVersion"),
    RouteTemplate("routeTemplate"),
    ErrorCode("errorCode"),
    CorrelationId("correlationId"),
    NetworkClass("networkClass"),
    DurationBucket("durationBucket"),
}

data class TelemetryEvent internal constructor(
    val name: TelemetryEventName,
    val attributes: Map<TelemetryAttributeKey, String>,
)
