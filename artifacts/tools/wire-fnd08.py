# FR-FND-08：Filters 的三个筛选字段改成集合，语义 = 同类内"或"、跨类"与"。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
ROOT = r"D:\src\dsh\wheremy\app\src"

def rw(path, pairs, must=True):
    p = ROOT + "\\" + path
    t = io.open(p, encoding="utf-8").read()
    for old, new in pairs:
        if old in t:
            t = t.replace(old, new)
        elif must:
            print("  !! %s 里没匹配到：%s" % (path, old.strip()[:60]))
    io.open(p, "w", encoding="utf-8", newline="").write(t)
    print("  已改 %s" % path)

# 1) Models.kt —— 字段改集合
rw(
    r"main\java\com\wheremy\app\data\repo\Models.kt",
    [
        (
            """data class Filters(
    val category: String? = null,
    val season: String? = null,
    val tag: String? = null,""",
            """data class Filters(
    /**
     * 组合筛选（`FR-FND-08`）：同一类里选多个是**或**，不同类之间是**与**。
     *
     * 例：`categories = {衣物, 电器}` + `seasons = {冬}` = 「衣物**或**电器」里那些**冬**天的。
     * 空集合 = 这一类不筛。
     */
    val categories: Set<String> = emptySet(),
    val seasons: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),""",
        ),
    ],
)

# 2) ItemRepository —— 标签候选并集 + matches 改集合
rw(
    r"main\java\com\wheremy\app\data\repo\ItemRepository.kt",
    [
        (
            """        val tagOk = if (filters.tag.isNullOrBlank()) {
            base
        } else {
            val ids = itemDao.itemIdsWithTag(filters.tag).toSet()
            base.filter { it.id in ids }
        }""",
            """        // 标签：多个标签是**或**——分别取"带这个标签的物品 id"，再并起来（FR-FND-08）
        val wanted = filters.tags.filter { it.isNotBlank() }
        val tagOk = if (wanted.isEmpty()) {
            base
        } else {
            val ids = wanted.flatMap { tag -> itemDao.itemIdsWithTag(tag) }.toSet()
            base.filter { it.id in ids }
        }""",
        ),
        (
            """        if (filters.category != null && item.category != filters.category) return false
        if (filters.season != null && filters.season !in splitList(item.seasons)) return false""",
            """        // 同一类里"或"、不同类之间"与"（FR-FND-08）
        if (filters.categories.isNotEmpty() && item.category !in filters.categories) return false
        if (filters.seasons.isNotEmpty() && splitList(item.seasons).none { it in filters.seasons }) return false""",
        ),
    ],
)

# 3) GalleryViewModel —— narrowing / 标签文案 / 三个 toggle
rw(
    r"main\java\com\wheremy\app\ui\gallery\GalleryViewModel.kt",
    [
        (
            """            filters.category != null ||
            filters.season != null ||
            filters.tag != null ||""",
            """            filters.categories.isNotEmpty() ||
            filters.seasons.isNotEmpty() ||
            filters.tags.isNotEmpty() ||""",
        ),
        (
            """                filters.season?.let { add("季节：$it") }
                filters.tag?.let { add("#$it") }""",
            """                if (filters.seasons.isNotEmpty()) add("季节：" + filters.seasons.joinToString("、"))
                if (filters.tags.isNotEmpty()) add(filters.tags.joinToString(" ") { "#$it" })""",
        ),
        (
            """    fun toggleCategory(category: String) {
        _state.update {
            it.copy(filters = it.filters.copy(category = if (it.filters.category == category) null else category))
        }
        refresh()
    }""",
            """    /** 组合筛选（`FR-FND-08`）：再点一次取消这一类里的这一个；同类内是"或"、跨类是"与"。 */
    fun toggleCategory(category: String) {
        _state.update { it.copy(filters = it.filters.copy(categories = it.filters.categories.toggled(category))) }
        refresh()
    }""",
        ),
        (
            """    fun toggleSeason(season: String) {
        _state.update {
            it.copy(filters = it.filters.copy(season = if (it.filters.season == season) null else season))
        }
        refresh()
    }""",
            """    fun toggleSeason(season: String) {
        _state.update { it.copy(filters = it.filters.copy(seasons = it.filters.seasons.toggled(season))) }
        refresh()
    }""",
        ),
        (
            """    fun toggleTag(tag: String) {
        _state.update {
            it.copy(filters = it.filters.copy(tag = if (it.filters.tag == tag) null else tag))
        }
        refresh()
    }""",
            """    fun toggleTag(tag: String) {
        _state.update { it.copy(filters = it.filters.copy(tags = it.filters.tags.toggled(tag))) }
        refresh()
    }""",
        ),
    ],
)

# 4) GalleryScreen —— 芯片 selected 判据 + hasFilters
rw(
    r"main\java\com\wheremy\app\ui\gallery\GalleryScreen.kt",
    [
        ("val hasFilters = vm.state.value.let { it.query.isNotBlank() || it.filters.category != null }",
         "val hasFilters = vm.state.value.let { it.query.isNotBlank() || it.filters.categories.isNotEmpty() }"),
        ("selected = state.filters.category == category,", "selected = category in state.filters.categories,"),
        ("selected = state.filters.season == season,", "selected = season in state.filters.seasons,"),
        ("selected = state.filters.tag == tag,", "selected = tag in state.filters.tags,"),
    ],
)

# 5) 调用点：DevSeedViewModel 与两个测试
rw(r"main\java\com\wheremy\app\ui\debug\DevSeedViewModel.kt",
   [('Filters(tag = "自检种子")', 'Filters(tags = setOf("自检种子"))')])
rw(r"test\java\com\wheremy\app\data\repo\ItemRepositoryTest.kt",
   [('Filters(tag = "冬装")', 'Filters(tags = setOf("冬装"))')])
rw(r"test\java\com\wheremy\app\ui\gallery\GalleryViewModelTest.kt",
   [('com.wheremy.app.data.repo.Filters(category = "衣物")', 'com.wheremy.app.data.repo.Filters(categories = setOf("衣物"))')])

print("完成")
