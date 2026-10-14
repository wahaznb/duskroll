package app.doomscroller.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.SystemClock
import androidx.compose.ui.text.style.TextAlign
import app.doomscroller.data.PerApp
import app.doomscroller.BuildConfig
import kotlinx.coroutines.delay

@Composable
fun DoomApp(vm: HomeViewModel) {
    val dark = isSystemInDarkTheme()
    val settings by vm.settings.collectAsState()
    var tab by remember { mutableStateOf(0) }
    DoomTheme(dark = dark) {
        CrtShell(fx = settings.fxIntensity) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                DitherField(fx = settings.fxIntensity, dotColor = ink())
                Column(Modifier.fillMaxSize()) {
                    CascadeSwap(tab = tab, fx = settings.fxIntensity, modifier = Modifier.weight(1f)) {
                        if (tab == 0) HomeTab(vm) else SettingsTab(vm)
                    }
                    TabBar(tab = tab, onTab = { tab = it })
                }
            }
        }
    }
}

@Composable
private fun TabBar(tab: Int, onTab: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .border(width = 1.dp, color = ink())
            .background(MaterialTheme.colorScheme.surface)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        listOf("HOME", "SETTINGS").forEachIndexed { i, name ->
            val active = tab == i
            Box(
                Modifier.weight(1f)
                    .background(if (active) ink() else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(
                        role = androidx.compose.ui.semantics.Role.Tab,
                        onClick = { onTab(i) },
                    )
                    .semantics { selected = active }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name,
                    fontFamily = AppFont, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (active) MaterialTheme.colorScheme.background else ink(),
                )
            }
        }
    }
}


@Composable
private fun HomeTab(vm: HomeViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val sReels by vm.sessionReels.collectAsState()
    val sMs by vm.sessionMs.collectAsState()
    val fg = MaterialTheme.colorScheme.onBackground

    // Check for lockout — show timer if locked out
    val lockoutUntil by vm.lockoutUntil.collectAsState()
    val now = SystemClock.elapsedRealtime()
    val timeLeft = if (lockoutUntil > now) lockoutUntil - now else 0L

    if (lockoutUntil > now) {
        LockoutScreen(
            timeLeftMs = timeLeft,
            reelsScrolled = settings.reelsTotal,
            onDismiss = { /* lockout enforced by service */ },
        )
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DoomTitle()
        val limitMs = settings.thresholdMinutes * 60_000L
        val prog = if (limitMs > 0) (sMs.toFloat() / limitMs).coerceIn(0f, 1f) else 0f
        Text(
            "SCROLL LESS.",
            fontFamily = AppFont, color = ink(), fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        // live session readout — decodes like a WD2 profiler
        ScrambleText(
            text = "${sReels}R",
            style = TextStyle(fontFamily = AppFont, color = fg, fontSize = 30.sp),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        LinearProgressIndicator(
            progress = { prog },
            modifier = Modifier
                .fillMaxWidth().height(10.dp)
                .shake(if (prog >= 0.9f) 0.5f else 0f),
            color = ink(),
            trackColor = fg.copy(alpha = 0.15f),
        )
        Text(
            text = sessionLine(sMs, limitMs),
            fontFamily = AppFont, color = fg, fontSize = 14.sp,
        )
        AnalyticsBlock(vm)


        // COUNT vs SLAY mode switch
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SlayerButton(
                onClick = { vm.setEnforce(false) },
                modifier = Modifier.weight(1f),
                red = !settings.enforceMode,
                label = "COUNT",
            )
            SlayerButton(
                onClick = { vm.setEnforce(true) },
                modifier = Modifier.weight(1f),
                red = settings.enforceMode,
                label = "STOP",
            )
        }

        if (!settings.firstRunDone) SetupCard(vm)

        Spacer(Modifier.height(4.dp))
        PermStatusCard()
    }
}

/** Minimal lowercase app title — one font family, no outline/shadow. */
@Composable
private fun DoomTitle() {
    val fg = MaterialTheme.colorScheme.onBackground
    Text(
        text = "doomscroller",
        fontFamily = AppFont,
        fontWeight = FontWeight.Bold,
        color = fg,
        fontSize = 20.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Lockout screen: warning + timer until user can reopen the app. */
@Composable
private fun LockoutScreen(
    timeLeftMs: Long,
    reelsScrolled: Long,
    onDismiss: () -> Unit,
) {
    val fg = MaterialTheme.colorScheme.onBackground
    // 5-second guilt trip then show timer
    var warned by remember { mutableStateOf(false) }
    LaunchedEffect(timeLeftMs) {
        delay(5000L)
        warned = true
    }

    val mins = timeLeftMs / 60_000L
    val secs = (timeLeftMs % 60_000L) / 1000L

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, Color.Red)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!warned) {
            Text(
                "WARNING",
                fontFamily = AppFont,
                fontWeight = FontWeight.Bold,
                color = Color.Red,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                "You scrolled $reelsScrolled reels and still came back for more.\n\nYou can reopen the app in $mins:${"%02d".format(secs)} min/sec.",
                fontFamily = AppFont,
                color = fg,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                "Warning: scrolling detected within 5 seconds of app restart.",
                fontFamily = AppFont,
                color = Color.Red,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "App will close again if you scroll.",
                fontFamily = AppFont,
                color = fg,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Time remaining: $mins:${"%02d".format(secs)}",
                fontFamily = AppFont,
                color = fg,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Permission status — shows state, not setup nagging, when granted. */
@Composable
private fun PermStatusCard() {
    val context = LocalContext.current
    // Re-check grants continuously — no new dependency, negligible cost.
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10000L)
            tick += 1
        }
    }
    tick // re-check on every tick
    val a11y = remember(tick) { isA11yOn(context) }
    val overlay = remember(tick) { android.provider.Settings.canDrawOverlays(context) }
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, ink())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PermRow(
            name = "MONITOR",
            on = a11y,
            onOpen = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
        )
        PermRow(
            name = "OVERLAY",
            on = overlay,
            onOpen = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            },
        )
    }
}

