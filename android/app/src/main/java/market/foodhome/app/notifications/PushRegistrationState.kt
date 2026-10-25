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
    val eventExpiries: Map<String, Long> = emptyMap(),
    val openedEvents: Set<String> = emptySet(),
    val syncRequired: Boolean = false,
) {
    fun clear() = copy(revision = revision + 1, bindingId = null, needsBinding = false,
        seenEvents = emptyList(), optedIn = false, eventExpiries = emptyMap(), openedEvents = emptySet(), syncRequired = false)

    fun beginBinding() = clear().copy(optedIn = true, needsBinding = true)

    fun tokenChanged(digest: String): PushRegistrationState = if (tokenDigest == digest) this else copy(
        revision = revision + if (bindingId != null || tokenDigest != null) 1 else 0,
        bindingId = null,
        tokenDigest = digest,
        needsBinding = optedIn,
        seenEvents = emptyList(),
        eventExpiries = emptyMap(), openedEvents = emptySet(), syncRequired = false,
    )

    fun bound(expectedRevision: Long, receipt: String, tokenDigest: String): PushRegistrationState? =
        if (revision != expectedRevision || !optedIn) null else copy(
            bindingId = receipt, tokenDigest = tokenDigest, needsBinding = false, seenEvents = emptyList(),
            eventExpiries = emptyMap(), openedEvents = emptySet(), syncRequired = false,
        )

    private fun owns(push: VisiblePush, nowMillis: Long) = optedIn && !needsBinding && bindingId != null &&
        push.bindingId == bindingId && push.expiresAtMillis > nowMillis

    private fun liveEvents(nowMillis: Long) = seenEvents.filter { (eventExpiries[it] ?: Long.MAX_VALUE) > nowMillis }

    fun accepts(push: VisiblePush, nowMillis: Long): Boolean = owns(push, nowMillis) &&
        push.eventId !in liveEvents(nowMillis) && liveEvents(nowMillis).size < MAX_EVENTS

    // Never evict a live receipt: capacity exhaustion fails closed until expiry,
    // rather than allowing a duplicate broadcast to reappear after 128 messages.
    fun record(push: VisiblePush, nowMillis: Long): PushRegistrationState? {
        if (!accepts(push, nowMillis)) return null
        val ids = liveEvents(nowMillis) + push.eventId
        return copy(seenEvents = ids,
            eventExpiries = eventExpiries.filterKeys { it in ids } + (push.eventId to push.expiresAtMillis),
            openedEvents = openedEvents.intersect(ids.toSet()))
    }

    fun opened(push: VisiblePush, nowMillis: Long): PushRegistrationState? {
        if (!owns(push, nowMillis) || push.eventId !in liveEvents(nowMillis) || push.eventId in openedEvents) return null
        if (push.expiresAtMillis > (eventExpiries[push.eventId] ?: Long.MAX_VALUE)) return null
        return copy(openedEvents = openedEvents + push.eventId)
    }

    override fun toString() = "PushRegistrationState(<redacted>)"

    companion object { const val MAX_EVENTS = 128 }
}
