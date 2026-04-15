package com.example.browserblock

import android.app.ActivityManager
import android.app.ApplicationStartInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * BootReceiver — restarts [ForegroundPollingService] after device boot.
 *
 * Listens for:
 *  - android.intent.action.BOOT_COMPLETED          (standard)
 *  - android.intent.action.QUICKBOOT_POWERON        (OnePlus fast-boot)
 *  - com.htc.intent.action.QUICKBOOT_POWERON        (HTC fast-boot)
 *
 * Declared with android:priority="999" so it fires before other receivers.
 * Exported: false — only the system can trigger it.
 *
 * Always starts [ForegroundPollingService] on boot regardless of which permissions
 * are currently granted. The service checks permissions internally and degrades
 * gracefully — if accessibility is enabled it stops itself immediately; if
 * PACKAGE_USAGE_STATS is missing it skips polling ticks silently.
 *
 * Guard (from Block APK analysis — BootReceiver.onReceive):
 *  On API 35+, checks [ApplicationStartInfo.wasForceStopped]. If the user
 *  explicitly force-stopped the app, we respect that and do NOT auto-restart.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) return
        if (Build.VERSION.SDK_INT >= 35 && wasAppForceStopped(context)) return

        val pm = context.getSystemService(android.os.PowerManager::class.java)
        val wl = pm?.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "BrowserBlock:BootReceiver"
        )
        wl?.acquire(10_000L)

        startPollingService(context)

        if (wl?.isHeld == true) {
            wl.release()
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun startPollingService(context: Context) {
        ForegroundPollingService.start(context)
        AlarmKeepaliveReceiver.schedule(context)
    }

    @Suppress("NewApi") // guarded by Build.VERSION.SDK_INT >= 35 at call site
    private fun wasAppForceStopped(context: Context): Boolean {
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        val history: List<ApplicationStartInfo> = am.getHistoricalProcessStartReasons(1)
        return history.firstOrNull()?.wasForceStopped() == true
    }
}
