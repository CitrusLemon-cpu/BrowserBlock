package com.example.browserblock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat

/**
 * PowerSaveReceiver — manifest-declared receiver for ACTION_POWER_SAVE_MODE_CHANGED.
 *
 * WHY MANIFEST-DECLARED (not dynamic):
 *   Ultra battery saver kills the app process entirely. A dynamically-registered
 *   receiver dies with the process and never fires. By declaring this receiver in
 *   the manifest, Android wakes a new process to deliver the broadcast even
 *   when the app is completely dead — this is the core UBS recovery mechanism.
 *
 * WHAT IT DOES (on power-save OFF):
 *   1. Immediately starts [ForegroundPollingService] — polling resumes before the
 *      user even notices UBS ended.
 *   2. If [BlockerAccessibilityService] is no longer in the enabled list (common
 *      after UBS on some OEMs), posts a persistent reminder notification prompting
 *      the user to re-enable it.
 *
 * WHAT IT DOES (on power-save ON):
 *   Nothing — the OS will kill/throttle us anyway. We survive by being manifest-
 *   declared and re-activating on the OFF event.
 */
class PowerSaveReceiver : BroadcastReceiver() {

    companion object {
        private const val REMINDER_CHANNEL_ID = "browserblock_reminders"
        internal const val REMINDER_NOTIFICATION_ID = 2001
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) return

        val pm = context.getSystemService(PowerManager::class.java) ?: return

        // Only act when power-save mode has just ended (deactivated).
        if (pm.isPowerSaveMode) return

        // Step 1: restart ForegroundPollingService immediately.
        ForegroundPollingService.start(context)
        ServiceRestartWorker.ensureScheduled(context)

        // Step 2: if accessibility was disabled by UBS, post a reminder notification.
        if (!isAccessibilityEnabled(context)) {
            postAccessibilityReminder(context)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = ComponentName(context, BlockerAccessibilityService::class.java)
        val flatFull = target.flattenToString()
        val flatShort = target.flattenToShortString()
        return enabled.split(":").any { entry ->
            entry.equals(flatFull, ignoreCase = true) ||
                entry.equals(flatShort, ignoreCase = true)
        }
    }

    private fun postAccessibilityReminder(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        // Create channel (idempotent — safe to call repeatedly)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
            }
            nm.createNotificationChannel(channel)
        }

        val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(
            context,
            REMINDER_NOTIFICATION_ID,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_text))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(context.getString(R.string.reminder_text_expanded)))
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify(REMINDER_NOTIFICATION_ID, notification)
    }
}
