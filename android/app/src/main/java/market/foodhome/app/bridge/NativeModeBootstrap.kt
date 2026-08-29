package market.foodhome.app.bridge

import org.json.JSONObject
import java.net.URI

object NativeModeBootstrap {
    fun userAgent(existing: String, contract: NativeModeContract): String {
        if (!contract.isSafeBootstrap()) return existing
        val tokens = existing.split(' ').filter(String::isNotBlank)
        if (contract.userAgentProduct in tokens) return existing
        return (tokens + contract.userAgentProduct).joinToString(" ")
    }

    fun documentStartScript(
        contract: NativeModeContract,
        trustedOrigin: URI,
        platform: String,
    ): String {
        if (!contract.isSafeBootstrap() || !trustedOrigin.isExactHttpsOrigin()) return ""

        val originLiteral = JSONObject.quote(trustedOrigin.toASCIIString())
        val globalLiteral = JSONObject.quote(contract.globalObjectName)
        val marker = JSONObject()
            .put("protocol", contract.protocol)
            .put("version", contract.version)
            .put("platform", platform)
            .toString()

        return """
            (() => {
              if (window !== window.top || window.location.origin !== $originLiteral) return;
              if (Object.prototype.hasOwnProperty.call(window, $globalLiteral)) return;
              Object.defineProperty(window, $globalLiteral, {
                value: Object.freeze($marker),
                writable: false,
                enumerable: false,
                configurable: false
              });
            })();
        """.trimIndent()
    }
}

private fun NativeModeContract.isSafeBootstrap(): Boolean =
    protocol == "foodhome.native-mode" &&
        version == 1 &&
        globalObjectName.isNotBlank() &&
        userAgentProduct.isNotBlank() &&
        userAgentProduct.none(Char::isWhitespace) &&
        !securityBoundary &&
        trustedOriginOnly &&
        mainFrameOnly

private fun URI.isExactHttpsOrigin(): Boolean =
    scheme.equals("https", ignoreCase = true) &&
        host != null &&
        userInfo == null &&
        query == null &&
        fragment == null &&
        (path.isNullOrEmpty() || path == "/")
