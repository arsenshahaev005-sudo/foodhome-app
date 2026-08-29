package market.foodhome.app.payments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentCoordinatorTest {
    private var now = 10_000L
    private val store = RecordingStore()
    private var launchCount = 0
    private val coordinator = PaymentCoordinator(
        policy = testPolicy(),
        store = store,
        launcher = PaymentLauncher {
            launchCount += 1
            assertTrue(store.writes.isNotEmpty())
            true
        },
        nowMillis = { now },
        eventId = { "payment-event-1" },
    )

    @Test
    fun `persists before launch and caps recovery at twenty four hours`() {
        val result = coordinator.open(request(serverExpiry = Long.MAX_VALUE))

        assertEquals(PaymentOperationResult.Presented(PaymentFlow.CardAcquiring), result)
        assertEquals(1, launchCount)
        assertEquals(2, store.writes.size)
        assertEquals(
            now + PaymentRecoverySnapshot.LOCAL_MAX_LIFETIME_MILLIS,
            store.current?.expiresAtEpochMillis,
        )
    }

    @Test
    fun `same presented context is idempotent and another context remains blocked`() {
        coordinator.open(request())

        assertEquals(PaymentOperationResult.Presented(PaymentFlow.CardAcquiring), coordinator.open(request()))
        assertEquals(
            PaymentOperationResult.Failure("PAYMENT_IN_PROGRESS"),
            coordinator.open(request(context = "another-context")),
        )
        assertEquals(1, launchCount)
    }

    @Test
    fun `restart and app resume create event without relaunching`() {
        coordinator.open(request())
        val restarted = PaymentCoordinator(
            policy = testPolicy(),
            store = store,
            launcher = PaymentLauncher { launchCount += 1; true },
            nowMillis = { now },
            eventId = { "payment-event-1" },
        )

        val first = restarted.recordReturn("coldStart")
        val repeated = restarted.recordReturn("appResumed")

        assertEquals(first, repeated)
        assertEquals("coldStart", repeated?.reason)
        assertEquals(1, launchCount)
    }

    @Test
    fun `javascript delivery alone does not delete event and ack does not clear recovery`() {
        coordinator.open(request())
        val event = coordinator.recordReturn("appResumed")

        assertEquals(event, coordinator.pendingEvent())
        assertEquals(event, coordinator.pendingEvent())
        assertEquals(
            PaymentOperationResult.Acknowledged("payment-event-1"),
            coordinator.acknowledge("payment-event-1"),
        )
        assertNull(coordinator.pendingEvent())
        assertTrue(store.current != null)
        assertEquals(PaymentRecoveryState.EventAcknowledged, store.current?.state)
        assertEquals(
            PaymentOperationResult.Acknowledged("payment-event-1"),
            coordinator.acknowledge("payment-event-1"),
        )
        assertNull(coordinator.recordReturn("appResumed"))
    }

    @Test
    fun `unknown ack fails and explicit matching clear removes recovery`() {
        coordinator.open(request())
        coordinator.recordReturn("appResumed")

        assertEquals(
            PaymentOperationResult.Failure("EVENT_NOT_FOUND"),
            coordinator.acknowledge("another-event"),
        )
        assertEquals(
            PaymentOperationResult.Failure("PAYMENT_RECOVERY_NOT_FOUND"),
            coordinator.clear("another-context", "terminal"),
        )
        assertEquals(PaymentOperationResult.Cleared, coordinator.clear("opaque-context", "terminal"))
        assertNull(store.current)
    }

    @Test
    fun `expired state is removed and does not launch automatically`() {
        coordinator.open(request(serverExpiry = now + 5))
        now += 5

        assertNull(coordinator.pendingEvent())
        assertNull(store.current)
        assertEquals(1, launchCount)
    }

    @Test
    fun `launch failure stays recoverable and is never automatically retried`() {
        val failingStore = RecordingStore()
        var attempts = 0
        val failing = PaymentCoordinator(
            policy = testPolicy(),
            store = failingStore,
            launcher = PaymentLauncher { attempts += 1; false },
            nowMillis = { now },
        )

        assertEquals(PaymentOperationResult.Failure("LAUNCH_FAILED"), failing.open(request()))
        assertEquals(PaymentRecoveryState.LaunchFailed, failingStore.current?.state)
        assertEquals(PaymentOperationResult.Failure("PAYMENT_IN_PROGRESS"), failing.open(request()))
        assertEquals(1, attempts)
    }

    @Test
    fun `open payment requires an explicit current user action`() {
        assertEquals(
            PaymentOperationResult.Failure("PAYMENT_USER_ACTION_REQUIRED"),
            coordinator.open(request(userInitiated = false)),
        )
        assertFalse(store.writes.isNotEmpty())
        assertEquals(0, launchCount)
    }

    private fun request(
        context: String = "opaque-context",
        serverExpiry: Long = now + 60_000,
        userInitiated: Boolean = true,
    ) = OpenPaymentRequest(
        rawUrl = "https://pay.example.invalid/checkout?session=opaque",
        recoveryContext = context,
        serverExpiresAtEpochMillis = serverExpiry,
        userInitiated = userInitiated,
    )

    private fun testPolicy() = PaymentLaunchPolicy(
        listOf(
            PaymentProviderRule(
                flow = PaymentFlow.CardAcquiring,
                exactHost = "pay.example.invalid",
                pathPrefix = "/checkout",
                allowedQueryKeys = setOf("session"),
            ),
        ),
    )
}

private class RecordingStore : PaymentRecoveryStore {
    var current: PaymentRecoverySnapshot? = null
    val writes = mutableListOf<PaymentRecoverySnapshot>()

    override fun read(): PaymentRecoverySnapshot? = current

    override fun write(snapshot: PaymentRecoverySnapshot): Boolean {
        writes += snapshot
        current = snapshot
        return true
    }

    override fun clear(): Boolean {
        current = null
        return true
    }
}
