package market.foodhome.app.payments

import java.util.UUID

fun interface PaymentLauncher {
    fun launch(destination: ValidatedPaymentDestination): Boolean
}

class PaymentCoordinator(
    private val policy: PaymentLaunchPolicy,
    private val store: PaymentRecoveryStore,
    private val launcher: PaymentLauncher,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val eventId: () -> String = { "payment-${UUID.randomUUID()}" },
) {
    @Synchronized
    fun open(request: OpenPaymentRequest): PaymentOperationResult {
        if (!request.userInitiated) return failure("PAYMENT_USER_ACTION_REQUIRED")
        if (!PaymentValuePolicy.acceptsRecoveryContext(request.recoveryContext)) {
            return failure("INVALID_PAYLOAD")
        }
        val currentTime = nowMillis()
        val existing = activeSnapshot(currentTime)
        if (existing != null) {
            return if (existing.recoveryContext == request.recoveryContext &&
                existing.state == PaymentRecoveryState.Presented
            ) {
                PaymentOperationResult.Presented(existing.flow)
            } else {
                failure("PAYMENT_IN_PROGRESS")
            }
        }
        val destination = policy.classify(request.rawUrl)
            ?: return failure("PAYMENT_URL_NOT_ALLOWED")
        val localExpiry = saturatingAdd(currentTime, PaymentRecoverySnapshot.LOCAL_MAX_LIFETIME_MILLIS)
        val effectiveExpiry = minOf(request.serverExpiresAtEpochMillis, localExpiry)
        if (effectiveExpiry <= currentTime) return failure("PAYMENT_RECOVERY_EXPIRED")

        val prepared = PaymentRecoverySnapshot(
            recoveryContext = request.recoveryContext,
            flow = destination.flow,
            state = PaymentRecoveryState.Prepared,
            createdAtEpochMillis = currentTime,
            expiresAtEpochMillis = effectiveExpiry,
        )
        if (!store.write(prepared)) return failure("PAYMENT_RECOVERY_FAILED")

        val didLaunch = launcher.launch(destination)
        val finalState = prepared.copy(
            state = if (didLaunch) PaymentRecoveryState.Presented else PaymentRecoveryState.LaunchFailed,
        )
        if (!store.write(finalState)) return failure("PAYMENT_RECOVERY_FAILED")
        return if (didLaunch) {
            PaymentOperationResult.Presented(destination.flow)
        } else {
            failure("LAUNCH_FAILED")
        }
    }

    @Synchronized
    fun recordReturn(reason: String): PendingNativePaymentEvent? {
        if (reason !in PaymentValuePolicy.returnReasons) return null
        val currentTime = nowMillis()
        val snapshot = activeSnapshot(currentTime) ?: return null
        if (snapshot.state == PaymentRecoveryState.EventAcknowledged) return null
        if (snapshot.pendingEventId != null && snapshot.returnReason != null) {
            return snapshot.toPendingEvent()
        }
        val generatedId = eventId()
        if (!PaymentValuePolicy.acceptsEventId(generatedId)) return null
        val returned = snapshot.copy(
            state = PaymentRecoveryState.Returned,
            pendingEventId = generatedId,
            returnReason = reason,
            returnOccurredAtEpochMillis = currentTime,
        )
        if (!store.write(returned)) return null
        return returned.toPendingEvent()
    }

    @Synchronized
    fun pendingEvent(): PendingNativePaymentEvent? =
        activeSnapshot(nowMillis())?.toPendingEvent()

    @Synchronized
    fun hasActiveRecovery(): Boolean = activeSnapshot(nowMillis()) != null

    @Synchronized
    fun acknowledge(eventId: String): PaymentOperationResult {
        if (!PaymentValuePolicy.acceptsEventId(eventId)) return failure("INVALID_PAYLOAD")
        val snapshot = activeSnapshot(nowMillis()) ?: return failure("EVENT_NOT_FOUND")
        if (snapshot.acknowledgedEventId == eventId) {
            return PaymentOperationResult.Acknowledged(eventId)
        }
        if (snapshot.pendingEventId != eventId) return failure("EVENT_NOT_FOUND")
        val acknowledged = snapshot.copy(
            state = PaymentRecoveryState.EventAcknowledged,
            pendingEventId = null,
            returnReason = null,
            returnOccurredAtEpochMillis = null,
            acknowledgedEventId = eventId,
        )
        if (!store.write(acknowledged)) return failure("PAYMENT_RECOVERY_FAILED")
        return PaymentOperationResult.Acknowledged(eventId)
    }

    @Synchronized
    fun clear(recoveryContext: String, reason: String): PaymentOperationResult {
        if (!PaymentValuePolicy.acceptsRecoveryContext(recoveryContext) ||
            reason !in PaymentValuePolicy.clearReasons
        ) {
            return failure("INVALID_PAYLOAD")
        }
        val snapshot = store.read() ?: return failure("PAYMENT_RECOVERY_NOT_FOUND")
        if (snapshot.recoveryContext != recoveryContext) {
            return failure("PAYMENT_RECOVERY_NOT_FOUND")
        }
        return if (store.clear()) PaymentOperationResult.Cleared
        else failure("PAYMENT_RECOVERY_FAILED")
    }

    private fun activeSnapshot(now: Long): PaymentRecoverySnapshot? {
        val snapshot = store.read() ?: return null
        if (snapshot.isExpired(now)) {
            store.clear()
            return null
        }
        return snapshot
    }

    private fun PaymentRecoverySnapshot.toPendingEvent(): PendingNativePaymentEvent? {
        val id = pendingEventId ?: return null
        val reason = returnReason ?: return null
        val occurredAt = returnOccurredAtEpochMillis ?: return null
        return PendingNativePaymentEvent(id, recoveryContext, reason, occurredAt)
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

    private fun failure(code: String) = PaymentOperationResult.Failure(code)
}
