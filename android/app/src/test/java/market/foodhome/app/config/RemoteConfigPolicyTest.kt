package market.foodhome.app.config

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RemoteConfigPolicyTest {
    private val now = Instant.parse("2026-08-28T12:00:00Z")

    @Test
    fun `missing invalid and expired config use safe default`() {
        val invalid = snapshot(schemaValid = false)
        val expired = snapshot(expiresAt = now)

        assertEquals(RemoteConfigPolicy.safeDefault, resolve(null))
        assertEquals(RemoteConfigPolicy.safeDefault, resolve(invalid))
        assertEquals(RemoteConfigPolicy.safeDefault, resolve(expired))
    }

    @Test
    fun `validated config only intersects compiled capabilities`() {
        val result = resolve(
            snapshot(
                requestedCapabilities = setOf("share", "openPayment"),
                updateMode = UpdateMode.SOFT,
            ),
        )

        assertEquals(setOf("share"), result.enabledCapabilities)
        assertEquals(UpdateMode.SOFT, result.updateMode)
        assertEquals(true, result.baseWebEnabled)
    }

    @Test
    fun `current valid config wins over last known good`() {
        val result = resolve(
            current = snapshot(requestedCapabilities = setOf("share")),
            cached = snapshot(requestedCapabilities = setOf("requestLocation")),
        )

        assertEquals(EffectiveRemoteConfig.Source.VALIDATED_CONFIG, result.source)
        assertEquals(setOf("share"), result.enabledCapabilities)
    }

    @Test
    fun `valid last known good is used when current config is invalid`() {
        val result = resolve(
            current = snapshot(schemaValid = false),
            cached = snapshot(requestedCapabilities = setOf("requestLocation")),
        )

        assertEquals(EffectiveRemoteConfig.Source.LAST_KNOWN_GOOD, result.source)
        assertEquals(setOf("requestLocation"), result.enabledCapabilities)
    }

    @Test
    fun `expired last known good cannot extend its ttl`() {
        val result = resolve(
            current = null,
            cached = snapshot(expiresAt = now),
        )

        assertEquals(RemoteConfigPolicy.safeDefault, result)
    }

    @Test
    fun `payment requires built in provider policy remote enablement and supported flow`() {
        val enabled = RemoteConfigPolicy.resolve(
            snapshot = snapshot(
                requestedCapabilities = setOf("openPayment"),
                paymentFlowVersion = "test-v1",
            ),
            now = now,
            builtInCapabilities = setOf("openPayment"),
            providerPolicyCapabilities = setOf("openPayment"),
            supportedPaymentFlowVersions = setOf("test-v1"),
        )
        val noProviderPolicy = RemoteConfigPolicy.resolve(
            snapshot = snapshot(
                requestedCapabilities = setOf("openPayment"),
                paymentFlowVersion = "test-v1",
            ),
            now = now,
            builtInCapabilities = setOf("openPayment"),
            providerPolicyCapabilities = emptySet(),
            supportedPaymentFlowVersions = setOf("test-v1"),
        )

        assertEquals(setOf("openPayment"), enabled.enabledCapabilities)
        assertEquals("test-v1", enabled.paymentFlowVersion)
        assertEquals(emptySet<String>(), noProviderPolicy.enabledCapabilities)
        assertEquals("disabled", noProviderPolicy.paymentFlowVersion)
    }

    private fun resolve(
        current: RemoteConfigSnapshot?,
        cached: RemoteConfigSnapshot? = null,
    ) = RemoteConfigPolicy.resolve(
        snapshot = current,
        cachedSnapshot = cached,
        now = now,
        builtInCapabilities = setOf("share", "requestLocation", "openPayment"),
        providerPolicyCapabilities = setOf("share", "requestLocation"),
        supportedPaymentFlowVersions = emptySet(),
    )

    private fun snapshot(
        schemaValid: Boolean = true,
        issuedAt: Instant = now.minusSeconds(60),
        expiresAt: Instant = now.plusSeconds(60),
        requestedCapabilities: Set<String> = emptySet(),
        updateMode: UpdateMode = UpdateMode.NONE,
        paymentFlowVersion: String = "disabled",
    ) = RemoteConfigSnapshot(
        schemaValid = schemaValid,
        issuedAt = issuedAt,
        expiresAt = expiresAt,
        maintenanceEnabled = false,
        updateMode = updateMode,
        requestedCapabilities = requestedCapabilities,
        paymentFlowVersion = paymentFlowVersion,
    )
}
