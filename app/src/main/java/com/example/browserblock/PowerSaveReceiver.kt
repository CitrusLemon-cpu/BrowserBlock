package com.example.browserblock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * PowerSaveReceiver — reacts to power-save mode state changes.
 *
 * WHY MANIFEST-DECLARED (not dynamic):
 *   Ultra battery saver kills the app process entirely. A dynamically-registered
 *   receiver dies with the process and never fires. By declaring this receiver in
 *   the manifest, Android wakes a new process to deliver the broadcast even
 *   when the app is completely dead.
 *
 * WHAT IT DOES:
 *   When [PowerManager.ACTION_POWER_SAVE_MODE_CHANGED] fires and power-save mode
 *   has just been DEACTIVATED (i.e., ultra battery saver ended), we immediately
 *   restart [ForegroundPollingService].
 *
 *   We deliberately do nothing when power-save mode is ACTIVATED — the OS will
 *   kill or throttle us anyway; no point fighting it. The restart on deactivation
 *   is the key mechanism that survives ultra battery saver mode.
 *
 * NOTE:
 *   Some OEMs (Huawei, MIUI) do not send this broadcast reliably from their
 *   proprietary ultra battery saver. For those, the [BlockerAccessibilityService]
 *   watchdog and the JobScheduler setOverrideDeadline(0) mechanism are the
 *   reliable fallbacks (to be wired up in later implementation steps).
 */
class PowerSaveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) return

        val pm = context.getSystemService(PowerManager::class.java) ?: return

        // Only act when power-save mode has just ended (deactivated).
        // If it's now ON, the system will throttle us anyway — skip.
        if (pm.isPowerSaveMode) return

        // Power-save just ended → restart the polling service immediately.
        val serviceIntent = Intent(context, ForegroundPollingService::class.java).apply {
            action = ForegroundPollingService.ACTION_START
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
