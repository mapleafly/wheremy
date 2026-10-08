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
import org.junit.Assert.assertFalse
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
 * 位置仓库（`ARCHITECTURE.md` §6.2）：同级重排、防环三道 require、删除门槛、一位置一张照片。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocationRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var store: PhotoStore
    private lateinit var repo: LocationRepository
    private lateinit var items: ItemRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = PhotoStore(File(tmp.root, "photos"))
        repo = LocationRepository(db, store)
        items = ItemRepository(db, store)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `新位置落在同级末尾`() = runBlocking {
        val first = repo.add(parentId = null, name = "家", kind = "房间")
        val second = repo.add(parentId = null, name = "办公室", kind = "房间")

        assertEquals(0, repo.byId(first)!!.sortOrder)
        assertEquals(1, repo.byId(second)!!.sortOrder)
        assertEquals(listOf("家", "办公室"), repo.tree().roots().map { it.name })
    }

    @Test
    fun `同级重名与空名都拒`() {
        runBlocking {
            repo.add(parentId = null, name = "家", kind = "房间")

            assertEquals(
                "同一层级下已有「家」",
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { repo.add(parentId = null, name = " 家 ", kind = "房间") }
                }.message,
            )
            assertEquals(
                "位置名称不能为空",
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { repo.add(parentId = null, name = "   ", kind = null) }
                }.message,
            )
        }
    }

    @Test
    fun `不同层级可以重名`() = runBlocking {
        val home = repo.add(parentId = null, name = "家", kind = "房间")
        val office = repo.add(parentId = null, name = "办公室", kind = "房间")

        repo.add(parentId = home, name = "抽屉", kind = "抽屉")
        repo.add(parentId = office, name = "抽屉", kind = "抽屉")

        assertEquals(2, repo.all().count { it.name == "抽屉" })
    }

    @Test
    fun `update 一次写名字与类型`() = runBlocking {
        val home = repo.add(parentId = null, name = "家", kind = "房间")

        repo.update(home, name = "自己家", kind = "收纳盒")

        val node = repo.byId(home)!!
        assertEquals("自己家", node.name)
        assertEquals("收纳盒", node.kind)
    }

    @Test
    fun `changeParent 的三道 require`() {
        runBlocking {
            val home = repo.add(parentId = null, name = "家", kind = "房间")
            val bedroom = repo.add(parentId = home, name = "主卧", kind = "房间")
            val wardrobe = repo.add(parentId = bedroom, name = "衣柜", kind = "衣柜")

            assertEquals(
                "不能把位置移到它自己下面",
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { repo.changeParent(wardrobe, wardrobe) }
                }.message,
            )
            assertEquals(
                "目标位置不存在",
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { repo.changeParent(wardrobe, "不存在") }
                }.message,
            )
            assertEquals(
                "不能把位置移到它的子位置下面",
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { repo.changeParent(bedroom, wardrobe) }
                }.message,
            )
        }
    }

    @Test
    fun `changeParent 成功后落在新层末尾`() = runBlocking {
        val home = repo.add(parentId = null, name = "家", kind = "房间")
        val kitchen = repo.add(parentId = home, name = "厨房", kind = "房间")
        val box = repo.add(parentId = home, name = "收纳盒", kind = "收纳盒")

        repo.changeParent(box, null)

        val node = repo.byId(box)!!
        assertNull(node.parentId)
        assertEquals("落到根这一层的末尾", 1, node.sortOrder)
        assertEquals(listOf("家", "收纳盒"), repo.tree().roots().map { it.name })
        assertEquals(listOf("厨房"), repo.tree().childrenOf(home).map { it.name })
    }

    @Test
    fun `shiftWithinParent 重排整条同级而不是交换`() = runBlocking {
        val a = repo.add(parentId = null, name = "A", kind = null)
        val b = repo.add(parentId = null, name = "B", kind = null)

        assertTrue(repo.shiftWithinParent(b, up = true))

        assertEquals(listOf("B", "A"), repo.tree().roots().map { it.name })
        assertEquals("重排号必须是 0 和 1，不能留重复值", setOf(0, 1), repo.all().map { it.sortOrder }.toSet())
        assertFalse("到顶了就不能再上移", repo.shiftWithinParent(b, up = true))
        assertTrue(repo.shiftWithinParent(a, up = true))
        assertEquals(listOf("A", "B"), repo.tree().roots().map { it.name })
    }

    @Test
    fun `删除门槛看子位置与本层物品`() {
        runBlocking {
            val home = repo.add(parentId = null, name = "家", kind = "房间")
            val bedroom = repo.add(parentId = home, name = "主卧", kind = "房间")
            val itemId = items.newDraft(testJpeg())
            items.confirm(ItemDraft(id = itemId, name = "电钻", locationId = bedroom))

            val error = assertThrows(LocationNotEmptyException::class.java) {
                runBlocking { repo.delete(home) }
            }
            assertEquals("有 1 个子位置", 1, error.childCount)
            assertEquals("本层没有物品（物品在子位置上）", 0, error.itemCount)

            val itemError = assertThrows(LocationNotEmptyException::class.java) {
                runBlocking { repo.delete(bedroom) }
            }
            assertEquals(0, itemError.childCount)
            assertEquals(1, itemError.itemCount)
        }
    }

    @Test
    fun `空位置能删且会清掉它的照片`() = runBlocking {
        val box = repo.add(parentId = null, name = "收纳盒", kind = "收纳盒")
        repo.setPhoto(box, testJpeg())
        val path = repo.photoOf(box)!!.filePath

        repo.delete(box)

        assertNull(repo.byId(box))
        assertFalse("照片行没了，文件也要回收", store.file(path).exists())
    }

    @Test
    fun `一个位置只留一张照片`() = runBlocking {
        val box = repo.add(parentId = null, name = "收纳盒", kind = "收纳盒")

        repo.setPhoto(box, testJpeg(600, 300))
        val old = repo.photoOf(box)!!.filePath
        repo.setPhoto(box, testJpeg(500, 400))

        assertEquals(1, db.photoDao().forOwner(com.wheremy.app.data.db.PhotoOwner.LOCATION, box).size)
        assertTrue("新图在", store.file(repo.photoOf(box)!!.filePath).exists())
        assertFalse("旧图被回收", store.file(old).exists())
    }

    @Test
    fun `给已经没了的位置挂照片要拒`() {
        runBlocking {
            val error = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.setPhoto("不存在的位置", testJpeg()) }
            }
            assertEquals("这个位置已经不在了，照片没处可挂", error.message)
        }
    }

    @Test
    fun `clearPhoto 在没有照片时是空操作`() = runBlocking {
        val box = repo.add(parentId = null, name = "收纳盒", kind = "收纳盒")

        repo.clearPhoto(box)

        assertNull(repo.photoOf(box))
    }

    @Test
    fun `subtreeItemCount 含子位置的物品`() = runBlocking {
        val home = repo.add(parentId = null, name = "家", kind = "房间")
        val bedroom = repo.add(parentId = home, name = "主卧", kind = "房间")
        val wardrobe = repo.add(parentId = bedroom, name = "衣柜", kind = "衣柜")

        val inWardrobe = items.newDraft(testJpeg())
        items.confirm(ItemDraft(id = inWardrobe, name = "外套", locationId = wardrobe))

        assertEquals("物品在衣柜里，不在家的本层", 0, repo.itemCount(home))
        assertEquals(1, repo.subtreeItemCount(home))
        assertEquals(1, repo.subtreeItemCount(bedroom))
    }

    @Test
    fun `位置类型词表是预置芯片而不是枚举`() {
        // 界面上"也可自己填"不能省：这里断的是顺序与条数
        assertEquals(8, LOCATION_KINDS.size)
        assertEquals("房间", LOCATION_KINDS.first())
        assertEquals("其他", LOCATION_KINDS.last())
    }
}
