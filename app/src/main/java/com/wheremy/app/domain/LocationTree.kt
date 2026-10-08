package com.wheremy.app.domain

import com.wheremy.app.data.db.LocationNodeEntity

/** 位置树的内存节点（`ARCHITECTURE.md` §7：树全量入内存，不写递归 SQL）。 */
data class LocationNode(val id: String, val parentId: String?, val name: String, val kind: String?, val sortOrder: Int)

fun LocationNodeEntity.toDomain(): LocationNode = LocationNode(
    id = id,
    parentId = parentId,
    name = name,
    kind = kind,
    sortOrder = sortOrder,
)

/**
 * 全量位置树（`RULES.md` §8）。
 *
 * 三条要记牢的规则：
 * 1. **`parentId == null` 或者"父不存在"都算根**——手改过的备份包能造出孤儿，
 *    读侧把孤儿当根显示，写侧由外键当场拒（`RULES.md` §12.4）。
 * 2. 同级次序是 `compareBy(sortOrder, name)`，也就是**重排号相同的两个节点按名字排**。
 * 3. `pathOf` 用 `seen` 断环：数据坏掉时不能把界面卡死。
 */
class LocationTree(private val nodes: List<LocationNode>) {

    private val byId: Map<String, LocationNode> = nodes.associateBy { it.id }

    private val ordering = compareBy<LocationNode>({ it.sortOrder }, { it.name })

    val size: Int get() = nodes.size

    fun node(id: String): LocationNode? = byId[id]

    fun roots(): List<LocationNode> =
        nodes.filter { it.parentId == null || byId[it.parentId] == null }.sortedWith(ordering)

    fun childrenOf(parentId: String?): List<LocationNode> =
        nodes.filter { it.parentId == parentId }.sortedWith(ordering)

    /** 从根到该节点（含自己）。数据有环时在 `seen` 处停下并反转，不抛异常。 */
    fun pathOf(id: String): List<LocationNode> {
        val out = mutableListOf<LocationNode>()
        val seen = mutableSetOf<String>()
        var current = byId[id]
        while (current != null && seen.add(current.id)) {
            out += current
            current = current.parentId?.let { byId[it] }
        }
        return out.reversed()
    }

    fun breadcrumb(id: String, sep: String = " / "): String = pathOf(id).joinToString(sep) { it.name }

    /**
     * 喂给多模态模型的候选：每个**可收纳层级**一条完整路径（"家 / 主卧 / 衣柜"）。
     * 先序遍历所以次序稳定（同层按 `sortOrder` 再按名字）。
     *
     * **根不列**——根是"家 / 工作间"那一层，不是容器，`SuggestLocationUseCase` 也从不建议根。
     * 父节点丢失的脏数据按 [roots] 的同一判法处理，它的路径就只剩自己的名字。
     */
    fun selectablePaths(): List<String> {
        val out = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        fun visit(id: String) {
            if (!seen.add(id)) return
            val node = byId[id] ?: return
            if (node.parentId != null) out += breadcrumb(id)
            childrenOf(id).forEach { visit(it.id) }
        }
        roots().forEach { visit(it.id) }
        return out
    }

    /** 自己 + 所有后代。**含自己**（`subtreeItemCount` 直接拿它求和）。栈式实现：树很深也不会爆栈。 */
    fun descendantIds(id: String): Set<String> {
        val out = linkedSetOf<String>()
        val stack = ArrayDeque(listOf(id))
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (!out.add(current)) continue
            childrenOf(current).forEach { stack.addLast(it.id) }
        }
        return out
    }
}

fun buildLocationTree(nodes: List<LocationNodeEntity>): LocationTree = LocationTree(nodes.map { it.toDomain() })
