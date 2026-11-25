import ActivityKit
import Foundation

@available(iOS 16.2, *)
struct LiveOrderAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        let stage: String

        var title: String {
            switch stage {
            case "WAITING_FOR_PAYMENT": return "Ожидает оплаты"
            case "WAITING_FOR_RECIPIENT": return "Ожидает получателя"
            case "WAITING_FOR_ACCEPTANCE": return "Отправлен продавцу"
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
