package com.example.browserblock

import android.app.ActivityManager
import android.app.ApplicationStartInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

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
 * Guard (from Block APK analysis — BootReceiver.onReceive):
 *  On API 35+, checks [ApplicationStartInfo.wasForceStopped]. If the user
 *  explicitly force-stopped the app, we respect that and do NOT auto-restart.
 *  This prevents an infinite restart loop when the user intends to stop the app.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) return

        // Guard: do not restart if the user force-stopped the app (API 35+)
        if (Build.VERSION.SDK_INT >= 35 && wasAppForceStopped(context)) return

        startPollingService(context)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun startPollingService(context: Context) {
        val serviceIntent = Intent(context, ForegroundPollingService::class.java).apply {
            action = ForegroundPollingService.ACTION_START
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    @Suppress("NewApi") // guarded by Build.VERSION.SDK_INT >= 35 at call site
    private fun wasAppForceStopped(context: Context): Boolean {
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        val history: List<ApplicationStartInfo> = am.getHistoricalProcessStartReasons(1)
        return history.firstOrNull()?.wasForceStopped() == true
    }
}
