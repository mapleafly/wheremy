package com.wheremy.app.data.db

/**
 * `search_text` 这一列的算法（`RULES.md` §10.3 / `ARCHITECTURE.md` §4.4）。
 *
 * 只做一件事：把参与全文搜索的字段拼成一串、小写化、压掉多余空白。
 * 读侧（搜索、排序、建议）各自再做自己的小写化——**全库不止这一处 lowercase**。
 */
object SearchTextBuilder {

    /**
     * 参与拼接的字段集合的版本号。
     * 改了 [build] 的参数就加一：启动时拿它跟设置里存的值比，不等就补算一遍已有物品。
     * 1 → 2 是因为加了颜色与材质。
     */
    const val FIELD_VERSION = 2

    /**
     * 拼接顺序**不是**参数表的顺序：标签插在 `aliases` 与 `description` 中间。
     * 空值（含空标签列表）不进串，尾部再统一压空白，所以不会留下多余空格。
     */
    fun build(
        name: String,
        brand: String?,
        color: String?,
        material: String?,
        aliases: String?,
        description: String?,
        notes: String?,
        tags: List<String>,
    ): String = listOfNotNull(
        name,
        brand,
        color,
        material,
        aliases,
        tags.joinToString(" ").ifBlank { null },
        description,
        notes,
    ).joinToString(" ")
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()
}
