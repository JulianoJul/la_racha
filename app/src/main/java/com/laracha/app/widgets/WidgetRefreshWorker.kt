package com.laracha.app.widgets

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Best-effort fallback only; Android may defer it under Doze and battery restrictions. */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.updateAll(applicationContext)
        return Result.success()
    }
}

object WidgetRefreshScheduler {
    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "widget-date-refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
