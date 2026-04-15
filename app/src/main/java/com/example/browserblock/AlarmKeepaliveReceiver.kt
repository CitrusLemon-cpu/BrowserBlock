package com.example.browserblock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

class AlarmKeepaliveReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmKeepalive"
        private const val INTERVAL_MS = 15 * 60 * 1000L
        private const val REQUEST_CODE = 9001

        fun schedule(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                Log.d(TAG, "SCHEDULE_EXACT_ALARM not granted — skipping.")
                return
            }

            val pi = pendingIntent(context)
            am.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + INTERVAL_MS,
                pi
            )
            Log.d(TAG, "Scheduled keepalive alarm in ${INTERVAL_MS / 60_000}m.")
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            am.cancel(pendingIntent(context))
        }

        private fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, AlarmKeepaliveReceiver::class.java)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm fired — checking service.")
        if (ForegroundPollingService.instance == null &&
            BlockerAccessibilityService.instance == null
        ) {
            Log.d(TAG, "Service dead — restarting ForegroundPollingService.")
            ForegroundPollingService.start(context)
        }
        schedule(context)
    }
}
