package ru.forstudent.schedule.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import ru.forstudent.schedule.alarm.AlarmScheduler
import ru.forstudent.schedule.data.ScheduleRepository
import ru.forstudent.schedule.data.SyncOutcome
import ru.forstudent.schedule.widget.TodayWidget
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = ScheduleRepository(applicationContext)
        return when (repository.sync()) {
            SyncOutcome.Updated -> {
                AlarmScheduler(applicationContext).reconcile(repository.snapshot())
                TodayWidget.updateAll(applicationContext)
                Result.success()
            }
            SyncOutcome.Throttled -> Result.success()
            is SyncOutcome.Failed -> Result.retry()
        }
    }

    companion object {
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("schedule-periodic", ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun enqueueInitial(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("schedule-initial", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network).build())
        }
    }
}
