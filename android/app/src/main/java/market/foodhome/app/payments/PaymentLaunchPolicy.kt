package market.foodhome.app.payments

import java.net.URI
import java.nio.charset.StandardCharsets

data class PaymentProviderRule(
    val flow: PaymentFlow,
    val exactHost: String,
    val pathPrefix: String,
    val allowedQueryKeys: Set<String> = emptySet(),
)

data class ValidatedPaymentDestination(
    val uri: URI,
    val flow: PaymentFlow,
)

class PaymentLaunchPolicy(
    rules: Collection<PaymentProviderRule>,
    private val maxUrlLength: Int = 2_048,
) {
    private val rules = rules.toList()

    fun classify(rawUrl: String): ValidatedPaymentDestination? {
        if (
            rawUrl.isEmpty() ||
            rawUrl != rawUrl.trim() ||
            rawUrl.toByteArray(StandardCharsets.UTF_8).size > maxUrlLength ||
            rawUrl.any(::isAmbiguousCharacter)
        ) return null
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        if (!uri.isAbsolute || uri.isOpaque || !uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.userInfo != null || uri.host == null || uri.port !in setOf(-1, 443)) return null
        if (uri.rawFragment != null) return null

        val normalizedHost = uri.host.lowercase()
        if (!isUnambiguousHost(normalizedHost)) return null
        val rawPath = uri.rawPath?.takeIf(String::isNotEmpty) ?: "/"
        if (!isUnambiguousPath(rawPath)) return null
        val queryKeys = queryKeys(uri.rawQuery) ?: return null
        val rule = rules.firstOrNull { candidate ->
            normalizedHost == candidate.exactHost.lowercase() &&
                pathMatches(rawPath, candidate.pathPrefix) &&
                queryKeys.all(candidate.allowedQueryKeys::contains)
        } ?: return null
        return ValidatedPaymentDestination(uri.normalize(), rule.flow)
    }

    private fun pathMatches(rawPath: String, prefix: String): Boolean {
        if (!rawPath.startsWith("/") || !prefix.startsWith("/")) return false
        val normalizedPrefix = prefix.trimEnd('/').ifEmpty { "/" }
        return rawPath == normalizedPrefix ||
            normalizedPrefix == "/" ||
            rawPath.startsWith("$normalizedPrefix/")
    }

    private fun queryKeys(rawQuery: String?): Set<String>? {
        if (rawQuery == null) return emptySet()
        if (rawQuery.isEmpty()) return emptySet()
        val keys = mutableSetOf<String>()
        for (part in rawQuery.split('&')) {
            val key = part.substringBefore('=')
            if (key.isEmpty() || '%' in key || !key.all { it.isLetterOrDigit() || it in "_-" }) {
                return null
            }
            val rawValue = part.substringAfter('=', missingDelimiterValue = "")
            if (containsNestedDestination(rawValue)) return null
            keys += key
        }
        return keys
    }

    private fun containsNestedDestination(rawValue: String): Boolean {
        var decoded = rawValue
        repeat(MAX_DECODE_PASSES + 1) { pass ->
            val normalized = decoded.lowercase()
            if (FORBIDDEN_NESTED_SCHEMES.any(normalized::contains)) return true
            if (pass == MAX_DECODE_PASSES) return false
            decoded = percentDecodeOnce(decoded) ?: return true
        }
        return true
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

    private fun isUnambiguousPath(path: String): Boolean {
        val normalized = path.lowercase()
        if (ENCODED_PATH_SEPARATORS.any(normalized::contains)) return false
        if ("//" in path || '\\' in path) return false
        return path.split('/').none { it == "." || it == ".." }
    }

    companion object {
        /** Provider trust is deliberately empty until the real Tochka mobile PoC is approved. */
        fun production(): PaymentLaunchPolicy = PaymentLaunchPolicy(emptyList())

        private const val MAX_DECODE_PASSES = 4
        private val BIDI_CONTROLS = setOf(
            '\u200E', '\u200F',
            '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
            '\u2066', '\u2067', '\u2068', '\u2069',
        )
        private val FORBIDDEN_NESTED_SCHEMES = listOf(
            "http://", "https://", "javascript:", "data:", "file:", "intent:",
        )
        private val ENCODED_PATH_SEPARATORS = listOf("%2f", "%5c", "%2e")
    }
}
