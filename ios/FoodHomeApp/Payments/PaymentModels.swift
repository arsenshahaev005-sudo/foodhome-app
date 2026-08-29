import Foundation

enum PaymentFlow: String, Codable, Equatable, Sendable {
    case cardAcquiring
    case sbpCyclops
}

enum PaymentRecoveryState: String, Codable, Equatable, Sendable {
    case prepared
    case presented
    case launchFailed
    case returned
    case eventAcknowledged
}

struct PaymentRecoverySnapshot: Codable, Equatable, Sendable {
    static let currentSchemaVersion = 1
    static let localMaximumLifetimeMilliseconds: Int64 = 24 * 60 * 60 * 1_000

    let schemaVersion: Int
    let recoveryContext: String
    let flow: PaymentFlow
    let state: PaymentRecoveryState
    let createdAtEpochMilliseconds: Int64
    let expiresAtEpochMilliseconds: Int64
    let pendingEventID: String?
    let returnReason: String?
    let returnOccurredAtEpochMilliseconds: Int64?
    let acknowledgedEventID: String?

    init(
        schemaVersion: Int = PaymentRecoverySnapshot.currentSchemaVersion,
        recoveryContext: String,
        flow: PaymentFlow,
        state: PaymentRecoveryState,
        createdAtEpochMilliseconds: Int64,
        expiresAtEpochMilliseconds: Int64,
        pendingEventID: String? = nil,
        returnReason: String? = nil,
        returnOccurredAtEpochMilliseconds: Int64? = nil,
        acknowledgedEventID: String? = nil
    ) {
        self.schemaVersion = schemaVersion
        self.recoveryContext = recoveryContext
        self.flow = flow
        self.state = state
        self.createdAtEpochMilliseconds = createdAtEpochMilliseconds
        self.expiresAtEpochMilliseconds = expiresAtEpochMilliseconds
        self.pendingEventID = pendingEventID
        self.returnReason = returnReason
        self.returnOccurredAtEpochMilliseconds = returnOccurredAtEpochMilliseconds
        self.acknowledgedEventID = acknowledgedEventID
    }

    func isExpired(at now: Int64) -> Bool { now >= expiresAtEpochMilliseconds }
}

struct OpenPaymentRequest: Sendable {
    let rawURL: String
    let recoveryContext: String
    let serverExpiresAtEpochMilliseconds: Int64
    let userInitiated: Bool
}

enum PaymentOperationResult: Equatable, Sendable {
    case presented(PaymentFlow)
    case acknowledged(eventID: String)
    case cleared
    case failure(code: String, retryable: Bool = false)
}

struct PendingNativePaymentEvent: Equatable, Sendable {
    let eventID: String
    let recoveryContext: String
    let reason: String
    let occurredAtEpochMilliseconds: Int64

    func bridgeValue(protocolName: String, version: Int) -> [String: Any] {
        [
            "protocol": protocolName,
            "version": version,
            "eventId": eventID,
            "name": "paymentReturned",
            "payload": [
                "recoveryContext": recoveryContext,
                "reason": reason,
            ],
            "occurredAt": ISO8601DateFormatter.foodHome.string(
                from: Date(timeIntervalSince1970: Double(occurredAtEpochMilliseconds) / 1_000)
            ),
        ]
    }
}

enum PaymentValuePolicy {
    static let returnReasons: Set<String> = [
        "appResumed", "universalLink", "coldStart", "webViewRecovered",
    ]
    static let clearReasons: Set<String> = [
        "terminal", "expired", "logout", "accountChanged", "abandoned",
    ]

    static func acceptsRecoveryContext(_ value: String) -> Bool {
        accepts(value, maximumLength: 128)
    }

    static func acceptsEventID(_ value: String) -> Bool {
        accepts(value, maximumLength: 64)
    }

    private static func accepts(_ value: String, maximumLength: Int) -> Bool {
        guard !value.isEmpty, value.count <= maximumLength,
              (value.first?.isLetter == true || value.first?.isNumber == true)
        else {
            return false
        }
        return value.allSatisfy { $0.isLetter || $0.isNumber || "._:-".contains($0) }
    }
}

private extension ISO8601DateFormatter {
    static let foodHome: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()
}
