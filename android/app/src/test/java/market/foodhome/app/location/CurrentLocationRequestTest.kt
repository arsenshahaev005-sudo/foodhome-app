package market.foodhome.app.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentLocationRequestTest {
    private val fix = LocationRequestResult.Granted(51.5, 46.0, 150.0, precise = false)

    @Test fun `network succeeds while enabled GPS never finds a fix`() {
        val harness = Harness()
        harness.start()
        assertEquals(setOf("network", "gps"), harness.callbacks.keys)
        harness.reply("network", fix)
        assertEquals(listOf(fix), harness.results)
        assertEquals(setOf("network", "gps"), harness.cancelled.toSet())
        assertTrue(harness.timeoutCancelled)
        harness.reply("gps", fix.copy(latitude = 52.0))
        harness.timeout()
        assertEquals(1, harness.results.size)
    }

    @Test fun `one unavailable source does not stop the other source`() {
        val harness = Harness()
        harness.start()
        harness.reply("network", null)
        assertTrue(harness.results.isEmpty())
        harness.reply("gps", fix.copy(precise = true))
        assertEquals(listOf(fix.copy(precise = true)), harness.results)
    }

    @Test fun `failed GPS does not discard pending network result`() {
        val harness = Harness()
        harness.start()
        harness.reply("gps", null)
        assertTrue(harness.results.isEmpty())
        harness.reply("network", fix)
        assertEquals(listOf(fix), harness.results)
    }

    @Test fun `both unavailable sources finish once with cleanup`() {
        val harness = Harness()
        harness.start()
        harness.reply("network", null)
        harness.reply("gps", null)
        assertEquals("CAPABILITY_UNAVAILABLE", (harness.results.single() as LocationRequestResult.Failed).code)
        assertEquals(2, harness.cancelled.size)
        assertTrue(harness.timeoutCancelled)
    }

    @Test fun `provider exception still permits fallback`() {
        val harness = Harness(failing = "network")
        harness.start()
        harness.reply("gps", fix)
        assertEquals(listOf(fix), harness.results)
    }

    @Test fun `no available providers fails without starting sensors or timer`() {
        val harness = Harness(providers = emptyList())
        harness.start()
        assertEquals("CAPABILITY_UNAVAILABLE", (harness.results.single() as LocationRequestResult.Failed).code)
        assertTrue(harness.callbacks.isEmpty())
        assertEquals(0L, harness.delay)
    }

    @Test fun `approximate permission works with only network provider`() {
        val harness = Harness(providers = listOf("network"))
        harness.start()
        harness.reply("network", fix)
        assertEquals(listOf(fix), harness.results)
    }

    @Test fun `timeout cancels all providers and rejects late results`() {
        val harness = Harness()
        harness.start()
        assertEquals(8_000L, harness.delay)
        harness.timeout()
        assertEquals("TIMEOUT", (harness.results.single() as LocationRequestResult.Failed).code)
        assertEquals(2, harness.cancelled.size)
        harness.reply("network", fix)
        assertEquals(1, harness.results.size)
    }

    @Test fun `cancel is silent and ignores already queued callbacks`() {
        val harness = Harness()
        harness.start()
        harness.request.cancel()
        harness.reply("network", fix)
        harness.timeout()
        harness.request.cancel()
        assertTrue(harness.results.isEmpty())
        assertEquals(2, harness.cancelled.size)
    }

    @Test fun `synchronous recent fix cancels its handle and does not start GPS`() {
        val harness = Harness(immediate = fix)
        harness.start()
        assertEquals(listOf(fix), harness.results)
        assertEquals(setOf("network"), harness.callbacks.keys)
        assertEquals(listOf("network"), harness.cancelled)
        assertTrue(harness.timeoutCancelled)
    }

    @Test fun `duplicate provider callback cannot finish another provider prematurely`() {
        val harness = Harness()
        harness.start()
        harness.reply("network", null)
        harness.reply("network", fix)
        assertTrue(harness.results.isEmpty())
        harness.reply("gps", fix)
        assertEquals(listOf(fix), harness.results)
    }

    @Test fun `invalid provider coordinates do not become clamped fabricated locations`() {
        for (invalid in listOf(
            fix.copy(latitude = Double.NaN), fix.copy(latitude = 91.0),
            fix.copy(longitude = Double.POSITIVE_INFINITY), fix.copy(longitude = -181.0),
            fix.copy(accuracyMeters = -1.0), fix.copy(accuracyMeters = Double.NaN),
        )) {
            val harness = Harness()
            harness.start()
            harness.reply("network", invalid)
            assertTrue(harness.results.isEmpty())
            harness.reply("gps", fix)
            assertEquals(listOf(fix), harness.results)
        }
    }

    private class Harness(
        providers: List<String> = listOf("network", "gps"),
        failing: String? = null,
        immediate: LocationRequestResult.Granted? = null,
    ) {
        val callbacks = linkedMapOf<String, (LocationRequestResult.Granted?) -> Unit>()
        val cancelled = mutableListOf<String>()
        val results = mutableListOf<LocationRequestResult>()
        var delay = 0L
        var timeoutCancelled = false
        var timeout: () -> Unit = {}
        val request = CurrentLocationRequest(
            providers, 8_000,
            scheduleTimeout = { millis, action ->
                delay = millis
                timeout = action
                val cancel: () -> Unit = { timeoutCancelled = true }
                cancel
            },
            queryProvider = { provider, receive ->
                if (provider == failing) throw IllegalArgumentException("Provider unavailable")
                callbacks[provider] = receive
                immediate?.let(receive)
                val cancel: () -> Unit = { cancelled.add(provider) }
                cancel
            },
            completion = results::add,
        )

        fun start() = request.start()
        fun reply(provider: String, value: LocationRequestResult.Granted?) = callbacks.getValue(provider)(value)
    }
}
