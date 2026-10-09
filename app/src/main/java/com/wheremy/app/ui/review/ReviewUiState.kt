package com.wheremy.app.ui.review

import com.wheremy.app.data.repo.ItemDraft
import com.wheremy.app.ui.LocationGuessOutcome

/**
 * 确认页的一个槽位（`RULES.md` §1.3–§1.5）。
 *
 * `saved` 与 `itemId` 是**两件事**：`itemId != null` 说的是"库里已经有这一行了"，
 * `saved` 说的是"用户已经按过保存（确认）"。第 1 件即使已落库，用户没点保存也算没走完。
 */
data class ReviewSlot(
    val itemId: String?,
    val draft: ItemDraft,
    val saved: Boolean = false,
    /** 用户**手打**过的字段名；空 = 一个字都没动过。空壳判定要用它。 */
    val touched: Set<String> = emptySet(),
    val thumbPath: String? = null,
    val locationPath: String? = null,
    val failure: String? = null,
) {
    /**
     * 空壳（可回收的草稿，`RULES.md` §1.4）：四个条件全要。
     * 这条规则**只有两个读者**（退出时的回收器与退出框里那句话），不许在别处重写一遍。
     */
    val isThrowawayShell: Boolean
        get() = itemId != null && !saved && draft.name.isBlank() && touched.isEmpty()
}

/** 「选择位置」对话框的一个候选。 */
data class LocationOption(val id: String, val path: String)

/** 确认页的整屏状态。`finished` 是**唯一的关页出口**（`UI.md` §6.7）。 */
data class ReviewUiState(
    val slots: List<ReviewSlot> = emptyList(),
    val selected: Int = 0,
    val analyzing: Boolean = false,
    /** AI 失败的那一类（`RULES.md` §13）；文案由 `UiFormat.failureText` 一处生成 */
    val failure: com.wheremy.app.data.ai.FailureReason? = null,
    val confirmExit: Boolean = false,
    val finished: Boolean = false,
    val notice: String? = null,
    /** AI 猜的位置（P3 才可能有值） */
    val guess: String? = null,
    val guessApplied: Boolean = false,
    val placeAlreadyPicked: Boolean = false,
) {
    val current: ReviewSlot? get() = slots.getOrNull(selected)

    /** 退出后**不会留下**的件数。 */
    val notStored: Int get() = slots.count { it.itemId == null }

    /** 会以 DRAFT **留在**待归类的件数。 */
    val leftBehind: Int get() = slots.count { it.itemId != null && !it.saved && !it.isThrowawayShell }

    /** 库里已经存好、离开不动它们的件数。 */
    val alreadySaved: Int get() = slots.count { it.itemId != null && it.saved }

    /** 芯片上的圆点（"这颗走完没有"）。 */
    val unsaved: Int get() = slots.count { !it.saved }

    /** 没落库的槽位必须有名才允许开行；已落库的那件即使名字为空也允许按（保存是把它搬出去）。 */
    val canSave: Boolean get() = current?.let { it.itemId != null || it.draft.name.isNotBlank() } ?: false

    /** 「重新分析」只在**照片已经落库**时给：没落库时没有可重跑的对象，按下去不会有任何反应（`UI.md` §6.4）。 */
    val canRetry: Boolean get() = slots.any { it.itemId != null }

    /** 位置建议的三种结局（互斥，`UI.md` §6.4）。 */
    val guessOutcome: LocationGuessOutcome?
        get() = when {
            guess == null -> null
            guessApplied -> LocationGuessOutcome.FILLED
            placeAlreadyPicked -> LocationGuessOutcome.KEPT_USER_CHOICE
            else -> LocationGuessOutcome.NO_MATCH
        }
}
