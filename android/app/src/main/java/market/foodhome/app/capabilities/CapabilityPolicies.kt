package market.foodhome.app.capabilities

import java.net.URI

data class SharePayload(
    val title: String?,
    val text: String?,
    val url: URI,
)

class FoodHomeSharePolicy(
    private val trustedOrigin: URI,
    private val maxUrlLength: Int = 2_048,
) {
    fun parse(title: String?, text: String?, rawUrl: String?): SharePayload? {
        if (title != null && title.length > 120) return null
        if (text != null && text.length > 1_000) return null
        if (rawUrl.isNullOrBlank() || rawUrl.length > maxUrlLength) return null

        val candidate = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        if (!candidate.isAbsolute || candidate.isOpaque) return null
        if (!candidate.scheme.equals("https", ignoreCase = true)) return null
        if (!candidate.scheme.equals(trustedOrigin.scheme, ignoreCase = true)) return null
        if (candidate.host?.equals(trustedOrigin.host, ignoreCase = true) != true) return null
        if (effectivePort(candidate) != effectivePort(trustedOrigin)) return null
        if (candidate.userInfo != null) return null

        return SharePayload(title, text, candidate.normalize())
    }

    private fun effectivePort(uri: URI): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> -1
    }
}

object CapabilityPurposePolicy {
    fun accepts(value: String?): Boolean = value != null && value.isNotBlank() && value.length <= 160
}
