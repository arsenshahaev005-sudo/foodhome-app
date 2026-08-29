package market.foodhome.app.config

import java.time.Instant

enum class UpdateMode { NONE, SOFT, HARD }

data class RemoteConfigSnapshot(
    val schemaValid: Boolean,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val maintenanceEnabled: Boolean,
    val updateMode: UpdateMode,
    val requestedCapabilities: Set<String>,
    val paymentFlowVersion: String = "disabled",
)

data class EffectiveRemoteConfig(
    val source: Source,
    val baseWebEnabled: Boolean,
    val maintenanceEnabled: Boolean,
    val updateMode: UpdateMode,
    val enabledCapabilities: Set<String>,
    val paymentFlowVersion: String,
) {
    enum class Source { SAFE_DEFAULT, VALIDATED_CONFIG, LAST_KNOWN_GOOD }
}

object RemoteConfigPolicy {
    val safeDefault = EffectiveRemoteConfig(
        source = EffectiveRemoteConfig.Source.SAFE_DEFAULT,
        baseWebEnabled = true,
        maintenanceEnabled = false,
        updateMode = UpdateMode.NONE,
        enabledCapabilities = emptySet(),
        paymentFlowVersion = "disabled",
    )

    fun resolve(
        snapshot: RemoteConfigSnapshot?,
        cachedSnapshot: RemoteConfigSnapshot? = null,
        now: Instant,
        builtInCapabilities: Set<String>,
        providerPolicyCapabilities: Set<String>,
        supportedPaymentFlowVersions: Set<String>,
    ): EffectiveRemoteConfig {
        resolveValidated(
            snapshot = snapshot,
            source = EffectiveRemoteConfig.Source.VALIDATED_CONFIG,
            now = now,
            builtInCapabilities = builtInCapabilities,
            providerPolicyCapabilities = providerPolicyCapabilities,
            supportedPaymentFlowVersions = supportedPaymentFlowVersions,
        )?.let { return it }

        return resolveValidated(
            snapshot = cachedSnapshot,
            source = EffectiveRemoteConfig.Source.LAST_KNOWN_GOOD,
            now = now,
            builtInCapabilities = builtInCapabilities,
            providerPolicyCapabilities = providerPolicyCapabilities,
            supportedPaymentFlowVersions = supportedPaymentFlowVersions,
        ) ?: safeDefault
    }

    private fun resolveValidated(
        snapshot: RemoteConfigSnapshot?,
        source: EffectiveRemoteConfig.Source,
        now: Instant,
        builtInCapabilities: Set<String>,
        providerPolicyCapabilities: Set<String>,
        supportedPaymentFlowVersions: Set<String>,
    ): EffectiveRemoteConfig? {
        if (
            snapshot == null ||
            !snapshot.schemaValid ||
            snapshot.issuedAt.isAfter(now) ||
            !snapshot.expiresAt.isAfter(now)
        ) return null

        val effective = builtInCapabilities
            .intersect(providerPolicyCapabilities)
            .intersect(snapshot.requestedCapabilities)
            .toMutableSet()
        val paymentFlowIsSupported = snapshot.paymentFlowVersion != "disabled" &&
            snapshot.paymentFlowVersion in supportedPaymentFlowVersions
        if (!paymentFlowIsSupported) effective.remove("openPayment")

        return EffectiveRemoteConfig(
            source = source,
            baseWebEnabled = true,
            maintenanceEnabled = snapshot.maintenanceEnabled,
            updateMode = snapshot.updateMode,
            enabledCapabilities = effective,
            paymentFlowVersion = if ("openPayment" in effective) {
                snapshot.paymentFlowVersion
            } else {
                "disabled"
            },
        )
    }
}
