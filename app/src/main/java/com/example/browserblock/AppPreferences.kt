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
}
