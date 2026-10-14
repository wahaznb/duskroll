package app.doomscroller.domain

import app.doomscroller.data.FirstRunScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanHeuristicsTest {

    @Test
    fun `revanced package is keyword hit`() {
        assertTrue(FirstRunScanner.isKeywordHit("app.revanced.android.youtube", "YouTube"))
    }

    @Test
    fun `insta mod label is keyword hit`() {
        assertTrue(FirstRunScanner.isKeywordHit("com.instafel.app", "InstaFlow"))
    }

    @Test
    fun `calculator is not a hit`() {
        assertFalse(FirstRunScanner.isKeywordHit("com.android.calculator2", "Calculator"))
    }

    @Test
    fun `duplicate labels are twins`() {
        val counts = mapOf("instagram" to 2, "camera" to 1)
        assertTrue(FirstRunScanner.isTwin(counts, "Instagram"))
        assertFalse(FirstRunScanner.isTwin(counts, "Camera"))
    }

    @Test
    fun `sideload alone without keyword is not a hit`() {
        assertTrue(FirstRunScanner.reasonsFor(keyword = false, sideloaded = true, twin = false).isEmpty())
    }

    @Test
    fun `keyword plus sideload yields mod reason`() {
        val r = FirstRunScanner.reasonsFor(keyword = true, sideloaded = true, twin = false)
        assertEquals(
            setOf(FirstRunScanner.Reason.KEYWORD, FirstRunScanner.Reason.SIDELOADED_MOD),
            r,
        )
    }

    @Test
    fun `twin needs keyword or sideload`() {
        assertTrue(FirstRunScanner.reasonsFor(false, false, true).isEmpty())
        assertTrue(
            FirstRunScanner.reasonsFor(false, true, true).contains(FirstRunScanner.Reason.TWIN)
        )
    }
}
