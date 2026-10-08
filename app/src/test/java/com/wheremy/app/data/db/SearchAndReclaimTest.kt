package com.wheremy.app.data.db

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.item
import com.wheremy.app.data.location
import com.wheremy.app.data.photo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * P1 的三个出口判据里，"中文命中"与"引用计数"在这一支。
 * 每一条断言都对应文档里的一句话，注释里给出出处。
 */
@RunWith(RobolectricTestRunner::class)
class SearchAndReclaimTest {

    private lateinit var db: WheremyDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `中文按子串就能命中`() = runBlocking {
        val dao = db.itemDao()
        dao.upsert(item(id = "coat", name = "深灰羽绒外套", searchText = "深灰羽绒外套 uniqlo 深灰"))
        dao.upsert(item(id = "drill", name = "电钻", searchText = "电钻 博世"))

        assertEquals(listOf("coat"), dao.searchLike("%外套%").map { it.id })
        assertEquals(listOf("coat"), dao.searchLike("%羽绒%").map { it.id })
        assertEquals(listOf("drill"), dao.searchLike("%博世%").map { it.id })
        assertTrue("搜不到的词就是空", dao.searchLike("%不存在的词%").isEmpty())
    }

    @Test
    fun `英文搜索靠写入侧小写化`() = runBlocking {
        val dao = db.itemDao()
        // searchText 写入时已 lowercase（RULES.md §10.3），读侧把用户输入也 lowercase
        dao.upsert(item(id = "mac", name = "MacBook Pro", searchText = "macbook pro apple"))

        assertEquals(listOf("mac"), dao.searchLike("%${"MacBook".lowercase()}%").map { it.id })
    }

    @Test
    fun `搜索结果上限是 300 且按 updatedAt 倒序`() = runBlocking {
        val dao = db.itemDao()
        // 造 305 条，验证"截断发生在打分之前"这个硬上限确实在 SQL 里
        dao.upsertAll(
            (1..305).map {
                item(id = "i-$it", name = "螺丝盒$it", searchText = "螺丝盒$it", updatedAt = it.toLong())
            },
        )

        val hits = dao.searchLike("%螺丝盒%")
        assertEquals(300, hits.size)
        assertEquals("最新的排最前", "i-305", hits.first().id)
        assertTrue("被截掉的是最旧的", hits.none { it.id == "i-1" })
    }

    @Test
    fun `标签筛选大小写不敏感且认 item_tag 的行`() = runBlocking {
        val dao = db.itemDao()
        dao.upsert(item(id = "mac", name = "MacBook", searchText = "macbook"))
        dao.insertTags(listOf(ItemTagEntity(itemId = "mac", tag = "MacBook")))

        assertEquals(listOf("mac"), dao.itemIdsWithTag("macbook"))
        assertEquals(listOf("mac"), dao.itemIdsWithTag("MACBOOK"))
        assertTrue("没这个标签就是空", dao.itemIdsWithTag("windows").isEmpty())
    }

    @Test
    fun `待归类是没有位置或还是草稿的那些`() = runBlocking {
        val dao = db.itemDao()
        // 有位置的行必须先有位置节点，否则外键当场拒（这正是我们要的行为，见 DatabaseSmokeTest）
        db.locationDao().upsert(location(id = "room", name = "主卧"))

        dao.upsert(item(id = "no-location", name = "A", locationId = null, status = ItemStatus.CONFIRMED))
        dao.upsert(item(id = "draft", name = "B", locationId = "room", status = ItemStatus.DRAFT))
        dao.upsert(item(id = "filed", name = "C", locationId = "room", status = ItemStatus.CONFIRMED))

        assertEquals(setOf("no-location", "draft"), dao.inbox().map { it.id }.toSet())
    }

    @Test
    fun `搬移用同一个时间戳写 lastMovedAt 与 updatedAt`() = runBlocking {
        val dao = db.itemDao()
        db.locationDao().upsert(location(id = "box", name = "收纳盒"))
        db.locationDao().upsert(location(id = "drawer", name = "抽屉"))
        dao.upsert(item(id = "drill", name = "电钻", locationId = "box", updatedAt = 1L))

        dao.moveTo(itemId = "drill", locationId = "drawer", movedAt = 99L)

        val moved = dao.atLocation("drawer").single()
        assertEquals(99L, moved.lastMovedAt)
        assertEquals("两个值必须一致，否则'刚搬到哪'和'最近动过'会打架", 99L, moved.updatedAt)
    }

    @Test
    fun `引用计数只在还有别的行指着它时才不为零`() = runBlocking {
        val photoDao = db.photoDao()
        photoDao.upsert(photo(id = "p1", ownerId = "i-1", filePath = "originals/a.jpg", thumbPath = "thumbs/a.jpg"))
        photoDao.upsert(photo(id = "p2", ownerId = "i-2", filePath = "originals/a.jpg", thumbPath = "thumbs/a.jpg"))

        assertEquals("两条物品行共用同一份字节", 2, photoDao.refsTo("originals/a.jpg"))
        assertEquals(2, photoDao.refsTo("thumbs/a.jpg"))

        photoDao.deleteForOwner(PhotoOwner.ITEM, "i-1")
        assertEquals(1, photoDao.refsTo("originals/a.jpg"))

        photoDao.deleteForOwner(PhotoOwner.ITEM, "i-2")
        assertEquals("再没有任何照片行指着它", 0, photoDao.refsTo("originals/a.jpg"))
    }

    @Test
    fun `除自己之外还有哪些主人指着这个路径`() = runBlocking {
        val photoDao = db.photoDao()
        // 同一份文件：物品 i-1、另一个物品 i-2、以及一个位置 l-1 都在用
        photoDao.upsert(photo(id = "p1", ownerType = PhotoOwner.ITEM, ownerId = "i-1"))
        photoDao.upsert(photo(id = "p2", ownerType = PhotoOwner.ITEM, ownerId = "i-2"))
        photoDao.upsert(photo(id = "p3", ownerType = PhotoOwner.LOCATION, ownerId = "l-1"))

        val others = photoDao.otherOwnersOnPath(PhotoOwner.ITEM, "i-1", "originals/a.jpg")

        // "照片会不会跟着没"要看的是"除我之外还有谁"：1 个别的物品 + 1 个位置
        assertEquals(1, others.first { it.ownerType == PhotoOwner.ITEM }.owners)
        assertEquals(1, others.first { it.ownerType == PhotoOwner.LOCATION }.owners)
    }
}
