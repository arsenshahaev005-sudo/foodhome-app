package market.foodhome.app.bridge

import org.json.JSONException
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.net.URI

sealed interface BridgeRequestResult {
    data class Accepted(val request: BridgeRequest) : BridgeRequestResult
    data class Rejected(val requestId: String?, val code: String) : BridgeRequestResult
}

data class BridgeRequest(
    val requestId: String,
    val method: String,
    val payload: JSONObject,
)

class BridgeRequestValidator(
    private val manifest: BridgeManifest,
    private val trustedOrigin: URI = manifest.trustedProductionOrigin,
) {
    private val requestIdPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$")

    fun validate(rawMessage: String): BridgeRequestResult {
        if (rawMessage.toByteArray(StandardCharsets.UTF_8).size > manifest.maxMessageBytes) {
            return BridgeRequestResult.Rejected(null, "PAYLOAD_TOO_LARGE")
        }

        val json = try {
            JSONObject(rawMessage)
        } catch (_: JSONException) {
            return BridgeRequestResult.Rejected(null, "INVALID_MESSAGE")
        }
        if (!isJsonStructureSafe(json)) {
            return BridgeRequestResult.Rejected(null, "INVALID_MESSAGE")
        }

        val requestId = json.optString("requestId").takeIf(requestIdPattern::matches)
        if (json.optString("protocol") != manifest.protocol) {
            return BridgeRequestResult.Rejected(requestId, "INVALID_MESSAGE")
        }
        if (json.optInt("version", -1) != manifest.bridgeMajor) {
            return BridgeRequestResult.Rejected(requestId, "VERSION_NOT_SUPPORTED")
        }
        val payload = json.optJSONObject("payload")
        if (requestId == null || payload == null) {
            return BridgeRequestResult.Rejected(requestId, "INVALID_MESSAGE")
        }

        val method = json.optString("method")
        if (method !in manifest.methods) {
            return BridgeRequestResult.Rejected(requestId, "METHOD_NOT_SUPPORTED")
        }
        if (!BridgePayloadPolicy(trustedOrigin, manifest.maxMessageBytes).accepts(method, payload)) {
            return BridgeRequestResult.Rejected(requestId, "INVALID_PAYLOAD")
        }
        return BridgeRequestResult.Accepted(BridgeRequest(requestId, method, payload))
    }

    private fun isJsonStructureSafe(root: JSONObject): Boolean {
        var visited = 0
        val pending = ArrayDeque<Pair<Any, Int>>()
        pending.addLast(root to 1)
        while (pending.isNotEmpty()) {
            val (value, depth) = pending.removeLast()
            visited += 1
            if (visited > manifest.maxJsonNodes || depth > manifest.maxJsonDepth) return false
            when (value) {
                is JSONObject -> {
                    val names = value.keys()
                    while (names.hasNext()) {
                        val name = names.next()
                        if (name in FORBIDDEN_JSON_KEYS) return false
                        pending.addLast(value.get(name) to depth + 1)
                    }
                }
                is JSONArray -> {
                    for (index in 0 until value.length()) {
                        pending.addLast(value.get(index) to depth + 1)
                    }
                }
            }
        }
        return true
    }

    private companion object {
        val FORBIDDEN_JSON_KEYS = setOf("__proto__", "constructor", "prototype")
    }
}
