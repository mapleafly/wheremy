# 把 FR-AI-11 的芯片云合并接对：usedFacets 缓存在 VM 里，refresh 时更新；再加 mergeFacets。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app\ui\gallery\GalleryViewModel.kt"
t = io.open(PATH, encoding="utf-8").read()

# 1) 缓存字段
old = "    private var refreshSeq = 0"
new = """    private var refreshSeq = 0

    /**
     * 库里**真出现过**的类别与季节（`FR-AI-11`）。
     *
     * 缓存在这里而不是每次读库：词表变化（`settings.changes`）与列表刷新是两条独立的路径，
     * 两边都要用同一份"数据里有什么"。
     */
    private var usedFacets: Pair<Set<String>, Set<String>> = emptySet<String>() to emptySet()"""
assert old in t
t = t.replace(old, new, 1)

# 2) refresh：先取 usedFacets，再算芯片云
old2 = """            val tags = items.allTags()
            if (seq != refreshSeq) return@launch // 有更新的刷新在飞：这次的结果已经过期，丢掉
            _state.update { it.copy(rows = rows, tags = tags, loading = false, capped = outcome.capped) }"""
new2 = """            val tags = items.allTags()
            usedFacets = items.usedFacetValues()
            val saved = settings.current()
            if (seq != refreshSeq) return@launch // 有更新的刷新在飞：这次的结果已经过期，丢掉
            _state.update {
                it.copy(
                    rows = rows,
                    tags = tags,
                    loading = false,
                    capped = outcome.capped,
                    // 词表 ∪ 库里真出现过的值：模型回了词表外的类别/季节时，那一件也得能筛出来
                    categories = mergeFacets(saved.categories.ifEmpty { CATEGORIES }, usedFacets.first),
                    seasons = mergeFacets(saved.seasons.ifEmpty { SEASONS }, usedFacets.second),
                )
            }"""
assert old2 in t, "refresh 的收尾没匹配"
t = t.replace(old2, new2, 1)

# 3) 文件尾加 mergeFacets
t = t.rstrip() + '''

/**
 * 芯片云 = 生效词表（保持原有顺序）∪ 库里出现过的值（追加在末尾、按字典序）。
 *
 * 顺序有意为之：用户熟悉的预设排在前面，模型"发明"的那些缀在后面——
 * 一眼能看出哪些是词表外的（`FR-AI-11`）。
 */
private fun mergeFacets(wordList: List<String>, used: Set<String>): List<String> =
    wordList + used.filter { it !in wordList }.sorted()
'''
io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("GalleryViewModel 已接好芯片云合并")
