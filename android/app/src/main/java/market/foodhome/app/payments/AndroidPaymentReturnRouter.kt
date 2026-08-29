package market.foodhome.app.payments

import market.foodhome.app.telemetry.TelemetryEventName
import market.foodhome.app.telemetry.TelemetryReporter

class AndroidPaymentReturnRouter(
    private val coordinator: PaymentCoordinator,
    private val telemetry: TelemetryReporter? = null,
) {
    private var listener: (() -> Unit)? = null
    private var leftForeground = false

    fun attach(listener: () -> Unit): AutoCloseable {
        this.listener = listener
        if (coordinator.pendingEvent() != null) listener()
        return AutoCloseable {
            if (this.listener === listener) this.listener = null
        }
    }

    fun onColdStart() = record("coldStart")

    fun onStop() {
        leftForeground = true
    }

    fun onResume() {
        if (!leftForeground) return
        leftForeground = false
        record("appResumed")
    }

    fun onAppLink() = record("appLink")

    fun onWebViewRecovered() = record("webViewRecovered")

    private fun record(reason: String) {
        val hadActiveRecovery = coordinator.hasActiveRecovery()
        if (coordinator.recordReturn(reason) != null) {
            listener?.invoke()
        } else if (hadActiveRecovery) {
            telemetry?.record(
                TelemetryEventName.PaymentReturnFailed,
                attributes = mapOf("errorCode" to "PAYMENT_RETURN_RECORD_FAILED"),
            )
        }
    }
}
