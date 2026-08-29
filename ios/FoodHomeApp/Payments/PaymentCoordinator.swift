import Foundation

@MainActor
protocol PaymentLaunching: AnyObject {
    func launch(_ destination: ValidatedPaymentDestination) -> Bool
}

@MainActor
final class PaymentCoordinator {
    private let policy: PaymentLaunchPolicy
    private let store: PaymentRecoveryStoring
    private let launcher: PaymentLaunching
    private let nowMilliseconds: () -> Int64
    private let eventID: () -> String

    init(
        policy: PaymentLaunchPolicy,
        store: PaymentRecoveryStoring,
        launcher: PaymentLaunching,
        nowMilliseconds: @escaping () -> Int64 = {
            Int64((Date().timeIntervalSince1970 * 1_000).rounded(.down))
        },
        eventID: @escaping () -> String = { "payment-\(UUID().uuidString.lowercased())" }
    ) {
        self.policy = policy
        self.store = store
        self.launcher = launcher
        self.nowMilliseconds = nowMilliseconds
        self.eventID = eventID
    }

    func open(_ request: OpenPaymentRequest) -> PaymentOperationResult {
        guard request.userInitiated else { return .failure(code: "PAYMENT_USER_ACTION_REQUIRED") }
        guard PaymentValuePolicy.acceptsRecoveryContext(request.recoveryContext) else {
            return .failure(code: "INVALID_PAYLOAD")
        }
        let now = nowMilliseconds()
        if let existing = activeSnapshot(at: now) {
            if existing.recoveryContext == request.recoveryContext, existing.state == .presented {
                return .presented(existing.flow)
            }
            return .failure(code: "PAYMENT_IN_PROGRESS")
        }
        guard let destination = policy.classify(request.rawURL) else {
            return .failure(code: "PAYMENT_URL_NOT_ALLOWED")
        }
        let localExpiry = now.addingReportingOverflow(
            PaymentRecoverySnapshot.localMaximumLifetimeMilliseconds
        )
        let cappedLocalExpiry = localExpiry.overflow ? Int64.max : localExpiry.partialValue
        let effectiveExpiry = min(request.serverExpiresAtEpochMilliseconds, cappedLocalExpiry)
        guard effectiveExpiry > now else { return .failure(code: "PAYMENT_RECOVERY_EXPIRED") }

        let prepared = PaymentRecoverySnapshot(
            recoveryContext: request.recoveryContext,
            flow: destination.flow,
            state: .prepared,
            createdAtEpochMilliseconds: now,
            expiresAtEpochMilliseconds: effectiveExpiry
        )
        guard store.write(prepared) else { return .failure(code: "PAYMENT_RECOVERY_FAILED") }
        let didLaunch = launcher.launch(destination)
        let finalSnapshot = copy(prepared, state: didLaunch ? .presented : .launchFailed)
        guard store.write(finalSnapshot) else { return .failure(code: "PAYMENT_RECOVERY_FAILED") }
        return didLaunch ? .presented(destination.flow) : .failure(code: "LAUNCH_FAILED")
    }

    func recordReturn(reason: String) -> PendingNativePaymentEvent? {
        guard PaymentValuePolicy.returnReasons.contains(reason) else { return nil }
        let now = nowMilliseconds()
        guard let snapshot = activeSnapshot(at: now), snapshot.state != .eventAcknowledged else {
            return nil
        }
        if let pending = pendingEvent(from: snapshot) { return pending }
        let generatedID = eventID()
        guard PaymentValuePolicy.acceptsEventID(generatedID) else { return nil }
        let returned = copy(
            snapshot,
            state: .returned,
            pendingEventID: generatedID,
            returnReason: reason,
            returnOccurredAtEpochMilliseconds: now
        )
        guard store.write(returned) else { return nil }
        return pendingEvent(from: returned)
    }

    func pendingEvent() -> PendingNativePaymentEvent? {
        activeSnapshot(at: nowMilliseconds()).flatMap { pendingEvent(from: $0) }
    }

    func acknowledge(eventID: String) -> PaymentOperationResult {
        guard PaymentValuePolicy.acceptsEventID(eventID) else {
            return .failure(code: "INVALID_PAYLOAD")
        }
        guard let snapshot = activeSnapshot(at: nowMilliseconds()) else {
            return .failure(code: "EVENT_NOT_FOUND")
        }
        if snapshot.acknowledgedEventID == eventID { return .acknowledged(eventID: eventID) }
        guard snapshot.pendingEventID == eventID else { return .failure(code: "EVENT_NOT_FOUND") }
        let acknowledged = copy(
            snapshot,
            state: .eventAcknowledged,
            pendingEventID: nil,
            returnReason: nil,
            returnOccurredAtEpochMilliseconds: nil,
            acknowledgedEventID: eventID
        )
        guard store.write(acknowledged) else { return .failure(code: "PAYMENT_RECOVERY_FAILED") }
        return .acknowledged(eventID: eventID)
    }

    func clear(recoveryContext: String, reason: String) -> PaymentOperationResult {
        guard PaymentValuePolicy.acceptsRecoveryContext(recoveryContext),
              PaymentValuePolicy.clearReasons.contains(reason)
        else {
            return .failure(code: "INVALID_PAYLOAD")
        }
        guard let snapshot = store.read(), snapshot.recoveryContext == recoveryContext else {
            return .failure(code: "PAYMENT_RECOVERY_NOT_FOUND")
        }
        return store.clear() ? .cleared : .failure(code: "PAYMENT_RECOVERY_FAILED")
    }

    func hasActiveRecovery() -> Bool { activeSnapshot(at: nowMilliseconds()) != nil }

    private func activeSnapshot(at now: Int64) -> PaymentRecoverySnapshot? {
        guard let snapshot = store.read() else { return nil }
        if snapshot.isExpired(at: now) {
            _ = store.clear()
            return nil
        }
        return snapshot
    }

    private func pendingEvent(from snapshot: PaymentRecoverySnapshot) -> PendingNativePaymentEvent? {
        guard let eventID = snapshot.pendingEventID,
              let reason = snapshot.returnReason,
              let occurredAt = snapshot.returnOccurredAtEpochMilliseconds
        else {
            return nil
        }
        return PendingNativePaymentEvent(
            eventID: eventID,
            recoveryContext: snapshot.recoveryContext,
            reason: reason,
            occurredAtEpochMilliseconds: occurredAt
        )
    }

    private func copy(
        _ value: PaymentRecoverySnapshot,
        state: PaymentRecoveryState,
        pendingEventID: String? = nil,
        returnReason: String? = nil,
        returnOccurredAtEpochMilliseconds: Int64? = nil,
        acknowledgedEventID: String? = nil
    ) -> PaymentRecoverySnapshot {
        PaymentRecoverySnapshot(
            schemaVersion: value.schemaVersion,
            recoveryContext: value.recoveryContext,
            flow: value.flow,
            state: state,
            createdAtEpochMilliseconds: value.createdAtEpochMilliseconds,
            expiresAtEpochMilliseconds: value.expiresAtEpochMilliseconds,
            pendingEventID: pendingEventID,
            returnReason: returnReason,
            returnOccurredAtEpochMilliseconds: returnOccurredAtEpochMilliseconds,
            acknowledgedEventID: acknowledgedEventID
        )
    }
}
