package com.example.browserblock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class BlockerAccessibilityService : AccessibilityService() {

    companion object {
        private const val DEBUG_NOTIFICATION_CHANNEL_ID = "browserblock_debug"
        const val ACTION_DEBUG_BLOCK = "com.example.browserblock.DEBUG_BLOCK"
        const val ACTION_DEBUG_ALLOW = "com.example.browserblock.DEBUG_ALLOW"
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_CLASS = "extra_class"

        @Volatile var instance: BlockerAccessibilityService? = null
    }

    private val handler = Handler(Looper.getMainLooper())

    private var isBlockingActive = false
    private var accessibilitySettingsObserver: ContentObserver? = null

    private val blockEnforceRunnable = object : Runnable {
        override fun run() {
            if (!isBlockingActive) return
            if (!AppPreferences.isPaused && BlockActivity.instance == null) {
                val intent = Intent(this@BlockerAccessibilityService, BlockActivity::class.java)
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP) }
                startActivity(intent)
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (AppPreferences.isPaused) {
            isBlockingActive = false
            handler.removeCallbacks(blockEnforceRunnable)
            BlockActivity.finishIfShowing()
            handler.removeCallbacks(usageStatsCheckRunnable)
        }
    }

    private val debugActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return
            val cls = intent.getStringExtra(EXTRA_CLASS) ?: return
            when (intent.action) {
                ACTION_DEBUG_BLOCK -> AppPreferences.addUserBlockedActivity(pkg, cls)
                ACTION_DEBUG_ALLOW -> AppPreferences.addUnblockedActivity(pkg, cls)
            }
        }
    }

    private val usageStatsCheckRunnable = object : Runnable {
        override fun run() {
            if (BlockActivity.instance == null) return
            val foreground = UsageStatsHelper.getForegroundPackage(this@BlockerAccessibilityService)
            if (foreground != null &&
                !AppPreferences.isWatched(foreground) &&
                foreground != applicationContext.packageName
            ) {
                isBlockingActive = false
                handler.removeCallbacks(blockEnforceRunnable)
                BlockActivity.finishIfShowing()
                handler.removeCallbacks(this)
            } else {
                handler.postDelayed(this, 2_000L)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AppPreferences.registerListener(preferenceListener)

        val channel = NotificationChannel(
            DEBUG_NOTIFICATION_CHANNEL_ID,
            "Debug — Activity Names",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val filter = IntentFilter().apply {
            addAction(ACTION_DEBUG_BLOCK)
            addAction(ACTION_DEBUG_ALLOW)
        }
        ContextCompat.registerReceiver(
            this,
            debugActionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        serviceInfo = serviceInfo?.also { info ->
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.notificationTimeout = 100
        }

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val enabledServices = Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                val thisComponent = ComponentName(
                    this@BlockerAccessibilityService,
                    BlockerAccessibilityService::class.java
                )
                val flatFull = thisComponent.flattenToString()
                val flatShort = thisComponent.flattenToShortString()
                val stillEnabled = enabledServices.split(":").any { entry ->
                    entry.equals(flatFull, ignoreCase = true) ||
                        entry.equals(flatShort, ignoreCase = true)
                }
                if (!stillEnabled) {
                    stopSelf()
                }
            }
        }
        accessibilitySettingsObserver = observer
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            observer
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        if (AppPreferences.isPaused) {
            isBlockingActive = false
            handler.removeCallbacks(blockEnforceRunnable)
            BlockActivity.finishIfShowing()
            handler.removeCallbacks(usageStatsCheckRunnable)
            return
        }

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return

        if (className.startsWith("android.widget.") ||
            className.startsWith("android.view.") ||
            (className.startsWith("android.app.") && !className.contains("Activity"))
        ) return

        // Ignore events from our own package — BlockActivity appearing on screen
        // would otherwise cancel the active blocking state.
        if (packageName == applicationContext.packageName) return

        if (AppPreferences.isDebugMode && AppPreferences.isWatched(packageName)) {
            postDebugNotification(packageName, className)
        }

        if (!AppPreferences.isWatched(packageName)) {
            isBlockingActive = false
            handler.removeCallbacks(blockEnforceRunnable)
            BlockActivity.finishIfShowing()
            handler.removeCallbacks(usageStatsCheckRunnable)
            return
        }

        val mode = AppPreferences.getBlockingMode(packageName)
        val allowedActivities =
            (WatchedApps.curatedAllowedActivities[packageName] ?: emptySet()) +
                AppPreferences.getUnblockedActivities(packageName)
        val userBlocked = AppPreferences.getUserBlockedActivities(packageName)

        val isBrowserActivity = when (mode) {
            BlockingMode.KEYWORD ->
                className in userBlocked ||
                    (className !in allowedActivities &&
                        WatchedApps.BROWSER_KEYWORDS.any { keyword -> className.contains(keyword) })
            BlockingMode.ALLOWLIST ->
                className !in allowedActivities
        }

        if (isBrowserActivity) {
            AppPreferences.logBlockedActivity(packageName, className)
            if (!isBlockingActive) {
                isBlockingActive = true
                handler.removeCallbacks(blockEnforceRunnable)
                handler.post(blockEnforceRunnable)
            }
            handler.removeCallbacks(usageStatsCheckRunnable)
            handler.postDelayed(usageStatsCheckRunnable, 2_000L)
        } else {
            if (isBlockingActive) {
                isBlockingActive = false
                handler.removeCallbacks(blockEnforceRunnable)
                BlockActivity.finishIfShowing()
            }
            handler.removeCallbacks(usageStatsCheckRunnable)
        }
    }

    override fun onInterrupt() {
        isBlockingActive = false
        handler.removeCallbacks(blockEnforceRunnable)
        BlockActivity.finishIfShowing()
        handler.removeCallbacks(usageStatsCheckRunnable)
        ForegroundPollingService.start(this)
    }

    override fun onDestroy() {
        instance = null
        isBlockingActive = false
        handler.removeCallbacks(blockEnforceRunnable)
        BlockActivity.finishIfShowing()
        AppPreferences.unregisterListener(preferenceListener)
        accessibilitySettingsObserver?.let {
            contentResolver.unregisterContentObserver(it)
            accessibilitySettingsObserver = null
        }
        try {
            unregisterReceiver(debugActionReceiver)
        } catch (_: Exception) {
        }
        handler.removeCallbacks(usageStatsCheckRunnable)
        super.onDestroy()
    }

    private fun postDebugNotification(packageName: String, className: String) {
        val packageShortName = packageName.substringAfterLast('.')
        val classShortName = if (className.startsWith("$packageName.")) {
            className.removePrefix("$packageName.")
        } else {
            className.substringAfterLast('.')
        }
        val blockIntent = Intent(ACTION_DEBUG_BLOCK).apply {
            setPackage(this@BlockerAccessibilityService.packageName)
            putExtra(EXTRA_PACKAGE, packageName)
            putExtra(EXTRA_CLASS, className)
        }
        val allowIntent = Intent(ACTION_DEBUG_ALLOW).apply {
            setPackage(this@BlockerAccessibilityService.packageName)
            putExtra(EXTRA_PACKAGE, packageName)
            putExtra(EXTRA_CLASS, className)
        }
        val blockPi = PendingIntent.getBroadcast(
            this,
            (packageName + className + "block").hashCode(),
            blockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val allowPi = PendingIntent.getBroadcast(
            this,
            (packageName + className + "allow").hashCode(),
            allowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, DEBUG_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("[$packageShortName] → $classShortName")
            .setContentText(classShortName)
            .setSubText(packageName)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .addAction(0, "Block this", blockPi)
            .addAction(0, "Allow this", allowPi)
            .build()
        getSystemService(NotificationManager::class.java).notify(className.hashCode(), notification)
    }
}
