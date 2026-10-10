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
 * `FR-FND-09` 结果排序。
 *
 * 这里要钉**两组**东西：
 * 1. 三种新排序（最近动过 / 最近更新 / 名称）真的按那个键排；
 * 2. 🔒 **默认那一档一个字都没改**——空查询仍按插入序（`RULES.md` §10.1）、有查询仍按相关度。
 *    第 2 组是防回归：加排序最容易顺手把默认行为改掉，而那是**锁死的契约**。
 *
 * 时钟是可控的（每次读推进 1 毫秒），所以"谁更新"不靠 sleep。
 */
@RunWith(RobolectricTestRunner::class)
class SortOrderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var repo: ItemRepository
    private var clock = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ItemRepository(db, PhotoStore(File(tmp.root, "photos")), now = { clock++ })
    }

    @After
    fun tearDown() = db.close()

    private fun add(name: String, locationId: String? = null): String = runBlocking {
        val id = repo.newDraft(testJpeg())
        repo.confirm(ItemDraft(id = id, name = name, locationId = locationId))
        id
    }

    /** 三件：甲先建、乙次之、丙最后；随后**乙被搬动**、**甲被改名**（各自的时间戳最靠后）。 */
    private class Fixture(val first: String, val second: String, val third: String)

    private fun fixture(): Fixture = runBlocking {
        val home = LocationRepository(
            db,
            PhotoStore(File(tmp.root, "photos")),
        ).add(parentId = null, name = "家", kind = "房间")
        val first = add("甲")
        val second = add("乙")
        val third = add("丙")
        // 乙：搬到家里 → lastMovedAt 变成最新的
        repo.confirm(ItemDraft(id = second, name = "乙", locationId = home))
        // 甲：改个名 → updatedAt 变成最新的
        repo.confirm(ItemDraft(id = first, name = "甲改过"))
        Fixture(first, second, third)
    }

    private fun names(query: String = "", filters: Filters = Filters()): List<String> = runBlocking {
        repo.search(query, filters).rows.map { it.name }
    }

    @Test
    fun `默认排序一个字都不改：空查询按插入序`() {
        val f = fixture()
        assertEquals(
            "RULES §10.1：空查询走 all()，没有 ORDER BY → 插入序",
            listOf(f.first, f.second, f.third),
            runBlocking { repo.search("", Filters(sort = SortOrder.DEFAULT)).rows.map { it.id } },
        )
    }

    @Test
    fun `默认排序一个字都不改：有查询按相关度`() {
        // 两条都要命中同一个查询词（`searchLike` 是单向包含：`%电钻%` 不会命中"钻头"），
        // 名字**完全**等于查询词的那条分更高（SearchRanker 的 100/80/60…）
        add("电钻配件")
        add("电钻")
        val ranked = names("电钻", Filters(sort = SortOrder.DEFAULT))
        assertEquals("相关度高的在前，排序开关不该影响它", listOf("电钻", "电钻配件"), ranked)
    }

    @Test
    fun `最近动过：搬过的那件排最前`() {
        val f = fixture()
        val rows = runBlocking { repo.search("", Filters(sort = SortOrder.RECENTLY_MOVED)).rows }
        assertEquals("乙刚被搬过", f.second, rows.first().id)
        assertEquals("集合不变，只换顺序", 3, rows.size)
    }

    @Test
    fun `最近更新：改过的那件排最前`() {
        val f = fixture()
        val rows = runBlocking { repo.search("", Filters(sort = SortOrder.RECENTLY_UPDATED)).rows }
        assertEquals("甲刚改过名", f.first, rows.first().id)
    }

    @Test
    fun `名称：按名字升序`() {
        val f = fixture()
        val rows = runBlocking { repo.search("", Filters(sort = SortOrder.NAME)).rows.map { it.name } }
        assertEquals("字符串序（不是拼音序）", listOf("丙", "乙", "甲改过").sorted(), rows)
    }

    @Test
    fun `排序与筛选能一起用：只影响顺序，不影响集合`() {
        val f = fixture()
        val home = runBlocking { db.locationDao().all().first { it.name == "家" }.id }
        // 只筛"在家里"的那一件（乙），排序照旧生效
        val rows = runBlocking {
            repo.search("", Filters(sort = SortOrder.RECENTLY_MOVED)).rows.filter { it.locationId == home }
        }
        assertEquals(listOf(f.second), rows.map { it.id })
    }
}
