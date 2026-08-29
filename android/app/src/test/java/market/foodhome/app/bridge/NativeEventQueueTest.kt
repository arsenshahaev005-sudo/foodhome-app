package market.foodhome.app.bridge

import market.foodhome.app.payments.OpenPaymentRequest
import market.foodhome.app.payments.PaymentCoordinator
import market.foodhome.app.payments.PaymentFlow
import market.foodhome.app.payments.PaymentLaunchPolicy
import market.foodhome.app.payments.PaymentLauncher
import market.foodhome.app.payments.PaymentProviderRule
import market.foodhome.app.payments.PaymentRecoverySnapshot
import market.foodhome.app.payments.PaymentRecoveryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class NativeEventQueueTest {
    @Test
    fun `script is stable until ack and never claims payment status`() {
        val store = QueueStore()
        val coordinator = coordinator(store)
        coordinator.open(
            OpenPaymentRequest(
                "https://pay.example.invalid/checkout",
                "opaque-context",
                50_000,
                true,
            ),
        )
        coordinator.recordReturn("appResumed")
        val queue = NativeEventQueue(manifest(), URI("https://foodhome.market"), coordinator)

        val first = queue.pendingDispatchScript()
        val second = queue.pendingDispatchScript()

        assertEquals(first, second)
        assertTrue(first?.contains("foodhome:native-event") == true)
        assertTrue(first?.contains("payment-event-1") == true)
        assertFalse(first?.contains("paid") == true)
        coordinator.acknowledge("payment-event-1")
        assertNull(queue.pendingDispatchScript())
        assertTrue(store.current != null)
    }

    private fun coordinator(store: QueueStore) = PaymentCoordinator(
        policy = PaymentLaunchPolicy(
            listOf(PaymentProviderRule(PaymentFlow.CardAcquiring, "pay.example.invalid", "/checkout")),
        ),
        store = store,
        launcher = PaymentLauncher { true },
        nowMillis = { 10_000 },
        eventId = { "payment-event-1" },
    )

    private fun manifest() = BridgeManifest(
        protocol = "foodhome.bridge",
        contractVersion = "1.3.0",
        bridgeMajor = 1,
        supportedVersions = setOf(1),
        globalObjectName = "FoodHomeBridge",
        handshakeEventName = "foodhome:bridge-ready",
        nativeEventName = "foodhome:native-event",
        nativeMode = NativeModeContract(
            "foodhome.native-mode", 1, "FoodHomeNative", "FoodHomeNative/1", false, true, true,
        ),
        maxMessageBytes = 32_768,
        methods = setOf("openPayment", "ackNativeEvent"),
        phase0Capabilities = emptySet(),
        compiledCapabilities = emptySet(),
        builtInCapabilities = setOf("openPayment"),
        advertisedCapabilities = emptySet(),
    )
}

private class QueueStore : PaymentRecoveryStore {
    var current: PaymentRecoverySnapshot? = null
    override fun read(): PaymentRecoverySnapshot? = current
    override fun write(snapshot: PaymentRecoverySnapshot): Boolean {
        current = snapshot
        return true
    }
    override fun clear(): Boolean {
        current = null
        return true
    }
}
