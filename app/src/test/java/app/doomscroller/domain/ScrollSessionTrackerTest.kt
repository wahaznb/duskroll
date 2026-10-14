package app.doomscroller.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollSessionTrackerTest {

    @Test
    fun `first scroll starts session`() {
        val t = ScrollSessionTracker(thresholdMs = 600_000)
        val e = t.onScroll(1_000L, "com.example.app")
        assertTrue(e is ScrollSessionTracker.Event.SessionStarted)
        assertEquals(0L, t.sessionDurationMs(1_000L))
    }

    @Test
    fun `continuous scrolling triggers threshold exactly once per session`() {
        val t = ScrollSessionTracker(thresholdMs = 600_000)
        t.onScroll(0L)
        var triggered = 0
        var time = 0L
        // scroll every 5s for 12 minutes of wall time
        while (time <= 12 * 60_000L) {
            val e = t.onScroll(time, "com.example.app")
            if (e is ScrollSessionTracker.Event.ThresholdReached) triggered++
            time += 5_000L
        }
        assertEquals(1, triggered)
    }

    @Test
    fun `idle gaps never reset the session`() {
        val t = ScrollSessionTracker(thresholdMs = 600_000)
        t.onScroll(0L, "com.example.app")
        t.onScroll(60_000L, "com.example.app")
        // 5 min idle, same app: still the same watching session
        val e = t.onScroll(60_000L + 300_000L, "com.example.app")
        assertTrue(e is ScrollSessionTracker.Event.SessionContinued)
        assertEquals(360_000L, t.sessionDurationMs(60_000L + 300_000L))
        assertEquals(3L, t.currentScrollCount())
    }

    @Test
    fun `switching apps restarts the session`() {
        val t = ScrollSessionTracker(thresholdMs = 600_000)
        t.onScroll(0L, "com.app.a")
        t.onScroll(60_000L, "com.app.a")
        val e = t.onScroll(120_000L, "com.app.b")
        assertTrue(e is ScrollSessionTracker.Event.SessionRestarted)
        assertEquals(0L, t.sessionDurationMs(120_000L))
        assertEquals("com.app.b", t.sessionPackage())
    }

    @Test
    fun `snooze suppresses trigger until expiry`() {
        val t = ScrollSessionTracker(thresholdMs = 60_000)
        t.onScroll(0L)
        t.snooze(10_000L, 300_000L)
        val e = t.onScroll(20_000L)
        assertTrue(e is ScrollSessionTracker.Event.InCooldown)
        // after snooze, fresh scrolls start a new session and can trigger
        val e2 = t.onScroll(320_000L)
        assertTrue(
            e2 is ScrollSessionTracker.Event.SessionStarted ||
                e2 is ScrollSessionTracker.Event.SessionRestarted
        )
    }

    @Test
    fun `ignored packages never start a session`() {
        val t = ScrollSessionTracker()
        t.onScroll(0L, "app.doomscroller")
        assertEquals(0L, t.currentScrollCount())
        assertEquals(0L, t.sessionDurationMs(5_000L))
    }

    @Test
    fun `session stays active without any tick`() {
        val t = ScrollSessionTracker()
        t.onScroll(0L)
        assertTrue(t.isSessionActive())
        t.reset()
        assertTrue(!t.isSessionActive())
    }

    @Test
    fun `pause freezes the clock and resume continues it`() {
        val t = ScrollSessionTracker(thresholdMs = 600_000)
        t.onScroll(0L, "com.app.a")
        t.onScroll(60_000L, "com.app.a")
        t.pause(120_000L)
        assertTrue(t.isPaused())
        // 10 minutes away: clock frozen at 2 min
        assertEquals(120_000L, t.sessionDurationMs(720_000L))
        t.resume(720_000L)
        assertTrue(!t.isPaused())
        assertEquals(180_000L, t.sessionDurationMs(780_000L))
    }

    @Test
    fun `restore brings back a stashed session`() {
        val t = ScrollSessionTracker(thresholdMs = 600_000)
        t.onScroll(0L, "com.app.a")
        t.onScroll(60_000L, "com.app.a")
        t.pause(90_000L)
        t.reset()
        assertTrue(!t.isSessionActive())
        t.restore("com.app.a", 90_000L, 200_000L)
        assertEquals("com.app.a", t.sessionPackage())
        assertEquals(110_000L, t.sessionDurationMs(220_000L))
    }
}
