package com.example.browserblock

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * ForegroundPollingService — persistent fallback service for UBS/accessibility-dead scenarios.
 *
 * PRIMARY ROLE:
 *   When [BlockerAccessibilityService] is alive, this service yields to it immediately
 *   ([onStartCommand] calls [stopSelf] if accessibility is enabled). The service is kept
 *   alive only as a fallback when accessibility is unavailable (e.g., during/after UBS).
 *
 * SECONDARY ROLE (hard-block polling mode):
 *   When accessibility is not enabled, polls [UsageStatsHelper.getForegroundPackage] every
 *   [POLL_INTERVAL_MS] ms. If a watched package is in the foreground and the user is not
 *   paused, launches [BlockActivity] directly. This is coarser than the accessibility path
 *   (no URL-level granularity — whole browser is blocked), but keeps coverage alive.
 *
 * HANDOFF LOGIC (ContentObserver on ENABLED_ACCESSIBILITY_SERVICES):
 *   - Accessibility enabled  → [stopSelf]; accessibility service takes over cleanly.
 *   - Accessibility disabled → [startPollingLoop]; remain alive in hard-block mode.
 *
 * PERMISSION DEGRADATION:
 *   If PACKAGE_USAGE_STATS is not granted, [UsageStatsHelper.getForegroundPackage] returns
 *   null. The polling loop skips that tick silently — no crash, no spin.
 */
class ForegroundPollingService : Service() {

    companion object {
        private const val TAG = "ForegroundPollingSvc"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 500L

        const val ACTION_START = "com.example.browserblock.action.START_POLLING"

        /** Safe to call from any context — idempotent if already running. */
        fun start(context: Context) {
            val intent = Intent(context, ForegroundPollingService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var accessibilityObserver: ContentObserver? = null
    private var isPolling = false

    // ── Polling runnable ────────────────────────────────────────────────────

    private val pollingRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        registerAccessibilityObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand — accessibility=${isAccessibilityServiceEnabled()}")

        if (isAccessibilityServiceEnabled()) {
            // Accessibility is alive — this service is not needed right now.
            // Stop self; the accessibility service handles blocking.
            Log.d(TAG, "Accessibility active, stopping self.")
            stopSelf()
        } else {
            // Accessibility is not available — enter polling/hard-block mode.
            startPollingLoop()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        stopPollingLoop()
        accessibilityObserver?.let {
            contentResolver.unregisterContentObserver(it)
            accessibilityObserver = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── ContentObserver ─────────────────────────────────────────────────────

    /**
     * Registers a ContentObserver on [Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES].
     *
     * Fires whenever the user or system changes which accessibility services are enabled:
     *  - If our service is now in the list → hand off and stop self.
     *  - If our service is no longer in the list (UBS just killed it) → start polling.
     */
    private fun registerAccessibilityObserver() {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (isAccessibilityServiceEnabled()) {
                    Log.d(TAG, "Accessibility re-enabled — stopping ForegroundPollingService.")
                    stopPollingLoop()
                    stopSelf()
                } else {
                    Log.d(TAG, "Accessibility disabled — switching to hard-block polling mode.")
                    startPollingLoop()
                }
            }
        }
        accessibilityObserver = observer
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            /* notifyForDescendants = */ false,
            observer
        )
    }

    // ── Polling loop ────────────────────────────────────────────────────────

    private fun startPollingLoop() {
        if (isPolling) return
        isPolling = true
        Log.d(TAG, "Starting polling loop at ${POLL_INTERVAL_MS}ms interval.")
        handler.removeCallbacks(pollingRunnable)
        handler.post(pollingRunnable)
    }

    private fun stopPollingLoop() {
        isPolling = false
        handler.removeCallbacks(pollingRunnable)
        Log.d(TAG, "Polling loop stopped.")
    }

    /**
     * One tick of the polling loop.
     *
     * Gets the foreground package via UsageStats. If it's a watched package
     * and blocking is not paused, launches [BlockActivity] (hard-block mode —
     * no URL granularity available without accessibility).
     *
     * If UsageStats permission is missing, [UsageStatsHelper.getForegroundPackage]
     * returns null and this tick is skipped silently.
     */
    private fun checkForegroundApp() {
        val pkg = UsageStatsHelper.getForegroundPackage(this) ?: return

        if (AppPreferences.isWatched(pkg) && !AppPreferences.isPaused) {
            if (BlockActivity.instance == null) {
                Log.d(TAG, "Hard-block: launching BlockActivity for $pkg")
                val blockIntent = Intent(this, BlockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(blockIntent)
            }
        } else {
            // User left the watched app — dismiss block if showing
            if (BlockActivity.instance != null && !AppPreferences.isWatched(pkg)) {
                BlockActivity.finishIfShowing()
            }
        }
    }

    // ── Accessibility check ─────────────────────────────────────────────────

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = ComponentName(this, BlockerAccessibilityService::class.java)
        val flatFull = target.flattenToString()
        val flatShort = target.flattenToShortString()
        return enabled.split(":").any { entry ->
            entry.equals(flatFull, ignoreCase = true) ||
                entry.equals(flatShort, ignoreCase = true)
        }
    }

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
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
