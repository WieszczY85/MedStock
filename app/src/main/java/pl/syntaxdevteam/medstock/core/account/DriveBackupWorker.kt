package pl.syntaxdevteam.medstock.core.account

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class DriveBackupWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : Worker(appContext, workerParameters) {

    override fun doWork(): Result {
        val stateStore = AccountStateStore(applicationContext)
        val state = stateStore.getState()
        if (!state.isConnected || !state.driveBackupEnabled) return Result.success()

        return runCatching {
            DriveBackupSnapshotRepository(applicationContext).createAndUploadSnapshot(state.email)
            stateStore.markBackupCreated()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = {
                if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.success()
            },
        )
    }

    private companion object {
        const val MAX_RETRY_ATTEMPTS = 3
    }
}

object DriveBackupScheduler {

    fun reconcile(context: Context) {
        val state = AccountStateStore(context).getState()
        if (!state.isConnected || !state.driveBackupEnabled) {
            cancel(context)
            return
        }

        schedule(context)
        if (isBackupOverdue(state.lastBackupEpochMillis, System.currentTimeMillis())) {
            enqueueOverdueBackup(context)
        }
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(BACKUP_INTERVAL_HOURS, TimeUnit.HOURS)
            .setInitialDelay(BACKUP_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MIN_BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(OVERDUE_WORK_NAME)
    }

    private fun enqueueOverdueBackup(context: Context) {
        val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MIN_BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            OVERDUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    internal fun isBackupOverdue(lastBackupEpochMillis: Long?, nowEpochMillis: Long): Boolean {
        return lastBackupEpochMillis == null || nowEpochMillis - lastBackupEpochMillis >= BACKUP_INTERVAL_MILLIS
    }

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private const val PERIODIC_WORK_NAME = "drive-backup-periodic"
    private const val OVERDUE_WORK_NAME = "drive-backup-overdue"
    private const val BACKUP_INTERVAL_HOURS = 24L
    private const val BACKUP_INTERVAL_MILLIS = BACKUP_INTERVAL_HOURS * 60L * 60L * 1000L
    private const val MIN_BACKOFF_MINUTES = 15L
}
