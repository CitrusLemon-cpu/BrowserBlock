package com.example.browserblock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * Application subclass — declared in the manifest as android:name=".BrowserBlockApp".
 *
 * Responsibilities (to be implemented in later steps):
 *  - Create the persistent notification channel used by ForegroundPollingService.
 *  - Initialise AppPreferences so the singleton is ready before any component starts.
 */
class BrowserBlockApp : Application() {

    companion object {
        const val MONITORING_CHANNEL_ID = "browserblock_monitoring_v2"
    }

    override fun onCreate() {
        super.onCreate()
        AppPreferences.init(this)
        createNotificationChannels()
        ServiceRestartWorker.ensureScheduled(this)
    }

    // ── Notification channels ───────────────────────────────────────────────

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.deleteNotificationChannel(getString(R.string.notification_channel_id))

            val blockingChannel = NotificationChannel(
                MONITORING_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(blockingChannel)
        }
    }
}
