package app.doomscroller.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "doomscroller_settings")

data class DoomSettings(
    val enabled: Boolean = true,
    val thresholdMinutes: Int = 10,
    val snoozeMinutes: Int = 5,
    /** Packages under watch. Empty = monitor everything (default until user restricts). */
    val targetApps: Set<String> = emptySet(),
    val firstRunDone: Boolean = false,
    /** 0 = off, 1 = reduced, 2 = full FX. */
    val fxIntensity: Int = 2,
    val chipEnabled: Boolean = true,
    /** true = break overlay past limit; false = count silently. */
    val enforceMode: Boolean = true,
    /** Fire color hex — minimal 3-color system for doom aesthetic: black, white, and user-picked accent. */
    val fireColor: String = DEFAULT_FIRE_HEX,
    val reelsTotal: Long = 0L,
    val msTotal: Long = 0L,
    val sessionsToday: Long = 0L,
    val appStats: Map<String, AppStat> = emptyMap(),
    val closeStreak: Int = 0,
    /** Lockout until timestamp (elapsedRealtime) — 1hr after forced close. */
    val lockoutUntil: Long = 0L,
    /** Grace period after fire hits 30% (milliseconds). User-configurable. */
    val gracePeriodMs: Long = 5_000L,
    /** Lockout duration after forced close (milliseconds). User-configurable. */
    val lockoutDurationMs: Long = 60 * 60 * 1000L,
)

const val DEFAULT_FIRE_HEX = "#FF3B2C"  // Bright, dramatic red — the "user-picked" accent

/** Minimal 3-color doom palette: black background, white text, bright accent for fire. */
val DOOM_COLORS = listOf(
    "#000000",  // pure black — doom darkness
    "#FFFFFF",  // pure white — doom contrast
    DEFAULT_FIRE_HEX,  // user-picked accent — #FF3B2C, bright red
)

