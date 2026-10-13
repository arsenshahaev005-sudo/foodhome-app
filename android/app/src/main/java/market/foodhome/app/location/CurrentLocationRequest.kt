package market.foodhome.app.location

/** One foreground request; all callbacks and cancellation run on the main thread. */
internal class CurrentLocationRequest(
    providers: List<String>,
    private val timeoutMillis: Long,
    private val scheduleTimeout: (Long, () -> Unit) -> (() -> Unit),
    private val queryProvider: (String, (LocationRequestResult.Granted?) -> Unit) -> (() -> Unit),
    private val completion: (LocationRequestResult) -> Unit,
) {
    private val pending = providers.distinct().toMutableSet()
    private val cancellations = mutableListOf<() -> Unit>()
    private var cancelTimeout: (() -> Unit)? = null
    private var completed = false
    private var started = false

    fun start() {
        check(!started)
        started = true
        if (pending.isEmpty()) {
            finish(unavailable())
            return
        }
        cancelTimeout = scheduleTimeout(timeoutMillis) {
            finish(LocationRequestResult.Failed("TIMEOUT", "Location request timed out", retryable = true))
        }
        // Register every available source before waiting: an enabled GPS can have no fix indoors.
        for (provider in pending.toList()) {
            if (completed) break
            try {
                val cancel = queryProvider(provider) { location ->
                    if (completed || !pending.remove(provider)) return@queryProvider
                    if (location != null && location.isValid()) finish(location)
                    else if (pending.isEmpty()) finish(unavailable())
                }
                // A recent fix may be delivered synchronously before registration returns.
                if (completed) runCatching(cancel) else cancellations.add(cancel)
            } catch (_: Exception) {
                pending.remove(provider)
                if (pending.isEmpty()) finish(unavailable())
            }
        }
    }

    fun cancel() {
        if (completed) return
        completed = true
        cleanup()
    }

    private fun finish(result: LocationRequestResult) {
        if (completed) return
        completed = true
        cleanup()
        completion(result)
    }

    private fun cleanup() {
        cancelTimeout?.let { runCatching(it) }
        cancelTimeout = null
        cancellations.forEach { runCatching(it) }
        cancellations.clear()
        pending.clear()
    }

    private fun unavailable() = LocationRequestResult.Failed(
        "CAPABILITY_UNAVAILABLE", "Location is unavailable", retryable = true,
    )

    private fun LocationRequestResult.Granted.isValid() =
        latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude.isFinite() && longitude in -180.0..180.0 &&
            accuracyMeters.isFinite() && accuracyMeters in 0.0..100_000.0
}
