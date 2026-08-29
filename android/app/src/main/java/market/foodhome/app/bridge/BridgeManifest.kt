package market.foodhome.app.bridge

import org.json.JSONObject
import java.io.InputStream
import java.net.URI

data class NativeModeContract(
    val protocol: String,
    val version: Int,
    val globalObjectName: String,
    val userAgentProduct: String,
    val securityBoundary: Boolean,
    val trustedOriginOnly: Boolean,
    val mainFrameOnly: Boolean,
)

data class RateLimitRule(
    val maxRequests: Int,
    val windowSeconds: Int,
) {
    val isSafe: Boolean
        get() = maxRequests in 1..10 && windowSeconds in 1..3_600
}

data class BridgeManifest(
    val protocol: String,
    val contractVersion: String,
    val bridgeMajor: Int,
    val supportedVersions: Set<Int>,
    val globalObjectName: String,
    val handshakeEventName: String,
    val nativeEventName: String = "foodhome:native-event",
    val nativeMode: NativeModeContract,
    val maxMessageBytes: Int,
    val methods: Set<String>,
    val phase0Capabilities: Set<String>,
    val compiledCapabilities: Set<String> = emptySet(),
    val builtInCapabilities: Set<String> = compiledCapabilities,
    val advertisedCapabilities: Set<String> = compiledCapabilities,
    val trustedProductionOrigin: URI = URI("https://foodhome.market"),
    val maxJsonDepth: Int = 12,
    val maxJsonNodes: Int = 512,
    val rateLimits: Map<String, RateLimitRule> = emptyMap(),
) {
    companion object {
        fun from(input: InputStream): BridgeManifest {
            val json = JSONObject(input.bufferedReader().use { it.readText() })
            val limits = json.getJSONObject("limits")
            val maxJsonDepth = limits.getInt("maxJsonDepth")
            val maxJsonNodes = limits.getInt("maxJsonNodes")
            require(maxJsonDepth in 4..32) { "Unsafe bridge JSON depth limit" }
            require(maxJsonNodes in 64..2_048) { "Unsafe bridge JSON node limit" }
            val nativeMode = json.getJSONObject("nativeMode")
            val documentStart = nativeMode.getJSONObject("documentStart")
            return BridgeManifest(
                protocol = json.getString("protocol"),
                contractVersion = json.getString("contractVersion"),
                bridgeMajor = json.getInt("bridgeMajor"),
                supportedVersions = json.getJSONArray("supportedVersions").toIntSet(),
                globalObjectName = json.getString("globalObjectName"),
                handshakeEventName = json.getJSONObject("handshake").getString("eventName"),
                nativeEventName = json.getJSONObject("nativeEvents").getString("eventName"),
                nativeMode = NativeModeContract(
                    protocol = nativeMode.getString("protocol"),
                    version = nativeMode.getInt("version"),
                    globalObjectName = nativeMode.getString("globalObjectName"),
                    userAgentProduct = nativeMode.getString("userAgentProduct"),
                    securityBoundary = nativeMode.getBoolean("securityBoundary"),
                    trustedOriginOnly = documentStart.getBoolean("trustedOriginOnly"),
                    mainFrameOnly = documentStart.getBoolean("mainFrameOnly"),
                ),
                maxMessageBytes = limits.getInt("maxMessageBytes"),
                methods = json.getJSONArray("methods").toStringSet(),
                phase0Capabilities = json.getJSONArray("phase0Capabilities").toStringSet(),
                compiledCapabilities = json.getJSONArray("compiledCapabilities").toStringSet(),
                builtInCapabilities = json.getJSONArray("builtInCapabilities").toStringSet(),
                advertisedCapabilities = json.getJSONArray("advertisedCapabilities").toStringSet(),
                trustedProductionOrigin = URI(json.getString("trustedProductionOrigin")),
                maxJsonDepth = maxJsonDepth,
                maxJsonNodes = maxJsonNodes,
                rateLimits = json.optJSONObject("rateLimits").toRateLimitMap(),
            )
        }
    }
}

private fun org.json.JSONArray.toStringSet(): Set<String> = buildSet {
    for (index in 0 until length()) add(getString(index))
}

private fun org.json.JSONArray.toIntSet(): Set<Int> = buildSet {
    for (index in 0 until length()) add(getInt(index))
}

private fun JSONObject?.toRateLimitMap(): Map<String, RateLimitRule> {
    if (this == null) return emptyMap()
    return buildMap {
        val names = keys()
        while (names.hasNext()) {
            val method = names.next()
            val value = optJSONObject(method) ?: continue
            val rule = RateLimitRule(
                maxRequests = value.optInt("maxRequests", -1),
                windowSeconds = value.optInt("windowSeconds", -1),
            )
            if (rule.isSafe) put(method, rule)
        }
    }
}
