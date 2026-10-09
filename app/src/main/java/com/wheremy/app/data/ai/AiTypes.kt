package com.wheremy.app.data.ai

import kotlinx.serialization.Serializable

/**
 * AI 分析的结局（`RULES.md` §13.1）。**只有一处映射**：界面报的那句话与留痕里的标签都从它拿，
 * 不许有两份说法。
 */
enum class FailureReason {
    NO_CONFIG,
    OFFLINE,
    TIMEOUT,
    UNAUTHORIZED,
    RATE_LIMITED,
    SERVER,
    BAD_RESPONSE,
    BAD_REQUEST,
    INSUFFICIENT_BALANCE,
    TRUNCATED,
    NOTHING_FOUND,
    ;

    /** 📐 鉴权与配置类重试没有意义：只有服务端/网络抖动值得再试一次（`RULES.md` §13.1）。 */
    val retryable: Boolean
        get() = this in RETRYABLE

    companion object {
        val RETRYABLE = setOf(SERVER, OFFLINE, TIMEOUT, RATE_LIMITED)
    }
}

/** 模型认出来的一件物品（字段都可空：不确定时模型应当填 null，我们也不编造）。 */
@Serializable
data class AnalysisItem(
    val name: String? = null,
    val category: String? = null,
    val seasons: List<String> = emptyList(),
    val color: String? = null,
    val material: String? = null,
    val brand: String? = null,
    val aliases: List<String> = emptyList(),
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val containerGuess: String? = null,
) {
    val isEmpty: Boolean
        get() = name.isNullOrBlank() &&
            category.isNullOrBlank() &&
            seasons.isEmpty() &&
            color.isNullOrBlank() &&
            material.isNullOrBlank() &&
            brand.isNullOrBlank() &&
            aliases.isEmpty() &&
            description.isNullOrBlank() &&
            tags.isEmpty()
}

/** 一次分析的结果：成功给若干件（可能为空列表 = 模型照做回了 `items: []`），失败给原因。 */
sealed interface AnalysisOutcome {
    data class Success(val items: List<AnalysisItem>, val raw: String) : AnalysisOutcome

    data class Failure(val reason: FailureReason, val raw: String? = null) : AnalysisOutcome
}
