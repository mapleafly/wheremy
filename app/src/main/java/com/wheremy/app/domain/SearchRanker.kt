package com.wheremy.app.domain

import com.wheremy.app.data.db.ItemEntity

/**
 * 搜索打分（`ARCHITECTURE.md` §7）。**分数只影响次序，不影响命中集合**——
 * 命中集合是 SQL 里那句 `searchText LIKE '%q%'`。
 *
 * 优先级：名字完全相同 > 名字前缀 > 名字包含 > 别名 > 派生列。
 * `q` 进来之前已经小写化（全库不止一处 lowercase，见 `ARCHITECTURE.md` §4.4）。
 */
object SearchRanker {

    fun score(item: ItemEntity, query: String): Int {
        val q = query.lowercase()
        if (q.isBlank()) return 0
        val name = item.name.lowercase()
        val aliases = item.aliases.orEmpty().lowercase()

        return when {
            name == q -> 100
            name.startsWith(q) -> 80
            name.contains(q) -> 60
            aliases.contains(q) -> 40
            item.searchText.contains(q) -> 20
            else -> 0
        }
    }

    fun rank(items: List<ItemEntity>, query: String): List<ItemEntity> = items.sortedByDescending { score(it, query) }
}
