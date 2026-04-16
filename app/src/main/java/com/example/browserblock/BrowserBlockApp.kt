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

        // Start ForegroundPollingService immediately on every process creation.
        // This is critical for LockScreenClean recovery: when MIUI force-stops
        // the app and Android restarts the process for NotificationListenerService,
        // this ensures our foreground service is back up within seconds — matching
        // Block's (com.wverlaek.block) behavior of restarting its FGS from
        // Application.onCreate().
        //
        // The service runs its 500ms activity polling loop at all times:
        // - When accessibility is alive: cooperative mode — polls getForegroundActivity()
        //   and triggers blocking via BlockerAccessibilityService.triggerExternalBlock()
        //   when it detects browser Activities that accessibility events missed
        //   (e.g., WeChat reusing MMWebViewUI on repeated opens).
        // - When accessibility is dead (UBS, force-stop): owns blocking directly
        //   via overlay/BlockActivity, the only detection path available.
        //
        // Guard: only start if the user has completed setup. Before setup,
        // required permissions (PACKAGE_USAGE_STATS, notification channel) may
        // not be granted yet, and starting the service would show a notification
        // before the user expects it.
        if (AppPreferences.isSetupComplete) {
            ForegroundPollingService.start(this)
        }

        // WorkManager remains as a secondary keepalive for edge cases where
        // the process is cold-started without a system-bound service trigger.
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
