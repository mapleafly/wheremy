package com.wheremy.app.ui.review

import com.wheremy.app.data.repo.ItemDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 确认页的四本账与两个判据（`RULES.md` §1.3–§1.5）。
 * 这些都是纯函数式的算属性，所以不必起 VM——**它们是"数错了就会对着已确认的物品说错话"的地方**。
 */
class ReviewUiStateTest {

    private fun slot(
        itemId: String? = "i-1",
        name: String = "",
        saved: Boolean = false,
        touched: Set<String> = emptySet(),
    ) = ReviewSlot(
        itemId = itemId,
        draft = ItemDraft(id = itemId ?: "新的一件", name = name),
        saved = saved,
        touched = touched,
    )

    @Test
    fun `四本账各数各的`() {
        val state = ReviewUiState(
            slots = listOf(
                slot(itemId = null, name = "内存里的"), // 没落库
                slot(itemId = "i-1", name = "留在待归类"), // 落库未保存
                slot(itemId = "i-2", name = "已经存好", saved = true),
            ),
        )

        assertEquals(1, state.notStored)
        assertEquals(1, state.leftBehind)
        assertEquals(1, state.alreadySaved)
        assertEquals("unsaved 含已落库但没确认的", 2, state.unsaved)
    }

    @Test
    fun `空壳四个条件全要`() {
        assertTrue("落库 + 没保存 + 没名字 + 没动过 = 空壳", slot().isThrowawayShell)
        assertFalse("动过一个字段就不是壳", slot(touched = setOf("quantity")).isThrowawayShell)
        assertFalse("有名字不是壳", slot(name = "电钻").isThrowawayShell)
        assertFalse("已确认不是壳", slot(saved = true).isThrowawayShell)
        assertFalse("没落库的件本来就什么都不留", slot(itemId = null).isThrowawayShell)
    }

    @Test
    fun `能按保存的判据`() {
        val notStoredBlank = ReviewUiState(slots = listOf(slot(itemId = null)))
        assertFalse("没落库又没名字：不许开行", notStoredBlank.canSave)

        val notStoredNamed = ReviewUiState(slots = listOf(slot(itemId = null, name = "电钻")))
        assertTrue(notStoredNamed.canSave)

        val storedBlank = ReviewUiState(slots = listOf(slot(itemId = "i-1")))
        assertTrue("已落库的那件即使名字为空也允许按：保存是把它搬出去", storedBlank.canSave)

        assertFalse("没有当前件时不给按", ReviewUiState().canSave)
    }
}
