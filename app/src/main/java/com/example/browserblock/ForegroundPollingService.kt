package com.example.browserblock

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * ForegroundPollingService — long-lived foreground service that monitors the
 * foreground application using [UsageStatsHelper].
 *
 * Declared in the manifest as foregroundServiceType="specialUse" with a
 * PROPERTY_SPECIAL_USE_FGS_SUBTYPE justification string (required for API 34+
 * Play Store review).
 *
 * Returns [Service.START_STICKY] from [onStartCommand] so Android will restart
 * it after process death. Coupled with [BootReceiver] and [PowerSaveReceiver]
 * for reliable restart after reboots and ultra-battery-saver exits.
 *
 * JobScheduler watchdog hook (from APK analysis — yuanli pattern):
 *   On start, schedules a one-shot JobService (to be created in a later step)
 *   with setOverrideDeadline(0) so that if the service is killed, the job fires
 *   immediately when the system resumes jobs and re-starts this service.
 *
 * Logic to be implemented in a later step.
 */
class ForegroundPollingService : Service() {

    // ── Lifecycle ───────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // TODO: begin usage-stats polling loop
        // TODO: schedule JobScheduler watchdog with setOverrideDeadline(0)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO: cancel polling coroutine / handler
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Foreground notification ─────────────────────────────────────────────

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
            .setSmallIcon(android.R.drawable.ic_menu_compass) // placeholder — replace with real icon
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001

        /** Action used by [BootReceiver] and [PowerSaveReceiver] to start/restart the service. */
        const val ACTION_START = "com.example.browserblock.action.START_POLLING"

        /** Starts or restarts the service. Safe to call when already running — START_STICKY is idempotent. */
        fun start(context: Context) {
            val intent = Intent(context, ForegroundPollingService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
