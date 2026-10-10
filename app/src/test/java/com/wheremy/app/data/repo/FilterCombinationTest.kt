package com.wheremy.app.data.repo

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.testJpeg
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * `FR-FND-08` 组合筛选：**同类内"或"、跨类"与"**。
 *
 * 这条最容易写反的地方是"多个类别"该是并集还是交集——用户的说法是
 * 「既要冬装**又要**外套」时是跨类（与），而「冬装**或**秋装」是同类（或）。
 * 所以这里把两种都钉住，外加"空集合 = 这一类不筛"。
 */
@RunWith(RobolectricTestRunner::class)
class FilterCombinationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var repo: ItemRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ItemRepository(db, PhotoStore(File(tmp.root, "photos")))
    }

    @After
    fun tearDown() = db.close()

    private fun add(
        name: String,
        category: String? = null,
        seasons: List<String> = emptyList(),
        tags: List<String> = emptyList(),
    ): String = runBlocking {
        val id = repo.newDraft(testJpeg())
        repo.confirm(ItemDraft(id = id, name = name, category = category, seasons = seasons, tags = tags))
        id
    }

    private fun names(filters: Filters): List<String> = runBlocking {
        repo.search("", filters).rows.map { it.name }
    }

    @Test
    fun `同一类里选多个是或`() {
        add("羽绒服", category = "衣物", seasons = listOf("冬"))
        add("电钻", category = "电器")
        add("雨伞", category = "其他")

        assertEquals(
            "衣物 或 电器",
            listOf("羽绒服", "电钻"),
            names(Filters(categories = setOf("衣物", "电器"))),
        )
    }

    @Test
    fun `不同类之间是与`() {
        add("羽绒服", category = "衣物", seasons = listOf("冬"))
        add("短袖", category = "衣物", seasons = listOf("夏"))
        add("电暖器", category = "电器", seasons = listOf("冬"))

        assertEquals(
            "衣物 且 冬",
            listOf("羽绒服"),
            names(Filters(categories = setOf("衣物"), seasons = setOf("冬"))),
        )
    }

    @Test
    fun `两个标签是或，标签与类别是与`() {
        add("羽绒服", category = "衣物", tags = listOf("冬装"))
        add("围巾", category = "衣物", tags = listOf("冬装"))
        add("电钻", category = "工具", tags = listOf("自检种子"))
        add("雨伞", category = "其他")

        assertEquals("冬装 或 自检种子", listOf("羽绒服", "围巾", "电钻"), names(Filters(tags = setOf("冬装", "自检种子"))))
        assertEquals(
            "（冬装 或 自检种子）且 衣物",
            listOf("羽绒服", "围巾"),
            names(Filters(tags = setOf("冬装", "自检种子"), categories = setOf("衣物"))),
        )
    }

    @Test
    fun `季节也支持多个`() {
        add("羽绒服", seasons = listOf("冬"))
        add("短袖", seasons = listOf("夏"))
        add("风衣", seasons = listOf("春", "秋"))

        assertEquals("冬 或 秋", listOf("羽绒服", "风衣"), names(Filters(seasons = setOf("冬", "秋"))))
    }

    @Test
    fun `空集合等于这一类不筛`() {
        add("羽绒服", category = "衣物", seasons = listOf("冬"))
        add("电钻", category = "电器")

        assertEquals("三类都空 → 全都出来", listOf("羽绒服", "电钻"), names(Filters()))
        assertEquals(
            "只有季节空 → 类别照筛",
            listOf("羽绒服"),
            names(Filters(categories = setOf("衣物"))),
        )
    }

    @Test
    fun `组合筛选与查询词能一起用`() {
        add("羽绒服", category = "衣物")
        add("羽绒被", category = "衣物")
        add("电钻", category = "电器")

        // 有查询词时结果按**相关度**排（同分时次序不定），所以这里比集合：要点是"两个条件都算数"
        assertEquals(
            "查询词 + 类别，两个条件都算数",
            setOf("羽绒服", "羽绒被"),
            runBlocking { repo.search("羽绒", Filters(categories = setOf("衣物"))).rows.map { it.name }.toSet() },
        )
    }
}
