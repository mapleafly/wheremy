package com.wheremy.app.domain

/**
 * 位置建议（`RULES.md` §5.4）：把模型给的 `container_guess` 对到用户自己的位置树上。
 *
 * 次序固定，五条判据一条都不能少：
 * 1. `guess` 空白/为 null → null；
 * 2. **整条路径原样命中**优先（两边空白全删掉再比）——两个不同父节点下都叫"抽屉"时，
 *    只认名字会挑错家，而模型这次拿到的本来就是完整串；
 * 3. 退回模糊打分：`kind == guess || name == guess` → 3；`kind` 互为包含 → 2；`name` 互为包含 → 1；否则 0；
 * 4. 取胜者：分数更高；**分数相同且 > 0 时取更浅的**；同分又同深度**不换**（于是拿到先序里最早的那个）；
 * 5. 都对不上 → null。**绝不虚构一个用户家没有的位置**；
 * 6. 根层级永不参与（根是"家/工作间"那一层，不是容器）。
 */
object SuggestLocationUseCase {

    fun suggest(selectable: List<LocationPath>, guess: String?): String? {
        val wanted = guess?.replace(" ", "")?.replace("\u3000", "")?.takeIf { it.isNotBlank() } ?: return null
        if (selectable.isEmpty()) return null

        // 2 整条路径原样命中
        val normalized = selectable.map { it to it.path.replace(" ", "").replace("\u3000", "") }
        normalized.firstOrNull { (_, path) -> path == wanted }?.let { return it.first.id }

        // 3 模糊打分
        var bestId: String? = null
        var bestScore = 0
        var bestDepth = Int.MAX_VALUE
        selectable.forEach { candidate ->
            val score = score(candidate, wanted)
            if (score > 0 && (score > bestScore || (score == bestScore && candidate.depth < bestDepth))) {
                bestId = candidate.id
                bestScore = score
                bestDepth = candidate.depth
            }
        }
        return bestId
    }

    private fun score(candidate: LocationPath, wanted: String): Int = when {
        candidate.kind == wanted || candidate.name == wanted -> 3
        candidate.kind != null && containsEither(candidate.kind, wanted) -> 2
        containsEither(candidate.name, wanted) -> 1
        else -> 0
    }

    private fun containsEither(a: String, b: String): Boolean = a.contains(b) || b.contains(a)
}

/**
 * 一根可选路径（喂给模型的是 [path]，对回来的是 [id]）。
 * [depth] 从 0 起算；根层级**不进这个清单**。
 */
data class LocationPath(val id: String, val path: String, val name: String, val kind: String?, val depth: Int)
