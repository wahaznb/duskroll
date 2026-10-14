package app.doomscroller.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelCounterTest {

    @Test
    fun `one fling burst settles into one reel`() {
        val r = ReelCounter(settleMs = 900, minBurstDisplacementPx = 400)
        // rapid scrolls with big deltas = one fling through a reel
        var t = 0L
        repeat(8) { r.onScroll(t, 300); t += 100L }
        assertEquals(0L, r.reels) // not settled yet
        assertTrue(r.onTick(t + 1_000L))
        assertEquals(1L, r.reels)
    }

    @Test
    fun `tiny jitter burst does not count`() {
        val r = ReelCounter(settleMs = 900, minBurstDisplacementPx = 400, minBurstEvents = 99)
        var t = 0L
        repeat(3) { r.onScroll(t, 10); t += 100L }
        r.onTick(t + 2_000L)
        assertEquals(0L, r.reels)
        assertEquals(1L, r.burstsSeen)
    }

    @Test
    fun `three separate flings count three reels`() {
        val r = ReelCounter(settleMs = 900)
        var t = 0L
        repeat(3) {
            repeat(6) { r.onScroll(t, 500); t += 80L }
            t += 2_000L
            r.onTick(t)
        }
        assertEquals(3L, r.reels)
    }

    @Test
    fun `no deltas fallback needs real displacement`() {
        val r = ReelCounter(settleMs = 900)
        var t = 0L
        repeat(7) { r.onScroll(t, 0); t += 100L } // API < 28, no deltas, no movement
        assertTrue(r.onTick(t + 1_000L))
        assertEquals(0L, r.reels)
    }

    @Test
    fun `chat typing jitter never counts`() {
        val r = ReelCounter(settleMs = 900)
        var t = 0L
        // 12 keystrokes nudging the field a few px each, silence
        repeat(12) { r.onScroll(t, 3, -1, false); t += 70L }
        r.onTick(t + 2_000L)
        assertEquals(0L, r.reels)
    }

    @Test
    fun `audible playback confirms mid-size bursts`() {
        val r = ReelCounter(settleMs = 900)
        var t = 0L
        repeat(7) { r.onScroll(t, 5, -1, true); t += 100L }
        r.onTick(t + 2_000L)
        assertEquals(1L, r.reels)
    }

    @Test
    fun `tick with no burst returns false`() {
        val r = ReelCounter()
        assertFalse(r.onTick(10_000L))
    }

    @Test
    fun `index mode counts exact page advances`() {
        val r = ReelCounter()
        r.onScroll(0L, 0, 0) // baseline
        r.onScroll(100L, 0, 1)
        r.onScroll(200L, 0, 2)
        assertEquals(2L, r.reels)
    }

    @Test
    fun `index jump across one fling counts each page`() {
        val r = ReelCounter()
        r.onScroll(0L, 0, 4)
        r.onScroll(120L, 0, 9)
        assertEquals(5L, r.reels)
    }

    @Test
    fun `scrolling back up does not count but rebaselines`() {
        val r = ReelCounter()
        r.onScroll(0L, 0, 5)
        r.onScroll(100L, 0, 3) // back up: no count
        assertEquals(0L, r.reels)
        r.onScroll(200L, 0, 4) // down again past baseline: counts 1
        assertEquals(1L, r.reels)
    }

    @Test
    fun `index mode suppresses burst double count`() {
        val r = ReelCounter()
        var t = 0L
        repeat(8) { r.onScroll(t, 300, 7); t += 100L } // same index: a held page
        r.onTick(t + 2_000L)
        assertEquals(0L, r.reels)
    }
}
