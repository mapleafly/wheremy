package com.wheremy.app.data.repo

import com.wheremy.app.data.db.ItemEntity

/**
 * UI 与仓库之间唯一的载体（`ARCHITECTURE.md` §6.3）。
 * **entity 不上界面层**；列表 ↔ 列的转换（CSV、trim、空串归一）全部由仓库收口。
 */

/**
 * 录入/编辑页的草稿。**只有 `id` 没有默认值**，其余全有默认：
 * 界面上的输入框直接映射到这里，所以"空字符串"与"null"是两种不同的中间态，
 * 落到库里之前由 `ItemRepository.fieldsOf` 统一收口。
 */
data class ItemDraft(
    val id: String,
    val name: String = "",
    val category: String? = null,
    val seasons: List<String> = emptyList(),
    val color: String? = null,
    val brand: String? = null,
    val material: String? = null,
    val aliases: List<String> = emptyList(),
    val description: String? = null,
    val notes: String? = null,
    val quantity: Int = 1,
    val locationId: String? = null,
    val expiresAt: Long? = null,
    val expireKind: String? = null,
    val tags: List<String> = emptyList(),
)

/**
 * 照片墙的筛选条件：**同类内"或"、跨类"与"**（FR-FND-08）。
 * `expireUntil` 非空 = "只看到期日不晚于这个毫秒"，也就是**临近到期与已过期一起**；
 * 没有到期日的物品在它非空时一律不算进来。
 */
data class Filters(
    /**
     * 组合筛选（`FR-FND-08`）：同一类里选多个是**或**，不同类之间是**与**。
     *
     * 例：`categories = {衣物, 电器}` + `seasons = {冬}` = 「衣物**或**电器」里那些**冬**天的。
     * 空集合 = 这一类不筛。
     */
    val categories: Set<String> = emptySet(),
    val seasons: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val onlyInbox: Boolean = false,
    val expireUntil: Long? = null,
    /**
     * 结果排序（`FR-FND-09`）。
     *
     * 🔒 `DEFAULT` 就是现在这套、一个字都不改：**空查询按插入序**（`RULES.md` §10.1 的 `all()` 没有 ORDER BY）、
     * **有查询按相关度**（`SearchRanker`）。其余三种是用户**显式**选的，选了就以排序为准。
     */
    val sort: SortOrder = SortOrder.DEFAULT,
)

/** 结果排序的四个选项（`FR-FND-09`）。 */
enum class SortOrder(val label: String) {
    DEFAULT("默认"),
    RECENTLY_MOVED("最近动过"),
    RECENTLY_UPDATED("最近更新"),
    NAME("名称"),
}

/** 列表页一行要的全部。 */
data class ItemSummary(val item: ItemEntity, val tags: List<String>, val thumbPath: String?, val locationPath: String?)

/**
 * 删除确认框要的三个数。"照片会不会跟着没"必须分档说，所以不能只给一个布尔。
 * `otherOwners` 数的是**非物品**的主人（位置记录）：正常操作造不出来，手改过的备份包能造出来。
 */
data class PhotoDeletion(val hasPhoto: Boolean, val otherItems: Int, val otherOwners: Int)

/** 词表：顺序就是界面上芯片的顺序（`ARCHITECTURE.md` §6.3）。 */
val CATEGORIES = listOf(
    "衣物", "数码", "电器", "药品", "食品", "工具", "文档", "饰品", "厨具", "玩具", "运动", "其他",
)

val SEASONS = listOf("春", "夏", "秋", "冬")

val EXPIRE_KINDS = listOf("保质期", "保修", "证件有效期", "其他")
