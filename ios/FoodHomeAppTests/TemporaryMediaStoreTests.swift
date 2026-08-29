import XCTest
@testable import FoodHomeApp

final class TemporaryMediaStoreTests: XCTestCase {
    func testCleanupDeletesOnlyStaleOwnedMedia() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(
            UUID().uuidString,
            isDirectory: true
        )
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let now = Date(timeIntervalSince1970: 200_000)
        let store = TemporaryMediaStore(cacheDirectory: root, now: { now })
        let source = root.appendingPathComponent("source.jpg")
        try Data("image".utf8).write(to: source)
        let stale = try XCTUnwrap(store.copyImage(from: source))
        try FileManager.default.setAttributes(
            [.modificationDate: Date(timeIntervalSince1970: 0)],
            ofItemAtPath: stale.path
        )
        let unrelated = root.appendingPathComponent("keep.txt")
        try Data("keep".utf8).write(to: unrelated)

        XCTAssertEqual(store.cleanupStale(maxAge: 60), 1)
        XCTAssertFalse(FileManager.default.fileExists(atPath: stale.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: unrelated.path))
    }
}
