package app.doomscroller.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * First-run scan for scroll-trap candidates, including modded clients
 * (ReVanced, Vanced, Insta mods, TikTok mods…) without relying on
 * hardcoded package names.
 *
 * Fully offline heuristics:
 * - KEYWORD: package or label looks like a known feed/video app or mod
 *   ("revanced", "vanced", "insta", "tiktok", "shorts", "reels", "tube"…).
 * - SIDELOADED: not installed from Play Store (mods usually are sideloaded).
 *   SIDELOADED alone is NOT a hit — only combined with KEYWORD.
 * - TWIN: two installed apps share the same launcher label
 *   (e.g. two "Instagram"s) — one of them is almost certainly a mod.
 * - Cert fingerprint shown so the user can compare twins.
 *
 * Results are presented to the user for confirmation — nothing is
 * auto-added to the watchlist.
 */
object FirstRunScanner {

    enum class Reason { KEYWORD, SIDELOADED_MOD, TWIN }

    data class AppHit(
        val packageName: String,
        val label: String,
        val reasons: Set<Reason>,
        val sideloaded: Boolean,
        val certShort: String,
    )

    data class AppEntry(val packageName: String, val label: String)

    /** All launchable apps, for the manual target picker. */
    fun listAll(context: Context): List<AppEntry> =
        launcherEntries(context).sortedBy { it.label.lowercase(java.util.Locale.ROOT) }

    private fun launcherEntries(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        return pm.queryIntentActivities(launcher, 0).mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            AppEntry(pkg, ri.loadLabel(pm)?.toString() ?: pkg)
        }.distinctBy { it.packageName }
    }

    private val KEYWORDS = listOf(
        "revanced", "vanced", "reels", "shorts", "tiktok", "instagram",
        "insta", "facebook", "twitter", "youtube",
        "snapchat", "reddit", "pinterest", "threads", "likee", "kwai",
        "triller", "bilibili",
    )

    fun scan(context: Context): List<AppHit> {
        val pm = context.packageManager
        val entries = launcherEntries(context)
        if (entries.isEmpty()) return emptyList()

        val labelCounts = entries.groupingBy { it.label.lowercase(java.util.Locale.ROOT) }.eachCount()

        return entries.mapNotNull { e ->
            val keyword = isKeywordHit(e.packageName, e.label)
            val sideloaded = isSideloaded(pm, e.packageName)
            val twin = isTwin(labelCounts, e.label)

            val reasons = reasonsFor(keyword, sideloaded, twin)
            if (reasons.isEmpty()) return@mapNotNull null
            AppHit(e.packageName, e.label, reasons, sideloaded, certShort(pm, e.packageName))
        }.sortedWith(
            compareByDescending<AppHit> { Reason.SIDELOADED_MOD in it.reasons }
                .thenByDescending { Reason.TWIN in it.reasons }
                .thenBy { it.label.lowercase(java.util.Locale.ROOT) }
        )
    }

    /** Pure heuristics — JVM-testable without a PackageManager. */
    fun isKeywordHit(pkg: String, label: String): Boolean {
        val haystack = "$pkg $label".lowercase(java.util.Locale.ROOT)
        return KEYWORDS.any { it in haystack }
    }

    fun isTwin(labelCounts: Map<String, Int>, label: String): Boolean =
        (labelCounts[label.lowercase(java.util.Locale.ROOT)] ?: 1) > 1

    fun reasonsFor(keyword: Boolean, sideloaded: Boolean, twin: Boolean): Set<Reason> =
        buildSet {
            if (keyword) add(Reason.KEYWORD)
            if (keyword && sideloaded) add(Reason.SIDELOADED_MOD)
            if (twin && (keyword || sideloaded)) add(Reason.TWIN)
        }

    private fun isSideloaded(pm: PackageManager, pkg: String): Boolean {
        return try {
            val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(pkg).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(pkg)
            }
            source == null || !source.contains("vending", ignoreCase = true)
        } catch (_: Exception) {
            true
        }
    }

    private fun certShort(pm: PackageManager, pkg: String): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            }
            val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }
            val first = sigs?.firstOrNull()?.toByteArray() ?: return "??"
            val digest = MessageDigest.getInstance("SHA-256").digest(first)
            digest.take(4).joinToString("") { "%02X".format(java.util.Locale.US, it) }
        } catch (_: Exception) {
            "??"
        }
    }
}
