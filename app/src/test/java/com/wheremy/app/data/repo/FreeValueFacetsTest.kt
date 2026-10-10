package com.wheremy.app.data.repo

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.SearchTextBuilder
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.settings.testSettings
import com.wheremy.app.data.testJpeg
import com.wheremy.app.ui.gallery.GalleryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * `FR-AI-11`：**词表外的类别/季节也要筛得到、搜得到**（`RULES.md` §5.7 的方案②）。
 *
 * §5.7 要求二选一、不许留着不选。这里选的是②：**保持入库**（不丢模型的答案），
 * 把筛选与搜索都改成"也认自由值"——具体三处：
 * 1. `search_text` 纳入类别与季节（字段版本 2 → 3）；
 * 2. 筛选本来就走精确相等，值在库里就能筛（芯片云得先有这个值，见第 3 条）；
 * 3. 照片墙的芯片云 = 生效词表 ∪ 库里真出现过的值。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FreeValueFacetsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var repo: ItemRepository
    private lateinit var locations: LocationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val store = PhotoStore(File(tmp.root, "photos"))
        repo = ItemRepository(db, store)
        locations = LocationRepository(db, store)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun add(name: String, category: String? = null, seasons: List<String> = emptyList()): String = runBlocking {
        val id = repo.newDraft(testJpeg())
        repo.confirm(ItemDraft(id = id, name = name, category = category, seasons = seasons))
        id
    }

    @Test
    fun `词表外的类别能被搜到（search_text 里有它）`() {
        val id = add("移动硬盘", category = "数码产品")

        assertEquals(
            "按词表外的类别名搜得到",
            listOf(id),
            runBlocking { repo.search("数码产品", Filters()).rows.map { it.id } },
        )
    }

    @Test
    fun `词表外的季节能被搜到、也能被筛到`() {
        val id = add("羽绒服", category = "衣物", seasons = listOf("冬季"))
        add("短袖", category = "衣物", seasons = listOf("夏"))

        assertEquals(
            "搜「冬季」",
            listOf(id),
            runBlocking { repo.search("冬季", Filters()).rows.map { it.id } },
        )
        assertEquals(
            "按自由值筛（筛选走精确相等，值在库里就能筛）",
            listOf(id),
            runBlocking { repo.search("", Filters(seasons = setOf("冬季"))).rows.map { it.id } },
        )
    }

    @Test
    fun `自由值与预设值能同时筛（同类内是或）`() {
        val free = add("移动硬盘", category = "数码产品")
        val preset = add("电钻", category = "工具")

        assertEquals(
            setOf(free, preset),
            runBlocking { repo.search("", Filters(categories = setOf("数码产品", "工具"))).rows.map { it.id }.toSet() },
        )
    }

    @Test
    fun `照片墙的芯片云跟着数据长：词表在前，自由值缀在后`() {
        add("移动硬盘", category = "数码产品", seasons = listOf("冬季"))
        val vm = GalleryViewModel(repo, locations, testSettings())

        val state = runBlocking {
            repeat(200) {
                val s = vm.state.value
                if (!s.loading && "数码产品" in s.categories && "冬季" in s.seasons) return@runBlocking s
                Thread.sleep(10)
            }
            vm.state.value
        }

        assertTrue("自由类别进芯片云：${state.categories}", "数码产品" in state.categories)
        assertTrue("自由季节进芯片云：${state.seasons}", "冬季" in state.seasons)
        assertEquals("预设仍然排在前面（顺序是有意的）", listOf("衣物", "数码", "电器"), state.categories.take(3))
        assertTrue(
            "自由值缀在词表之后",
            state.categories.indexOf("数码产品") > state.categories.indexOf("其他"),
        )
    }

    @Test
    fun `没有自由值时芯片云就是词表本身`() {
        add("电钻", category = "工具", seasons = listOf("冬"))
        val vm = GalleryViewModel(repo, locations, testSettings())

        val state = runBlocking {
            repeat(200) {
                val s = vm.state.value
                if (!s.loading) return@runBlocking s
                Thread.sleep(10)
            }
            vm.state.value
        }

        assertEquals(CATEGORIES, state.categories)
        assertEquals(SEASONS, state.seasons)
    }

    @Test
    fun `字段版本是 3：加了类别与季节就必须加一，否则老库不会补算`() {
        assertEquals(3, SearchTextBuilder.FIELD_VERSION)
        assertTrue(
            "类别与季节确实进了 search_text",
            SearchTextBuilder.build(
                name = "移动硬盘",
                brand = null,
                color = null,
                material = null,
                aliases = null,
                description = null,
                notes = null,
                tags = emptyList(),
                category = "数码产品",
                seasons = "冬季",
            ).contains("数码产品"),
        )
    }
}
