package app.doomscroller.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import app.doomscroller.data.SettingsRepository
import app.doomscroller.data.sanitizeFireHex
import app.doomscroller.domain.ReelCounter
import app.doomscroller.domain.ScrollSessionTracker
import app.doomscroller.overlay.BreakOverlayService
import app.doomscroller.overlay.ChipOverlayManager
import app.doomscroller.overlay.FireWallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live session numbers for the Home tab. Updated by the service. */
object SessionStats {
    private val _sessionReels = MutableStateFlow(0L)
    val sessionReels: StateFlow<Long> = _sessionReels.asStateFlow()
    private val _sessionMs = MutableStateFlow(0L)
    val sessionMs: StateFlow<Long> = _sessionMs.asStateFlow()

    fun post(reels: Long, ms: Long) {
        _sessionReels.value = reels
        _sessionMs.value = ms
    }

    fun clear() = post(0L, 0L)
}

/**
 * Scroll-gesture watchdog. Counts scrolls + reels (fling/settle bursts)
 * in watched apps and fires the break overlay past the limit.
 *
 * Watch rules: empty watchlist = monitor everything. Otherwise only
 * listed packages count. Scrolling detected in an unlisted app triggers
 * a one-time "add to targets?" prompt (notification) instead.
 */
class ScrollDetectionService : AccessibilityService() {

    // All tracker/reel/counter state lives on the Main thread: service
    // callbacks already run there, so the ticker does too. DataStore and
    // binder calls used here are main-safe. No cross-thread mutation.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var settingsJob: Job? = null
    private var tickerJob: Job? = null

    private val tracker = ScrollSessionTracker()
    private val reels = ReelCounter()
    private lateinit var settings: SettingsRepository
    private lateinit var chip: ChipOverlayManager
    private lateinit var firewall: FireWallManager
    @Volatile private var fxLevel = 2

    @Volatile private var enabled = true
    @Volatile private var enforce = true
    @Volatile private var targets: Set<String> = emptySet()
    @Volatile private var perApp: Map<String, app.doomscroller.data.PerApp> = emptyMap()
    @Volatile private var globalThresholdMin = 10
    @Volatile private var closeStreak = 0
    @Volatile private var chipEnabled = true
    @Volatile private var fireArgb = app.doomscroller.ui.DEFAULT_FIRE
    @Volatile private var lockoutDurationMs: Long = 60 * 60 * 1000L
    private data class SavedSession(
        var reels: Long,
        var bankedMs: Long,
        var fired: Boolean,
    )
    private val savedSessions = mutableMapOf<String, SavedSession>()
    @Volatile private var foregroundPkg: String? = null
    @Volatile private var lastActivePkg: String? = null
    /** Packages that are never "the foreground app": system UI, IME, self. */
    private var nonAppPkgs: Set<String> = emptySet()
    private var sessionReels = 0L
    /** Batched lifetime counters — flushed to disk every 10s / on session end. */
    private var pendingMs = 0L
    private var pendingReels = 0L
    private var pendingSessions = 0L
    private var tickCount = 0
    /** Fire-ramp + close timing. */
    private var rampDoneFired = false
    /** 10-second wait after fire reaches 30% before closing. */
    private var closeWaitJob: Job? = null
    private var lastFromIdx = -1

    companion object {
        /** Wait time after fire reaches 30% before closing (10 seconds). */
        const val CLOSE_WAIT_MS = 10_000L
        /** Below this per-event displacement (same index) = typing jitter. */
        const val MIN_SCROLL_PX = 24
    }

