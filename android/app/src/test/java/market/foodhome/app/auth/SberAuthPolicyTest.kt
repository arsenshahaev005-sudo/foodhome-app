package market.foodhome.app.auth

import java.net.URI
import java.net.URLEncoder
import org.junit.Assert.*
import org.junit.Test
import market.foodhome.app.config.AppEnvironmentResolver

class SberAuthPolicyTest {
    private val id = "12345678-1234-1234-1234-123456789abc"
    private val origin = "https://staging.foodhome.market"
    private val policy = SberAuthPolicy(URI(origin))
    private val params = linkedMapOf(
        "response_type" to "code", "client_id" to "synthetic-client",
        "redirect_uri" to "$origin/auth/sber/callback", "scope" to "openid mobile",
        "state" to "s".repeat(43), "nonce" to "n".repeat(64),
        "login_hint" to "79991234567", "code_challenge" to "c".repeat(43), "code_challenge_method" to "S256",
    )
    private fun url(values: Map<String, String> = params) =
        "https://id.sber.ru/CSAFront/oidc/authorize.do?" + values.entries.joinToString("&") {
            it.key + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
    @Test fun `launch allows only the bank authorize endpoint and exact callback`() {
        assertTrue(policy.acceptsAuthorization(id, url()))
        assertFalse(policy.acceptsAuthorization(id, url().replace("id.sber.ru", "id.sber.ru.attacker.example")))
        assertFalse(policy.acceptsAuthorization(id, url(params + ("redirect_uri" to "https://foodhome.market/auth/sber/callback"))))
        assertFalse(policy.acceptsAuthorization(id, url(params + ("client_secret" to "must-not-cross-bridge"))))
        assertFalse(policy.acceptsAuthorization(id, url() + "&state=duplicate"))
        assertFalse(policy.acceptsAuthorization("invalid", url()))
        assertFalse(policy.acceptsAuthorization(id, url(params + ("code_challenge_method" to "plain"))))
    }
    @Test fun `return rejects query credentials wrong origin and extra fragment fields`() {
        val link = "$origin/auth/sber/app-return#attempt_id=$id&ticket=" + "t".repeat(43)
        assertEquals(id, policy.returnAttempt(link))
        assertNull(policy.returnAttempt(link.replace("staging.foodhome.market", "foodhome.market")))
        assertNull(policy.returnAttempt(link.replace("#", "?")))
        assertNull(policy.returnAttempt(link + "&next=/admin"))
        assertNull(policy.returnAttempt(link + "&ticket=duplicate"))
        assertNull(policy.returnAttempt(link.replace("/app-return", "/app-return/")))
    }
    @Test fun `staging environment never falls back to production`() {
        val env = AppEnvironmentResolver.resolve(true, "https://foodhome.market", isStaging = true)
        assertEquals(origin, env.trustedOrigin.toString())
        assertEquals("$origin/", env.baseUrl.toString())
        assertThrows(IllegalArgumentException::class.java) {
            AppEnvironmentResolver.resolve(false, null, isStaging = true)
        }
    }
}
