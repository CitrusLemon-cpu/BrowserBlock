package com.example.browserblock

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

data class ForegroundActivity(val packageName: String, val className: String)

/**
 * UsageStatsHelper — object for querying foreground app information.
 *
 * Uses [UsageStatsManager] (requires PACKAGE_USAGE_STATS) to determine
 * which app / Activity is currently in the foreground.
 *
 * Two query strategies depending on API level:
 *
 *  API 21–28: [UsageStatsManager.queryUsageStats] with a short time window,
 *    then find the package with the most recent lastTimeUsed.
 *    Granularity is ~1 second; may be slightly stale.
 *
 *  API 29+:   [UsageStatsManager.queryEvents] with TYPE_ACTIVITY_RESUMED /
 *    TYPE_ACTIVITY_PAUSED. More accurate — fires per-Activity transition.
 *
 * [ForegroundPollingService] calls [getForegroundPackage] on a periodic timer
 * (polling interval TBD — ~500 ms is a reasonable starting point).
 *
 * Logic to be implemented in a later step.
 */
object UsageStatsHelper {

    private const val QUERY_WINDOW_MS = 5_000L  // look back 5 s for usage stats

    /**
     * Returns the package name of the current foreground application,
     * or null if PACKAGE_USAGE_STATS has not been granted or the query fails.
     */
    fun getForegroundPackage(context: Context): String? {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getForegroundPackageViaEvents(usm)
        } else {
            getForegroundPackageViaStats(usm)
        }
    }

    /**
     * Returns the package name and Activity class name of the current foreground Activity,
     * or null if PACKAGE_USAGE_STATS has not been granted or the query fails.
     *
     * Uses ACTIVITY_RESUMED events on API 29+ and MOVE_TO_FOREGROUND on API 21-28.
     */
    fun getForegroundActivity(context: Context): ForegroundActivity? {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - QUERY_WINDOW_MS, now) ?: return null
        var lastPackage: String? = null
        var lastClass: String? = null
        val event = UsageEvents.Event()
        val targetType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == targetType) {
                lastPackage = event.packageName
                lastClass = event.className
            }
        }
        return if (lastPackage != null && lastClass != null) {
            ForegroundActivity(lastPackage, lastClass)
        } else {
            null
        }
    }

    /**
     * API 29+: iterate recent events and return the package of the last
     * ACTIVITY_RESUMED event.
     */
    @Suppress("NewApi") // guarded by Build.VERSION.SDK_INT >= Q at call site
    private fun getForegroundPackageViaEvents(usm: UsageStatsManager): String? {
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - QUERY_WINDOW_MS, now) ?: return null
        var lastPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastPackage = event.packageName
            }
        }
        return lastPackage
    }

    /**
     * API 21–28: query usage stats over a short window and pick the package
     * with the most recent [android.app.usage.UsageStats.lastTimeUsed].
     */
    private fun getForegroundPackageViaStats(usm: UsageStatsManager): String? {
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - QUERY_WINDOW_MS,
            now
        ) ?: return null
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }
}
