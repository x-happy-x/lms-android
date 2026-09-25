package ru.mrcrubs.lms.android.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.mrcrubs.lms.android.LmsApp
import ru.mrcrubs.lms.android.data.AppSettings
import ru.mrcrubs.lms.core.ApiException
import java.util.concurrent.TimeUnit

/** Periodically polls the router in the background and notifies about finished jobs. */
class JobCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as LmsApp).container
        val settings = container.settings.current()
        if (!settings.isConfigured || !settings.notificationsEnabled) return Result.success()
        return try {
            container.monitor.process(container.router.jobs(activeOnly = false))
            Result.success()
        } catch (_: ApiException) {
            // Router unreachable (e.g. phone is outside the home network): try on the next period.
            Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "job-check"

        fun schedule(context: Context, settings: AppSettings) {
            val workManager = WorkManager.getInstance(context)
            if (!settings.isConfigured || !settings.notificationsEnabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<JobCheckWorker>(
                settings.backgroundIntervalMinutes.toLong().coerceAtLeast(15),
                TimeUnit.MINUTES,
            ).setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            ).build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
