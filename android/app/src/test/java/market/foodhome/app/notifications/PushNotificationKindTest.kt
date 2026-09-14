package market.foodhome.app.notifications

import org.junit.Assert.*
import org.junit.Test

class PushNotificationKindTest {
    @Test fun `only explicit seller new order selects special channel`() {
        assertEquals(PushNotificationKind.SellerNewOrder, PushNotificationKind.forEvent("seller.order.new"))
        for (event in listOf("chat.message", "order.updated")) {
            assertEquals(PushNotificationKind.Update, PushNotificationKind.forEvent(event))
        }
        for (event in listOf("order.created", "buyer.order.new", "payment.success", "SELLER.ORDER.NEW", "")) {
            assertNull(PushNotificationKind.forEvent(event))
        }
    }

    @Test fun `stable channel identifiers agree with coordinator`() {
        assertEquals(AndroidNotificationCoordinator.UPDATES_CHANNEL_ID, PushNotificationKind.Update.channelId)
        assertEquals(AndroidNotificationCoordinator.SELLER_ORDERS_CHANNEL_ID, PushNotificationKind.SellerNewOrder.channelId)
        assertNotEquals(PushNotificationKind.Update.channelId, PushNotificationKind.SellerNewOrder.channelId)
    }
}
