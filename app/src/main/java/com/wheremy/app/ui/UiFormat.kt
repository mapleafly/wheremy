package com.wheremy.app.ui

import com.wheremy.app.data.repo.PhotoDeletion
import com.wheremy.app.util.daysUntil
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 界面文案的**唯一生成处**（`UI.md` §3.7 / `RULES.md` §9.3）：
 * 照片墙、详情页、待归类三处用的是同一句话，一处生成三处显示。
 */
object UiFormat {

    /** 距到期天数的三种说法；天数为插值。 */
    fun expireHint(days: Long): String = when {
        days < 0 -> "已经过期 ${-days} 天"
        days == 0L -> "就是今天"
        else -> "还剩 $days 天"
    }

    fun formatDate(expiresAt: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(expiresAt).atZone(zone).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)

    /** 到期行的整句：`到期 · 2026-11-03（还剩 28 天）`。 */
    fun expireLine(kind: String?, expiresAt: Long): String =
        "${kind ?: "到期"} · ${formatDate(expiresAt)}（${expireHint(daysUntil(expiresAt))}）"

    /**
     * 删除框正文：**四档，顺序即优先级**（`RULES.md` §9.3）。
     *
     * 第二、三档必须分开：`otherItems` 说的是"另一件**物品**"，`otherOwners` 涵盖位置记录等别的持有者。
     * 对着一张只被某个位置共用的图说"还有 N 件物品在用"就是假话。
     */
    fun deleteWarningOf(deletion: PhotoDeletion): String = when {
        !deletion.hasPhoto -> "这条记录会被删除，无法恢复。"
        deletion.otherItems > 0 ->
            "这张照片还有 ${deletion.otherItems} 件物品在用，删掉这一件只会去掉这一件的记录，照片留着。"
        deletion.otherOwners > 0 ->
            "这张照片还有别的记录在用，删掉这一件只会去掉这一件的记录，照片留着。"
        else -> "照片和记录会一起删除，无法恢复。"
    }
}

/**
 * 待删除的那一件：**id 和那句话一起存**。
 * 弹框期间列表可能刷新（照片墙尤其如此），只存 id 的话就没人知道该说哪句实话。
 */
data class PendingDelete(val itemId: String, val warning: String)
