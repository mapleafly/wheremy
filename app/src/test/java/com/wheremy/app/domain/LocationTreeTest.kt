package com.wheremy.app.domain

import com.wheremy.app.data.location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 位置树是**全量入内存**的那种（`RULES.md` §8）：这一支测的是读侧规则——
 * 孤儿当根、同级次序、断环、先序可选项。
 */
class LocationTreeTest {

    private fun tree() = buildLocationTree(
        listOf(
            location(id = "home", parentId = null, name = "家", sortOrder = 0),
            location(id = "bedroom", parentId = "home", name = "主卧", sortOrder = 1),
            location(id = "study", parentId = "home", name = "书房", sortOrder = 0),
            location(id = "wardrobe", parentId = "bedroom", name = "衣柜", sortOrder = 0),
            location(id = "drawer", parentId = "wardrobe", name = "抽屉", sortOrder = 0),
            // 父不存在 = 孤儿：读侧当根，写侧由外键当场拒（§12.4）
            location(id = "orphan", parentId = "已经没了的父", name = "孤儿柜", sortOrder = 2),
        ),
    )

    @Test
    fun `父不存在的节点也算根`() {
        assertEquals(setOf("home", "orphan"), tree().roots().map { it.id }.toSet())
    }

    @Test
    fun `同级按 sortOrder 再按名字排`() {
        // 书房的 sortOrder 是 0、主卧是 1，所以书房在前
        assertEquals(listOf("study", "bedroom"), tree().childrenOf("home").map { it.id })
    }

    @Test
    fun `breadcrumb 从根拼到该节点`() {
        assertEquals("家 / 主卧 / 衣柜 / 抽屉", tree().breadcrumb("drawer"))
    }

    @Test
    fun `可选项是先序路径且根不列`() {
        // 根（家）是"家/工作间"那一层，不是容器：候选里只列它下面的层级
        assertEquals(
            listOf("家 / 书房", "家 / 主卧", "家 / 主卧 / 衣柜", "家 / 主卧 / 衣柜 / 抽屉", "孤儿柜"),
            tree().selectablePaths(),
        )
    }

    @Test
    fun `后代查询含自己`() {
        val ids = tree().descendantIds("bedroom")
        assertEquals(setOf("bedroom", "wardrobe", "drawer"), ids)
        assertTrue("含自己：subtreeItemCount 直接拿它求和", "bedroom" in ids)
    }

    @Test
    fun `数据有环时 pathOf 不会卡死`() {
        // 手改过的备份包可能造出 a→b→a
        val cyclic = buildLocationTree(
            listOf(
                location(id = "a", parentId = "b", name = "A", sortOrder = 0),
                location(id = "b", parentId = "a", name = "B", sortOrder = 0),
            ),
        )
        val path = cyclic.pathOf("a")
        assertTrue("要么断在环上，要么给出有限路径", path.size <= 2)
        assertEquals("A", path.last().name)
    }

    @Test
    fun `空树不炸`() {
        val empty = buildLocationTree(emptyList())
        assertEquals(0, empty.size)
        assertTrue(empty.roots().isEmpty())
        assertTrue(empty.selectablePaths().isEmpty())
        // 叶子节点的后代就是它自己（含自己这条语义在 subtreeItemCount 那里被依赖）
        assertEquals(setOf("drawer"), tree().descendantIds("drawer"))
    }
}
