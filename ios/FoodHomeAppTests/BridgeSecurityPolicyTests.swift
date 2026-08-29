import XCTest
@testable import FoodHomeApp

final class BridgeSecurityPolicyTests: XCTestCase {
    private let manifest = BridgeManifest(
        protocolName: "foodhome.bridge",
        contractVersion: "1.2.0",
        bridgeMajor: 1,
        supportedVersions: [1],
        globalObjectName: "FoodHomeBridge",
        handshake: .init(transport: "dom-event", eventName: "foodhome:bridge-ready"),
        nativeMode: .init(
            protocolName: "foodhome.native-mode",
            version: 1,
            globalObjectName: "FoodHomeNative",
            userAgentProduct: "FoodHomeNative/1",
            securityBoundary: false,
            documentStart: .init(trustedOriginOnly: true, mainFrameOnly: true)
        ),
        limits: .init(
            maxMessageBytes: 512,
            requestTimeoutMs: 10_000,
            maxRequestIdLength: 64,
            maxUrlLength: 2_048
        ),
        methods: ["share"],
        phase0Capabilities: []
    )

    func testBridgeRequiresExactOriginAndMainFrame() {
        let policy = BridgeOriginPolicy(
            trustedOrigin: URL(string: "https://foodhome.market")!
        )
        XCTAssertTrue(
            policy.accepts(
                frameURL: URL(string: "https://foodhome.market/orders/123"),
                isMainFrame: true
            )
        )
        XCTAssertFalse(
            policy.accepts(
                frameURL: URL(string: "https://foodhome.market/orders/123"),
                isMainFrame: false
            )
        )
        XCTAssertFalse(
            policy.accepts(
                frameURL: URL(string: "https://cdn.foodhome.market"),
                isMainFrame: true
            )
        )
        XCTAssertFalse(
            policy.accepts(
                frameURL: URL(string: "http://foodhome.market"),
                isMainFrame: true
            )
        )
    }

    func testUnknownMethodAndVersionReturnTypedRejection() throws {
        let validator = BridgeRequestValidator(manifest: manifest)
        let unknown = Data(
            #"{"protocol":"foodhome.bridge","version":1,"requestId":"r1","method":"eval","payload":{}}"#.utf8
        )
        let unsupported = Data(
            #"{"protocol":"foodhome.bridge","version":2,"requestId":"r2","method":"share","payload":{}}"#.utf8
        )

        XCTAssertEqual(
            validator.validate(unknown),
            .rejected(requestID: "r1", code: "METHOD_NOT_SUPPORTED")
        )
        XCTAssertEqual(
            validator.validate(unsupported),
            .rejected(requestID: "r2", code: "VERSION_NOT_SUPPORTED")
        )
    }

    func testMessageLimitCountsUTF8Bytes() {
        var smallManifest = manifest
        smallManifest = BridgeManifest(
            protocolName: smallManifest.protocolName,
            contractVersion: smallManifest.contractVersion,
            bridgeMajor: smallManifest.bridgeMajor,
            supportedVersions: smallManifest.supportedVersions,
            globalObjectName: smallManifest.globalObjectName,
            handshake: smallManifest.handshake,
            nativeMode: smallManifest.nativeMode,
            limits: .init(
                maxMessageBytes: 4,
                requestTimeoutMs: 10_000,
                maxRequestIdLength: 64,
                maxUrlLength: 2_048
            ),
            methods: smallManifest.methods,
            phase0Capabilities: smallManifest.phase0Capabilities,
            compiledCapabilities: smallManifest.compiledCapabilities
        )
        XCTAssertEqual(
            BridgeRequestValidator(manifest: smallManifest).validate(Data("яяя".utf8)),
            .rejected(requestID: nil, code: "PAYLOAD_TOO_LARGE")
        )
    }

    func testDeepAndPrototypeShapedJSONFailsClosed() {
        let strictManifest = BridgeManifest(
            protocolName: manifest.protocolName,
            contractVersion: manifest.contractVersion,
            bridgeMajor: manifest.bridgeMajor,
            supportedVersions: manifest.supportedVersions,
            globalObjectName: manifest.globalObjectName,
            handshake: manifest.handshake,
            nativeMode: manifest.nativeMode,
            limits: .init(
                maxMessageBytes: 2_048,
                requestTimeoutMs: 10_000,
                maxRequestIdLength: 64,
                maxUrlLength: 2_048,
                maxJsonDepth: 4,
                maxJsonNodes: 64
            ),
            methods: manifest.methods,
            phase0Capabilities: manifest.phase0Capabilities
        )
        let validator = BridgeRequestValidator(manifest: strictManifest)
        let deep = Data(
            #"{"protocol":"foodhome.bridge","version":1,"requestId":"r1","method":"share","payload":{"url":"https://foodhome.market","future":{"a":{"b":{"c":1}}}}}"#.utf8
        )
        let prototype = Data(
            #"{"protocol":"foodhome.bridge","version":1,"requestId":"r2","method":"share","payload":{"url":"https://foodhome.market","__proto__":{}}}"#.utf8
        )

        XCTAssertEqual(
            validator.validate(deep),
            .rejected(requestID: nil, code: "INVALID_MESSAGE")
        )
        XCTAssertEqual(
            validator.validate(prototype),
            .rejected(requestID: nil, code: "INVALID_MESSAGE")
        )
    }

    func testPhaseOneHandshakeUsesCanonicalEventAndAdvertisesNoCapability() throws {
        let script = try XCTUnwrap(
            BridgeHandshakeScript.create(
                manifest: manifest,
                appVersion: "0.1.0",
                buildNumber: "1",
                platform: "ios"
            )
        )

        XCTAssertTrue(script.contains("foodhome:bridge-ready"))
        XCTAssertTrue(script.contains(#""capabilities":[]"#))
        XCTAssertFalse(script.contains(#""capabilities":["share"]"#))
    }

    func testNativeModeBootstrapIsExactOriginMainFrameAndMinimal() throws {
        let script = try XCTUnwrap(
            NativeModeBootstrap.script(
                manifest: manifest,
                trustedOrigin: URL(string: "https://foodhome.market")!,
                platform: "ios"
            )
        )

        XCTAssertEqual(NativeModeBootstrap.userAgentProduct(manifest: manifest), "FoodHomeNative/1")
        XCTAssertTrue(script.contains(#"window !== window.top"#))
        XCTAssertTrue(script.contains(#"window.location.origin !== "https://foodhome.market""#))
        XCTAssertTrue(script.contains(#""protocol":"foodhome.native-mode""#))
        XCTAssertTrue(script.contains(#""version":1"#))
        XCTAssertTrue(script.contains(#""platform":"ios""#))
        XCTAssertTrue(script.contains("Object.freeze"))
        XCTAssertFalse(script.contains("token"))
        XCTAssertFalse(script.contains("userId"))
        XCTAssertFalse(script.contains("installation"))
        XCTAssertFalse(script.contains("*://"))
    }
}
