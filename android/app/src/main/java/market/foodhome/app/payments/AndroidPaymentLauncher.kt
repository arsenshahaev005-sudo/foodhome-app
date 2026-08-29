package market.foodhome.app.payments

import android.content.Context
import android.content.Intent
import android.net.Uri

class AndroidPaymentLauncher(
    private val context: Context,
) : PaymentLauncher {
    override fun launch(destination: ValidatedPaymentDestination): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(destination.uri.toASCIIString())).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
