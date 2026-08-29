package market.foodhome.app.bridge

import java.net.URI

class BridgeOriginPolicy(
    private val trustedOrigin: URI,
) {
    fun accepts(sourceOrigin: String?, isMainFrame: Boolean): Boolean {
        if (!isMainFrame || sourceOrigin.isNullOrBlank()) return false
        val source = runCatching { URI(sourceOrigin) }.getOrNull() ?: return false
        val pathIsOriginOnly = source.path.isNullOrEmpty() || source.path == "/"
        return source.scheme.equals("https", ignoreCase = true) &&
            source.scheme.equals(trustedOrigin.scheme, ignoreCase = true) &&
            source.host?.equals(trustedOrigin.host, ignoreCase = true) == true &&
            effectivePort(source) == effectivePort(trustedOrigin) &&
            source.userInfo == null &&
            source.query == null &&
            source.fragment == null &&
            pathIsOriginOnly
    }

    private fun effectivePort(uri: URI): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> -1
    }
}
