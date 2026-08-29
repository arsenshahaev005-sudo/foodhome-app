package market.foodhome.app.payments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentRecoveryStoreTest {
    @Test
    fun `codec round trips only opaque recovery fields`() {
        val snapshot = PaymentRecoverySnapshot(
            recoveryContext = "opaque-context-123",
            flow = PaymentFlow.SbpCyclops,
            state = PaymentRecoveryState.Returned,
            createdAtEpochMillis = 1_000,
            expiresAtEpochMillis = 2_000,
            pendingEventId = "payment-event-1",
            returnReason = "appResumed",
            returnOccurredAtEpochMillis = 1_500,
        )

        val encoded = PaymentRecoveryCodec.encode(snapshot)

        assertEquals(snapshot, PaymentRecoveryCodec.decode(encoded))
        for (forbidden in listOf("url", "query", "amount", "currency", "orderId", "userId", "token")) {
            assertFalse(encoded.contains(forbidden, ignoreCase = true))
        }
    }

    @Test
    fun `codec rejects corrupt or unsupported records`() {
        assertNull(PaymentRecoveryCodec.decode("not-json"))
        assertNull(
            PaymentRecoveryCodec.decode(
                """{"schemaVersion":2,"recoveryContext":"ctx","flow":"cardAcquiring","state":"prepared","createdAtEpochMillis":1,"expiresAtEpochMillis":2}""",
            ),
        )
        assertTrue(PaymentRecoveryCodec.decode(PaymentRecoveryCodec.encode(validSnapshot())) != null)
    }

    private fun validSnapshot() = PaymentRecoverySnapshot(
        recoveryContext = "ctx",
        flow = PaymentFlow.CardAcquiring,
        state = PaymentRecoveryState.Prepared,
        createdAtEpochMillis = 1,
        expiresAtEpochMillis = 2,
    )
}
