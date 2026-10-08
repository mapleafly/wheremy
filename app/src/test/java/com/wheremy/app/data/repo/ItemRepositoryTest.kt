package com.wheremy.app.data.repo

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.ItemStatus
import com.wheremy.app.data.db.PhotoOwner
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.item
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.testJpeg
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 物品仓库的跨层不变量（`ARCHITECTURE.md` §6.1）。
 * 这些断言对应文档里写死的契约：两行同生同死、空串归一、`lastMovedAt` 三档、
 * 标签走 `item_tag`、先删行再回收文件。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ItemRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var store: PhotoStore
    private lateinit var repo: ItemRepository
    private lateinit var locations: LocationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = PhotoStore(File(tmp.root, "photos"))
        repo = ItemRepository(db, store)
        locations = LocationRepository(db, store)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `newDraft 写两个文件并落两行`() = runBlocking {
        val id = repo.newDraft(testJpeg())

        val item = repo.get(id)
        assertNotNull(item)
        assertEquals(ItemStatus.DRAFT, item!!.status)
        assertEquals("新草稿没名字", "", item.name)
        assertEquals("派生列先给空串", "", item.searchText)

        val photos = repo.photosOf(id)
        assertEquals(1, photos.size)
        assertTrue("原图文件要真在", store.file(photos.single().filePath).exists())
        assertTrue("缩略图文件要真在", store.file(photos.single().thumbPath).exists())

        assertNotNull("第一次取能拿到分析图", repo.takePendingAnalysis())
        assertNull("取一次即清", repo.takePendingAnalysis())
    }

    @Test
    fun `confirm 拒绝空名称`() {
        runBlocking {
            val id = repo.newDraft(testJpeg())
            val error = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.confirm(ItemDraft(id = id, name = "   ")) }
            }
            assertEquals("名称不能为空", error.message)
        }
    }

    @Test
    fun `confirm 写 searchText 标签与状态`() = runBlocking {
        val box = locations.add(parentId = null, name = "家", kind = "房间")
        val id = repo.newDraft(testJpeg())

        repo.confirm(
            ItemDraft(
                id = id,
                name = "深灰羽绒外套",
                brand = "Uniqlo",
                color = "深灰",
                material = "羽绒",
                locationId = box,
                tags = listOf("冬装", "外套", "冬装"),
            ),
        )

        val item = repo.get(id)!!
        assertEquals(ItemStatus.CONFIRMED, item.status)
        assertTrue("searchText 要含小写化的品牌", item.searchText.contains("uniqlo"))
        assertTrue(item.searchText.contains("深灰"))
        assertEquals("标签去重后整批替换（SQL 按字节序排，中文不是拼音序）", listOf("冬装", "外套"), repo.allTags())
        assertEquals("新建就落位：lastMovedAt 要有值", item.updatedAt, item.lastMovedAt)
    }

    @Test
    fun `confirm 位置没变时 lastMovedAt 不动`() = runBlocking {
        val home = locations.add(parentId = null, name = "家", kind = "房间")
        val box = locations.add(parentId = home, name = "收纳盒", kind = "收纳盒")
        val id = repo.newDraft(testJpeg())

        repo.confirm(ItemDraft(id = id, name = "电钻", locationId = box))
        val first = repo.get(id)!!.lastMovedAt

        repo.confirm(ItemDraft(id = id, name = "电钻", locationId = box))
        assertEquals("位置没变就不该刷新", first, repo.get(id)!!.lastMovedAt)

        repo.confirm(ItemDraft(id = id, name = "电钻", locationId = home))
        assertTrue("换位置必须刷新", repo.get(id)!!.lastMovedAt!! > first!!)
    }

    @Test
    fun `改了到期日会重新武装提醒`() = runBlocking {
        val id = repo.newDraft(testJpeg())
        repo.confirm(ItemDraft(id = id, name = "牛奶", expiresAt = 1000L))

        // 模拟"提醒已经发过"
        val notified = repo.get(id)!!.copy(expireNotifiedAt = 999L)
        db.itemDao().upsert(notified)

        repo.confirm(ItemDraft(id = id, name = "牛奶", expiresAt = 1000L))
        assertEquals("到期日没变：提醒记录保留", 999L, repo.get(id)!!.expireNotifiedAt)

        repo.confirm(ItemDraft(id = id, name = "牛奶", expiresAt = 2000L))
        assertNull("到期日变了：要能再提醒一次", repo.get(id)!!.expireNotifiedAt)
    }

    @Test
    fun `keepAsDraft 写字段但不动位置`() = runBlocking {
        val home = locations.add(parentId = null, name = "家", kind = "房间")
        val id = repo.newDraft(testJpeg())
        repo.confirm(ItemDraft(id = id, name = "护照", locationId = home))

        repo.keepAsDraft(ItemDraft(id = id, name = "护照（待补）", locationId = null, notes = "在抽屉里"))

        val item = repo.get(id)!!
        assertEquals(ItemStatus.DRAFT, item.status)
        assertEquals("位置不动", home, item.locationId)
        assertEquals("在抽屉里", item.notes)
        assertTrue("翻回草稿就该出现在待归类里", repo.inbox().any { it.id == id })
    }

    @Test
    fun `move 对空名字不转已确认`() = runBlocking {
        val home = locations.add(parentId = null, name = "家", kind = "房间")
        val draftId = repo.newDraft(testJpeg())

        repo.move(draftId, home)

        val stillDraft = repo.get(draftId)!!
        assertEquals("空名转 CONFIRMED 会从收件箱消失且搜不到", ItemStatus.DRAFT, stillDraft.status)
        assertEquals(home, stillDraft.locationId)
    }

    @Test
    fun `标签筛选认 item_tag 行而不是描述里的字面`() = runBlocking {
        val tagged = repo.newDraft(testJpeg())
        repo.confirm(ItemDraft(id = tagged, name = "羽绒外套", tags = listOf("冬装")))

        val mentioned = repo.newDraft(testJpeg())
        repo.confirm(
            ItemDraft(id = mentioned, name = "收纳袋", description = "装冬装用的，不是冬装"),
        )

        val hits = repo.rankedSearch("", Filters(tag = "冬装"))

        assertEquals(listOf(tagged), hits.map { it.id })
    }

    @Test
    fun `搜不到的词返回空且空 query 走全量`() = runBlocking {
        val id = repo.newDraft(testJpeg())
        repo.confirm(ItemDraft(id = id, name = "电钻"))

        assertTrue(repo.rankedSearch("不存在的东西").isEmpty())
        assertEquals(1, repo.rankedSearch("").size)
        assertEquals(1, repo.rankedSearch("电钻").size)
    }

    @Test
    fun `共用一张照的第二件不写新文件`() = runBlocking {
        val first = repo.newDraft(testJpeg())
        val before = repo.photosOf(first).single()

        val second = repo.newDraftSharingPhoto(first)

        val shared = repo.photosOf(second).single()
        assertEquals("路径必须一样", before.filePath, shared.filePath)
        assertEquals(2, db.photoDao().refsTo(before.filePath))
    }

    @Test
    fun `delete 先删行再按引用计数回收`() = runBlocking {
        val first = repo.newDraft(testJpeg())
        val second = repo.newDraftSharingPhoto(first)
        val path = repo.photosOf(first).single().filePath

        repo.delete(first)
        assertTrue("还有别人指着它，一个字节都不许动", store.file(path).exists())
        assertEquals(1, db.photoDao().refsTo(path))

        repo.delete(second)
        assertFalse("没人指着了才删", store.file(path).exists())
    }

    @Test
    fun `swapPhoto 换完回收旧文件`() = runBlocking {
        val id = repo.newDraft(testJpeg())
        val old = repo.photosOf(id).single()

        repo.swapPhoto(id, testJpeg(1000, 500))

        val fresh = repo.photosOf(id).single()
        assertTrue("新图在", store.file(fresh.filePath).exists())
        assertFalse("旧图被回收", store.file(old.filePath).exists())
        assertFalse(store.file(old.thumbPath).exists())
    }

    @Test
    fun `summaries 带上标签 缩略图与位置路径`() = runBlocking {
        val home = locations.add(parentId = null, name = "家", kind = "房间")
        val box = locations.add(parentId = home, name = "收纳盒", kind = "收纳盒")
        val id = repo.newDraft(testJpeg())
        repo.confirm(ItemDraft(id = id, name = "电钻", locationId = box, tags = listOf("工具")))

        val summary = repo.summaries(listOf(repo.get(id)!!)).single()

        assertEquals(listOf("工具"), summary.tags)
        assertNotNull(summary.thumbPath)
        assertEquals("家 / 收纳盒", summary.locationPath)
    }

    @Test
    fun `rebuildSearchText 只补算真变了的那条`() = runBlocking {
        val stale = item(id = "stale", name = "MacBook", brand = "Apple", searchText = "旧值")
        val fresh = item(id = "fresh", name = "护照", searchText = "护照")
        db.itemDao().upsertAll(listOf(stale, fresh))

        val changed = repo.rebuildSearchText()

        assertEquals("只有 stale 需要补算", 1, changed)
        assertEquals("macbook apple", repo.get("stale")!!.searchText)
        assertEquals("护照", repo.get("fresh")!!.searchText)
    }

    @Test
    fun `photoDeletionOf 报出有没有照片与别人还用不用`() = runBlocking {
        val none = repo.newDraft(testJpeg())
        repo.delete(none)
        assertEquals(PhotoDeletion(false, 0, 0), repo.photoDeletionOf(none))

        val first = repo.newDraft(testJpeg())
        val second = repo.newDraftSharingPhoto(first)
        val shared = repo.photoDeletionOf(second)
        assertTrue(shared.hasPhoto)
        assertEquals("除自己之外还有一个物品在用", 1, shared.otherItems)
        assertEquals(0, shared.otherOwners)
        assertNotNull(first)
    }

    @Test
    fun `location 行的照片走位置主人`() = runBlocking {
        val home = locations.add(parentId = null, name = "家", kind = "房间")
        locations.setPhoto(home, testJpeg())

        val photo = locations.photoOf(home)!!
        assertEquals(PhotoOwner.LOCATION, photo.ownerType)
        assertEquals(home, photo.ownerId)
    }
}
