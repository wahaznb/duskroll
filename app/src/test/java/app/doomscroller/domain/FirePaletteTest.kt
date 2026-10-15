package app.doomscroller.domain

import app.doomscroller.data.DEFAULT_FIRE_HEX
import app.doomscroller.data.DEFAULT_FIRE_HIGH_HEX
import app.doomscroller.data.DEFAULT_FIRE_LOW_HEX
import app.doomscroller.data.effectiveLimit
import app.doomscroller.data.sanitizeFireHex
import org.junit.Assert.assertEquals
import org.junit.Test

class FirePaletteTest {

    @Test
    fun `valid hex is uppercased`() {
        assertEquals("#FF3B2C", sanitizeFireHex("#ff3b2c"))
    }

    @Test
    fun `whitespace is trimmed`() {
        assertEquals("#FF3B2C", sanitizeFireHex("  #ff3b2c  "))
    }

    @Test
    fun `garbage falls back to default`() {
        assertEquals(DEFAULT_FIRE_HEX, sanitizeFireHex("red"))
        assertEquals(DEFAULT_FIRE_HEX, sanitizeFireHex("#FFF"))
        assertEquals(DEFAULT_FIRE_HEX, sanitizeFireHex(null))
    }

    @Test
    fun `per-stop fallbacks stay in their own lane`() {
        assertEquals(DEFAULT_FIRE_LOW_HEX, sanitizeFireHex("oops", DEFAULT_FIRE_LOW_HEX))
        assertEquals(DEFAULT_FIRE_HIGH_HEX, sanitizeFireHex("", DEFAULT_FIRE_HIGH_HEX))
    }

    @Test
    fun `streak halves the limit`() {
        assertEquals(10, effectiveLimit(10, 0))
        assertEquals(5, effectiveLimit(10, 1))
        assertEquals(2, effectiveLimit(10, 2))
    }

    @Test
    fun `streak caps at ten and never drops below one`() {
        assertEquals(1, effectiveLimit(10, 10))
        assertEquals(1, effectiveLimit(10, 100))
        assertEquals(1, effectiveLimit(1, 5))
    }
}
