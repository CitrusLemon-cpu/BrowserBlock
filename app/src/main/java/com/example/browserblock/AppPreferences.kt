package com.example.browserblock

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * AppPreferences — singleton SharedPreferences wrapper.
 *
 * Single source of truth for all persisted user settings.
 * Initialise once in [BrowserBlockApp.onCreate] via [AppPreferences.init].
 *
 * All keys are private constants; access is through typed properties.
 * This prevents scattered magic-string keys across the codebase.
 *
 * Thread-safety: SharedPreferences reads/writes on the calling thread.
 * For write-heavy operations use [edit { ... }] with apply() (async).
 *
 * Logic (actual default values and validation) to be implemented in later steps.
 */
object AppPreferences {

    private const val PREFS_NAME = "browserblock_prefs"

    // ── Key constants ───────────────────────────────────────────────────────

    private const val KEY_SETUP_COMPLETE = "setup_complete"
    private const val KEY_BLOCKING_MODE = "blocking_mode"
    private const val KEY_WATCHED_PACKAGES = "watched_packages"    // JSON array string
    private const val KEY_BLOCKED_KEYWORDS = "blocked_keywords"    // JSON array string
    private const val KEY_ALLOWLIST_URLS = "allowlist_urls"        // JSON array string
    private const val KEY_IS_PAUSED = "is_paused"
    private const val KEY_IS_DEBUG_MODE = "is_debug_mode"
    private const val KEY_USER_BLOCKED_PREFIX = "user_blocked_"
    private const val KEY_UNBLOCKED_PREFIX = "unblocked_"
    private const val KEY_PKG_BLOCKING_MODE_PREFIX = "pkg_mode_"
    private const val KEY_BLOCK_LOG_PREFIX = "block_log_"
    private const val MAX_BLOCK_LOG_ENTRIES = 50
    private const val KEY_IS_BLOCKING_ACTIVE = "is_blocking_active"
    private const val KEY_DEVICE_ADMIN_ENABLED = "device_admin_enabled"
    private const val KEY_NOTIFICATION_LISTENER_ENABLED = "notification_listener_enabled"
    private const val KEY_BATTERY_EXEMPT = "battery_exempt"
    private const val KEY_USAGE_STATS_GRANTED = "usage_stats_granted"

    // ── Internal state ──────────────────────────────────────────────────────

    private lateinit var prefs: SharedPreferences

    /** Must be called once from [BrowserBlockApp.onCreate] before any component reads prefs. */
    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ── Properties ──────────────────────────────────────────────────────────

