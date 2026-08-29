package market.foodhome.app.recovery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class CrashLoopBreakerTest {
    @Test
    fun `second same route crash inside window trips breaker`() {
        val breaker = CrashLoopBreaker(window = Duration.ofSeconds(60))
        val start = Instant.parse("2026-08-28T12:00:00Z")

        assertFalse(breaker.recordCrash("https://foodhome.market/orders/123", start))
        assertTrue(
            breaker.recordCrash("https://foodhome.market/orders/123", start.plusSeconds(10)),
        )
    }

    @Test
    fun `different route or expired crash allows one restore`() {
        val breaker = CrashLoopBreaker(window = Duration.ofSeconds(60))
        val start = Instant.parse("2026-08-28T12:00:00Z")

        assertFalse(breaker.recordCrash("https://foodhome.market/orders/123", start))
        assertFalse(breaker.recordCrash("https://foodhome.market/cart", start.plusSeconds(10)))
        assertFalse(
            breaker.recordCrash("https://foodhome.market/cart", start.plusSeconds(71)),
        )
    }
}
