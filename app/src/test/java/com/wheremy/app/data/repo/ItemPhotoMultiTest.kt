package com.wheremy.app.data.repo

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.ItemPhotoEntity
import com.wheremy.app.data.db.PhotoOwner
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.testJpeg
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `FR-SRC-08`（一件物品多张照片）的数据层规矩 —— 先写测试，看它红。
 *
 * 这两条今天**必红**，因为它们钉的正是多图会踩的坑（`ADR-26` 的"坏后果"）：
 * 1. 删物品时只回收**第一张**的照片文件，其余几张成了谁都不认的孤儿字节；
 * 2. 删除确认框那句"照片会不会跟着没"只按第一张算，于是**界面说了不成立的话**（`ADR-22`：这是 P1）。
 *
 * 表本来就支持多行（`item_photo` 主键是行 id、带 `sortOrder`），所以测试直接写第二行来模拟"多图已经存在"，
 * 不需要等上层 API。
 */
@RunWith(RobolectricTestRunner::class)
class ItemPhotoMultiTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var store: PhotoStore
    private lateinit var items: ItemRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = PhotoStore(java.io.File(tmp.root, "photos"))
        items = ItemRepository(db, store)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** 建一件物品（自带一张照片），返回它的 id 与第一行照片。 */
    private fun newItem(name: String): Pair<String, ItemPhotoEntity> = runBlocking {
        val id = items.newDraft(testJpeg())
        items.confirm(ItemDraft(id = id, name = name))
        id to db.photoDao().forOwner(PhotoOwner.ITEM, id).single()
    }

    /** 给某件物品**再挂一张**照片：写文件 + 插行（`sortOrder` 追加在末尾，`RULES.md` §3.4）。 */
    private fun appendPhoto(itemId: String, existing: Int): ItemPhotoEntity = runBlocking {
        val stored = store.write(testJpeg(width = 600, height = 600))
        val row = ItemPhotoEntity(
            id = "photo-$itemId-$existing",
            ownerType = PhotoOwner.ITEM,
            ownerId = itemId,
            filePath = stored.originalPath,
            thumbPath = stored.thumbPath,
            sortOrder = existing,
            createdAt = 2_000L + existing,
        )
        db.photoDao().upsert(row)
        row
    }

    @Test
    fun `删物品会回收该物品全部照片的文件，不是只看第一张`() {
        val (id, first) = newItem("电钻")
        val second = appendPhoto(id, existing = 1)

        val firstFile = store.file(first.filePath)
        val secondFile = store.file(second.filePath)
        assertTrue("前置：两张原图都写着盘", firstFile.exists() && secondFile.exists())

        runBlocking { items.delete(id) }

        assertFalse("第一张的原图应被回收", firstFile.exists())
        assertFalse("第二张的原图也该被回收——否则就是没人认的孤儿字节", secondFile.exists())
        assertFalse("第二张的缩略图同理", store.file(second.thumbPath).exists())
    }

    @Test
    fun `删除确认框要按全部照片数别的物品，不能只数第一张`() {
        val (mine, mineFirst) = newItem("电钻")
        val mineSecond = appendPhoto(mine, existing = 1)

        // 另一件物品借用了**第二张**这张图（共享同一个文件，这在同一次分析出的多件里很常见）
        val (other, _) = newItem("螺丝刀")
        runBlocking {
            db.photoDao().deleteForOwner(PhotoOwner.ITEM, other)
            db.photoDao().upsert(
                ItemPhotoEntity(
                    id = "shared-row",
                    ownerType = PhotoOwner.ITEM,
                    ownerId = other,
                    filePath = mineSecond.filePath,
                    thumbPath = mineSecond.thumbPath,
                    sortOrder = 0,
                    createdAt = 3_000L,
                ),
            )
        }
        runBlocking {
            assertTrue("前置：第二张确实被另一件物品指着", db.photoDao().refsTo(mineSecond.filePath) == 2)
            assertTrue("前置：第一张只有自己指着", db.photoDao().refsTo(mineFirst.filePath) == 1)
        }

        val deletion = runBlocking { items.photoDeletionOf(mine) }

        assertEquals("第一张没人共用，另有共用的是第二张——确认框必须把这件事说出来", 1, deletion.otherItems)
    }

    @Test
    fun `封面恒是第一张（sortOrder 最小的那张）`() {
        val (id, first) = newItem("电钻")
        appendPhoto(id, existing = 1)

        val cover = runBlocking { items.thumbPathOf(id) }

        assertEquals("封面必须是第一张，换了封面就像丢了图（RULES.md §3.4）", first.thumbPath, cover)
    }

    // —— 以下钉的是新加的 API（加一张 / 删一张）——

    @Test
    fun `加一张追加在末尾，封面不动`() {
        val (id, first) = newItem("电钻")

        val added = runBlocking { items.addPhotoToItem(id, testJpeg(width = 500, height = 500)) }

        assertTrue("加成功要返回 true", added)
        val rows = runBlocking { items.photosOf(id) }
        assertEquals("两张", 2, rows.size)
        assertEquals("第 1 张的 sortOrder 最小（封面）", 0, rows[0].sortOrder)
        assertEquals("新的追加在末尾，不插中间", 1, rows[1].sortOrder)
        assertEquals("封面没被换掉", first.thumbPath, runBlocking { items.thumbPathOf(id) })
    }

    @Test
    fun `到 9 张上限就不再写，且一个字节都不落盘`() {
        val (id, _) = newItem("电钻")
        repeat(ItemRepository.MAX_PHOTOS_PER_ITEM - 1) { i -> appendPhoto(id, existing = i + 1) }
        val before = runBlocking { items.photosOf(id) }
        assertEquals("前置：已经 9 张", ItemRepository.MAX_PHOTOS_PER_ITEM, before.size)

        val added = runBlocking { items.addPhotoToItem(id, testJpeg(width = 400, height = 400)) }

        assertFalse("到上限要拒", added)
        assertEquals("还是 9 张", ItemRepository.MAX_PHOTOS_PER_ITEM, runBlocking { items.photosOf(id) }.size)
    }

    @Test
    fun `物品行已不在时不写文件也不写行`() {
        val (id, _) = newItem("电钻")
        runBlocking { items.delete(id) }

        val added = runBlocking { items.addPhotoToItem(id, testJpeg()) }

        assertFalse("行都没了，别写出一对没人认的文件", added)
    }

    @Test
    fun `删掉某一张之后，别的张不受影响且封面换成新的第一张`() {
        val (id, first) = newItem("电钻")
        val second = appendPhoto(id, existing = 1)
        val third = appendPhoto(id, existing = 2)

        runBlocking { items.removePhoto(first.id) }

        val rows = runBlocking { items.photosOf(id) }
        assertEquals("剩两张", 2, rows.size)
        assertFalse("被删那张的原图要回收", store.file(first.filePath).exists())
        assertTrue("别人那张一个字都不动", store.file(second.filePath).exists())
        assertTrue("第三张也不动", store.file(third.filePath).exists())
        assertEquals("封面自动换成现在的第一张", second.thumbPath, runBlocking { items.thumbPathOf(id) })
    }

    @Test
    fun `删掉别人还在用的那一张时文件留着（引用计数）`() {
        val (mine, mineFirst) = newItem("电钻")
        val mineSecond = appendPhoto(mine, existing = 1)

        // 另一件物品借用了第二张
        val (other, _) = newItem("螺丝刀")
        runBlocking {
            db.photoDao().deleteForOwner(PhotoOwner.ITEM, other)
            db.photoDao().upsert(
                ItemPhotoEntity(
                    id = "shared-row-2",
                    ownerType = PhotoOwner.ITEM,
                    ownerId = other,
                    filePath = mineSecond.filePath,
                    thumbPath = mineSecond.thumbPath,
                    sortOrder = 0,
                    createdAt = 4_000L,
                ),
            )
        }

        runBlocking { items.removePhoto(mineSecond.id) }

        assertTrue("还有一件指着它，文件必须留着", store.file(mineSecond.filePath).exists())
        assertTrue("第一张只有自己用，删它才会回收", store.file(mineFirst.filePath).exists())
    }
}
