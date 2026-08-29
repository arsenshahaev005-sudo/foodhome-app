package market.foodhome.app.payments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentLaunchPolicyTest {
    private val policy = PaymentLaunchPolicy(
        listOf(
            PaymentProviderRule(
                flow = PaymentFlow.CardAcquiring,
                exactHost = "pay.example.invalid",
                pathPrefix = "/checkout",
                allowedQueryKeys = setOf("session"),
            ),
            PaymentProviderRule(
                flow = PaymentFlow.SbpCyclops,
                exactHost = "sbp.example.invalid",
                pathPrefix = "/qr",
            ),
        ),
    )

    @Test
    fun `accepts only exact injected https destination`() {
        val destination = policy.classify("https://pay.example.invalid:443/checkout/start?session=opaque")

        assertNotNull(destination)
        assertEquals(PaymentFlow.CardAcquiring, destination?.flow)
    }

    @Test
    fun `rejects host port user info and path mismatches`() {
        assertNull(policy.classify("https://child.pay.example.invalid/checkout"))
        assertNull(policy.classify("https://pay.example.invalid:444/checkout"))
        assertNull(policy.classify("https://user@pay.example.invalid/checkout"))
        assertNull(policy.classify("https://pay.example.invalid/checkout-evil"))
        assertNull(policy.classify("https://unknown.example.invalid/checkout"))
    }

    @Test
    fun `rejects malformed encoded and overlong values without whole-url decoding`() {
        assertNull(policy.classify("https%3A%2F%2Fpay.example.invalid%2Fcheckout"))
        assertNull(policy.classify("https://pay.example.invalid/checkout?%73ession=opaque"))
        assertNull(policy.classify("https://pay.example.invalid/checkout\n"))
        assertNull(policy.classify("https://pay.example.invalid/checkout/${"x".repeat(2_100)}"))
    }

    @Test
    fun `rejects unexpected query keys and fragments`() {
        assertNull(policy.classify("https://pay.example.invalid/checkout?redirect=https://evil.invalid"))
        assertNull(policy.classify("https://pay.example.invalid/checkout?session=https%253A%252F%252Fevil.invalid"))
        assertNull(policy.classify("https://pay.example.invalid/checkout#return"))
        assertNotNull(policy.classify("https://sbp.example.invalid/qr"))
    }

    @Test
    fun `rejects ambiguous host path and unicode forms`() {
        for (url in listOf(
            " https://pay.example.invalid/checkout",
            "https://pay.example.invalid./checkout",
            "https://pay.example.invalid\\@evil.invalid/checkout",
            "https://pay.example.invalid/checkout/%2Fescape",
            "https://pay.example.invalid/checkout/%2e%2e/escape",
            "https://pay.example.invalid/checkout//escape",
            "https://pay.example.invalid/checkout/\u202Eevil",
        )) {
            assertNull(policy.classify(url))
        }
    }

    @Test
    fun `production policy trusts no provisional provider destination`() {
        assertNull(PaymentLaunchPolicy.production().classify("https://pay.example.invalid/checkout"))
    }
}
