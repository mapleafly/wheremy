package com.wheremy.app.ui.gallery

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.ItemEntity
import com.wheremy.app.data.db.ItemStatus
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.item
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.repo.ItemDraft
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.data.testJpeg
import com.wheremy.app.util.expireWindowEnd
import com.wheremy.app.util.localStartOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * 照片墙的三段式搜索与筛选（`RULES.md` §10）。
 * 这里钉的是几条**很容易写反**的规则：空查询时的次序、标签筛选的口径、标签芯片的大小写合并、
 * 以及"有筛选条件时不能说还没有物品"。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GalleryViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var store: PhotoStore
    private lateinit var items: ItemRepository
    private lateinit var locations: LocationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = PhotoStore(File(tmp.root, "photos"))
        items = ItemRepository(db, store)
        locations = LocationRepository(db, store)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun gallery(inboxMode: Boolean = false): GalleryViewModel = GalleryViewModel(items, locations).also {
        it.setInboxMode(inboxMode)
    }

    private fun awaitRows(
        vm: GalleryViewModel,
        description: String,
        predicate: (GalleryUiState) -> Boolean,
    ): GalleryUiState {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val state = vm.state.value
            if (predicate(state)) return state
            Thread.sleep(20)
        }
        throw AssertionError("等不到：$description，当前 = ${vm.state.value}")
    }

    private fun addItem(name: String, locationId: String? = null, searchText: String = name.lowercase()): String =
        runBlocking {
            val id = items.newDraft(testJpeg())
            items.confirm(ItemDraft(id = id, name = name, locationId = locationId))
            if (searchText != name.lowercase()) {
                db.itemDao().upsert(db.itemDao().byId(id)!!.copy(searchText = searchText))
            }
            id
        }

    /**
     * FR-FND-07：`LIMIT 300` 截断发生在**打分之前**，界面必须把这件事说出来。
     *
     * 两边都要钉：正好撞到上限时 `capped = true`；只搜到一条时 `false`
     * （不然用户会被一句"还有更多"骗去反复缩小范围）。
     */
    @Test
    fun `搜索撞到三百条上限时界面要说出来`() {
        val total = ItemRepository.SEARCH_LIMIT + 1
        runBlocking {
            // 直接写行：301 次 newDraft 会把每张照片都编三遍 JPEG，测试慢到没意义
            repeat(total) { index ->
                db.itemDao().upsert(
                    ItemEntity(
                        id = "bulk-$index",
                        name = "物品$index",
                        category = null,
                        seasons = null,
                        color = null,
                        brand = null,
                        material = null,
                        aliases = null,
                        description = null,
                        notes = null,
                        quantity = 1,
                        locationId = null,
                        status = ItemStatus.DRAFT,
                        expiresAt = null,
                        expireKind = null,
                        expireNotifiedAt = null,
                        searchText = "共用词 物品$index",
                        createdAt = index.toLong(),
                        updatedAt = index.toLong(),
                        lastMovedAt = null,
                    ),
                )
            }
        }

        val vm = gallery()
        vm.onQueryChange("共用词")
        val cappedState = awaitRows(vm, "撞上限那一次") { it.capped }
        assertTrue("结果被 300 截断，界面要知情", cappedState.capped)
        assertEquals(ItemRepository.SEARCH_LIMIT, cappedState.rows.size)

        vm.onQueryChange("物品${total - 1}")
        val few = awaitRows(vm, "只搜到一条") { !it.capped && it.rows.size == 1 }
        assertFalse("没撞上限就不许说还有更多", few.capped)
    }

    @Test
    fun `空查询时按插入序而不是最近更新`() {
        val first = addItem("第一件")
        Thread.sleep(5)
        val second = addItem("第二件")

        val vm = gallery()
        val rows = awaitRows(vm, "两件都读出来") { it.rows.size == 2 }.rows

        // RULES §10.1：空查询走 all()，没有 ORDER BY → rowid 序（插入先后）
        assertEquals(listOf(first, second), rows.map { it.item.id })

        // 编辑第一件**不该**把它挪到后面（@Upsert 原地更新、不改 rowid）
        runBlocking { items.confirm(ItemDraft(id = first, name = "第一件改过")) }
        vm.refresh()
        val after = awaitRows(vm, "改名后重读") { it.rows.size == 2 }.rows
        assertEquals(listOf(first, second), after.map { it.item.id })
    }

    @Test
    fun `有词时名字命中的排前面`() {
        addItem("收纳盒", searchText = "收纳盒 装外套用的")
        val coat = addItem("深灰羽绒外套", searchText = "深灰羽绒外套")

        val vm = gallery()
        vm.onQueryChange("外套")
        val rows = awaitRows(vm, "搜到两件") { it.rows.size == 2 }.rows

        assertEquals("名字命中的在前", coat, rows.first().item.id)
    }

    @Test
    fun `标签筛选认 item_tag 行`() {
        val tagged = addItem("羽绒外套")
        runBlocking {
            items.confirm(ItemDraft(id = tagged, name = "羽绒外套", tags = listOf("冬装")))
        }
        addItem("收纳袋", searchText = "收纳袋 装冬装用的")

        val vm = gallery()
        vm.toggleTag("冬装")
        val rows = awaitRows(vm, "只筛出打标的") { it.rows.map { r -> r.item.id } == listOf(tagged) }.rows

        assertEquals(listOf(tagged), rows.map { it.item.id })
    }

    @Test
    fun `标签芯片大小写只留一颗`() {
        val a = addItem("MacBook")
        runBlocking { items.confirm(ItemDraft(id = a, name = "MacBook", tags = listOf("数码"))) }
        val b = addItem("iPad")
        runBlocking { items.confirm(ItemDraft(id = b, name = "iPad", tags = listOf("数码"))) }

        val vm = gallery()
        val state = awaitRows(vm, "标签读出来") { it.tags.isNotEmpty() }

        assertEquals("同一颗标签不该出现两次", listOf("数码"), state.tags)
    }

    @Test
    fun `分类与季节是 AND 关系`() {
        val a = addItem("T恤")
        runBlocking {
            val id = a
            items.confirm(ItemDraft(id = id, name = "T恤", category = "衣物", seasons = listOf("夏")))
        }
        val b = addItem("棉衣")
        runBlocking { items.confirm(ItemDraft(id = b, name = "棉衣", category = "衣物", seasons = listOf("冬"))) }

        val vm = gallery()
        vm.toggleCategory("衣物")
        vm.toggleSeason("夏")
        val rows = awaitRows(vm, "筛出夏天那件") { it.rows.map { r -> r.item.id } == listOf(a) }.rows

        assertEquals(listOf(a), rows.map { it.item.id })
    }

    @Test
    fun `快到期与已过期一起进来`() {
        val expired = addItem("过期牛奶")
        val soon = addItem("快到期电池")
        val far = addItem("很久以后")
        runBlocking {
            val today = LocalDate.now()
            items.confirm(
                ItemDraft(id = expired, name = "过期牛奶", expiresAt = localStartOfDay(today.minusDays(2))),
            )
            items.confirm(
                ItemDraft(id = soon, name = "快到期电池", expiresAt = localStartOfDay(today.plusDays(2))),
            )
            items.confirm(
                ItemDraft(id = far, name = "很久以后", expiresAt = localStartOfDay(today.plusDays(200))),
            )
        }

        val vm = gallery()
        vm.toggleExpiring()
        val rows = awaitRows(vm, "筛出两件") { it.rows.map { r -> r.item.id }.toSet() == setOf(expired, soon) }.rows

        assertEquals(setOf(expired, soon), rows.map { it.item.id }.toSet())
        assertTrue("窗口上界来自唯一来源", vm.state.value.filters.expireUntil!! <= expireWindowEnd(3))
    }

    @Test
    fun `有筛选条件时不算还没有物品`() {
        val plain = GalleryUiState(rows = emptyList())
        assertFalse("什么都没设：该说'还没有物品'", plain.narrowing)

        assertTrue(GalleryUiState(query = "外套").narrowing)
        assertTrue(GalleryUiState(filters = com.wheremy.app.data.repo.Filters(category = "衣物")).narrowing)
        assertTrue(GalleryUiState(filters = com.wheremy.app.data.repo.Filters(expireUntil = 1L)).narrowing)
        assertTrue(
            "非待归类页上的'只看待归类'算用户自己加的筛选",
            GalleryUiState(filters = com.wheremy.app.data.repo.Filters(onlyInbox = true)).narrowing,
        )
        assertFalse(
            "待归类页自带的 onlyInbox 不算",
            GalleryUiState(inboxMode = true, filters = com.wheremy.app.data.repo.Filters(onlyInbox = true)).narrowing,
        )
    }

    @Test
    fun `更多筛选那颗芯片点名开了哪些条件`() {
        val state = GalleryUiState(
            filters = com.wheremy.app.data.repo.Filters(
                season = "夏",
                tag = "冬装",
                expireUntil = 1L,
            ),
        )
        assertEquals("更多筛选 · 快到期/已过期 季节：夏 #冬装", state.moreFiltersLabel)
        assertEquals("更多筛选", GalleryUiState().moreFiltersLabel)
    }

    @Test
    fun `删除前把话一起存下来`() {
        val id = addItem("电钻")

        val vm = gallery()
        awaitRows(vm, "读出来") { it.rows.size == 1 }
        vm.askDelete(id)

        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && vm.state.value.pendingDelete == null) Thread.sleep(20)
        val pending = vm.state.value.pendingDelete!!
        assertEquals(id, pending.itemId)
        assertEquals("照片和记录会一起删除，无法恢复。", pending.warning)

        vm.confirmDelete()
        val after = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < after && vm.state.value.rows.isNotEmpty()) Thread.sleep(20)
        assertTrue(vm.state.value.rows.isEmpty())
        assertNull(runBlocking { items.get(id) })
    }

    @Test
    fun `搬到暂不归类会把位置清空`() {
        val home = runBlocking { locations.add(parentId = null, name = "家", kind = "房间") }
        val id = addItem("电钻", locationId = home)

        val vm = gallery()
        awaitRows(vm, "读出来") { it.rows.size == 1 }
        vm.move(id, null)

        // 两件事都要等：库里的行改了，且屏幕上的那一行也刷成了「待归类」
        assertNull(
            "库里该清空位置",
            awaitRows(vm, "屏幕上的位置行变成待归类") { it.rows.firstOrNull()?.locationPath == null }
                .let { runBlocking { items.get(id) } }
                ?.locationId,
        )
    }

    @Test
    fun `待归类模式只列没位置或还是草稿的`() {
        val home = runBlocking { locations.add(parentId = null, name = "家", kind = "房间") }
        val placed = addItem("已归位的", locationId = home)
        val draft = runBlocking { items.newDraft(testJpeg()) }

        val vm = gallery(inboxMode = true)
        val rows = awaitRows(vm, "待归类列表") { it.rows.map { r -> r.item.id } == listOf(draft) }.rows

        assertEquals(setOf(draft), rows.map { it.item.id }.toSet())
        assertFalse(rows.any { it.item.id == placed })
        assertTrue("标题之外，收件箱谓词也要真的开", vm.state.value.filters.onlyInbox)
        assertTrue(vm.state.value.inboxMode)
    }
}
