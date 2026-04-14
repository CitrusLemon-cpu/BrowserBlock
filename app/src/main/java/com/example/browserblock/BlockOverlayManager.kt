package com.example.browserblock

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.view.WindowManager

/**
 * BlockOverlayManager — plain Kotlin class (not an Activity or Service).
 *
 * Manages the system-alert-window overlay used to display [BlockActivity]
 * and handles the "open elsewhere" redirect flow.
 *
 * Called from [BlockerAccessibilityService] and [ForegroundPollingService]
 * when a blocking condition is detected.
 *
 * Requires SYSTEM_ALERT_WINDOW permission (Settings.canDrawOverlays).
 *
 * Two display strategies:
 *
 *  Strategy A — launch [BlockActivity]:
 *    Simplest approach. Starts BlockActivity with FLAG_ACTIVITY_NEW_TASK.
 *    Works reliably when the foreground app is a browser we monitor.
 *    BlockActivity's dedicated taskAffinity means it doesn't pollute the
 *    browser's back stack.
 *
 *  Strategy B — raw WindowManager overlay (future option):
 *    Attaches a View directly to WindowManager with TYPE_APPLICATION_OVERLAY.
 *    More aggressive; survives app-switch. Reserved for cases where
 *    Strategy A is insufficient (e.g., game engines that swallow Activities).
 *
 * "OpenElsewhere" logic:
 *    When the user taps "Open in allowed browser", [openUrlInAllowedBrowser]
 *    resolves the blocked URL and fires an Intent that excludes all watched
 *    browser packages, forcing the OS to offer or auto-select an allowed one.
 *
 * Logic to be implemented in a later step.
 */
class BlockOverlayManager(private val context: Context) {

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Show the block screen for [blockedUrl].
     * Launches [BlockActivity] with the URL passed as an extra.
     */
    fun showBlockScreen(blockedUrl: String) {
        val intent = Intent(context, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_BLOCKED_URL, blockedUrl)
        }
        context.startActivity(intent)
    }

    /**
     * Dismiss the block screen if it is currently shown.
     * TODO: implement via a shared state flag or broadcast.
     */
    fun dismissBlockScreen() {
        // TODO: send a broadcast / use a LiveData/StateFlow to tell BlockActivity to finish()
    }

    /**
     * Open [url] in a browser that is NOT in [WatchedApps.DEFAULT_BROWSERS].
     *
     * Sends a VIEW intent. By not specifying a package, the OS resolves to
     * the user's default handler. If that handler is a watched browser, the
     * system chooser is shown — the user must pick an allowed app.
     *
     * TODO: improve by filtering the chooser to exclude watched packages.
     */
    fun openUrlInAllowedBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    companion object {
        const val EXTRA_BLOCKED_URL = "extra_blocked_url"
    }
}
