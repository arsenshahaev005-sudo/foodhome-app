import XCTest
@testable import FoodHomeApp

final class RemoteConfigPolicyTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_777_377_600)

    func testMissingInvalidAndExpiredConfigUseSafeDefault() {
        XCTAssertEqual(resolve(nil), RemoteConfigPolicy.safeDefault)
        XCTAssertEqual(resolve(snapshot(schemaValid: false)), RemoteConfigPolicy.safeDefault)
        XCTAssertEqual(resolve(snapshot(expiresAt: now)), RemoteConfigPolicy.safeDefault)
    }

    func testValidConfigOnlyIntersectsCompiledCapabilities() {
        let resolved = resolve(
            snapshot(
                updateMode: .soft,
                requestedCapabilities: ["share", "openPayment"]
            )
        )

        XCTAssertEqual(resolved.enabledCapabilities, ["share"])
        XCTAssertEqual(resolved.updateMode, .soft)
        XCTAssertTrue(resolved.baseWebEnabled)
    }

    func testCurrentValidConfigWinsOverLastKnownGood() {
        let resolved = resolve(
            snapshot(requestedCapabilities: ["share"]),
            cached: snapshot(requestedCapabilities: ["requestLocation"])
        )

        XCTAssertEqual(resolved.source, .validatedConfig)
        XCTAssertEqual(resolved.enabledCapabilities, ["share"])
    }

    func testValidLastKnownGoodIsUsedWhenCurrentConfigIsInvalid() {
        let resolved = resolve(
            snapshot(schemaValid: false),
            cached: snapshot(requestedCapabilities: ["requestLocation"])
        )

        XCTAssertEqual(resolved.source, .lastKnownGood)
        XCTAssertEqual(resolved.enabledCapabilities, ["requestLocation"])
    }

    func testExpiredLastKnownGoodCannotExtendItsTTL() {
        let resolved = resolve(nil, cached: snapshot(expiresAt: now))
        XCTAssertEqual(resolved, RemoteConfigPolicy.safeDefault)
    }

    func testPaymentRequiresBuiltInProviderPolicyRemoteEnablementAndSupportedFlow() {
        let enabled = RemoteConfigPolicy.resolve(
            snapshot: snapshot(
                requestedCapabilities: ["openPayment"],
                paymentFlowVersion: "test-v1"
            ),
            now: now,
            builtInCapabilities: ["openPayment"],
            providerPolicyCapabilities: ["openPayment"],
            supportedPaymentFlowVersions: ["test-v1"]
        )
        let noProviderPolicy = RemoteConfigPolicy.resolve(
            snapshot: snapshot(
                requestedCapabilities: ["openPayment"],
                paymentFlowVersion: "test-v1"
            ),
            now: now,
            builtInCapabilities: ["openPayment"],
            providerPolicyCapabilities: [],
            supportedPaymentFlowVersions: ["test-v1"]
        )

        XCTAssertEqual(enabled.enabledCapabilities, ["openPayment"])
        XCTAssertEqual(enabled.paymentFlowVersion, "test-v1")
        XCTAssertEqual(noProviderPolicy.enabledCapabilities, [])
        XCTAssertEqual(noProviderPolicy.paymentFlowVersion, "disabled")
    }

    private func resolve(
        _ snapshot: RemoteConfigSnapshot?,
        cached: RemoteConfigSnapshot? = nil
    ) -> EffectiveRemoteConfig {
        RemoteConfigPolicy.resolve(
            snapshot: snapshot,
            cachedSnapshot: cached,
            now: now,
            builtInCapabilities: ["share", "requestLocation", "openPayment"],
            providerPolicyCapabilities: ["share", "requestLocation"],
            supportedPaymentFlowVersions: []
        )
    }

    private func snapshot(
        schemaValid: Bool = true,
        issuedAt: Date? = nil,
        expiresAt: Date? = nil,
        updateMode: UpdateMode = .none,
        requestedCapabilities: Set<String> = [],
        paymentFlowVersion: String = "disabled"
    ) -> RemoteConfigSnapshot {
        RemoteConfigSnapshot(
            schemaValid: schemaValid,
            issuedAt: issuedAt ?? now.addingTimeInterval(-60),
            expiresAt: expiresAt ?? now.addingTimeInterval(60),
            maintenanceEnabled: false,
            updateMode: updateMode,
            requestedCapabilities: requestedCapabilities,
            paymentFlowVersion: paymentFlowVersion
        )
    }
}
