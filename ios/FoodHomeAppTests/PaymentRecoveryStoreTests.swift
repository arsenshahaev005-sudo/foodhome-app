import XCTest
@testable import FoodHomeApp

final class PaymentRecoveryStoreTests: XCTestCase {
    func testAtomicStoreRoundTripsOnlyOpaqueRecoveryFields() throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = directory.appendingPathComponent("payment-recovery.json")
        let store = FilePaymentRecoveryStore(fileURL: file)
        let snapshot = PaymentRecoverySnapshot(
            recoveryContext: "opaque-context-123",
            flow: .sbpCyclops,
            state: .returned,
            createdAtEpochMilliseconds: 1_000,
            expiresAtEpochMilliseconds: 2_000,
            pendingEventID: "payment-event-1",
            returnReason: "appResumed",
            returnOccurredAtEpochMilliseconds: 1_500
        )

        XCTAssertTrue(store.write(snapshot))
        XCTAssertEqual(store.read(), snapshot)
        let stored = try String(contentsOf: file, encoding: .utf8)
        for forbidden in ["url", "query", "amount", "currency", "orderId", "userId", "token"] {
            XCTAssertFalse(stored.localizedCaseInsensitiveContains(forbidden))
        }
    }

    func testCorruptRecordIsRemoved() throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let file = directory.appendingPathComponent("payment-recovery.json")
        try Data("not-json".utf8).write(to: file)
        let store = FilePaymentRecoveryStore(fileURL: file)

        XCTAssertNil(store.read())
        XCTAssertFalse(FileManager.default.fileExists(atPath: file.path))
    }
}
