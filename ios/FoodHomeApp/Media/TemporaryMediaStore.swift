import Foundation

final class TemporaryMediaStore {
    static let defaultMaxAge: TimeInterval = 24 * 60 * 60

    private let directory: URL
    private let fileManager: FileManager
    private let now: () -> Date

    init(
        cacheDirectory: URL? = nil,
        fileManager: FileManager = .default,
        now: @escaping () -> Date = Date.init
    ) {
        let root = cacheDirectory ?? fileManager.urls(
            for: .cachesDirectory,
            in: .userDomainMask
        )[0]
        self.directory = root.appendingPathComponent("foodhome-media", isDirectory: true)
        self.fileManager = fileManager
        self.now = now
    }

    func copyImage(from source: URL) -> URL? {
        let fileExtension = source.pathExtension.isEmpty ? "img" : source.pathExtension.lowercased()
        let destination = uniqueURL(extension: fileExtension)
        do {
            try ensureDirectory()
            try fileManager.copyItem(at: source, to: destination)
            return destination
        } catch {
            return nil
        }
    }

    func writeJPEG(_ data: Data) -> URL? {
        let destination = uniqueURL(extension: "jpg")
        do {
            try ensureDirectory()
            try data.write(to: destination, options: .atomic)
            return destination
        } catch {
            return nil
        }
    }

    @discardableResult
    func cleanupStale(maxAge: TimeInterval = TemporaryMediaStore.defaultMaxAge) -> Int {
        guard let files = try? fileManager.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: [.contentModificationDateKey, .isRegularFileKey]
        ) else {
            return 0
        }
        let cutoff = now().addingTimeInterval(-maxAge)
        var removed = 0
        for file in files where file.lastPathComponent.hasPrefix("capture-") {
            guard let values = try? file.resourceValues(
                forKeys: [.contentModificationDateKey, .isRegularFileKey]
            ), values.isRegularFile == true,
                let modified = values.contentModificationDate,
                modified < cutoff
            else {
                continue
            }
            if (try? fileManager.removeItem(at: file)) != nil { removed += 1 }
        }
        return removed
    }

    private func ensureDirectory() throws {
        try fileManager.createDirectory(
            at: directory,
            withIntermediateDirectories: true
        )
    }

    private func uniqueURL(extension fileExtension: String) -> URL {
        directory
            .appendingPathComponent("capture-\(UUID().uuidString)")
            .appendingPathExtension(fileExtension)
    }
}
