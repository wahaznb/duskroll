package app.doomscroller.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.doomscroller.data.DoomSettings
import app.doomscroller.data.FirstRunScanner
import app.doomscroller.data.SettingsRepository
import app.doomscroller.service.SessionStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app.applicationContext)

    val settings: StateFlow<DoomSettings> = repo.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), DoomSettings()
    )
    /** Lockout until timestamp (elapsedRealtime). 0 = no lockout. */
    val lockoutUntil: StateFlow<Long> = settings
        .map { it.lockoutUntil }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val sessionReels = SessionStats.sessionReels
    val sessionMs = SessionStats.sessionMs

    private val _hits = MutableStateFlow<List<FirstRunScanner.AppHit>?>(null)
    val hits: StateFlow<List<FirstRunScanner.AppHit>?> = _hits

    private val _allApps = MutableStateFlow<List<FirstRunScanner.AppEntry>>(emptyList())
    val allApps: StateFlow<List<FirstRunScanner.AppEntry>> = _allApps

    private val _appLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val appLabels: StateFlow<Map<String, String>> = _appLabels

    val perApp: StateFlow<Map<String, app.doomscroller.data.PerApp>> = repo.perApp.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap()
    )

    fun setPerApp(pkg: String, cfg: app.doomscroller.data.PerApp?) =
        viewModelScope.launch { repo.setPerApp(pkg, cfg) }

    fun runScan() = viewModelScope.launch(Dispatchers.Default) {
        _hits.value = null
        _hits.value = runCatching { FirstRunScanner.scan(getApplication()) }
            .getOrDefault(emptyList())
    }

    fun loadAllApps() = viewModelScope.launch(Dispatchers.Default) {
        val list = runCatching { FirstRunScanner.listAll(getApplication()) }
            .getOrDefault(emptyList())
        _allApps.value = list
        val pm = getApplication<Application>().packageManager
        _appLabels.value = runCatching {
            list.associate { it.packageName to it.label }
        }.getOrDefault(emptyMap())
    }

    fun setEnabled(v: Boolean) = viewModelScope.launch { repo.setEnabled(v) }
    fun setThreshold(v: Int) = viewModelScope.launch { repo.setThresholdMinutes(v) }
    fun setSnooze(v: Int) = viewModelScope.launch { repo.setSnoozeMinutes(v) }
    fun setFx(v: Int) = viewModelScope.launch { repo.setFxIntensity(v) }
    fun setChip(v: Boolean) = viewModelScope.launch { repo.setChipEnabled(v) }
    fun setFireColor(hex: String) = viewModelScope.launch { repo.setFireColor(hex) }
    fun setFireLow(hex: String) = viewModelScope.launch { repo.setFireLow(hex) }
    fun setFireHigh(hex: String) = viewModelScope.launch { repo.setFireHigh(hex) }
    fun setEnforce(v: Boolean) = viewModelScope.launch { repo.setEnforceMode(v) }
    fun addTarget(pkg: String) = viewModelScope.launch { repo.addTarget(pkg) }
    fun removeTarget(pkg: String) = viewModelScope.launch { repo.removeTarget(pkg) }
    fun clearLockout() = viewModelScope.launch { repo.clearLockout() }
    fun setGracePeriodMs(ms: Long) = viewModelScope.launch { repo.setGracePeriodMs(ms) }
    fun setLockoutDurationMs(ms: Long) = viewModelScope.launch { repo.setLockoutDurationMs(ms) }

    /** First-run confirm: watch exactly [pkgs] (empty = watch everything). */
    fun finishSetup(pkgs: Set<String>) = viewModelScope.launch {
        repo.setTargets(pkgs)
        repo.setFirstRunDone(true)
    }
}