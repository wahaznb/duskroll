package app.doomscroller.domain

import app.doomscroller.data.PerApp
import app.doomscroller.data.PerAppCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerAppCodecTest {

    @Test
    fun `roundtrip preserves overrides`() {
        val map = mapOf(
            "app.revanced.android.youtube" to PerApp(thresholdMin = 5, enforce = false),
            "com.instagram.android" to PerApp(enforce = true),
        )
        assertEquals(map, PerAppCodec.decode(PerAppCodec.encode(map)))
    }

    @Test
    fun `blank and garbage decode to empty`() {
        assertTrue(PerAppCodec.decode("").isEmpty())
        assertTrue(PerAppCodec.decode("{oops").isEmpty())
    }

    @Test
    fun `threshold is coerced into bounds`() {
        val m = PerAppCodec.decode("""{"a":{"t":500}}""")
        assertEquals(120, m["a"]?.thresholdMin)
    }

    @Test
    fun `missing fields stay null for global fallback`() {
        val m = PerAppCodec.decode("""{"a":{}}""")
        assertEquals(PerApp(null, null), m["a"])
    }
}
