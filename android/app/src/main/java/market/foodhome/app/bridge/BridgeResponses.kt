package market.foodhome.app.bridge

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

object BridgeResponses {
    fun success(manifest: BridgeManifest, requestId: String, result: JSONObject): String = JSONObject()
        .put("protocol", manifest.protocol)
        .put("version", manifest.bridgeMajor)
        .put("requestId", requestId)
        .put("ok", true)
        .put("result", result)
        .toString()

    fun error(
        manifest: BridgeManifest,
        requestId: String,
        code: String,
        message: String,
        retryable: Boolean = false,
    ): String = JSONObject()
        .put("protocol", manifest.protocol)
        .put("version", manifest.bridgeMajor)
        .put("requestId", requestId)
        .put("ok", false)
        .put(
            "error",
            JSONObject()
                .put("code", code)
                .put("message", message.take(240))
                .put("retryable", retryable),
        )
        .toString()

    fun unavailable(manifest: BridgeManifest, requestId: String): String = JSONObject()
        .put("protocol", manifest.protocol)
        .put("version", manifest.bridgeMajor)
        .put("requestId", requestId)
        .put("ok", false)
        .put(
            "error",
            JSONObject()
                .put("code", "CAPABILITY_UNAVAILABLE")
                .put("message", "Capability is unavailable")
                .put("retryable", false),
        )
        .toString()

    fun rejected(manifest: BridgeManifest, requestId: String, code: String): String = JSONObject()
        .put("protocol", manifest.protocol)
        .put("version", manifest.bridgeMajor)
        .put("requestId", requestId)
        .put("ok", false)
        .put(
            "error",
            JSONObject()
                .put("code", code)
                .put("message", "Bridge request was rejected")
                .put("retryable", false),
        )
        .toString()
}

class TerminalReply {
    private val completed = AtomicBoolean(false)

    fun complete(block: () -> Unit): Boolean {
        if (!completed.compareAndSet(false, true)) return false
        block()
        return true
    }
}

object BridgeHandshakeScript {
    fun create(
        manifest: BridgeManifest,
        appVersion: String,
        buildNumber: String,
        platform: String,
    ): String {
        val detail = JSONObject()
            .put("protocol", manifest.protocol)
            .put("selectedVersion", manifest.bridgeMajor)
            .put("supportedVersions", JSONArray().put(manifest.bridgeMajor))
            .put("appVersion", appVersion)
            .put("buildNumber", buildNumber)
            .put("platform", platform)
            .put("builtInCapabilities", JSONArray(manifest.builtInCapabilities.sorted()))
            .put("capabilities", JSONArray(manifest.advertisedCapabilities.sorted()))
        return "window.dispatchEvent(new CustomEvent('${manifest.handshakeEventName}',{detail:${detail}}));"
    }
}
