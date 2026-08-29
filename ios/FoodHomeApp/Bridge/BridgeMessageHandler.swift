import Foundation
import WebKit

@MainActor
final class BridgeMessageHandler: NSObject, WKScriptMessageHandlerWithReply {
    private let manifest: BridgeManifest
    private let originPolicy: BridgeOriginPolicy
    private let validator: BridgeRequestValidator
    private let telemetry: TelemetryReporter
    private weak var dispatcher: BridgeCapabilityDispatching?

    init(
        manifest: BridgeManifest,
        trustedOrigin: URL,
        dispatcher: BridgeCapabilityDispatching? = nil,
        telemetry: TelemetryReporter? = nil
    ) {
        self.manifest = manifest
        self.originPolicy = BridgeOriginPolicy(trustedOrigin: trustedOrigin)
        self.validator = BridgeRequestValidator(
            manifest: manifest,
            trustedOrigin: trustedOrigin
        )
        self.telemetry = telemetry ?? .disabled(trustedOrigin: trustedOrigin)
        self.dispatcher = dispatcher
    }

    func userContentController(
        _ userContentController: WKUserContentController,
        didReceive message: WKScriptMessage,
        replyHandler: @escaping (Any?, String?) -> Void
    ) {
        let reply = ReplyOnce(replyHandler: replyHandler)
        guard originPolicy.accepts(
            frameURL: message.frameInfo.request.url,
            isMainFrame: message.frameInfo.isMainFrame
        ) else {
            telemetry.record(
                .bridgeRequestFailed,
                attributes: ["errorCode": "ORIGIN_OR_FRAME_NOT_ALLOWED"]
            )
            reply.complete(value: nil, error: "Bridge request rejected")
            return
        }
        guard let data = messageData(message.body) else {
            telemetry.record(
                .bridgeRequestFailed,
                attributes: ["errorCode": "INVALID_MESSAGE"]
            )
            reply.complete(value: nil, error: "Bridge request rejected")
            return
        }

        switch validator.validate(data) {
        case let .accepted(request):
            guard let dispatcher else {
                reply.complete(
                    value: errorResponse(
                        requestID: request.requestID,
                        code: "CAPABILITY_UNAVAILABLE",
                        message: "Capability is unavailable"
                    ),
                    error: nil
                )
                return
            }
            dispatcher.dispatch(request) { [weak self] result in
                guard let self else {
                    reply.complete(value: nil, error: "Bridge request failed")
                    return
                }
                switch result {
                case let .success(value):
                    reply.complete(
                        value: self.successResponse(
                            requestID: request.requestID,
                            result: value
                        ),
                        error: nil
                    )
                case let .failure(code, message, retryable):
                    reply.complete(
                        value: self.errorResponse(
                            requestID: request.requestID,
                            code: code,
                            message: message,
                            retryable: retryable
                        ),
                        error: nil
                    )
                }
            }
        case let .rejected(requestID, code):
            let eventName: TelemetryEventName
            switch code {
            case "METHOD_NOT_SUPPORTED":
                eventName = .bridgeMethodUnsupported
            case "VERSION_NOT_SUPPORTED":
                eventName = .bridgeVersionIncompatible
            default:
                eventName = .bridgeRequestFailed
            }
            telemetry.record(
                eventName,
                attributes: ["errorCode": code]
            )
            guard let requestID else {
                reply.complete(value: nil, error: "Bridge request rejected")
                return
            }
            reply.complete(
                value: errorResponse(
                    requestID: requestID,
                    code: code,
                    message: "Bridge request was rejected"
                ),
                error: nil
            )
        }
    }

    private func messageData(_ body: Any) -> Data? {
        if let string = body as? String { return string.data(using: .utf8) }
        guard JSONSerialization.isValidJSONObject(body) else { return nil }
        return try? JSONSerialization.data(withJSONObject: body)
    }

    private func successResponse(requestID: String, result: [String: Any]) -> String? {
        let value: [String: Any] = [
            "protocol": manifest.protocolName,
            "version": manifest.bridgeMajor,
            "requestId": requestID,
            "ok": true,
            "result": result,
        ]
        guard JSONSerialization.isValidJSONObject(value),
              let data = try? JSONSerialization.data(withJSONObject: value)
        else {
            return nil
        }
        return String(data: data, encoding: .utf8)
    }

    private func errorResponse(
        requestID: String,
        code: String,
        message: String,
        retryable: Bool = false
    ) -> String? {
        let value: [String: Any] = [
            "protocol": manifest.protocolName,
            "version": manifest.bridgeMajor,
            "requestId": requestID,
            "ok": false,
            "error": [
                "code": code,
                "message": String(message.prefix(240)),
                "retryable": retryable,
            ],
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: value) else { return nil }
        return String(data: data, encoding: .utf8)
    }
}

private final class ReplyOnce {
    private var completed = false
    private let replyHandler: (Any?, String?) -> Void

    init(replyHandler: @escaping (Any?, String?) -> Void) {
        self.replyHandler = replyHandler
    }

    func complete(value: Any?, error: String?) {
        guard !completed else { return }
        completed = true
        replyHandler(value, error)
    }
}

enum BridgeHandshakeScript {
    static func create(
        manifest: BridgeManifest,
        appVersion: String,
        buildNumber: String,
        platform: String
    ) -> String? {
        let detail: [String: Any] = [
            "protocol": manifest.protocolName,
            "selectedVersion": manifest.bridgeMajor,
            "supportedVersions": manifest.supportedVersions,
            "appVersion": appVersion,
            "buildNumber": buildNumber,
            "platform": platform,
            "builtInCapabilities": Array(manifest.builtInCapabilities).sorted(),
            "capabilities": Array(manifest.advertisedCapabilities).sorted(),
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: detail),
              let json = String(data: data, encoding: .utf8)
        else {
            return nil
        }
        return "window.dispatchEvent(new CustomEvent('\(manifest.handshake.eventName)',{detail:\(json)}));"
    }
}
