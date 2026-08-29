package market.foodhome.app.navigation

/**
 * Coordinates one trusted WebView generation without becoming an authorization boundary.
 */
class NavigationCoordinator(
    private val navigationPolicy: NavigationPolicy,
) {
    private val pendingDeepLinks = PendingDeepLinkStore(navigationPolicy)
    private var navigate: ((String) -> Unit)? = null
    private var trustedDocumentReady = false
    private var nextAttachmentId = 0L
    private var nextDocumentId = 0L
    private var currentAttachment: NavigationAttachment? = null
    private var currentDocument: TrustedDocumentAttachment? = null

    @Synchronized
    fun classify(rawUrl: String?): NavigationDecision = navigationPolicy.classify(rawUrl)

    @Synchronized
    fun attach(navigate: (String) -> Unit): NavigationAttachment {
        val attachment = NavigationAttachment(++nextAttachmentId)
        currentAttachment = attachment
        currentDocument = null
        this.navigate = navigate
        trustedDocumentReady = false
        return attachment
    }

    @Synchronized
    fun detach(attachment: NavigationAttachment) {
        if (currentAttachment != attachment) return
        currentAttachment = null
        currentDocument = null
        navigate = null
        trustedDocumentReady = false
    }

    @Synchronized
    fun offerDeepLink(rawUrl: String): Boolean {
        if (!pendingDeepLinks.offer(rawUrl)) return false
        deliverPendingRouteIfReady()
        return true
    }

    @Synchronized
    fun markTrustedDocumentReady(
        attachment: NavigationAttachment,
        document: TrustedDocumentAttachment,
    ) {
        if (currentAttachment != attachment || currentDocument != document) return
        trustedDocumentReady = true
        deliverPendingRouteIfReady()
    }

    @Synchronized
    fun markTrustedDocumentLoading(
        attachment: NavigationAttachment,
    ): TrustedDocumentAttachment? {
        if (currentAttachment != attachment) return null
        val document = TrustedDocumentAttachment(++nextDocumentId)
        currentDocument = document
        trustedDocumentReady = false
        return document
    }

    private fun deliverPendingRouteIfReady() {
        val sink = navigate ?: return
        val route = pendingDeepLinks.consumeWhenReady(trustedDocumentReady) ?: return
        trustedDocumentReady = false
        currentDocument = null
        sink(route)
    }
}

data class NavigationAttachment internal constructor(internal val id: Long)
data class TrustedDocumentAttachment internal constructor(internal val id: Long)
