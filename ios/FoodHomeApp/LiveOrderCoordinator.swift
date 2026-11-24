import ActivityKit
import CryptoKit
import Foundation

// Remember a Lock Screen dismissal until this activity would expire. Store only a
// fingerprint and a deadline; the registration secret stays in the Keychain.
struct LiveOrderDismissals {
    let defaults: UserDefaults
    private let key = "foodhome.order-activities.dismissals"

    init(defaults: UserDefaults = .standard) { self.defaults = defaults }

    private func fingerprint(_ order: UUID) -> String {
        SHA256.hash(data: Data(order.uuidString.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    private func active(at date: Date) -> [String: Double] {
        let stored = defaults.dictionary(forKey: key) as? [String: Double] ?? [:]
        let current = stored.filter { $0.value > date.timeIntervalSince1970 }
        if current.count != stored.count { defaults.set(current, forKey: key) }
        return current
    }

    func remember(_ order: UUID, until deadline: Date) {
        var values = active(at: Date())
        values[fingerprint(order)] = deadline.timeIntervalSince1970
        defaults.set(values, forKey: key)
    }

    func contains(_ order: UUID, at date: Date = Date()) -> Bool {
        active(at: date)[fingerprint(order)] != nil
    }

    func clear() { defaults.removeObject(forKey: key) }
}

@MainActor
final class LiveOrderCoordinator {
    static let shared = LiveOrderCoordinator()

    func resume() {
        if #available(iOS 16.2, *) { LiveOrderRuntime.shared.resume() }
    }

    func dispatch(payload: [String: Any], origin: URL, completion: @escaping (BridgeDispatchResult) -> Void) {
        func finish(_ status: String) {
            completion(.success(["capability": "manageOrderActivity", "status": status]))
        }
        guard #available(iOS 16.2, *) else { finish("unavailable"); return }
        let runtime = LiveOrderRuntime.shared
        if payload["action"] as? String == "clear", payload.count == 1 {
            runtime.clear()
            finish("ended")
            return
        }
        guard payload.count == 3, payload["action"] as? String == "sync",
              let rawID = payload["orderId"] as? String, let orderID = UUID(uuidString: rawID),
              let grant = payload["grant"] as? String, (1...1024).contains(grant.utf8.count),
              origin == AppEnvironmentResolver.current().trustedOrigin
        else {
            completion(.failure(code: "INVALID_MESSAGE", message: "Invalid order activity request."))
            return
        }
        let epoch = runtime.epoch
        Task {
            guard runtime.epoch == epoch else { finish("ended"); return }
            do { finish(try await runtime.sync(orderID, grant: grant, origin: origin)) }
            catch { completion(.failure(code: "CAPABILITY_UNAVAILABLE", message: "Order activity is unavailable.", retryable: true)) }
        }
    }
}

@available(iOS 16.2, *)
@MainActor
private final class LiveOrderRuntime {
    typealias Card = Activity<LiveOrderAttributes>

    static let shared = LiveOrderRuntime()
    private let client = LiveOrderClient()
    private let store = LiveOrderBindingStore()
    private let dismissals = LiveOrderDismissals()
    private var bindings = [UUID: LiveOrderBinding]()
    private var loaded = false
    private(set) var epoch = 0
    private var starting = [UUID: Task<String, Error>]()
    private var watchers = [UUID: [Task<Void, Never>]]()
    private var uploads = [UUID: Task<Void, Never>]()
    private var revocations = Set<UUID>()

    private func load() throws {
        guard !loaded else { return }
        let stored = try store.read()
        for binding in stored where binding.expiresAt > Date() { bindings[binding.id] = binding }
        if bindings.count != stored.count { try store.write(Array(bindings.values)) }
        loaded = true
    }

    private func save(_ item: LiveOrderBinding) throws {
        var next = bindings
        next[item.id] = item
        try store.write(Array(next.values))
        bindings = next
    }

    private func remove(_ id: UUID) {
        var next = bindings
        next.removeValue(forKey: id)
        if (try? store.write(Array(next.values))) != nil { bindings = next }
    }

    func resume() {
        guard (try? load()) != nil else { return }
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { clear(); return }
        let cards = Card.activities
        let liveIDs = Set(cards.map { $0.attributes.bindingID })
        for binding in Array(bindings.values) {
            if binding.revoking || binding.expiresAt <= Date() {
                retire(binding)
            } else if !liveIDs.contains(binding.id) && starting[binding.orderID] == nil {
                if binding.wasPresented == true { dismissals.remember(binding.orderID, until: binding.expiresAt) }
                retire(binding)
            }
        }
        for card in cards {
            guard let binding = bindings[card.attributes.bindingID], !binding.revoking,
                  binding.expiresAt > Date(), binding.origin == AppEnvironmentResolver.current().trustedOrigin else {
                Task { await card.end(nil, dismissalPolicy: .immediate) }
                continue
            }
            if card.activityState == .active || card.activityState == .stale { observe(card) }
            else {
                if card.activityState == .dismissed { dismissals.remember(binding.orderID, until: binding.expiresAt) }
                retire(binding)
            }
        }
    }

    func sync(_ order: UUID, grant: String, origin: URL) async throws -> String {
        try load()
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return "disabled" }
        if let pending = starting[order] { return try await pending.value }
        resume()
        if dismissals.contains(order) { return "ended" }
        if Card.activities.contains(where: {
            $0.attributes.orderID == order && bindings[$0.attributes.bindingID]?.revoking == false &&
                ($0.activityState == .active || $0.activityState == .stale)
        }) { return "active" }

        let generation = epoch
        let task = Task<String, Error> {
            var binding = LiveOrderBinding(orderID: order, origin: origin)
            try save(binding)
            do {
                let snapshot = try await client.register(binding, grant: grant)
                guard epoch == generation, !Task.isCancelled else { throw CancellationError() }
                let state = LiveOrderAttributes.ContentState(stage: snapshot.stage)
                if state.isFinal { retire(binding); return "ended" }
                binding.expiresAt = Date(timeIntervalSince1970: snapshot.expiresAt)
                guard binding.expiresAt > Date() else { throw LiveOrderError.invalidResponse }
                try save(binding)
                let card = try Card.request(
                    attributes: LiveOrderAttributes(orderID: order, bindingID: binding.id),
                    content: ActivityContent(state: state, staleDate: min(binding.expiresAt, Date().addingTimeInterval(7200))),
                    pushType: .token
                )
                binding.wasPresented = true
                try? save(binding)
                observe(card)
                return "active"
            } catch {
                retire(binding)
                throw error
            }
        }
        starting[order] = task
        defer { if epoch == generation { starting[order] = nil } }
        return try await task.value
    }

    func clear() {
        epoch += 1
        dismissals.clear()
        for task in starting.values { task.cancel() }
        starting.removeAll()
        if (try? load()) != nil {
            for binding in Array(bindings.values) { retire(binding) }
        }
        for card in Card.activities { Task { await card.end(nil, dismissalPolicy: .immediate) } }
    }

    private func observe(_ card: Card) {
        let id = card.attributes.bindingID
        if let token = card.pushToken { upload(token, id: id) }
        guard watchers[id] == nil else { return }
        let tokens = Task {
            for await token in card.pushTokenUpdates {
                if Task.isCancelled { break }
                self.upload(token, id: id)
            }
        }
        let states = Task {
            for await state in card.activityStateUpdates {
                if Task.isCancelled { break }
                if state == .dismissed || state == .ended {
                    if let binding = self.bindings[id], !binding.revoking {
                        if state == .dismissed { self.dismissals.remember(binding.orderID, until: binding.expiresAt) }
                        self.retire(binding)
                    }
                    break
                }
            }
        }
        watchers[id] = [tokens, states]
    }

    private func upload(_ token: Data, id: UUID) {
        guard !token.isEmpty, token.count <= 512, var binding = bindings[id], !binding.revoking else { return }
        let digest = SHA256.hash(data: token).map { String(format: "%02x", $0) }.joined()
        if binding.digest != digest {
            binding.digest = digest
            binding.sequence += 1
            guard (try? save(binding)) != nil else { return }
            uploads.removeValue(forKey: id)?.cancel()
        } else if binding.confirmed == binding.sequence || uploads[id] != nil { return }
        let sequence = binding.sequence
        uploads[id] = Task {
            defer { if self.bindings[id]?.sequence == sequence { self.uploads[id] = nil } }
            var retry: UInt64 = 2
            while !Task.isCancelled, let current = self.bindings[id], current.sequence == sequence,
                  !current.revoking, current.expiresAt > Date() {
                do {
                    try await self.client.setToken(token, for: current)
                    if var latest = self.bindings[id], latest.sequence == sequence, !latest.revoking {
                        latest.confirmed = sequence
                        try self.save(latest)
                    }
                    return
                } catch LiveOrderError.http(let status) where status == 403 || status == 404 {
                    guard !Task.isCancelled, let latest = self.bindings[id],
                          latest.sequence == sequence, !latest.revoking else { return }
                    self.retire(latest)
                    for card in Card.activities where card.attributes.bindingID == id {
                        await card.end(nil, dismissalPolicy: .immediate)
                    }
                    return
                } catch {
                    try? await Task.sleep(nanoseconds: retry * 1_000_000_000)
                    retry = min(retry * 2, 60)
                }
            }
        }
    }

    private func retire(_ original: LiveOrderBinding) {
        let id = original.id
        watchers.removeValue(forKey: id)?.forEach { $0.cancel() }
        uploads.removeValue(forKey: id)?.cancel()
        var binding = bindings[id] ?? original
        binding.revoking = true
        try? save(binding)
        guard binding.expiresAt > Date(), binding.origin == AppEnvironmentResolver.current().trustedOrigin else {
            remove(id)
            return
        }
        guard revocations.insert(id).inserted else { return }
        Task {
            defer { self.revocations.remove(id) }
            do {
                try await self.client.revoke(binding)
                self.remove(id)
            } catch LiveOrderError.http(let status) where status == 403 || status == 404 {
                self.remove(id)
            } catch {
                // The local card is already gone. Retry cleanup on the next foreground transition.
            }
        }
    }
}