    override fun onServiceConnected() {
        settings = SettingsRepository(applicationContext)
        chip = ChipOverlayManager(applicationContext)
        firewall = FireWallManager(applicationContext)
        nonAppPkgs = buildNonAppPkgs()
        settingsJob = scope.launch {
            settings.settings.collect { s ->
                enabled = s.enabled
                enforce = s.enforceMode
                targets = s.targetApps
                chipEnabled = s.chipEnabled
                globalThresholdMin = s.thresholdMinutes
                closeStreak = s.closeStreak
                fxLevel = s.fxIntensity
                fireArgb = try {
                    android.graphics.Color.parseColor(sanitizeFireHex(s.fireColor))
                } catch (_: Exception) {
                    app.doomscroller.ui.DEFAULT_FIRE
                }
                lockoutDurationMs = s.lockoutDurationMs
                if (!chipEnabled) chip.hide()
                tracker.updateThreshold(s.thresholdMinutes * 60_000L)
            }
        }
        scope.launch {
            settings.perApp.collect { perApp = it }
        }
        tickerJob = scope.launch {
            while (isActive) {
                delay(1_000L)
                val now = SystemClock.uptimeMillis()
                // Live 1Hz clock — but only while its app is foreground.
                // Backgrounded sessions freeze; nothing renders elsewhere.
                if (tracker.isSessionActive() && !tracker.isPaused() &&
                    foregroundPkg == tracker.sessionPackage()
                ) {
                    reels.onTick(now)
                    pendingMs += 1_000L
                    pushStats(now)
                }
                tickCount += 1
                if (tickCount % 10 == 0) flushPending()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            onForegroundChanged(event.packageName?.toString(), SystemClock.uptimeMillis())
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        if (!enabled) return

        val pkg = event.packageName?.toString() ?: return
        val now = SystemClock.uptimeMillis()
        val deltaY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            event.scrollDeltaY ?: 0
        } else 0
        val fromIndex = event.fromIndex

        val monitored = targets.isEmpty() || pkg in targets
        if (!monitored) return
        if (pkg in ScrollSessionTracker.IGNORED_PACKAGES) return

        // App switch ends the previous session (commit, idle chip);
        // the event below starts a fresh one — unless we stashed one.
        if (tracker.sessionPackage() != null && tracker.sessionPackage() != pkg) {
            endSession()
        }
        savedSessions.remove(pkg)?.let { saved ->
            if (tracker.sessionPackage() == null) {
                // Coming back: counts and clock resume where they left off.
                sessionReels = saved.reels
                rampDoneFired = saved.fired
                tracker.restore(pkg, saved.bankedMs, now)
            }
        }

        // Per-app threshold overrides the global one, then the close
        // streak halves it: relapse after a forced close, get half the time.
        val baseMin = perApp[pkg]?.thresholdMin ?: globalThresholdMin
        val effMin = app.doomscroller.data.effectiveLimit(baseMin, closeStreak)
        tracker.updateThreshold(effMin * 60_000L)

        val audio = try {
            (getSystemService(android.media.AudioManager::class.java))?.isMusicActive == true
        } catch (_: Exception) {
            false
        }
        // Typing jitter (tiny nudge, same index, no real travel) keeps the
        // clock alive but never counts as scrolling.
        val trivial = kotlin.math.abs(deltaY) < MIN_SCROLL_PX &&
            (fromIndex < 0 || fromIndex == lastFromIdx)
        if (fromIndex >= 0) lastFromIdx = fromIndex

        when (val ev = tracker.onScroll(now, pkg)) {
            is ScrollSessionTracker.Event.InCooldown -> Unit
            else -> {
                if (ev is ScrollSessionTracker.Event.SessionStarted ||
                    ev is ScrollSessionTracker.Event.SessionRestarted
                ) {
                    pendingSessions += 1
                }
                val before = reels.reels
                reels.onScroll(now, deltaY, fromIndex, audio)
                if (!trivial) {
                    if (reels.reels > before) {
                        sessionReels += 1
                        pendingReels += 1
                    }
                }
                lastActivePkg = pkg
                pushStats(now)
            }
        }
    }

    /**
     * System UI, keyboards and our own package are never "the foreground
     * app" — otherwise every keyboard pop fades the fire out and back
     * (visible blinking) and pauses the clock for no reason.
     */
    private fun buildNonAppPkgs(): Set<String> {
        val pkgs = ScrollSessionTracker.IGNORED_PACKAGES.toMutableSet()
        pkgs.add("android")
        pkgs.add("com.android.systemui")
        runCatching {
            val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            imm?.enabledInputMethodList?.forEach { pkgs.add(it.packageName) }
        }
        return pkgs
    }

    /**
     * Foreground tracking: fire (and the clock) belong to the watched
     * app only. Leaving it pauses the session, stashes the counts and
     * fades the fire out; returning restores everything.
     */
    private fun onForegroundChanged(pkg: String?, now: Long) {
        if (pkg == null || pkg in nonAppPkgs) return
        foregroundPkg = pkg
        val cur = tracker.sessionPackage() ?: return
        if (cur == pkg || tracker.isPaused()) return
        tracker.pause(now)
        chip.hide()
        savedSessions[cur] = SavedSession(
            sessionReels,
            tracker.sessionDurationMs(now),
            rampDoneFired,
        )
        firewall.hide()
        flushPending()
    }

    private fun flushPending() {
        val m = pendingMs
        val r = pendingReels
        val n = pendingSessions
        if (m == 0L && r == 0L && n == 0L) return
        pendingMs = 0L
        pendingReels = 0L
        pendingSessions = 0L
        // Attribution window: the batch mostly belongs to the current app.
        val app = lastActivePkg
        // NonCancellable: survives the scope.cancel() in onDestroy so the
        // final lifetime counts still land.
        scope.launch(kotlinx.coroutines.NonCancellable) {
            settings.commitBatch(n, r, m, app, r, m)
        }
    }

    private fun pushStats(now: Long) {
        val dur = tracker.sessionDurationMs(now)
        val limitMs = tracker.thresholdMs
        SessionStats.post(sessionReels, dur)
        if (chipEnabled) chip.show(sessionReels, dur, limitMs)
        // Fire starts at 90% of limit, spreads over 10% of limit
        val fireStartMs = (limitMs * 0.9).toLong()
        val fireDurationMs = (limitMs * 0.1).toLong()
        val over = (dur - fireStartMs).coerceAtLeast(0L)
        val fireProg = (over.toFloat() / fireDurationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
        // Fire burns only inside its own app.
        if (fireProg > 0f && foregroundPkg == tracker.sessionPackage()) {
            firewall.show(fireProg, fxLevel, fireArgb)
        } else {
            firewall.hide()
        }
        // Fire reached 30% — start the 10-second wait before closing
        if (fireProg >= 1f && !rampDoneFired && !tracker.isSnoozed(now)) {
            rampDoneFired = true
            startCloseWait()
        }
    }

    /** Fire reached 30% — wait 10 seconds, then close. */
    private fun startCloseWait() {
        closeWaitJob?.cancel()
        closeWaitJob = scope.launch {
            delay(CLOSE_WAIT_MS)
            // 10 seconds passed — close the app
            val pkg = lastActivePkg
            val appEnforce = if (pkg != null) perApp[pkg]?.enforce ?: enforce else enforce
            if (appEnforce) triggerBreak()
        }
    }

    private fun cancelCloseWait() {
        closeWaitJob?.cancel()
        closeWaitJob = null
    }

    private fun endSession() {
        sessionReels = 0L
        lastActivePkg = null
        lastFromIdx = -1
        rampDoneFired = false
        tracker.reset()
        SessionStats.clear()
        chip.hide()
        firewall.hide()
        flushPending()
    }

    private fun triggerBreak() {
        chip.hide()
        firewall.hide()
        BreakOverlayService.start(this)
        scope.launch { runCatching { settings.incrementCloseStreak() } }
        scope.launch {
            val snoozeMin = try {
                settings.settings.first().snoozeMinutes
            } catch (_: Exception) { 5 }
            tracker.snooze(SystemClock.uptimeMillis(), snoozeMin.coerceAtLeast(1) * 60_000L)
        }
        // Set lockout timer so reopen shows guilt-trip screen
        scope.launch {
            settings.setLockoutUntil(SystemClock.elapsedRealtime() + lockoutDurationMs)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        settingsJob?.cancel()
        tickerJob?.cancel()
        flushPending()
        scope.cancel()
        if (::chip.isInitialized) chip.destroy()
        if (::firewall.isInitialized) firewall.destroy()
        super.onDestroy()
    }
}
