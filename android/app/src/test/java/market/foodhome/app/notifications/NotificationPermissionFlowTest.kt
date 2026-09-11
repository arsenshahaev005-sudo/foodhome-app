package market.foodhome.app.notifications

import org.junit.Assert.*
import org.junit.Test

class NotificationPermissionFlowTest {
    private class Harness {
        var status = NotificationAuthorizationStatus.NotDetermined
        var foreground = true
        var durableWriteSucceeds = true
        var throwsOnLaunch = false
        var launches = 0
        var writes = 0
        val results = mutableListOf<NotificationPermissionResult>()
        val flow = NotificationPermissionFlow(
            status = { status },
            canLaunch = { foreground },
            markAttempted = {
                writes++
                if (durableWriteSucceeds) status = NotificationAuthorizationStatus.Denied
                durableWriteSucceeds
            },
            launch = {
                assertEquals(1, writes)
                launches++
                if (throwsOnLaunch) throw IllegalStateException("Activity stopped")
            },
        )
        fun request() = flow.request(results::add)
    }

    @Test fun `first foreground request launches OS once and returns actual grant`() {
        val h = Harness()
        h.request()
        assertEquals(1, h.launches)
        assertTrue(h.results.isEmpty())
        h.status = NotificationAuthorizationStatus.Authorized
        h.flow.onResult()
        assertEquals(listOf(NotificationPermissionResult.Status(h.status)), h.results)
        h.flow.onResult()
        assertEquals(1, h.results.size)
    }

    @Test fun `already allowed including pre Android 13 needs no dialog or marker write`() {
        val h = Harness().apply { status = NotificationAuthorizationStatus.Authorized }
        h.request()
        assertEquals(listOf(NotificationPermissionResult.Status(h.status)), h.results)
        assertEquals(0, h.launches)
        assertEquals(0, h.writes)
    }

    @Test fun `blocked channel denied or unavailable is returned without another prompt`() {
        for (status in listOf(NotificationAuthorizationStatus.Denied, NotificationAuthorizationStatus.Unavailable)) {
            val h = Harness().apply { this.status = status }
            h.request()
            assertEquals(listOf(NotificationPermissionResult.Status(status)), h.results)
            assertEquals(0, h.launches)
        }
    }

    @Test fun `denial or swipe dismissal never triggers an automatic retry`() {
        val h = Harness()
        h.request()
        h.flow.onResult()
        repeat(3) { h.request() }
        assertEquals(1, h.launches)
        assertEquals(1, h.writes)
        assertEquals(4, h.results.size)
        assertTrue(h.results.all { it == NotificationPermissionResult.Status(NotificationAuthorizationStatus.Denied) })
    }

    @Test fun `simultaneous request cannot replace original callback`() {
        val h = Harness()
        val other = mutableListOf<NotificationPermissionResult>()
        h.request()
        h.flow.request(other::add)
        assertEquals(listOf(NotificationPermissionResult.Cancelled), other)
        h.status = NotificationAuthorizationStatus.Authorized
        h.flow.onResult()
        assertEquals(listOf(NotificationPermissionResult.Status(h.status)), h.results)
        assertEquals(1, h.launches)
    }

    @Test fun `renderer or auth cancellation prevents late callback being attached to a new request`() {
        val h = Harness()
        h.request()
        h.flow.cancel()
        h.flow.cancel()
        val other = mutableListOf<NotificationPermissionResult>()
        h.flow.request(other::add)
        h.status = NotificationAuthorizationStatus.Authorized
        h.flow.onResult()
        assertEquals(listOf(NotificationPermissionResult.Cancelled), h.results)
        assertEquals(listOf(NotificationPermissionResult.Cancelled), other)
        h.request()
        assertEquals(NotificationPermissionResult.Status(h.status), h.results.last())
        assertEquals(1, h.launches)
    }

    @Test fun `background request does not consume the first attempt`() {
        val h = Harness().apply { foreground = false }
        h.request()
        assertEquals(listOf(NotificationPermissionResult.Cancelled), h.results)
        assertEquals(0, h.writes)
        h.foreground = true
        h.request()
        assertEquals(1, h.launches)
    }

    @Test fun `storage failure fails closed without showing OS dialog`() {
        val h = Harness().apply { durableWriteSucceeds = false }
        h.request()
        assertEquals(listOf(NotificationPermissionResult.Status(NotificationAuthorizationStatus.Unavailable)), h.results)
        assertEquals(0, h.launches)
    }

    @Test fun `launch exception completes once and does not produce retry loop`() {
        val h = Harness().apply { throwsOnLaunch = true }
        h.request()
        assertEquals(listOf(NotificationPermissionResult.Cancelled), h.results)
        h.flow.onResult()
        h.request()
        assertEquals(2, h.results.size)
        assertEquals(NotificationPermissionResult.Status(NotificationAuthorizationStatus.Denied), h.results.last())
        assertEquals(1, h.launches)
    }

    @Test fun `recreated owner honors the persistent attempt without another prompt`() {
        val h = Harness()
        h.request()
        val recreated = NotificationPermissionFlow({ h.status }, { true }, { error("must not write") }, { error("must not launch") })
        recreated.request(h.results::add)
        assertEquals(listOf(NotificationPermissionResult.Status(NotificationAuthorizationStatus.Denied)), h.results)
    }

    @Test fun `settings grant after refusal is recognized without another request`() {
        val h = Harness().apply { status = NotificationAuthorizationStatus.Denied }
        h.request()
        h.status = NotificationAuthorizationStatus.Authorized
        h.request()
        assertEquals(NotificationPermissionResult.Status(h.status), h.results.last())
        assertEquals(0, h.launches)
    }

    @Test fun `pre Android 13 disabled app is denied not undetermined`() {
        assertEquals(NotificationAuthorizationStatus.Denied, policy(runtime = false, enabled = false))
        assertEquals(NotificationAuthorizationStatus.Authorized, policy(runtime = false))
    }

    @Test fun `Android 13 distinguishes first attempt from completed or interrupted attempt`() {
        assertEquals(NotificationAuthorizationStatus.NotDetermined, policy(granted = false, enabled = false))
        assertEquals(NotificationAuthorizationStatus.Denied, policy(granted = false, enabled = false, attempted = true))
        assertEquals(NotificationAuthorizationStatus.Authorized, policy(attempted = true))
    }

    @Test fun `channel block takes precedence even when runtime grant exists or was never requested`() {
        assertEquals(NotificationAuthorizationStatus.Denied, policy(blocked = true))
        assertEquals(NotificationAuthorizationStatus.Denied, policy(granted = false, blocked = true))
        assertEquals(NotificationAuthorizationStatus.Denied, policy(runtime = false, blocked = true))
    }

    @Test fun `global OS block takes precedence over runtime grant`() {
        assertEquals(NotificationAuthorizationStatus.Denied, policy(enabled = false))
    }

    private fun policy(
        runtime: Boolean = true,
        granted: Boolean = true,
        enabled: Boolean = true,
        blocked: Boolean = false,
        attempted: Boolean = false,
    ) = NotificationPermissionPolicy.status(runtime, granted, enabled, blocked, attempted)
}
