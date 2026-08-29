import Foundation

enum UpdateMode: String, Equatable, Sendable {
    case none
    case soft
    case hard
}

struct RemoteConfigSnapshot: Sendable {
    let schemaValid: Bool
    let issuedAt: Date
    let expiresAt: Date
    let maintenanceEnabled: Bool
    let updateMode: UpdateMode
    let requestedCapabilities: Set<String>
    let paymentFlowVersion: String
}

struct EffectiveRemoteConfig: Equatable, Sendable {
    enum Source: Equatable, Sendable {
        case safeDefault
        case validatedConfig
        case lastKnownGood
    }

    let source: Source
    let baseWebEnabled: Bool
    let maintenanceEnabled: Bool
    let updateMode: UpdateMode
    let enabledCapabilities: Set<String>
    let paymentFlowVersion: String
}

enum RemoteConfigPolicy {
    static let safeDefault = EffectiveRemoteConfig(
        source: .safeDefault,
        baseWebEnabled: true,
        maintenanceEnabled: false,
        updateMode: .none,
        enabledCapabilities: [],
        paymentFlowVersion: "disabled"
    )

    static func resolve(
        snapshot: RemoteConfigSnapshot?,
        cachedSnapshot: RemoteConfigSnapshot? = nil,
        now: Date,
        builtInCapabilities: Set<String>,
        providerPolicyCapabilities: Set<String>,
        supportedPaymentFlowVersions: Set<String>
    ) -> EffectiveRemoteConfig {
        if let current = resolveValidated(
            snapshot: snapshot,
            source: .validatedConfig,
            now: now,
            builtInCapabilities: builtInCapabilities,
            providerPolicyCapabilities: providerPolicyCapabilities,
            supportedPaymentFlowVersions: supportedPaymentFlowVersions
        ) {
            return current
        }

        return resolveValidated(
            snapshot: cachedSnapshot,
            source: .lastKnownGood,
            now: now,
            builtInCapabilities: builtInCapabilities,
            providerPolicyCapabilities: providerPolicyCapabilities,
            supportedPaymentFlowVersions: supportedPaymentFlowVersions
        ) ?? safeDefault
    }

    private static func resolveValidated(
        snapshot: RemoteConfigSnapshot?,
        source: EffectiveRemoteConfig.Source,
        now: Date,
        builtInCapabilities: Set<String>,
        providerPolicyCapabilities: Set<String>,
        supportedPaymentFlowVersions: Set<String>
    ) -> EffectiveRemoteConfig? {
        guard let snapshot,
              snapshot.schemaValid,
              snapshot.issuedAt <= now,
              snapshot.expiresAt > now
        else {
            return nil
        }

        var effective = builtInCapabilities
            .intersection(providerPolicyCapabilities)
            .intersection(snapshot.requestedCapabilities)
        let paymentFlowIsSupported = snapshot.paymentFlowVersion != "disabled" &&
            supportedPaymentFlowVersions.contains(snapshot.paymentFlowVersion)
        if !paymentFlowIsSupported { effective.remove("openPayment") }

        return EffectiveRemoteConfig(
            source: source,
            baseWebEnabled: true,
            maintenanceEnabled: snapshot.maintenanceEnabled,
            updateMode: snapshot.updateMode,
            enabledCapabilities: effective,
            paymentFlowVersion: effective.contains("openPayment")
                ? snapshot.paymentFlowVersion
                : "disabled"
        )
    }
}
