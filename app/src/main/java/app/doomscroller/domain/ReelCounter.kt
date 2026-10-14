package app.doomscroller.domain

/**
 * Counts short-form items (reels/shorts) from a raw scroll-event stream.
 *
 * Two modes, no double counting:
 * - INDEX mode (exact): when the scroller reports item indices
 *   (RecyclerView/ViewPager2 `fromIndex`), every forward step is one
 *   item passed. A fling jumping 5 pages counts 5. Backward scrolls
 *   re-baseline without counting. This is exact, not heuristic.
 * - BURST mode (fallback): events arriving closer together than
 *   [settleMs] form one burst; a settled burst counts 1 reel only if it
 *   really moved — chat typing jitters (tiny displacement, many events)
 *   are rejected. Audible playback confirms ambiguous mid-size bursts.
 *
 * Pure Kotlin, no Android imports — unit-tested.
 */
class ReelCounter(
    private val settleMs: Long = DEFAULT_SETTLE_MS,
    private val minBurstDisplacementPx: Int = 400,
    private val minBurstEvents: Int = 6,
    private val minTypingProofPx: Int = 48,
    private val minAudioProofPx: Int = 16,
    private val maxIndexJump: Int = 20,
) {
    private var burstDisplacement = 0
    private var burstEvents = 0
    private var burstAudio = false
    private var lastEventMs: Long? = null
    private var burstOpen = false
    private var lastFromIndex: Int? = null

    var reels: Long = 0L
        private set
    var burstsSeen: Long = 0L
        private set

    fun onScroll(nowMs: Long, deltaYPx: Int = 0, fromIndex: Int = -1, audioPlaying: Boolean = false) {
        if (fromIndex >= 0) {
            onIndexedScroll(fromIndex)
            lastEventMs = nowMs
            return
        }
        val last = lastEventMs
        if (last == null || nowMs - last > settleMs) {
            closeBurst()
            burstOpen = true
            burstDisplacement = 0
            burstEvents = 0
            burstAudio = false
        }
        lastEventMs = nowMs
        burstDisplacement += kotlin.math.abs(deltaYPx)
        burstEvents += 1
        if (audioPlaying) burstAudio = true
    }

    private fun onIndexedScroll(fromIndex: Int) {
        // Index mode is authoritative — drop any open time-burst so the
        // same fling is never counted twice.
        closeBurst()
        val last = lastFromIndex
        lastFromIndex = fromIndex
        if (last == null) return // baseline, nothing passed yet
        val advance = fromIndex - last
        if (advance in 1..maxIndexJump) {
            reels += advance
            burstsSeen += 1
        }
    }

    /**
     * Settles an idle burst. Returns true if a reel completed.
     * Call on every scroll AND from a periodic ticker.
     */
    fun onTick(nowMs: Long): Boolean {
        val last = lastEventMs ?: return false
        if (!burstOpen) return false
        if (nowMs - last > settleMs) {
            closeBurst()
            return true
        }
        return false
    }

    fun reset() {
        burstDisplacement = 0
        burstEvents = 0
        burstAudio = false
        lastEventMs = null
        burstOpen = false
        lastFromIndex = null
        reels = 0L
        burstsSeen = 0L
    }

    private fun closeBurst() {
        if (!burstOpen) return
        burstsSeen += 1
        // Real movement always counts. Chat-typing jitter (many events,
        // almost no displacement, silence) never does. Audible playback
        // confirms ambiguous mid-size bursts.
        if (burstDisplacement >= minBurstDisplacementPx ||
            (burstEvents >= minBurstEvents && burstDisplacement >= minTypingProofPx) ||
            (burstEvents >= minBurstEvents && burstDisplacement >= minAudioProofPx && burstAudio)
        ) {
            reels += 1
        }
        burstOpen = false
        burstDisplacement = 0
        burstEvents = 0
        burstAudio = false
    }

    companion object {
        const val DEFAULT_SETTLE_MS: Long = 900L
    }
}
