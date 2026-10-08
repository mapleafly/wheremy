package com.wheremy.app.domain

import com.wheremy.app.data.repo.ItemDraft
import com.wheremy.app.data.repo.ItemRepository

/** 一次录入的产出（`ARCHITECTURE.md` §7）。 */
data class CaptureOutcome(val itemId: String, val draft: ItemDraft, val failure: String? = null)

/**
 * 录入编排（`RULES.md` §3.1）。
 *
 * 顺序是硬的：**先落库**——照片到位那一帧，物品行已经在库里了。
 * 所以 `newDraft` 之后立刻回调 `onDraftSaved(itemId)`，界面从那一刻起就有真实的 id 可用
 * （「稍后处理」那颗按钮的前提就是"它真的在库里"）。
 *
 * 📌 AI 分析（`analyzeInto`）属于 P3：这里留出同一个接缝——分析结果只覆盖模型**明确给出**的字段，
 * 且**非 DRAFT 直接丢弃**（迟到的 AI 不许改写已确认的物品）。P2 阶段 `analyzing` 恒为 false。
 */
class CaptureFlow(private val items: ItemRepository) {

    /** 选图/拍照进来的唯一入口。 */
    suspend fun capture(jpeg: ByteArray, onDraftSaved: (String) -> Unit): CaptureOutcome {
        val itemId = items.newDraft(jpeg)
        onDraftSaved(itemId)
        return CaptureOutcome(itemId = itemId, draft = items.draftOf(itemId))
    }

    /** 换一张：先写新文件 → 改行 → 回收旧文件（顺序在仓库里）。 */
    suspend fun replacePhoto(itemId: String, jpeg: ByteArray): ItemDraft {
        items.swapPhoto(itemId, jpeg)
        return items.draftOf(itemId)
    }
}
