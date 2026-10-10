package com.wheremy.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `search_text` 这一列的算法（`RULES.md` §10.3）。
 * 这些断言是"改拼接顺序就会红"的那种：顺序变了，搜索的相关性会跟着变。
 */
class SearchTextBuilderTest {

    @Test
    fun `拼接顺序是 name brand color material aliases tags description notes`() {
        val text = SearchTextBuilder.build(
            name = "外套",
            brand = "Uniqlo",
            color = "深灰",
            material = "羊毛",
            aliases = "大衣,风衣",
            description = "冬天的",
            notes = "挂在左边",
            tags = listOf("冬装", "外套"),
        )

        assertEquals(
            "外套 uniqlo 深灰 羊毛 大衣,风衣 冬装 外套 冬天的 挂在左边",
            text,
        )
    }

    @Test
    fun `全串小写化且压掉多余空白`() {
        val text = SearchTextBuilder.build(
            name = "MacBook   Pro",
            brand = "APPLE",
            color = null,
            material = null,
            aliases = null,
            description = null,
            notes = null,
            tags = emptyList(),
        )

        assertEquals("macbook pro apple", text)
    }

    @Test
    fun `空值与空标签不留多余空格`() {
        val text = SearchTextBuilder.build(
            name = "护照",
            brand = null,
            color = null,
            material = null,
            aliases = null,
            description = null,
            notes = null,
            tags = emptyList(),
        )

        assertEquals("护照", text)
        assertTrue("首尾不许有空白", text == text.trim())
    }

    @Test
    fun `标签在 aliases 与 description 之间`() {
        val text = SearchTextBuilder.build(
            name = "n",
            brand = null,
            color = null,
            material = null,
            aliases = "a",
            description = "d",
            notes = null,
            tags = listOf("t"),
        )

        assertEquals("n a t d", text)
    }

    @Test
    fun `字段版本是 3`() {
        // 这条守的是"闸门"本身，不是某个具体数字：
        // 1 → 2 是加了颜色与材质；2 → 3 是加了类别与季节（FR-AI-11：模型可能回词表外的值，也要能搜到）。
        // 改了拼接字段就**必须**加一，否则启动时不会补算已有物品，老数据搜不到新字段。
        assertEquals(3, SearchTextBuilder.FIELD_VERSION)
    }
}
