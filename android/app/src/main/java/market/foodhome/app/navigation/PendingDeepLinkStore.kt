package market.foodhome.app.navigation

class PendingDeepLinkStore(
    private val navigationPolicy: NavigationPolicy,
) {
    private var pendingUrl: String? = null

    @Synchronized
    fun offer(rawUrl: String): Boolean {
        val decision = navigationPolicy.classify(rawUrl)
        if (decision !is NavigationDecision.Internal) return false
        pendingUrl = decision.uri.toASCIIString()
        return true
    }

    @Synchronized
    fun consumeWhenReady(isBridgeReady: Boolean): String? {
        if (!isBridgeReady) return null
        return pendingUrl.also { pendingUrl = null }
    }
}