fun sanitizeFireHex(hex: String?): String {
    val upper = hex?.uppercase() ?: return DEFAULT_FIRE_HEX
    return if (DOOM_COLORS.contains(upper)) upper else DEFAULT_FIRE_HEX
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val THRESHOLD_MIN = intPreferencesKey("threshold_minutes")
        val SNOOZE_MIN = intPreferencesKey("snooze_minutes")
        val TARGETS = stringSetPreferencesKey("target_apps")
        val FIRST_RUN = booleanPreferencesKey("first_run_done")
        val FX = intPreferencesKey("fx_intensity")
        val CHIP = booleanPreferencesKey("chip_enabled")
        val CHIP_X = intPreferencesKey("chip_x")
        val CHIP_Y = intPreferencesKey("chip_y")
        val ENFORCE = booleanPreferencesKey("enforce_mode")
        val DAY = stringPreferencesKey("counter_day")
        val FIRE = stringPreferencesKey("fire_color")
        val PERAPP = stringPreferencesKey("perapp_json")
        val REELS = longPreferencesKey("reels_total")
        val MS = longPreferencesKey("ms_total")
        val SESSIONS = longPreferencesKey("sessions_total")
        val APPSTATS = stringPreferencesKey("appstats_json")
        val STREAK = intPreferencesKey("close_streak")
        /** Lockout until timestamp (elapsedRealtime) — 1hr after forced close. */
        val LOCKOUT_UNTIL = longPreferencesKey("lockout_until")
        /** Grace period after fire hits 30% (milliseconds). User-configurable. */
        val GRACE_PERIOD_MS = longPreferencesKey("grace_period_ms")
        /** Lockout duration after forced close (milliseconds). User-configurable. */
        val LOCKOUT_DURATION_MS = longPreferencesKey("lockout_duration_ms")
    }

    val settings: Flow<DoomSettings> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
        DoomSettings(
            enabled = prefs[Keys.ENABLED] ?: true,
            thresholdMinutes = (prefs[Keys.THRESHOLD_MIN] ?: 10).coerceIn(1, 120),
            snoozeMinutes = (prefs[Keys.SNOOZE_MIN] ?: 5).coerceIn(1, 60),
            targetApps = prefs[Keys.TARGETS] ?: emptySet(),
            firstRunDone = prefs[Keys.FIRST_RUN] ?: false,
            fxIntensity = (prefs[Keys.FX] ?: 2).coerceIn(0, 2),
            chipEnabled = prefs[Keys.CHIP] ?: true,
            enforceMode = prefs[Keys.ENFORCE] ?: true,
            fireColor = sanitizeFireHex(prefs[Keys.FIRE]),
            reelsTotal = prefs[Keys.REELS] ?: 0L,
            msTotal = prefs[Keys.MS] ?: 0L,
            sessionsToday = prefs[Keys.SESSIONS] ?: 0L,
            appStats = AppStatCodec.decode(prefs[Keys.APPSTATS].orEmpty()),
            closeStreak = prefs[Keys.STREAK] ?: 0,
            lockoutUntil = prefs[Keys.LOCKOUT_UNTIL] ?: 0L,
            gracePeriodMs = prefs[Keys.GRACE_PERIOD_MS] ?: 5_000L,
            lockoutDurationMs = prefs[Keys.LOCKOUT_DURATION_MS] ?: 60 * 60 * 1000L,
        )
    }

    suspend fun setEnabled(v: Boolean) = edit { it[Keys.ENABLED] = v }
    suspend fun setThresholdMinutes(v: Int) = edit { it[Keys.THRESHOLD_MIN] = v.coerceIn(1, 120) }
    suspend fun setSnoozeMinutes(v: Int) = edit { it[Keys.SNOOZE_MIN] = v.coerceIn(1, 60) }
    suspend fun setFirstRunDone(v: Boolean) = edit { it[Keys.FIRST_RUN] = v }
    suspend fun setFxIntensity(v: Int) = edit { it[Keys.FX] = v.coerceIn(0, 2) }
    suspend fun setChipEnabled(v: Boolean) = edit { it[Keys.CHIP] = v }
    suspend fun setEnforceMode(v: Boolean) = edit { it[Keys.ENFORCE] = v }
    suspend fun incrementCloseStreak() = edit {
        it[Keys.STREAK] = (it[Keys.STREAK] ?: 0) + 1
    }
    suspend fun setFireColor(hex: String) = edit {
        it[Keys.FIRE] = sanitizeFireHex(hex)
    }
    /** Set lockout until timestamp (elapsedRealtime). 0 = no lockout. */
    suspend fun setLockoutUntil(untilMs: Long) = edit {
        it[Keys.LOCKOUT_UNTIL] = untilMs
    }
    /** Clear lockout (set to 0). */
    suspend fun clearLockout() = edit {
        it[Keys.LOCKOUT_UNTIL] = 0L
    }
    /** Set grace period after fire hits 30% (milliseconds). */
    suspend fun setGracePeriodMs(periodMs: Long) = edit {
        it[Keys.GRACE_PERIOD_MS] = periodMs.coerceIn(1000L, 30000L)
    }
    /** Set lockout duration after forced close (milliseconds). */
    suspend fun setLockoutDurationMs(durationMs: Long) = edit {
        it[Keys.LOCKOUT_DURATION_MS] = durationMs.coerceIn(60000L, 24 * 60 * 60 * 1000L)
    }

    val perApp: Flow<Map<String, PerApp>> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs -> PerAppCodec.decode(prefs[Keys.PERAPP].orEmpty()) }

    suspend fun setPerApp(pkg: String, cfg: PerApp?) {
        context.dataStore.edit { prefs ->
            val map = PerAppCodec.decode(prefs[Keys.PERAPP].orEmpty()).toMutableMap()
            if (cfg == null || (cfg.thresholdMin == null && cfg.enforce == null)) {
                map.remove(pkg)
            } else {
                map[pkg] = cfg
            }
            prefs[Keys.PERAPP] = PerAppCodec.encode(map)
        }
    }

    suspend fun setChipPos(x: Int, y: Int) = edit {
        it[Keys.CHIP_X] = x
        it[Keys.CHIP_Y] = y
    }

    suspend fun chipPos(): Pair<Int, Int> {
        return runCatching {
            val prefs = context.dataStore.data.first()
            (prefs[Keys.CHIP_X] ?: 16) to (prefs[Keys.CHIP_Y] ?: 120)
        }.getOrDefault(16 to 120)
    }

    suspend fun addTarget(pkg: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TARGETS] = (prefs[Keys.TARGETS] ?: emptySet()) + pkg
        }
    }

    suspend fun removeTarget(pkg: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TARGETS] = (prefs[Keys.TARGETS] ?: emptySet()) - pkg
        }
    }

    suspend fun setTargets(pkgs: Set<String>) {
        context.dataStore.edit { it[Keys.TARGETS] = pkgs }
    }

    /** Midnight auto-reset: counters roll over when the day changes. */
    private fun rolloverIfNeeded(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        val today = java.time.LocalDate.now().toString()
        if (prefs[Keys.DAY] != today) {
            prefs[Keys.DAY] = today
            prefs[Keys.REELS] = 0L
            prefs[Keys.MS] = 0L
            prefs[Keys.SESSIONS] = 0L
            prefs[Keys.APPSTATS] = "{}"
            prefs[Keys.STREAK] = 0
        }
    }

    suspend fun addReels(n: Long) {
        context.dataStore.edit {
            rolloverIfNeeded(it)
            it[Keys.REELS] = (it[Keys.REELS] ?: 0L) + n
        }
    }

    suspend fun addMs(n: Long) {
        context.dataStore.edit {
            rolloverIfNeeded(it)
            it[Keys.MS] = (it[Keys.MS] ?: 0L) + n
        }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    /** One batched commit: sessions, reels, ms + per-app attribution. */
    suspend fun commitBatch(sessions: Long, reels: Long, ms: Long, appPkg: String?, appReels: Long, appMs: Long) {
        context.dataStore.edit { prefs ->
            rolloverIfNeeded(prefs)
            if (sessions > 0) prefs[Keys.SESSIONS] = (prefs[Keys.SESSIONS] ?: 0L) + sessions
            if (reels > 0) prefs[Keys.REELS] = (prefs[Keys.REELS] ?: 0L) + reels
            if (ms > 0) prefs[Keys.MS] = (prefs[Keys.MS] ?: 0L) + ms
            if (appPkg != null && (appReels > 0 || appMs > 0)) {
                val cur = AppStatCodec.decode(prefs[Keys.APPSTATS].orEmpty())
                prefs[Keys.APPSTATS] = AppStatCodec.encode(AppStatCodec.merge(cur, appPkg, appReels, appMs))
            }
        }
    }
}