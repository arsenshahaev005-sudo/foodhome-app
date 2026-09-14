package market.foodhome.app.notifications

/** Only the explicit server seller-new-order event selects the custom sound. */
internal enum class PushNotificationKind(val channelId: String) {
    Update("foodhome_updates"),
    SellerNewOrder("foodhome_seller_new_orders"),
    ;

    companion object {
        fun forEvent(eventType: String): PushNotificationKind? = when (eventType) {
            "seller.order.new" -> SellerNewOrder
            "order.updated", "chat.message" -> Update
            else -> null
        }
    }
}
