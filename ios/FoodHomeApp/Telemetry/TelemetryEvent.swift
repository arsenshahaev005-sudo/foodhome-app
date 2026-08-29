import Foundation

enum TelemetryEventName: String, CaseIterable, Sendable {
    case shellLaunch = "shell.launch"
    case webViewLoadFailed = "webview.load.failed"
    case webViewRendererTerminated = "webview.renderer.terminated"
    case bridgeRequestFailed = "bridge.request.failed"
    case bridgeMethodUnsupported = "bridge.method.unsupported"
    case bridgeVersionIncompatible = "bridge.version.incompatible"
    case deepLinkOpenFailed = "deeplink.open.failed"
    case pushOpenFailed = "push.open.failed"
    case paymentReturnFailed = "payment.return.failed"
    case mobileConfigFailed = "mobile.config.failed"
}

enum TelemetryAttributeKey: String, CaseIterable, Sendable {
    case platform
    case appVersion
    case bridgeVersion
    case webViewVersion
    case routeTemplate
    case errorCode
    case correlationId
    case networkClass
    case durationBucket
}

struct TelemetryEvent: Equatable, Sendable, CustomStringConvertible {
    let name: TelemetryEventName
    let attributes: [TelemetryAttributeKey: String]

    var description: String {
        let values = attributes
            .map { "\($0.key.rawValue)=\($0.value)" }
            .sorted()
            .joined(separator: ",")
        return "\(name.rawValue){\(values)}"
    }
}
