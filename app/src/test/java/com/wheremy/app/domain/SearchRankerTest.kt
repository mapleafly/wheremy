package com.wheremy.app.domain

import com.wheremy.app.data.item
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 打分只影响**次序**，不影响命中集合（命中集合在 SQL 的 `LIKE` 里）。
 * 这一支钉住五档的相对关系，避免以后调权重时把"名字优先"调没了。
 */
class SearchRankerTest {

    @Test
    fun `五档分数各就各位`() {
        val target = item(id = "x", name = "外套", aliases = "大衣,风衣", searchText = "外套 uniqlo 大衣,风衣")

        assertEquals(100, SearchRanker.score(target, "外套"))
        assertEquals(80, SearchRanker.score(target, "外"))
        assertEquals(60, SearchRanker.score(target, "套"))
        assertEquals(40, SearchRanker.score(target, "风衣"))
        assertEquals(20, SearchRanker.score(target, "uniqlo"))
        assertEquals(0, SearchRanker.score(target, "不存在"))
    }

    @Test
    fun `大小写不影响打分`() {
        val target = item(id = "x", name = "MacBook Pro", searchText = "macbook pro apple")

        assertEquals(100, SearchRanker.score(target, "macbook pro"))
        assertEquals(80, SearchRanker.score(target, "MACBOOK"))
        assertEquals(20, SearchRanker.score(target, "APPLE"))
    }

    @Test
    fun `rank 把名字命中的排到派生列命中的前面`() {
        val byName = item(id = "name", name = "冬装外套", searchText = "冬装外套")
        val bySearchText = item(id = "text", name = "收纳盒", searchText = "收纳盒 装外套用的")

        val ranked = SearchRanker.rank(listOf(bySearchText, byName), "外套")

        assertEquals(listOf("name", "text"), ranked.map { it.id })
    }

    @Test
    fun `空 query 不打分`() {
        val target = item(id = "x", name = "外套")
        assertEquals(0, SearchRanker.score(target, "  "))
    }
}
