package market.foodhome.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class NavigationPolicyTest {
    private val policy = NavigationPolicy(URI("https://foodhome.market"))

    @Test
    fun `exact origin is internal`() {
        val decision = policy.classify("https://foodhome.market/orders/123")
        assertTrue(decision is NavigationDecision.Internal)
    }

    @Test
    fun `ordinary external https is external`() {
        val decision = policy.classify("https://example.com/help")
        assertTrue(decision is NavigationDecision.External)
    }

    @Test
    fun `subdomain and lookalike never become internal`() {
        assertTrue(policy.classify("https://cdn.foodhome.market/image") is NavigationDecision.External)
        assertTrue(policy.classify("https://foodhome.market.evil.example/") is NavigationDecision.External)
    }

    @Test
    fun `forbidden schemes fail closed`() {
        for (url in listOf(
            "javascript:alert(1)",
            "data:text/html,hello",
            "file:///tmp/secret",
            "intent://orders/123#Intent;scheme=foodhome;end",
        )) {
            assertEquals(
                BlockReason.FORBIDDEN_SCHEME,
                (policy.classify(url) as NavigationDecision.Blocked).reason,
            )
        }
    }

    @Test
    fun `userinfo and unexpected trusted host port fail closed`() {
        assertEquals(
            BlockReason.USER_INFO,
            (policy.classify("https://foodhome.market@evil.example/") as NavigationDecision.Blocked).reason,
        )
        assertEquals(
            BlockReason.UNEXPECTED_PORT,
            (policy.classify("https://foodhome.market:444/orders") as NavigationDecision.Blocked).reason,
        )
    }

    @Test
    fun `encoded nested destination fails closed`() {
        for (url in listOf(
            "https://foodhome.market/?next=https%253A%252F%252Fevil.example",
            "https://foodhome.market/?next=https%25253A%25252F%25252Fevil.example",
            "https://foodhome.market/#next=javascript%253Aalert(1)",
            "https://foodhome.market/?next=intent%253A%252F%252Fbank",
        )) {
            assertEquals(
                BlockReason.NESTED_URL,
                (policy.classify(url) as NavigationDecision.Blocked).reason,
            )
        }
    }

    @Test
    fun `ambiguous raw characters fail closed`() {
        for (url in listOf(
            " https://foodhome.market/orders",
            "https://foodhome.market/orders ",
            "https://foodhome.market\\@evil.example/orders",
            "https://foodhome.market/\u202Eevil",
            "https://foodhome.market/\u0000evil",
        )) {
            assertEquals(
                BlockReason.MALFORMED,
                (policy.classify(url) as NavigationDecision.Blocked).reason,
            )
        }
    }

    @Test
    fun `trailing-dot and unicode lookalikes never become internal`() {
        assertEquals(
            BlockReason.INVALID_HOST,
            (policy.classify("https://foodhome.market./orders") as NavigationDecision.Blocked).reason,
        )
        assertTrue(policy.classify("https://xn--foodhme-8fg.example/orders") is NavigationDecision.External)
    }

    @Test
    fun `case normalization preserves valid exact origin`() {
        assertTrue(policy.classify("HTTPS://FOODHOME.MARKET/orders/123") is NavigationDecision.Internal)
    }
}
