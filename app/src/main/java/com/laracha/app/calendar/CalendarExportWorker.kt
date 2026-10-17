package com.laracha.app.calendar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.BackoffPolicy
import com.laracha.app.HabitApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** WorkManager is a best-effort retry mechanism, not a promise of exact or remote synchronization. */
class CalendarExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as HabitApplication).calendarCoordinator.retryPending()
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SecurityException) {
        Result.success() // user action/permission restoration is required; do not retry in a loop
    } catch (_: Exception) {
        Result.retry()
    }
}

object CalendarWorkScheduler {
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<CalendarExportWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("calendar-export-retry", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
