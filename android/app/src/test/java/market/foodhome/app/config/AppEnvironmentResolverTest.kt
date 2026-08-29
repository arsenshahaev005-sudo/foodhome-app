package market.foodhome.app.config

import org.junit.Assert.assertEquals
import org.junit.Test

class AppEnvironmentResolverTest {
    @Test
    fun `release ignores debug override`() {
        val environment = AppEnvironmentResolver.resolve(
            isDebug = false,
            debugBaseUrl = "https://localhost:8443",
        )

        assertEquals("https://foodhome.market/", environment.baseUrl.toString())
        assertEquals("https://foodhome.market", environment.trustedOrigin.toString())
    }

    @Test
    fun `debug accepts only local https origin`() {
        val local = AppEnvironmentResolver.resolve(true, "https://localhost:8443")
        val remote = AppEnvironmentResolver.resolve(true, "https://evil.example")
        val cleartext = AppEnvironmentResolver.resolve(true, "http://localhost:3000")

        assertEquals("https://localhost:8443", local.trustedOrigin.toString())
        assertEquals(AppEnvironmentResolver.productionOrigin, remote.trustedOrigin)
        assertEquals(AppEnvironmentResolver.productionOrigin, cleartext.trustedOrigin)
    }
}
