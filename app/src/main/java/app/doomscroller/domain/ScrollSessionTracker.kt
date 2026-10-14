package app.doomscroller.domain

/**
 * Scroll-session state machine.
 *
 * Watching semantics: a session NEVER expires on idle gaps — a pause
 * between scrolls means the user is still watching (reading, video
 * playing). The session ends only when scrolling starts in a different
 * app, on snooze, or on explicit reset. Reels and minutes are never
 * wiped by inactivity.
 *
 * Pure Kotlin, no Android imports — unit-tested.
 */
class ScrollSessionTracker(
    thresholdMs: Long = DEFAULT_THRESHOLD_MS,
) {
    var thresholdMs: Long = thresholdMs
        private set

    private var sessionStartMs: Long? = null
    private var lastScrollMs: Long? = null
    private var scrollCount: Long = 0L
    private var triggeredForSession: Boolean = false
    private var snoozedUntilMs: Long = 0L
    private var sessionPkg: String? = null
    /** Paused wall-clock: minutes only accrue while the app is foreground. */
    private var bankedMs: Long = 0L
    private var resumeStartMs: Long = 0L
    private var paused = false

    /** Result of feeding one scroll event into the tracker. */
    sealed interface Event {
        data object SessionStarted : Event
        data object SessionContinued : Event
        data object SessionRestarted : Event
        data class ThresholdReached(val sessionDurationMs: Long, val scrollCount: Long) : Event
        data object InCooldown : Event
    }

    fun updateThreshold(thresholdMs: Long) {
        require(thresholdMs > 0) { "thresholdMs must be > 0" }
        this.thresholdMs = thresholdMs
    }

    fun onScroll(nowMs: Long, packageName: String? = null): Event {
        if (packageName != null && IGNORED_PACKAGES.contains(packageName)) {
            return Event.SessionContinued
        }
        if (nowMs < snoozedUntilMs) return Event.InCooldown

        val start = sessionStartMs
        if (start == null || (packageName != null && sessionPkg != null && sessionPkg != packageName)) {
            val restarted = start != null
            sessionPkg = packageName
            sessionStartMs = nowMs
            resumeStartMs = nowMs
            bankedMs = 0L
            paused = false
            lastScrollMs = nowMs
            scrollCount = 1L
            triggeredForSession = false
            return if (restarted) Event.SessionRestarted else Event.SessionStarted
        }

        if (paused) {
            resumeStartMs = nowMs
            paused = false
        }
        lastScrollMs = nowMs
        scrollCount += 1

        val duration = sessionDurationMs(nowMs)
        if (!triggeredForSession && duration >= thresholdMs) {
            triggeredForSession = true
            return Event.ThresholdReached(duration, scrollCount)
        }
        return Event.SessionContinued
    }

    /** Restore a stashed session (returning to an app). Counts resume. */
    fun restore(packageName: String?, banked: Long, nowMs: Long) {
        sessionPkg = packageName
        sessionStartMs = nowMs
        resumeStartMs = nowMs
        bankedMs = banked.coerceAtLeast(0L)
        paused = false
        lastScrollMs = nowMs
    }

    fun snooze(nowMs: Long, snoozeMs: Long) {
        require(snoozeMs >= 0)
        snoozedUntilMs = nowMs + snoozeMs
        resetSessionOnly()
    }

    fun isSnoozed(nowMs: Long): Boolean = nowMs < snoozedUntilMs

    /** Freeze the clock (leaving the app). Counts are kept. */
    fun pause(nowMs: Long) {
        if (sessionStartMs == null || paused) return
        bankedMs += (nowMs - resumeStartMs).coerceAtLeast(0L)
        paused = true
    }

    /** Resume the clock (back in the app). */
    fun resume(nowMs: Long) {
        if (sessionStartMs == null || !paused) return
        resumeStartMs = nowMs
        lastScrollMs = nowMs
        paused = false
    }

    fun isPaused(): Boolean = paused

    /** Clears the session but keeps any active snooze. */
    fun reset() {
        resetSessionOnly()
    }

    fun sessionDurationMs(nowMs: Long): Long {
        if (sessionStartMs == null) return 0L
        val live = if (paused) 0L else (nowMs - resumeStartMs).coerceAtLeast(0L)
        return bankedMs + live
    }

    fun currentScrollCount(): Long = scrollCount

    fun sessionPackage(): String? = sessionPkg

    fun lastScrollAtMs(): Long? = lastScrollMs

    fun isSessionActive(): Boolean = sessionStartMs != null

    private fun resetSessionOnly() {
        sessionStartMs = null
        lastScrollMs = null
        scrollCount = 0L
        triggeredForSession = false
        sessionPkg = null
        bankedMs = 0L
        resumeStartMs = 0L
        paused = false
    }

    companion object {
        const val DEFAULT_THRESHOLD_MS: Long = 10L * 60L * 1000L

        /** Packages that must never start/extend a session. */
        val IGNORED_PACKAGES: Set<String> = setOf(
            "app.doomscroller",
            "com.android.systemui",
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
        )
    }
}
