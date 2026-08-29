package market.foodhome.app.recovery

import java.time.Duration
import java.time.Instant
import java.util.ArrayDeque

class CrashLoopBreaker(
    private val window: Duration = Duration.ofMinutes(1),
) {
    private val crashes = ArrayDeque<Instant>()
    private var activeRouteKey: String? = null

    @Synchronized
    fun recordCrash(routeKey: String, now: Instant): Boolean {
        if (activeRouteKey != routeKey) {
            crashes.clear()
            activeRouteKey = routeKey
        }
        crashes.addLast(now)
        val cutoff = now.minus(window)
        while (crashes.firstOrNull()?.isBefore(cutoff) == true) crashes.removeFirst()
        return crashes.size >= 2
    }

    @Synchronized
    fun reset() {
        crashes.clear()
        activeRouteKey = null
    }
}
