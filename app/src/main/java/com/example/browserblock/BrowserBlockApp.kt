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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    // ── Notification channels ───────────────────────────────────────────────

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val blockingChannel = NotificationChannel(
                getString(R.string.notification_channel_id),
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(blockingChannel)
        }
    }
}
