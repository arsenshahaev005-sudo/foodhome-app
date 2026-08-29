package market.foodhome.app.config

import java.net.URI

data class AppEnvironment(
    val baseUrl: URI,
    val trustedOrigin: URI,
)

object AppEnvironmentResolver {
    val productionOrigin: URI = URI("https://foodhome.market")
    private val localDebugHosts = setOf("localhost", "127.0.0.1", "::1")

    fun resolve(isDebug: Boolean, debugBaseUrl: String?): AppEnvironment {
        if (!isDebug || debugBaseUrl.isNullOrBlank()) return production()

        val candidate = runCatching { URI(debugBaseUrl.trim()).normalize() }.getOrNull()
            ?: return production()
        val host = candidate.host?.lowercase() ?: return production()
        val port = candidate.port
        val hasOnlyOriginPath = candidate.path.isNullOrEmpty() || candidate.path == "/"
        val isAllowed = candidate.scheme.equals("https", ignoreCase = true) &&
            host in localDebugHosts &&
            candidate.userInfo == null &&
            candidate.query == null &&
            candidate.fragment == null &&
            hasOnlyOriginPath &&
            (port == -1 || port in 1..65535)

        if (!isAllowed) return production()

        val origin = URI("https", null, host, port, null, null, null)
        return AppEnvironment(baseUrl = URI("${origin}/"), trustedOrigin = origin)
    }

    private fun production() = AppEnvironment(
        baseUrl = URI("https://foodhome.market/"),
        trustedOrigin = productionOrigin,
    )
}
