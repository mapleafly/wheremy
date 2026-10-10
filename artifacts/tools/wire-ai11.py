# FR-AI-11 方案②：保持入库 + 筛选/搜索也认自由值。
# 1) SearchTextBuilder 加 category/seasons，FIELD_VERSION 2 → 3
# 2) 三个调用点跟着改
# 3) 仓库加 usedFacetValues()（库里真出现过的类别与季节）
# 4) 照片墙芯片云 = 生效词表 ∪ 库里出现过的值
# 5) 确认页芯片行也带上"当前这个值"（词表外的也显示得出选中态）
import io
import sys

sys.stdout.reconfigure(errors="replace")
SRC = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app"

def rw(path, pairs):
    p = SRC + "\\" + path
    t = io.open(p, encoding="utf-8").read()
    for old, new in pairs:
        if old in t:
            t = t.replace(old, new, 1)
        else:
            print("  !! %s 没匹配：%s" % (path, old.strip().splitlines()[0][:70]))
    io.open(p, "w", encoding="utf-8", newline="").write(t)
    print("  已改 %s" % path)

# 1) SearchTextBuilder
rw(
    r"data\db\SearchTextBuilder.kt",
    [
        (
            """     * 1 → 2 是因为加了颜色与材质。
     */
    const val FIELD_VERSION = 2""",
            """     * 1 → 2 是因为加了颜色与材质；2 → 3 是因为加了类别与季节（`FR-AI-11`：
     * 模型可能回词表外的值，那类值也要能被搜到，不能只靠名字）。
     */
    const val FIELD_VERSION = 3""",
        ),
        (
            """    fun build(
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
    ).joinToString(" ")""",
            """    fun build(
        name: String,
        brand: String?,
        color: String?,
        material: String?,
        aliases: String?,
        description: String?,
        notes: String?,
        tags: List<String>,
        /** 类别与季节（CSV 或单个值）：`FR-AI-11` 之后它们也参与全文搜索。 */
        category: String? = null,
        seasons: String? = null,
    ): String = listOfNotNull(
        name,
        brand,
        color,
        material,
        aliases,
        category,
        seasons,
        tags.joinToString(" ").ifBlank { null },
        description,
        notes,
    ).joinToString(" ")""",
        ),
    ],
)

# 2) 三个调用点
rw(
    r"data\repo\ItemRepository.kt",
    [
        (
            """            searchText = SearchTextBuilder.build(
                name = merged.name,
                brand = merged.brand,
                color = merged.color,
                material = merged.material,
                aliases = merged.aliases,
                description = merged.description,
                notes = merged.notes,
                tags = tags,
            ),""",
            """            searchText = SearchTextBuilder.build(
                name = merged.name,
                brand = merged.brand,
                color = merged.color,
                material = merged.material,
                aliases = merged.aliases,
                description = merged.description,
                notes = merged.notes,
                tags = tags,
                category = merged.category,
                seasons = merged.seasons,
            ),""",
        ),
        (
            """                val text = SearchTextBuilder.build(
                    name = item.name,
                    brand = item.brand,
                    color = item.color,
                    material = item.material,
                    aliases = item.aliases,
                    description = item.description,
                    notes = item.notes,
                    tags = itemDao.tagsFor(item.id),
                )""",
            """                val text = SearchTextBuilder.build(
                    name = item.name,
                    brand = item.brand,
                    color = item.color,
                    material = item.material,
                    aliases = item.aliases,
                    description = item.description,
                    notes = item.notes,
                    tags = itemDao.tagsFor(item.id),
                    category = item.category,
                    seasons = item.seasons,
                )""",
        ),
        (
            """    private fun searchTextOf(draft: ItemDraft, name: String): String = SearchTextBuilder.build(
        name = name,
        brand = draft.brand.normalized(),
        color = draft.color.normalized(),
        material = draft.material.normalized(),
        aliases = csv(draft.aliases),
        description = draft.description.normalized(),
        notes = draft.notes.normalized(),
        tags = draft.tags.map { it.trim() }.filter { it.isNotEmpty() },
    )""",
            """    private fun searchTextOf(draft: ItemDraft, name: String): String = SearchTextBuilder.build(
        name = name,
        brand = draft.brand.normalized(),
        color = draft.color.normalized(),
        material = draft.material.normalized(),
        aliases = csv(draft.aliases),
        description = draft.description.normalized(),
        notes = draft.notes.normalized(),
        tags = draft.tags.map { it.trim() }.filter { it.isNotEmpty() },
        category = draft.category.normalized(),
        seasons = csv(draft.seasons),
    )""",
        ),
        (
            "    /** 检索结果 + 一句实话：这次是不是被 `LIMIT 300` 截了（FR-FND-07）。 */",
            '''    /**
     * 库里**真出现过**的类别与季节（`FR-AI-11`）。
     *
     * 用来把筛选芯片云撑到"数据里有的都能筛"：模型回了词表外的值（例如「冬季」「数码产品」），
     * 光有预设芯片的话那一件永远筛不出来。词表外的值由用户自己在确认页上决定留不留。
     */
    suspend fun usedFacetValues(): Pair<Set<String>, Set<String>> {
        val categories = mutableSetOf<String>()
        val seasons = mutableSetOf<String>()
        itemDao.all().forEach { item ->
            item.category?.trim()?.takeIf { it.isNotEmpty() }?.let { categories += it }
            splitList(item.seasons).forEach { seasons += it }
        }
        return categories to seasons
    }

    /** 检索结果 + 一句实话：这次是不是被 `LIMIT 300` 截了（FR-FND-07）。 */''',
        ),
    ],
)

# 3) 照片墙：芯片云 = 词表 ∪ 库里出现过的值
rw(
    r"ui\gallery\GalleryViewModel.kt",
    [
        (
            """                        categories = saved.categories.ifEmpty { CATEGORIES },
                        seasons = saved.seasons.ifEmpty { SEASONS },
                    )""",
            """                        // 词表 ∪ 库里真出现过的值（`FR-AI-11`）：模型回了词表外的类别/季节时，
                        // 那一件也得能筛出来——否则"记是记下了，只能靠名字找"
                        categories = mergeFacets(saved.categories.ifEmpty { CATEGORIES }, used.first),
                        seasons = mergeFacets(saved.seasons.ifEmpty { SEASONS }, used.second),
                    )""",
        ),
    ],
)

# 4) 确认页：芯片行带上当前值
rw(
    r"ui\review\ReviewScreen.kt",
    [
        (
            """            ChipRow("分类") {
                wordLists.categories.forEach { category ->""",
            """            ChipRow("分类") {
                // 带上"当前这个值"：模型回了词表外的类别时，芯片行上也要看得出选的是哪个（FR-AI-11）
                (wordLists.categories + listOfNotNull(cur.draft.category?.takeIf { it !in wordLists.categories }))
                    .forEach { category ->""",
        ),
        (
            """            ChipRow("季节") {
                wordLists.seasons.forEach { season ->""",
            """            ChipRow("季节") {
                (wordLists.seasons + cur.draft.seasons.filter { it !in wordLists.seasons }).forEach { season ->""",
        ),
    ],
)
print("完成")
