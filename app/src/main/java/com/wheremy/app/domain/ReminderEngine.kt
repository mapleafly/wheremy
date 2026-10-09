package com.wheremy.app.domain

import com.wheremy.app.data.db.ItemDao
import com.wheremy.app.util.daysUntil
import com.wheremy.app.util.expireWindowEnd
import com.wheremy.app.util.localStartOfDay
import java.time.Instant
import java.time.ZoneId

/** 一次到期检查的产出（`ARCHITECTURE.md` §7）。`ids` 带着命中集合，回写时用它——免得"数了 20 件"却标记了另 20 件。 */
data class ReminderPlan(val count: Int, val nearestDays: Long, val body: String, val ids: List<String>)

/**
 * 到期提醒的取数与措辞（`RULES.md` §11.2/§11.3）。
 *
 * 窗口三值：`windowStart = 今天 00:00`、`todayEnd = 明天 00:00`、`windowEnd = 第 leadDays 天 23:59:59.999`。
 * 上界只有 `expireWindowEnd` 一个来源——"点进通知看到的和通知里说的必须同一批"就靠它。
 */
class ReminderEngine(private val dao: ItemDao) {

    suspend fun plan(
        now: Long,
        leadDays: Int,
        force: Boolean = false,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ReminderPlan? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val windowStart = localStartOfDay(today, zone)
        val todayEnd = localStartOfDay(today.plusDays(1), zone)
        val windowEnd = expireWindowEnd(leadDays, today, zone)

        val hits = if (force) {
            dao.expiringIgnoringNotified(windowStart, windowEnd)
        } else {
            dao.expiringBy(windowStart, todayEnd, windowEnd)
        }
        if (hits.isEmpty()) return null

        // DAO 按 expiresAt 升序，所以第一件就是最早的
        val nearest = daysUntil(hits.first().expiresAt!!, today, zone)
        val todayCount = hits.count { it.expiresAt!! >= windowStart && it.expiresAt < todayEnd }
        return ReminderPlan(
            count = hits.size,
            nearestDays = nearest,
            body = body(hits.size, nearest, todayCount),
            ids = hits.map { it.id },
        )
    }

    suspend fun markNotified(ids: List<String>, notifiedAt: Long) {
        if (ids.isNotEmpty()) dao.markExpireNotified(ids, notifiedAt)
    }

    /**
     * 正文四变体（`RULES.md` §11.3），**按顺序判**。
     *
     * 两条判定纪律：已经过期和还剩几天是两件事（不许写成"临近到期，最近的已过期 3 天"）；
     * 「N 件今天到期」**只在真的每一件都是今天**时才成立。
     */
    fun body(count: Int, nearestDays: Long, todayCount: Int): String = when {
        nearestDays < 0 -> "$count 件物品到期或已过期，最早的已过期 ${-nearestDays} 天"
        todayCount == count -> "$count 件物品今天到期"
        todayCount > 0 -> "$count 件物品临近到期，其中 $todayCount 件今天到期"
        else -> "$count 件物品临近到期，最近的还剩 $nearestDays 天"
    }
}
