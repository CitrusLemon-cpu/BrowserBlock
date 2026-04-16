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
    private var isUrlScanningActive = false
    private var currentWatchedPackage: String? = null
    private var currentWatchedClassName: String? = null
    private var accessibilitySettingsObserver: ContentObserver? = null

    private val blockEnforceRunnable = object : Runnable {
        override fun run() {
            if (!isBlockingActive || AppPreferences.isPaused) return
            // Re-show the block screen if it was dismissed (e.g. system killed it).
            // HOME was already pressed at trigger time — don't press it again here.
            if (BlockActivity.instance == null) {
                val intent = Intent(this@BlockerAccessibilityService, BlockActivity::class.java)
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP) }
                startActivity(intent)
            }
            handler.postDelayed(this, 1_500L)
        }
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (AppPreferences.isPaused) {
            clearBlockingState()
            stopUrlScanning()
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

    private val urlScanRunnable = object : Runnable {
        override fun run() {
            if (!isUrlScanningActive || AppPreferences.isPaused) return
            if (AppPreferences.getBlockedKeywords().isEmpty()) {
                stopUrlScanning()
                return
            }
            performUrlScan()
            if (isUrlScanningActive && !AppPreferences.isPaused) {
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
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
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
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        if (AppPreferences.isPaused) {
            clearBlockingState()
            stopUrlScanning()
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
            clearBlockingState()
            stopUrlScanning()
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
            stopUrlScanning()
            AppPreferences.logBlockedActivity(packageName, className)
            if (!isBlockingActive) {
                isBlockingActive = true
                // Press HOME — evicts user from browser to launcher.
                // Block app (com.wverlaek.block) uses the same approach: performGlobalAction(2)
                // where 2 = GLOBAL_ACTION_HOME. BACK is wrong here — BACK just navigates
                // to the previous page in Chrome's history, keeping user in the browser.
                performGlobalAction(GLOBAL_ACTION_HOME)
                handler.removeCallbacks(blockEnforceRunnable)
                // Short delay so the HOME animation completes before BlockActivity launches.
                // Without this, BlockActivity can open on top of the browser mid-transition.
                handler.postDelayed(blockEnforceRunnable, 350L)
            }
            handler.removeCallbacks(usageStatsCheckRunnable)
            handler.postDelayed(usageStatsCheckRunnable, 2_000L)
        } else {
            if (isBlockingActive) {
                clearBlockingState()
            }
            handler.removeCallbacks(usageStatsCheckRunnable)
            startUrlScanning(packageName, className)
        }
    }

    override fun onInterrupt() {
        clearBlockingState()
        stopUrlScanning()
        ForegroundPollingService.start(this)
    }

    override fun onDestroy() {
        instance = null
        clearBlockingState()
        stopUrlScanning()
        AppPreferences.unregisterListener(preferenceListener)
        accessibilitySettingsObserver?.let {
            contentResolver.unregisterContentObserver(it)
            accessibilitySettingsObserver = null
        }
        try {
            unregisterReceiver(debugActionReceiver)
        } catch (_: Exception) {
        }
        ForegroundPollingService.start(this)
        super.onDestroy()
    }

    private fun performUrlScan() {
        val root = rootInActiveWindow ?: return
        try {
            val watchedPackage = currentWatchedPackage
            if (watchedPackage != null && root.packageName?.toString() != watchedPackage) {
                stopUrlScanning()
                return
            }

            val urls = WebViewUrlScanner.extractUrls(root)
            val match = WebViewUrlScanner.findBlockedMatch(urls, AppPreferences.getBlockedKeywords()) ?: return

            if (AppPreferences.isDebugMode) {
                postUrlScanDebugNotification(
                    packageName = watchedPackage ?: root.packageName?.toString().orEmpty(),
                    className = currentWatchedClassName ?: root.className?.toString().orEmpty(),
                    matchedUrl = match.url,
                    matchedKeyword = match.keyword
                )
            }

            if (!isBlockingActive) {
                isBlockingActive = true
                stopUrlScanning()
                performGlobalAction(GLOBAL_ACTION_HOME)
                handler.removeCallbacks(blockEnforceRunnable)
                handler.postDelayed(blockEnforceRunnable, 350L)
            }
            handler.removeCallbacks(usageStatsCheckRunnable)
            handler.postDelayed(usageStatsCheckRunnable, 2_000L)
        } finally {
            root.recycle()
        }
    }

    private fun startUrlScanning(packageName: String, className: String) {
        val blockedKeywords = AppPreferences.getBlockedKeywords()
        if (blockedKeywords.isEmpty()) {
            stopUrlScanning()
            return
        }
        currentWatchedPackage = packageName
        currentWatchedClassName = className
        if (isUrlScanningActive) return
        isUrlScanningActive = true
        handler.removeCallbacks(urlScanRunnable)
        handler.postDelayed(urlScanRunnable, 500L)
    }

    private fun stopUrlScanning() {
        isUrlScanningActive = false
        currentWatchedPackage = null
        currentWatchedClassName = null
        handler.removeCallbacks(urlScanRunnable)
    }

    private fun clearBlockingState() {
        isBlockingActive = false
        handler.removeCallbacks(blockEnforceRunnable)
        BlockActivity.finishIfShowing()
        handler.removeCallbacks(usageStatsCheckRunnable)
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

    private fun postUrlScanDebugNotification(
        packageName: String,
        className: String,
        matchedUrl: String,
        matchedKeyword: String,
    ) {
        val packageShortName = packageName.substringAfterLast('.', packageName)
        val classShortName = when {
            className.startsWith("$packageName.") -> className.removePrefix("$packageName.")
            className.isNotEmpty() -> className.substringAfterLast('.')
            else -> "Unknown"
        }
        val notification = NotificationCompat.Builder(this, DEBUG_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("[$packageShortName] URL blocked in $classShortName")
            .setContentText("\"$matchedKeyword\" matched $matchedUrl")
            .setSubText(packageName)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify((packageName + matchedKeyword + matchedUrl).hashCode(), notification)
    }
}
