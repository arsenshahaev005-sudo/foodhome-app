package market.foodhome.app.payments

import org.json.JSONObject
import java.time.Instant

enum class PaymentFlow(val wireValue: String) {
    CardAcquiring("cardAcquiring"),
    SbpCyclops("sbpCyclops"),
    ;

    companion object {
        fun fromWireValue(value: String): PaymentFlow? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class PaymentRecoveryState(val wireValue: String) {
    Prepared("prepared"),
    Presented("presented"),
    LaunchFailed("launchFailed"),
    Returned("returned"),
    EventAcknowledged("eventAcknowledged"),
    ;

    companion object {
        fun fromWireValue(value: String): PaymentRecoveryState? =
            entries.firstOrNull { it.wireValue == value }
    }
}

data class PaymentRecoverySnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val recoveryContext: String,
    val flow: PaymentFlow,
    val state: PaymentRecoveryState,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val pendingEventId: String? = null,
    val returnReason: String? = null,
    val returnOccurredAtEpochMillis: Long? = null,
    val acknowledgedEventId: String? = null,
) {
    fun isExpired(nowEpochMillis: Long): Boolean = nowEpochMillis >= expiresAtEpochMillis

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val LOCAL_MAX_LIFETIME_MILLIS = 24L * 60L * 60L * 1_000L
    }
}

data class OpenPaymentRequest(
    val rawUrl: String,
    val recoveryContext: String,
    val serverExpiresAtEpochMillis: Long,
    val userInitiated: Boolean,
)

sealed interface PaymentOperationResult {
    data class Presented(val flow: PaymentFlow) : PaymentOperationResult
    data class Acknowledged(val eventId: String) : PaymentOperationResult
    data object Cleared : PaymentOperationResult
    data class Failure(val code: String, val retryable: Boolean = false) : PaymentOperationResult
}

data class PendingNativePaymentEvent(
    val eventId: String,
    val recoveryContext: String,
    val reason: String,
    val occurredAtEpochMillis: Long,
) {
    fun toBridgeJson(protocol: String, version: Int): JSONObject = JSONObject()
        .put("protocol", protocol)
        .put("version", version)
        .put("eventId", eventId)
        .put("name", "paymentReturned")
        .put(
            "payload",
            JSONObject()
                .put("recoveryContext", recoveryContext)
                .put("reason", reason),
        )
        .put("occurredAt", Instant.ofEpochMilli(occurredAtEpochMillis).toString())
}

internal object PaymentRecoveryCodec {
    fun encode(snapshot: PaymentRecoverySnapshot): String = JSONObject()
        .put("schemaVersion", snapshot.schemaVersion)
        .put("recoveryContext", snapshot.recoveryContext)
        .put("flow", snapshot.flow.wireValue)
        .put("state", snapshot.state.wireValue)
        .put("createdAtEpochMillis", snapshot.createdAtEpochMillis)
        .put("expiresAtEpochMillis", snapshot.expiresAtEpochMillis)
        .apply {
            snapshot.pendingEventId?.let { put("pendingEventId", it) }
            snapshot.returnReason?.let { put("returnReason", it) }
            snapshot.returnOccurredAtEpochMillis?.let { put("returnOccurredAtEpochMillis", it) }
            snapshot.acknowledgedEventId?.let { put("acknowledgedEventId", it) }
        }
        .toString()

    fun decode(raw: String): PaymentRecoverySnapshot? = runCatching {
        val value = JSONObject(raw)
        if (value.length() !in 6..10) return@runCatching null
        val schemaVersion = value.getInt("schemaVersion")
        if (schemaVersion != PaymentRecoverySnapshot.CURRENT_SCHEMA_VERSION) return@runCatching null
        val recoveryContext = value.getString("recoveryContext")
        if (!PaymentValuePolicy.acceptsRecoveryContext(recoveryContext)) return@runCatching null
        val flow = PaymentFlow.fromWireValue(value.getString("flow")) ?: return@runCatching null
        val state = PaymentRecoveryState.fromWireValue(value.getString("state")) ?: return@runCatching null
        val createdAt = value.getLong("createdAtEpochMillis")
        val expiresAt = value.getLong("expiresAtEpochMillis")
        if (createdAt < 0 || expiresAt <= createdAt) return@runCatching null
        val eventId = value.optString("pendingEventId").takeIf(String::isNotBlank)
        if (eventId != null && !PaymentValuePolicy.acceptsEventId(eventId)) return@runCatching null
        val reason = value.optString("returnReason").takeIf(String::isNotBlank)
        if (reason != null && reason !in PaymentValuePolicy.returnReasons) return@runCatching null
        val returnOccurredAt = if (value.has("returnOccurredAtEpochMillis")) {
            value.getLong("returnOccurredAtEpochMillis")
        } else {
            null
        }
        if ((eventId == null) != (reason == null) || (eventId == null) != (returnOccurredAt == null)) {
            return@runCatching null
        }
        if (returnOccurredAt != null && returnOccurredAt < createdAt) return@runCatching null
        val acknowledgedEventId = value.optString("acknowledgedEventId").takeIf(String::isNotBlank)
        if (acknowledgedEventId != null && !PaymentValuePolicy.acceptsEventId(acknowledgedEventId)) {
            return@runCatching null
        }
        if (eventId != null && acknowledgedEventId != null) return@runCatching null
        PaymentRecoverySnapshot(
            schemaVersion = schemaVersion,
            recoveryContext = recoveryContext,
            flow = flow,
            state = state,
            createdAtEpochMillis = createdAt,
            expiresAtEpochMillis = expiresAt,
            pendingEventId = eventId,
            returnReason = reason,
            returnOccurredAtEpochMillis = returnOccurredAt,
            acknowledgedEventId = acknowledgedEventId,
        )
    }.getOrNull()
}

object PaymentValuePolicy {
    val returnReasons = setOf("appResumed", "appLink", "coldStart", "webViewRecovered")
    val clearReasons = setOf("terminal", "expired", "logout", "accountChanged", "abandoned")
    private val opaqueContext = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
    private val eventId = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$")

    fun acceptsRecoveryContext(value: String): Boolean = opaqueContext.matches(value)
    fun acceptsEventId(value: String): Boolean = eventId.matches(value)
}
