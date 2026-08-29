package market.foodhome.app.navigation

import java.net.URI
import java.nio.charset.StandardCharsets

sealed interface NavigationDecision {
    data class Internal(val uri: URI) : NavigationDecision
    data class External(val uri: URI) : NavigationDecision
    data class Blocked(val reason: BlockReason) : NavigationDecision
}

enum class BlockReason {
    EMPTY,
    TOO_LONG,
    MALFORMED,
    FORBIDDEN_SCHEME,
    USER_INFO,
    INVALID_HOST,
    UNEXPECTED_PORT,
    NESTED_URL,
}

class NavigationPolicy(
    private val trustedOrigin: URI,
    private val maxUrlLength: Int = 2048,
) {
    fun classify(rawUrl: String?): NavigationDecision {
        val raw = rawUrl.orEmpty()
        if (raw.isEmpty()) return NavigationDecision.Blocked(BlockReason.EMPTY)
        if (raw.toByteArray(StandardCharsets.UTF_8).size > maxUrlLength) {
            return NavigationDecision.Blocked(BlockReason.TOO_LONG)
        }
        if (raw != raw.trim() || raw.any(::isAmbiguousCharacter)) {
            return NavigationDecision.Blocked(BlockReason.MALFORMED)
        }

        val uri = runCatching { URI(raw).normalize() }.getOrNull()
            ?: return NavigationDecision.Blocked(BlockReason.MALFORMED)
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return NavigationDecision.Blocked(BlockReason.FORBIDDEN_SCHEME)
        }
        if (uri.userInfo != null) return NavigationDecision.Blocked(BlockReason.USER_INFO)

        val host = uri.host?.lowercase()
            ?: return NavigationDecision.Blocked(BlockReason.INVALID_HOST)
        if (!isUnambiguousHost(host)) {
            return NavigationDecision.Blocked(BlockReason.INVALID_HOST)
        }
        if (containsNestedUrl(uri.rawQuery) || containsNestedUrl(uri.rawFragment)) {
            return NavigationDecision.Blocked(BlockReason.NESTED_URL)
        }

        val trustedHost = trustedOrigin.host.lowercase()
        val port = effectivePort(uri)
        val trustedPort = effectivePort(trustedOrigin)
        if (host == trustedHost && port != trustedPort) {
            return NavigationDecision.Blocked(BlockReason.UNEXPECTED_PORT)
        }

        return if (
            uri.scheme.equals(trustedOrigin.scheme, ignoreCase = true) &&
            host == trustedHost &&
            port == trustedPort
        ) {
            NavigationDecision.Internal(uri)
        } else {
            NavigationDecision.External(uri)
        }
    }

    private fun containsNestedUrl(rawQuery: String?): Boolean {
        var decoded = rawQuery?.takeIf(String::isNotEmpty) ?: return false
        repeat(MAX_DECODE_PASSES + 1) { pass ->
            if (containsForbiddenDestination(decoded)) return true
            if (pass == MAX_DECODE_PASSES) return false
            decoded = percentDecodeOnce(decoded) ?: return true
        }
        return true
    }

    private fun containsForbiddenDestination(value: String): Boolean {
        val normalized = value.lowercase()
        return FORBIDDEN_NESTED_SCHEMES.any(normalized::contains)
    }

    private fun percentDecodeOnce(value: String): String? {
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] != '%') {
                result.append(value[index])
                index += 1
                continue
            }
            if (index + 2 >= value.length) return null
            val high = value[index + 1].digitToIntOrNull(16) ?: return null
            val low = value[index + 2].digitToIntOrNull(16) ?: return null
            result.append(((high shl 4) or low).toChar())
            index += 3
        }
        return result.toString()
    }

    private fun isAmbiguousCharacter(value: Char): Boolean =
        value.isWhitespace() || value.isISOControl() || value == '\\' || value in BIDI_CONTROLS

    private fun isUnambiguousHost(host: String): Boolean =
        !host.endsWith('.') && host.all { it.code in 0x21..0x7E }

    private fun effectivePort(uri: URI): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> -1
    }

    private companion object {
        const val MAX_DECODE_PASSES = 4
        val BIDI_CONTROLS = setOf(
            '\u200E', '\u200F',
            '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
            '\u2066', '\u2067', '\u2068', '\u2069',
        )
        val FORBIDDEN_NESTED_SCHEMES = listOf(
            "http://",
            "https://",
            "javascript:",
            "data:",
            "file:",
            "intent:",
        )
    }
}
