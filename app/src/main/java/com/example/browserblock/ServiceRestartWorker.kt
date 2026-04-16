package com.example.browserblock

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class ServiceRestartWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "ServiceRestartWorker"
        private const val UNIQUE_WORK_NAME = "service_keepalive"

        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceRestartWorker>(
                15, TimeUnit.MINUTES
            ).setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "WorkManager periodic keepalive ensured.")
        }
    }

    override fun doWork(): Result {
        Log.d(TAG, "WorkManager fired — checking service.")
        if (ForegroundPollingService.instance == null) {
            Log.d(TAG, "ForegroundPollingService dead — restarting via WorkManager.")
            ForegroundPollingService.start(applicationContext)
        }
        AlarmKeepaliveReceiver.schedule(applicationContext)
        return Result.success()
    }
}
