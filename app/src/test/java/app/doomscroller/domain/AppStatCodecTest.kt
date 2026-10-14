package app.doomscroller.domain

import app.doomscroller.data.AppStat
import app.doomscroller.data.AppStatCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStatCodecTest {

    @Test
    fun `roundtrip preserves per-app stats`() {
        val map = mapOf(
            "com.app.a" to AppStat(reels = 3, ms = 120_000L),
            "com.app.b" to AppStat(reels = 1, ms = 5_000L),
        )
        assertEquals(map, AppStatCodec.decode(AppStatCodec.encode(map)))
    }

    @Test
    fun `blank and garbage decode to empty`() {
        assertTrue(AppStatCodec.decode("").isEmpty())
        assertTrue(AppStatCodec.decode("{oops").isEmpty())
    }

    @Test
    fun `merge accumulates`() {
        val m = AppStatCodec.merge(emptyMap(), "a", 2, 60_000L)
        val m2 = AppStatCodec.merge(m, "a", 1, 30_000L)
        assertEquals(AppStat(3, 90_000L), m2["a"])
    }

    @Test
    fun `merge ignores empty deltas`() {
        val m = AppStatCodec.merge(mapOf("a" to AppStat(1, 1L)), "b", 0, 0)
        assertTrue("b" !in m)
        assertEquals(AppStat(1, 1L), m["a"])
    }

    @Test
    fun `negative values are clamped`() {
        val m = AppStatCodec.decode("""{"a":{"r":-2,"m":-5}}""")
        assertEquals(AppStat(0, 0), m["a"])
    }
}
