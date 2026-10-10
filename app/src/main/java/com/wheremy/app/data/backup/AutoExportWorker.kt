package com.wheremy.app.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wheremy.app.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 每天跑一次的自动导出（`FR-BAK-08`）。
 *
 * **开关在 worker 里判**，而不是"开的时候才入队"：周期任务一旦入队就一直在，
 * 这样用户在设置里开关一下立刻生效（下一次跑就按新开关走），也不用管"关掉时要不要取消"。
 */
@HiltWorker
class AutoExportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsRepository,
    private val exporter: AutoExporter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        if (settings.current().autoExportEnabled) {
            exporter.runOnce()
        } else {
            // 关着就什么都不做，也不写"上次结果"——那一行说的是上一次**真的跑了**的结果
            Result.success()
        }
    }.fold(
        onSuccess = { Result.success() },
        // 失败让 WorkManager 按退避重试；原因已经写进设置页那一行了
        onFailure = { Result.retry() },
    )
}

/** 入队（`WheremyApp.onCreate` 调一次；`KEEP` 保证不会重复排队）。 */
object AutoExportScheduler {

    /** 唯一任务名：`KEEP` 靠它认。 */
    const val UNIQUE_NAME = "wheremy-auto-export"

    /** 📐 一天一次。WorkManager 的最小周期就是 15 分钟，一天这个粒度够用。 */
    fun request(): PeriodicWorkRequest = PeriodicWorkRequestBuilder<AutoExportWorker>(1, TimeUnit.DAYS).build()

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request(),
        )
    }
}
