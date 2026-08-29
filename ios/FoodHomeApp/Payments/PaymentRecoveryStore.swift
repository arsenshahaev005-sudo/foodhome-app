import Foundation

protocol PaymentRecoveryStoring: AnyObject {
    func read() -> PaymentRecoverySnapshot?
    func write(_ snapshot: PaymentRecoverySnapshot) -> Bool
    func clear() -> Bool
}

final class FilePaymentRecoveryStore: PaymentRecoveryStoring {
    private let fileManager: FileManager
    private let fileURL: URL
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    init?(fileManager: FileManager = .default) {
        guard let applicationSupport = fileManager.urls(
            for: .applicationSupportDirectory,
            in: .userDomainMask
        ).first else {
            return nil
        }
        self.fileManager = fileManager
        self.fileURL = applicationSupport
            .appendingPathComponent("FoodHome", isDirectory: true)
            .appendingPathComponent("payment-recovery.json", isDirectory: false)
    }

    init(fileURL: URL, fileManager: FileManager = .default) {
        self.fileManager = fileManager
        self.fileURL = fileURL
    }

    func read() -> PaymentRecoverySnapshot? {
        guard let data = try? Data(contentsOf: fileURL),
              let snapshot = try? decoder.decode(PaymentRecoverySnapshot.self, from: data),
              isValid(snapshot)
        else {
            if fileManager.fileExists(atPath: fileURL.path) { _ = clear() }
            return nil
        }
        return snapshot
    }

    func write(_ snapshot: PaymentRecoverySnapshot) -> Bool {
        guard isValid(snapshot), let data = try? encoder.encode(snapshot) else { return false }
        let directory = fileURL.deletingLastPathComponent()
        do {
            try fileManager.createDirectory(
                at: directory,
                withIntermediateDirectories: true,
                attributes: nil
            )
            var directoryValues = URLResourceValues()
            directoryValues.isExcludedFromBackup = true
            var mutableDirectory = directory
            try mutableDirectory.setResourceValues(directoryValues)
            try data.write(to: fileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            return true
        } catch {
            return false
        }
    }

    func clear() -> Bool {
        guard fileManager.fileExists(atPath: fileURL.path) else { return true }
        do {
            try fileManager.removeItem(at: fileURL)
            return true
        } catch {
            try? fileManager.removeItem(at: fileURL)
            return false
        }
    }

    private func isValid(_ value: PaymentRecoverySnapshot) -> Bool {
        guard value.schemaVersion == PaymentRecoverySnapshot.currentSchemaVersion,
              PaymentValuePolicy.acceptsRecoveryContext(value.recoveryContext),
              value.createdAtEpochMilliseconds >= 0,
              value.expiresAtEpochMilliseconds > value.createdAtEpochMilliseconds
        else {
            return false
        }
        if let eventID = value.pendingEventID,
           !PaymentValuePolicy.acceptsEventID(eventID) {
            return false
        }
        if let acknowledged = value.acknowledgedEventID,
           !PaymentValuePolicy.acceptsEventID(acknowledged) {
            return false
        }
        let hasPending = value.pendingEventID != nil
        if hasPending != (value.returnReason != nil) ||
            hasPending != (value.returnOccurredAtEpochMilliseconds != nil) ||
            (hasPending && value.acknowledgedEventID != nil) {
            return false
        }
        if let reason = value.returnReason, !PaymentValuePolicy.returnReasons.contains(reason) {
            return false
        }
        return true
    }
}
