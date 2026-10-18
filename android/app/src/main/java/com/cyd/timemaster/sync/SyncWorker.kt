package com.cyd.timemaster.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cyd.timemaster.data.AppGraph
import java.util.concurrent.TimeUnit

/** WorkManager 定时/一次性同步任务：仅 WiFi 下自动执行 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = AppGraph.settings
        if (!settings.autoSync || !settings.isPaired) return Result.success()
        return when (val outcome = SyncManager.backupNow(applicationContext)) {
            is SyncManager.Outcome.Ok -> Result.success()
            is SyncManager.Outcome.Fail -> if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val PERIODIC_NAME = "timemaster_sync_periodic"
        private const val ONESHOT_NAME = "timemaster_sync_once"

        private val wifiConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()

        /** 每小时自动备份；在 Application 与设置变更后调用 */
        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                    .setConstraints(wifiConstraints)
                    .build(),
            )
        }

        /** 手动/启动时立即同步一次 */
        fun syncOnce(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONESHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(wifiConstraints)
                    .build(),
            )
        }
    }
}