@Composable
private fun PermRow(name: String, on: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$name: ${if (on) "● ACTIVE" else "○ OFF"}",
            fontFamily = AppFont, fontWeight = FontWeight.Bold,
            color = if (on) ink() else ink(),
            fontSize = 13.sp,
        )
        if (!on) {
            Text(
                "OPEN",
                fontFamily = AppFont, fontWeight = FontWeight.Bold,
                color = ink(),
                fontSize = 12.sp,
                modifier = Modifier.clickable(
                    role = androidx.compose.ui.semantics.Role.Button,
                    onClick = onOpen,
                ).padding(6.dp),
            )
        }
    }
}

private fun isA11yOn(context: android.content.Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val me = "${context.packageName}/.service.ScrollDetectionService"
    return enabled.split(':').any { it.equals(me, ignoreCase = true) }
}

private fun sessionLine(ms: Long, limitMs: Long): String {
    if (ms <= 0L) return "STATUS: IDLE — GO TOUCH GRASS"
    val mins = ms / 60_000L
    val left = ((limitMs - ms).coerceAtLeast(0L)) / 60_000L
    return "STATUS: SCROLLING $mins MIN — ${left}M LEFT"
}

@Composable
private fun AnalyticsBlock(vm: HomeViewModel) {
    val settings by vm.settings.collectAsState()
    val labels by vm.appLabels.collectAsState()
    LaunchedEffect(Unit) { vm.loadAllApps() }
    val fg = MaterialTheme.colorScheme.onBackground
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, fg.copy(alpha = 0.3f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("TODAY", fontFamily = AppFont, fontWeight = FontWeight.Bold, color = ink(), fontSize = 14.sp)
        Text(
            "${settings.reelsTotal} REELS // ${settings.msTotal / 60_000L} MIN // ${settings.sessionsToday} SESSIONS",
            fontFamily = AppFont, color = fg, fontSize = 13.sp,
        )
        val top = settings.appStats.entries.sortedByDescending { it.value.ms }.take(5)
        if (top.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            for ((pkg, stat) in top) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        labels[pkg] ?: pkg,
                        fontFamily = AppFont, color = fg, fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${stat.reels}R ${stat.ms / 60_000L}M",
                        fontFamily = AppFont, color = ink(), fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupCard(vm: HomeViewModel) {
    val hits by vm.hits.collectAsState()
    var selected by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(Unit) { vm.runScan() }
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, ink())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("⚠ FIRST SCAN — PICK YOUR TARGETS", fontFamily = AppFont, color = ink(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            "Suspects found on your phone (mods flagged by sideload + twin check). Tick to watch, or watch everything.",
            fontFamily = AppFont, color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp,
        )
        if (hits == null) {
            Text("SCANNING…", fontFamily = AppFont, color = ink(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        } else if (hits!!.isEmpty()) {
            Text("NO SUSPECTS. CLEAN PHONE. SUSPICIOUS.", fontFamily = AppFont, color = ink(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        } else {
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                hits!!.forEachIndexed { idx, h ->
                    val on = h.packageName in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().staggerIn(idx),
                    ) {
                        Checkbox(
                            checked = on,
                            onCheckedChange = {
                                selected = if (it) selected + h.packageName else selected - h.packageName
                            },
                            colors = CheckboxDefaults.colors(checkedColor = ink()),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(h.label, fontFamily = AppFont, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                            Text(
                                h.reasons.joinToString(" ") + (if (h.sideloaded) " SIDELOADED" else "") + " [${h.certShort}]",
                                fontFamily = AppFont, fontSize = 12.sp, color = ink(),
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SlayerButton(
                onClick = { vm.finishSetup(selected) },
                modifier = Modifier.weight(1f),
                red = true,
                label = "WATCH (${selected.size})",
            )
            SlayerButton(
                onClick = { vm.finishSetup(emptySet()) },
                modifier = Modifier.weight(1f),
                red = false,
                label = "WATCH ALL",
            )
        }
    }
}

@Composable
private fun SettingsTab(vm: HomeViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val allApps by vm.allApps.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadAllApps() }
    val fg = MaterialTheme.colorScheme.onBackground
    val labels by vm.appLabels.collectAsState()

    fun labelOf(pkg: String): String = labels[pkg] ?: pkg

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        JoJoTitle("SETTINGS")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("ENABLED", fontFamily = AppFont, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Switch(checked = settings.enabled, onCheckedChange = vm::setEnabled)
        }
        SliderRow("LIMIT", "${settings.thresholdMinutes} MIN", settings.thresholdMinutes.toFloat(), 1f..60f) {
            vm.setThreshold(it.toInt())
        }
        SliderRow("SNOOZE", "${settings.snoozeMinutes} MIN", settings.snoozeMinutes.toFloat(), 1f..30f) {
            vm.setSnooze(it.toInt())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("LIVE CHIP", fontFamily = AppFont, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Switch(checked = settings.chipEnabled, onCheckedChange = vm::setChip)
        }
        Text("FIRE COLOR", fontFamily = AppFont, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("SELECT DOOM PALETTE (3 colors: black, white, accent)", fontFamily = AppFont, color = fg.copy(alpha = 0.6f), fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (hex in app.doomscroller.data.DOOM_COLORS) {
                val selected = settings.fireColor.equals(hex, ignoreCase = true)
                Box(
                    Modifier.size(48.dp)
                        .background(
                            androidx.compose.ui.graphics.Color(
                                android.graphics.Color.parseColor(hex)
                            )
                        )
                        .border(
                            width = if (selected) 4.dp else 2.dp,
                            color = if (selected) fg else fg.copy(alpha = 0.3f),
                        )
                        .clickable(
                            role = androidx.compose.ui.semantics.Role.Button,
                            onClick = { vm.setFireColor(hex) },
                        ),
                )
            }
        }
        Text("FX INTENSITY", fontFamily = AppFont, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("OFF", "LOW", "FULL").forEachIndexed { i, name ->
                SlayerButton(
                    onClick = { vm.setFx(i) },
                    red = settings.fxIntensity == i,
                    label = name,
                )
            }
        }
        SlayerButton(
            onClick = {
                app.doomscroller.overlay.BreakActivity.start(context)
            },
            modifier = Modifier.fillMaxWidth(),
            red = false,
            label = "PREVIEW CLOSE",
        )

        Text(
            if (settings.targetApps.isEmpty()) "TARGETS: EVERYTHING" else "TARGETS: ${settings.targetApps.size} APPS",
            fontFamily = AppFont, color = ink(), fontSize = 14.sp, fontWeight = FontWeight.Bold,
        )
        settings.targetApps.forEachIndexed { idx, pkg ->
            PerAppRow(
                vm = vm,
                pkg = pkg,
                label = labelOf(pkg),
                fg = fg,
                globalThresholdMin = settings.thresholdMinutes,
                modifier = Modifier.staggerIn(idx),
            )
        }
        SlayerButton(
            onClick = { showPicker = !showPicker },
            modifier = Modifier.fillMaxWidth(),
            red = false,
            label = if (showPicker) "CLOSE ARSENAL" else "ADD TARGET",
        )
        if (showPicker) {
            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                allApps.forEach { a ->
                    val on = a.packageName in settings.targetApps
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = on,
                            onCheckedChange = { if (it) vm.addTarget(a.packageName) else vm.removeTarget(a.packageName) },
                            colors = CheckboxDefaults.colors(checkedColor = ink()),
                        )
                        Text(a.label, fontFamily = AppFont, fontSize = 13.sp, color = fg)
                        Spacer(Modifier.width(6.dp))
                        Text(a.packageName, fontFamily = AppFont, fontSize = 12.sp, color = ink())
                    }
                }
            }
        }
        AboutBlock()
    }
}

@Composable
private fun AboutBlock() {
    val context = LocalContext.current
    val info: Triple<String, Long, String> = remember {
        runCatching {
            val pm = context.packageManager
            val pkg = pm.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pkg.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toLong()
            }
            Triple(pkg.versionName ?: "?", code, BuildConfig.BUILD_TYPE)
        }.getOrDefault(Triple("?", -1L, "?"))
    }
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("ABOUT", fontFamily = AppFont, color = ink(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            "DOOMSCROLLER v${info.first} (${info.second})",
            fontFamily = AppFont,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
        )
        Text(
            "${context.packageName} • ${info.third.uppercase()} • GPL-3.0",
            fontFamily = AppFont, fontSize = 11.sp, color = ink(),
        )
    }
}

@Composable
private fun PerAppRow(
    vm: HomeViewModel,
    pkg: String,
    label: String,
    fg: Color,
    globalThresholdMin: Int,
    modifier: Modifier = Modifier,
) {
    val perApp by vm.perApp.collectAsState()
    val cfg = perApp[pkg]
    var open by remember { mutableStateOf(false) }
    val summary = buildString {
        append(cfg?.thresholdMin?.let { "${it}M" } ?: "GLOBAL")
        append(" / ")
        append(when (cfg?.enforce) { true -> "STOP"; false -> "COUNT"; null -> "GLOBAL" })
    }
    Column(
        modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, fg.copy(alpha = 0.3f))
            .padding(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = { open = !open },
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, fontFamily = AppFont, color = fg, fontSize = 13.sp)
                Text(summary, fontFamily = AppFont, color = ink(), fontSize = 11.sp)
            }
            Text(
                "SPARE ✕",
                fontFamily = AppFont, color = ink(), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(
                    role = androidx.compose.ui.semantics.Role.Button,
                    onClickLabel = "Remove target",
                    onClick = { vm.removeTarget(pkg) },
                )
                    .semantics {
                        contentDescription = "Remove $label from targets"
                    }
                    .padding(10.dp),
            )
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            val t = (cfg?.thresholdMin ?: 10).toFloat()
            var tmp by remember(cfg?.thresholdMin) { mutableStateOf(t) }
            Text(
                "LIMIT: ${tmp.toInt()} MIN (GLOBAL ${globalThresholdMin} MIN)",
                fontFamily = AppFont, color = fg, fontSize = 12.sp,
            )
            androidx.compose.material3.Slider(
                value = tmp,
                onValueChange = { tmp = it },
                onValueChangeFinished = {
                    vm.setPerApp(pkg, (cfg ?: PerApp()).copy(thresholdMin = tmp.toInt()))
                },
                valueRange = 1f..60f,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SlayerButton(
                    onClick = {
                        val next = when (cfg?.enforce) {
                            null -> true
                            true -> false
                            false -> null
                        }
                        val base = cfg ?: PerApp()
                        vm.setPerApp(pkg, base.copy(enforce = next).takeIf {
                            it.thresholdMin != null || it.enforce != null
                        })
                    },
                    red = cfg?.enforce != false,
                    label = "MODE: " + when (cfg?.enforce) {
                        true -> "STOP"
                        false -> "COUNT"
                        null -> "GLOBAL"
                    },
                )
                SlayerButton(
                    onClick = { vm.setPerApp(pkg, null) },
                    red = false,
                    label = "RESET",
                )
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val fg = MaterialTheme.colorScheme.onBackground
    // Local drag state — DataStore write fires once on release, not per pixel.
    var tmp by remember(v) { mutableStateOf(v) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontFamily = AppFont, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(value, fontFamily = AppFont, color = ink(), fontSize = 14.sp)
        }
        androidx.compose.material3.Slider(
            value = tmp,
            onValueChange = { tmp = it },
            onValueChangeFinished = { onChange(tmp) },
            valueRange = range,
        )
    }
}