    /** True once the user completes the SetupActivity onboarding flow. */
    var isSetupComplete: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) = prefs.edit { putBoolean(KEY_SETUP_COMPLETE, value) }

    /** The current [BlockingMode]. Defaults to KEYWORD on first install. */
    var blockingMode: BlockingMode
        get() = BlockingMode.valueOf(
            prefs.getString(KEY_BLOCKING_MODE, BlockingMode.KEYWORD.name) ?: BlockingMode.KEYWORD.name
        )
        set(value) = prefs.edit { putString(KEY_BLOCKING_MODE, value.name) }

    /** Whether [ForegroundPollingService] is currently active. */
    var isBlockingActive: Boolean
        get() = prefs.getBoolean(KEY_IS_BLOCKING_ACTIVE, false)
        set(value) = prefs.edit { putBoolean(KEY_IS_BLOCKING_ACTIVE, value) }

    /** Whether Device Admin is activated (enables screen-lock fallback). */
    var isDeviceAdminEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEVICE_ADMIN_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_DEVICE_ADMIN_ENABLED, value) }

    /** Whether Notification Listener access has been granted. */
    var isNotificationListenerEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_LISTENER_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFICATION_LISTENER_ENABLED, value) }

    /** Whether the app is exempt from battery optimisation. */
    var isBatteryExempt: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_EXEMPT, false)
        set(value) = prefs.edit { putBoolean(KEY_BATTERY_EXEMPT, value) }

    /** Whether PACKAGE_USAGE_STATS has been granted. */
    var isUsageStatsGranted: Boolean
        get() = prefs.getBoolean(KEY_USAGE_STATS_GRANTED, false)
        set(value) = prefs.edit { putBoolean(KEY_USAGE_STATS_GRANTED, value) }

    // ── Raw JSON accessors (serialisation handled by callers) ───────────────

    /** JSON array of watched package names (additions to [WatchedApps.DEFAULT_BROWSERS]). */
    var watchedPackagesJson: String
        get() = prefs.getString(KEY_WATCHED_PACKAGES, "[]") ?: "[]"
        set(value) = prefs.edit { putString(KEY_WATCHED_PACKAGES, value) }

    /** JSON array of blocked keyword strings (used in [BlockingMode.KEYWORD]). */
    var blockedKeywordsJson: String
        get() = prefs.getString(KEY_BLOCKED_KEYWORDS, "[]") ?: "[]"
        set(value) = prefs.edit { putString(KEY_BLOCKED_KEYWORDS, value) }

    /** JSON array of allowed URL patterns (used in [BlockingMode.ALLOWLIST]). */
    var allowlistUrlsJson: String
        get() = prefs.getString(KEY_ALLOWLIST_URLS, "[]") ?: "[]"
        set(value) = prefs.edit { putString(KEY_ALLOWLIST_URLS, value) }

    /** Whether blocking is temporarily paused by the user. */
    var isPaused: Boolean
        get() = prefs.getBoolean(KEY_IS_PAUSED, false)
        set(value) = prefs.edit { putBoolean(KEY_IS_PAUSED, value) }

    /** Whether debug mode is active (shows notification for every watched-app Activity). */
    var isDebugMode: Boolean
        get() = prefs.getBoolean(KEY_IS_DEBUG_MODE, false)
        set(value) = prefs.edit { putBoolean(KEY_IS_DEBUG_MODE, value) }

    /**
     * Returns true if [packageName] is currently being monitored.
     * Checks both [WatchedApps.DEFAULT_BROWSERS] and any user-added packages.
     */
    fun isWatched(packageName: String): Boolean {
        if (packageName in WatchedApps.NEVER_BLOCK) return false
        if (packageName in WatchedApps.DEFAULT_BROWSERS) return true
        return parseJsonStringArray(watchedPackagesJson).contains(packageName)
    }

    /**
     * Returns the [BlockingMode] for [packageName].
     * Falls back to the global [blockingMode] if no per-package override is set.
     */
    fun getBlockingMode(packageName: String): BlockingMode {
        val overrideName = prefs.getString(KEY_PKG_BLOCKING_MODE_PREFIX + packageName, null)
            ?: return blockingMode
        return try {
            BlockingMode.valueOf(overrideName)
        } catch (_: Exception) {
            blockingMode
        }
    }

    /** Returns the set of Activity class names the user has explicitly allowed for [packageName]. */
    fun getUnblockedActivities(packageName: String): Set<String> =
        parseJsonStringArray(prefs.getString(KEY_UNBLOCKED_PREFIX + packageName, "[]") ?: "[]")

    /** Returns the set of Activity class names the user has explicitly blocked for [packageName]. */
    fun getUserBlockedActivities(packageName: String): Set<String> =
        parseJsonStringArray(prefs.getString(KEY_USER_BLOCKED_PREFIX + packageName, "[]") ?: "[]")

    /** Adds [className] to the user-blocked activities list for [packageName]. */
    fun addUserBlockedActivity(packageName: String, className: String) {
        val key = KEY_USER_BLOCKED_PREFIX + packageName
        val updated = getUserBlockedActivities(packageName).toMutableSet().also { it.add(className) }
        prefs.edit { putString(key, setToJsonArray(updated)) }
    }

    /** Adds [className] to the explicitly-allowed (unblocked) activities for [packageName]. */
    fun addUnblockedActivity(packageName: String, className: String) {
        val key = KEY_UNBLOCKED_PREFIX + packageName
        val updated = getUnblockedActivities(packageName).toMutableSet().also { it.add(className) }
        prefs.edit { putString(key, setToJsonArray(updated)) }
    }

    /** Appends [className] to the per-package block log (capped at [MAX_BLOCK_LOG_ENTRIES], most-recent-first, deduped). */
    fun logBlockedActivity(packageName: String, className: String) {
        android.util.Log.d("BrowserBlock", "Blocked: $packageName / $className")
        val key = KEY_BLOCK_LOG_PREFIX + packageName
        val existing = parseJsonList(prefs.getString(key, "[]") ?: "[]").toMutableList()
        existing.remove(className)
        existing.add(0, className)
        if (existing.size > MAX_BLOCK_LOG_ENTRIES) existing.removeAt(existing.lastIndex)
        prefs.edit { putString(key, org.json.JSONArray(existing).toString()) }
    }

    /** Returns recently-blocked Activity class names for [packageName], most-recent-first. */
    fun getRecentlyBlockedActivities(packageName: String): List<String> =
        parseJsonList(prefs.getString(KEY_BLOCK_LOG_PREFIX + packageName, "[]") ?: "[]")

    /** Persists a per-package [BlockingMode] override. Falls back to [blockingMode] when read via [getBlockingMode]. */
    fun setPackageBlockingMode(packageName: String, mode: BlockingMode) {
        prefs.edit { putString(KEY_PKG_BLOCKING_MODE_PREFIX + packageName, mode.name) }
    }

    /** Removes [className] from the user-blocked list for [packageName]. */
    fun removeUserBlockedActivity(packageName: String, className: String) {
        val key = KEY_USER_BLOCKED_PREFIX + packageName
        val updated = getUserBlockedActivities(packageName).toMutableSet().also { it.remove(className) }
        prefs.edit { putString(key, setToJsonArray(updated)) }
    }

    /** Removes [className] from the user-allowed (unblocked) list for [packageName]. */
    fun removeUnblockedActivity(packageName: String, className: String) {
        val key = KEY_UNBLOCKED_PREFIX + packageName
        val updated = getUnblockedActivities(packageName).toMutableSet().also { it.remove(className) }
        prefs.edit { putString(key, setToJsonArray(updated)) }
    }

    /** Registers a [SharedPreferences.OnSharedPreferenceChangeListener]. */
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    /** Unregisters a [SharedPreferences.OnSharedPreferenceChangeListener]. */
    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun parseJsonStringArray(json: String): Set<String> {
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    /** Like [parseJsonStringArray] but preserves insertion order as a [List]. */
    private fun parseJsonList(json: String): List<String> {
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun setToJsonArray(set: Set<String>): String {
        return try {
            org.json.JSONArray(set.toList()).toString()
        } catch (_: Exception) {
            "[]"
        }
    }
}
