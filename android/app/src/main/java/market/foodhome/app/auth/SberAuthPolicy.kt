package market.foodhome.app.auth

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Narrow OAuth launch/return policy. It never grants a website session. */
class SberAuthPolicy(private val trustedOrigin: URI) {
    fun acceptsAuthorization(attemptId: String, rawUrl: String): Boolean {
        if (!UUID.matches(attemptId) || rawUrl.length > 2048) return false
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.host != "id.sber.ru" || uri.port !in setOf(-1, 443) ||
            uri.userInfo != null || uri.rawPath != "/CSAFront/oidc/authorize.do" || uri.fragment != null) return false
        val query = parameters(uri.rawQuery) ?: return false
        if (query.keys != setOf("response_type", "client_id", "redirect_uri", "scope", "state", "nonce", "login_hint", "code_challenge", "code_challenge_method")) return false
        return query["response_type"] == "code" &&
            query["redirect_uri"] == "$trustedOrigin/auth/sber/callback" &&
            query["scope"] == "openid mobile" &&
            query["client_id"]?.length in 1..128 &&
            query["state"]?.matches(Regex("[A-Za-z0-9_-]{40,96}")) == true &&
            query["nonce"]?.matches(Regex("[A-Za-z0-9_-]{40,96}")) == true &&
            query["login_hint"]?.matches(Regex("7[0-9]{10}")) == true &&
            query["code_challenge"]?.matches(Regex("[A-Za-z0-9_-]{43}")) == true &&
            query["code_challenge_method"] == "S256"
    }

    fun returnAttempt(rawUrl: String): String? {
        if (rawUrl.length > 512) return null
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != trustedOrigin.host || uri.port != trustedOrigin.port ||
            uri.userInfo != null || uri.rawPath != "/auth/sber/app-return" || uri.rawQuery != null) return null
        val params = parameters(uri.rawFragment) ?: return null
        val attempt = params["attempt_id"] ?: return null
        return attempt.takeIf { params.keys == setOf("attempt_id", "ticket") && UUID.matches(it) &&
            params["ticket"]?.matches(Regex("[A-Za-z0-9_-]{40,100}")) == true }
    }

    private fun parameters(raw: String?): Map<String, String>? = runCatching {
        val pairs = (raw ?: return null).split("&").map { part ->
            val keyValue = part.split("=", limit = 2)
            require(keyValue.size == 2)
            URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name()) to
                URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name())
        }
        require(pairs.size == pairs.map { it.first }.toSet().size)
        pairs.toMap()
    }.getOrNull()

    private companion object { val UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") }
}
