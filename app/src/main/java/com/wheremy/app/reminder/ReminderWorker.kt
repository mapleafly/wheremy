package com.wheremy.app.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** 每天一次的到期检查。日常排班**永远**走 `force = false`。 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val runner: ReminderRunner,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ReminderNotifier.ensureChannel(applicationContext)
        val sentence = runner.runOnce(applicationContext, force = false)
        return Result.success(workDataOf(sentence))
    }

    companion object {
        const val KEY_RESULT = "result"

        private fun workDataOf(value: String) = androidx.work.workDataOf(KEY_RESULT to value)
    }
}

/**
 * 排班（`RULES.md` §11.1）。
 *
 * 🔒 用 `KEEP` 而不是 `UPDATE`：提前几天是 Worker 每次现读设置，重排只会白白丢掉已排好的班次——
 * 改设置**不需要**也不许重新排班。WorkManager 自己会在重启后继续跑，所以**没有** BootReceiver。
 */
object ReminderScheduler {

    const val UNIQUE_NAME = "wheremy_expire_daily"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
