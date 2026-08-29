import Foundation

final class CrashLoopBreaker {
    private let window: TimeInterval
    private var crashes: [Date] = []
    private var activeRouteKey: String?

    init(window: TimeInterval = 60) {
        self.window = window
    }

    func recordCrash(routeKey: String, at now: Date) -> Bool {
        if activeRouteKey != routeKey {
            crashes.removeAll()
            activeRouteKey = routeKey
        }
        crashes.append(now)
        let cutoff = now.addingTimeInterval(-window)
        crashes.removeAll { $0 < cutoff }
        return crashes.count >= 2
    }

    func reset() {
        crashes.removeAll()
        activeRouteKey = nil
    }
}
