package com.wheremy.app.ui

import com.wheremy.app.data.ai.FailureReason
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

    /**
     * 批量删除的文案（`FR-OPR-06`）。
     *
     * 单件那句（[deleteWarningOf]）说的是"这一件"；批量必须把**两类照片分开数**：
     * 会跟着没的、还有别的记录在用所以会留着的——含糊说"照片可能被删"等于没说。
     *
     * 数口径：`photosToDelete` 是**文件张数**（一条文件没别人用才算它会没），
     * `photosKept` 是**记录件数**（同一条文件可能被两件共用），所以后者说"件"不说"张"。
     */
    fun deleteWarningOfBatch(count: Int, photosToDelete: Int, photosKept: Int): String = when {
        photosToDelete == 0 && photosKept == 0 -> "这 $count 条记录会被删除，无法恢复。"
        photosToDelete > 0 && photosKept > 0 ->
            "删除这 $count 件：$photosToDelete 张照片会跟着没；另有 $photosKept 件的照片还有别的记录在用，照片留着。"
        photosKept > 0 -> "删除这 $count 件：记录会没，照片留着（还有别的记录在用）。"
        else -> "删除这 $count 件：照片和记录会一起删除，无法恢复。"
    }

    /**
     * AI 失败的 11 条文案（`RULES.md` §13.2）——**逐字**，`null` 不显示。
     *
     * 三条写法纪律（别顺手改）：
     * 1. `TIMEOUT` **故意不写秒数**：超时由 `ApiPhotoAnalyzer.CALL_TIMEOUT_SECONDS` 说了算；
     * 2. `OFFLINE` **别说"已跳过"**：这条链路真发了请求（可重试的话两次），只是 `IOException` 收场；
     * 3. 每条都带**下一步**：没有一条只说"失败了"。
     */
    fun failureText(reason: FailureReason?): String? = when (reason) {
        null -> null
        FailureReason.NO_CONFIG -> "未启用 AI（还没配 API Key），字段留空，直接手填就行"
        FailureReason.OFFLINE -> "网络不可用，AI 没连上，先手填，联网后可以重试"
        FailureReason.TIMEOUT -> "AI 分析超时，先手填，稍后可以重试"
        FailureReason.UNAUTHORIZED -> "API Key 无效或被拒，先手填，换 key 后再点重新分析"
        FailureReason.RATE_LIMITED -> "AI 服务限流，稍后再试一次"
        FailureReason.SERVER -> "AI 服务暂不可用，可点重新分析"
        FailureReason.BAD_RESPONSE -> "AI 返回内容无法解析，可点重新分析"
        FailureReason.BAD_REQUEST -> "请求被服务端拒了，多半是设置里的模型名或接口地址填错，改对后再点重新分析"
        FailureReason.INSUFFICIENT_BALANCE -> "AI 账户没有可用余额或额度，充值或换账户后再点重新分析"
        FailureReason.TRUNCATED -> "AI 的回应太长被切断了，可点重新分析，或换一张只拍一件物品的照片"
        FailureReason.NOTHING_FOUND -> "AI 没从照片里认出物品，字段留空，直接手填就行"
    }

    /**
     * 位置建议的三句话（`UI.md` §6.4）——**互斥**。
     *
     * 🔒 光说"AI 猜的位置：橱柜"会被读成"它给了建议而界面没用"，**用户就是这么报的 bug**（批次 Z）。
     */
    fun locationGuessLine(guess: String, outcome: LocationGuessOutcome): String = when (outcome) {
        LocationGuessOutcome.FILLED -> "AI 猜的位置：$guess（已填进「放在哪里」）"
        LocationGuessOutcome.KEPT_USER_CHOICE -> "AI 猜的是「$guess」，这件已经选好位置了，没有改动"
        LocationGuessOutcome.NO_MATCH -> "AI 猜的是「$guess」，你的位置里没有对得上的，需要的话点「选择」新建一个"
    }
}

/** 位置建议的三种结局（`RULES.md` §5.3/§5.4）。 */
enum class LocationGuessOutcome { FILLED, KEPT_USER_CHOICE, NO_MATCH }

/**
 * 待删除的那一件：**id 和那句话一起存**。
 * 弹框期间列表可能刷新（照片墙尤其如此），只存 id 的话就没人知道该说哪句实话。
 */
data class PendingDelete(val itemId: String, val warning: String)

/** 批量删除的待确认状态（`FR-OPR-06`）：要删哪些 + 那句已经算好的话。 */
data class BatchDelete(val itemIds: List<String>, val warning: String)
