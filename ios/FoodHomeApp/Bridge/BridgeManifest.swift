import Foundation

struct BridgeManifest: Decodable, Sendable {
    struct Limits: Decodable, Sendable {
        let maxMessageBytes: Int
        let requestTimeoutMs: Int
        let maxRequestIdLength: Int
        let maxUrlLength: Int
        let maxJsonDepth: Int
        let maxJsonNodes: Int

        init(
            maxMessageBytes: Int,
            requestTimeoutMs: Int,
            maxRequestIdLength: Int,
            maxUrlLength: Int,
            maxJsonDepth: Int = 12,
            maxJsonNodes: Int = 512
        ) {
            self.maxMessageBytes = maxMessageBytes
            self.requestTimeoutMs = requestTimeoutMs
            self.maxRequestIdLength = maxRequestIdLength
            self.maxUrlLength = maxUrlLength
            self.maxJsonDepth = maxJsonDepth
            self.maxJsonNodes = maxJsonNodes
        }

        var isSafe: Bool {
            maxMessageBytes > 0 &&
                maxJsonDepth >= 4 && maxJsonDepth <= 32 &&
                maxJsonNodes >= 64 && maxJsonNodes <= 2_048
        }
    }

    struct RateLimitRule: Decodable, Sendable, Equatable {
        let maxRequests: Int
        let windowSeconds: Int

        var isSafe: Bool {
            maxRequests >= 1 && maxRequests <= 10 &&
                windowSeconds >= 1 && windowSeconds <= 3_600
        }
    }

    struct Handshake: Decodable, Sendable {
        let transport: String
        let eventName: String
    }

    struct NativeEvents: Decodable, Sendable {
        let transport: String
        let eventName: String
    }

    struct NativeMode: Decodable, Sendable {
        struct DocumentStart: Decodable, Sendable {
            let trustedOriginOnly: Bool
            let mainFrameOnly: Bool
        }

        let protocolName: String
        let version: Int
        let globalObjectName: String
        let userAgentProduct: String
        let securityBoundary: Bool
        let documentStart: DocumentStart

        private enum CodingKeys: String, CodingKey {
            case protocolName = "protocol"
            case version
            case globalObjectName
            case userAgentProduct
            case securityBoundary
            case documentStart
        }
    }

    let protocolName: String
    let contractVersion: String
    let bridgeMajor: Int
    let supportedVersions: [Int]
    let globalObjectName: String
    let handshake: Handshake
    let nativeEvents: NativeEvents
    let nativeMode: NativeMode
    let limits: Limits
    let methods: Set<String>
    let phase0Capabilities: Set<String>
    let compiledCapabilities: Set<String>
    let builtInCapabilities: Set<String>
    let advertisedCapabilities: Set<String>
    let rateLimits: [String: RateLimitRule]

    private enum CodingKeys: String, CodingKey {
        case protocolName = "protocol"
        case contractVersion
        case bridgeMajor
        case supportedVersions
        case globalObjectName
        case handshake
        case nativeEvents
        case nativeMode
        case limits
        case methods
        case phase0Capabilities
        case compiledCapabilities
        case builtInCapabilities
        case advertisedCapabilities
        case rateLimits
    }

    init(
        protocolName: String,
        contractVersion: String,
        bridgeMajor: Int,
        supportedVersions: [Int],
        globalObjectName: String,
        handshake: Handshake,
        nativeEvents: NativeEvents = .init(
            transport: "dom-event",
            eventName: "foodhome:native-event"
        ),
        nativeMode: NativeMode,
        limits: Limits,
        methods: Set<String>,
        phase0Capabilities: Set<String>,
        compiledCapabilities: Set<String> = [],
        builtInCapabilities: Set<String>? = nil,
        advertisedCapabilities: Set<String>? = nil,
        rateLimits: [String: RateLimitRule] = [:]
    ) {
        self.protocolName = protocolName
        self.contractVersion = contractVersion
        self.bridgeMajor = bridgeMajor
        self.supportedVersions = supportedVersions
        self.globalObjectName = globalObjectName
        self.handshake = handshake
        self.nativeEvents = nativeEvents
        self.nativeMode = nativeMode
        self.limits = limits
        self.methods = methods
        self.phase0Capabilities = phase0Capabilities
        self.compiledCapabilities = compiledCapabilities
        self.builtInCapabilities = builtInCapabilities ?? compiledCapabilities
        self.advertisedCapabilities = advertisedCapabilities ?? compiledCapabilities
        self.rateLimits = rateLimits
    }

    static func load(bundle: Bundle = .main) -> BridgeManifest? {
        guard let url = bundle.url(forResource: "manifest", withExtension: "json"),
              let data = try? Data(contentsOf: url)
        else {
            return nil
        }
        guard let manifest = try? JSONDecoder().decode(BridgeManifest.self, from: data),
              manifest.limits.isSafe
        else {
            return nil
        }
        return manifest
    }
}

enum NativeModeBootstrap {
    private struct Marker: Encodable {
        let protocolName: String
        let version: Int
        let platform: String

        private enum CodingKeys: String, CodingKey {
            case protocolName = "protocol"
            case version
            case platform
        }
    }

    static func userAgentProduct(manifest: BridgeManifest) -> String? {
        guard isSafe(manifest.nativeMode) else { return nil }
        return manifest.nativeMode.userAgentProduct
    }

    static func script(
        manifest: BridgeManifest,
        trustedOrigin: URL,
        platform: String
    ) -> String? {
        guard isSafe(manifest.nativeMode),
              let origin = exactHTTPSOrigin(trustedOrigin),
              let originLiteral = jsonString(origin),
              let globalLiteral = jsonString(manifest.nativeMode.globalObjectName),
              let markerData = try? JSONEncoder().encode(
                Marker(
                    protocolName: manifest.nativeMode.protocolName,
                    version: manifest.nativeMode.version,
                    platform: platform
                )
              ),
              let marker = String(data: markerData, encoding: .utf8)
        else {
            return nil
        }

        return """
        (() => {
          if (window !== window.top || window.location.origin !== \(originLiteral)) return;
          if (Object.prototype.hasOwnProperty.call(window, \(globalLiteral))) return;
          Object.defineProperty(window, \(globalLiteral), {
            value: Object.freeze(\(marker)),
            writable: false,
            enumerable: false,
            configurable: false
          });
        })();
        """
    }

    private static func isSafe(_ contract: BridgeManifest.NativeMode) -> Bool {
        contract.protocolName == "foodhome.native-mode" &&
            contract.version == 1 &&
            !contract.globalObjectName.isEmpty &&
            !contract.userAgentProduct.isEmpty &&
            contract.userAgentProduct.rangeOfCharacter(from: .whitespacesAndNewlines) == nil &&
            !contract.securityBoundary &&
            contract.documentStart.trustedOriginOnly &&
            contract.documentStart.mainFrameOnly
    }

    private static func exactHTTPSOrigin(_ url: URL) -> String? {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              components.scheme?.lowercased() == "https",
              components.host != nil,
              components.user == nil,
              components.password == nil,
              components.query == nil,
              components.fragment == nil,
              components.path.isEmpty || components.path == "/"
        else {
            return nil
        }

        var origin = URLComponents()
        origin.scheme = "https"
        origin.host = components.host?.lowercased()
        origin.port = components.port
        return origin.url?.absoluteString
    }

    private static func jsonString(_ value: String) -> String? {
        guard let data = try? JSONEncoder().encode(value) else { return nil }
        return String(data: data, encoding: .utf8)
    }
}
