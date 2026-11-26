import ActivityKit
import Foundation

@available(iOS 16.2, *)
struct LiveOrderAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        let stage: String
        let pickup: Bool?

        init(stage: String, pickup: Bool? = nil) {
            self.stage = stage
            self.pickup = pickup
        }

        var progress: LiveOrderProgress? {
            LiveOrderProgress(stage: stage, pickup: pickup)
        }

        var title: String {
            switch stage {
            case "WAITING_FOR_PAYMENT": return "Ожидает оплаты"
            case "WAITING_FOR_RECIPIENT": return "Ожидает получателя"
            case "WAITING_FOR_ACCEPTANCE": return "Ждём подтверждения продавца"
            case "BLOCKED_FOR_SURCHARGE_PAYMENT": return "Требуется доплата"
            case "COOKING": return "Готовится"
            case "READY_FOR_REVIEW": return "Проверьте фото заказа"
            case "READY_FOR_PICKUP": return "Можно забирать"
            case "READY_FOR_DELIVERY": return "Готов к доставке"
            case "DELIVERING": return "В пути"
            case "ARRIVED": return "Заказ на месте"
            case "COMPLETED": return "Получен"
            case "CANCELLED": return "Отменён"
            case "DISPUTE": return "Открыт спор"
            default: return "Статус обновлён"
            }
        }

        var compactTitle: String {
            switch stage {
            case "COOKING": return "Готовится"
            case "READY_FOR_REVIEW": return "Фото"
            case "READY_FOR_DELIVERY", "READY_FOR_PICKUP": return "Готов"
            case "DELIVERING": return "В пути"
            case "ARRIVED": return "На месте"
            case "COMPLETED": return "Получен"
            case "CANCELLED": return "Отменён"
            case "BLOCKED_FOR_SURCHARGE_PAYMENT", "WAITING_FOR_PAYMENT": return "Оплата"
            case "DISPUTE": return "Спор"
            default: return "Ожидание"
            }
        }

        var symbol: String {
            switch stage {
            case "COOKING": return "flame.fill"
            case "READY_FOR_REVIEW": return "photo"
            case "READY_FOR_DELIVERY", "READY_FOR_PICKUP": return "takeoutbag.and.cup.and.straw"
            case "DELIVERING": return "car.fill"
            case "ARRIVED": return "mappin.circle.fill"
            case "COMPLETED": return "checkmark.circle.fill"
            case "CANCELLED", "DISPUTE": return "exclamationmark.circle"
            default: return "clock"
            }
        }

        var isFinal: Bool { ["CANCELLED", "COMPLETED", "DISPUTE"].contains(stage) }
    }

    let orderID: UUID
    let bindingID: UUID

    var orderURL: URL {
        FoodHomeDeployment.current.origin
            .appendingPathComponent("orders").appendingPathComponent(orderID.uuidString.lowercased())
    }
}

struct LiveOrderProgress: Equatable {
    enum Step: Equatable {
        case placed, confirmation, preparing, ready, transit, received
    }

    enum Marker: Equatable {
        case completed, current, upcoming
    }

    let steps: [Step]
    let currentIndex: Int
    let isFinished: Bool
    private let isPickup: Bool

    init?(stage: String, pickup: Bool?) {
        let pickupOrder = pickup == true || stage == "READY_FOR_PICKUP"
        let deliveryStage = ["READY_FOR_DELIVERY", "DELIVERING", "ARRIVED"].contains(stage)
        let showsTransit = !pickupOrder && (pickup == false || deliveryStage)
        let current: Step
        switch stage {
        case "WAITING_FOR_PAYMENT", "WAITING_FOR_RECIPIENT":
            current = .placed
        case "WAITING_FOR_ACCEPTANCE":
            current = .confirmation
        case "COOKING", "READY_FOR_REVIEW":
            current = .preparing
        case "READY_FOR_DELIVERY", "READY_FOR_PICKUP":
            current = .ready
        case "DELIVERING", "ARRIVED":
            current = showsTransit ? .transit : .ready
        case "COMPLETED":
            current = .received
        default:
            return nil
        }

        var ordered: [Step] = [.placed, .confirmation, .preparing, .ready]
        if showsTransit {
            ordered.append(.transit)
        }
        ordered.append(.received)
        guard let index = ordered.firstIndex(of: current) else { return nil }
        steps = ordered
        currentIndex = index
        isFinished = current == .received
        isPickup = pickupOrder
    }

    var stageCountLabel: String { "\(currentIndex + 1) из \(steps.count)" }

    var accessibilitySummary: String {
        let next = steps.dropFirst(currentIndex + 1).map { title(for: $0) }
        if next.isEmpty {
            return "Все этапы пройдены"
        }
        return "Этап \(stageCountLabel). Далее \(next.joined(separator: ", "))"
    }

    func marker(at index: Int) -> Marker {
        if isFinished || index < currentIndex { return .completed }
        return index == currentIndex ? .current : .upcoming
    }

    func title(for step: Step) -> String {
        switch step {
        case .placed: return "Заказ"
        case .confirmation: return "Принятие"
        case .preparing: return "Готовим"
        case .ready: return isPickup ? "К выдаче" : "Готов"
        case .transit: return "В пути"
        case .received: return "Получен"
        }
    }
}
