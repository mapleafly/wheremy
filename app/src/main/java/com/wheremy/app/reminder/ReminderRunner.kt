package com.wheremy.app.reminder

import android.content.Context
import com.wheremy.app.data.settings.SettingsRepository
import com.wheremy.app.domain.ReminderEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 跑一次到期检查（`RULES.md` §11.4）。WorkManager 的每日班次与设置页那颗调试按钮走的是**同一段逻辑**，
 * 返回的那句话就是设置页 debug 区的全部内容。
 *
 * 🔒 **发不出去就不回写**：安装后先拒了通知权限、几天后才开开的用户，需要那几件物品还被说出来；
 * 在被拒的那几天里标记成"已提醒"，等于让提醒功能静默地把账欠掉。代价只是每天白算一次窗口。
 */
@Singleton
class ReminderRunner @Inject constructor(
    private val settings: SettingsRepository,
    private val engine: ReminderEngine,
) {

    /**
     * @param force 只给设置页那颗调试按钮用：放宽的是"今天说过几次"，不是把沉默的旧账翻回来。
     * @return 四档里唯一 reachable 的那一句。
     */
    suspend fun runOnce(context: Context, force: Boolean = false, now: Long = System.currentTimeMillis()): String {
        // 提前天数每次现读：排班请求里不带它，所以改设置立刻生效
        val leadDays = settings.current().reminderLeadDays
        val plan = engine.plan(now, leadDays, force)
            ?: return "窗口里没有临近到期的物品（提前 $leadDays 天）"

        val problem = ReminderNotifier.notifyProblem(context)
        if (problem != null) {
            return "${plan.count} 件临近到期，但通知发不出去：$problem"
        }

        ReminderNotifier.post(context, plan.body)
        // ⚠️ 只在真发出去之后；而且这一行在 force 判定之外——按调试按钮也算真的说过一次。
        // 用同一个 `now`：窗口按它算、回写也按它写，**全程只有一个时钟**（否则"最多两次"没法确定性验证）
        engine.markNotified(plan.ids, now)
        return "已发一条通知：${plan.body}"
    }
}
