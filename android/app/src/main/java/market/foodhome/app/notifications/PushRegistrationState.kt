package market.foodhome.app.notifications

/** Persist only opaque state; never persist a raw provider token or bearer nonce here. */
data class PushRegistrationState(
    val installationId: String,
    val revision: Long = 0,
    val bindingId: String? = null,
    val tokenDigest: String? = null,
    val needsBinding: Boolean = false,
    val seenEvents: List<String> = emptyList(),
    val optedIn: Boolean = bindingId != null,
) {
    fun clear() = copy(revision = revision + 1, bindingId = null, needsBinding = false, seenEvents = emptyList(), optedIn = false)

    fun beginBinding() = clear().copy(optedIn = true, needsBinding = true)

    fun tokenChanged(digest: String): PushRegistrationState = if (tokenDigest == digest) this else copy(
        revision = revision + if (bindingId != null || tokenDigest != null) 1 else 0,
        bindingId = null,
        tokenDigest = digest,
        needsBinding = optedIn,
        seenEvents = emptyList(),
    )

    fun bound(expectedRevision: Long, receipt: String, tokenDigest: String): PushRegistrationState? =
        if (revision != expectedRevision || !optedIn) null else copy(
            bindingId = receipt, tokenDigest = tokenDigest, needsBinding = false, seenEvents = emptyList(),
        )

    fun accepts(push: VisiblePush, nowMillis: Long): Boolean = optedIn && bindingId != null &&
        push.bindingId == bindingId && push.expiresAtMillis > nowMillis &&
        push.eventId !in seenEvents

    fun record(eventId: String) = copy(seenEvents = (seenEvents + eventId).takeLast(128))

    override fun toString() = "PushRegistrationState(<redacted>)"
}
