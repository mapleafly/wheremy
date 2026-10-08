package com.wheremy.app.data.repo

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.PhotoOwner
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photo
import com.wheremy.app.data.photos.PhotoStore
import kotlinx.coroutines.runBlocking
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
 * 照片文件回收的唯一规则（`ARCHITECTURE.md` §5.2）。
 * 这条规则错了会**删掉别人还在用的照片**——是这个项目最不可逆的一类 bug。
 */
@RunWith(RobolectricTestRunner::class)
class PhotoReclaimTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var store: PhotoStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = PhotoStore(File(tmp.root, "photos"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seedFile(relPath: String) {
        val file = store.file(relPath)
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `没人指着这个路径就真的删掉`() = runBlocking {
        seedFile("originals/a.jpg")
        seedFile("thumbs/a.jpg")

        // 调用契约：先删掉自己的行，再进来问
        deleteUnreferencedPhotoFiles(db.photoDao(), store, listOf("originals/a.jpg", "thumbs/a.jpg"))

        assertTrue(!store.file("originals/a.jpg").exists())
        assertTrue(!store.file("thumbs/a.jpg").exists())
    }

    @Test
    fun `还有别的行指着就一个字节都不许动`() = runBlocking {
        seedFile("originals/a.jpg")
        seedFile("thumbs/a.jpg")
        // 两条物品行共用同一份字节
        db.photoDao().upsert(photo(id = "p1", ownerId = "i-1"))
        db.photoDao().upsert(photo(id = "p2", ownerId = "i-2"))

        deleteUnreferencedPhotoFiles(db.photoDao(), store, listOf("originals/a.jpg", "thumbs/a.jpg"))

        assertTrue("还有 i-2 指着它", store.file("originals/a.jpg").exists())
        assertTrue(store.file("thumbs/a.jpg").exists())
    }

    @Test
    fun `删掉自己那一行之后就轮到它被回收`() = runBlocking {
        seedFile("originals/a.jpg")
        db.photoDao().upsert(photo(id = "p1", ownerId = "i-1"))
        db.photoDao().upsert(photo(id = "p2", ownerId = "i-2"))

        // i-1 退出录入页：先删自己的行
        db.photoDao().deleteForOwner(PhotoOwner.ITEM, "i-1")
        deleteUnreferencedPhotoFiles(db.photoDao(), store, listOf("originals/a.jpg"))

        assertTrue("i-2 还在用，不能删", store.file("originals/a.jpg").exists())

        db.photoDao().deleteForOwner(PhotoOwner.ITEM, "i-2")
        deleteUnreferencedPhotoFiles(db.photoDao(), store, listOf("originals/a.jpg"))

        assertTrue("这下没人用了", !store.file("originals/a.jpg").exists())
    }

    @Test
    fun `空路径与重复路径被跳过`() = runBlocking {
        seedFile("originals/a.jpg")

        deleteUnreferencedPhotoFiles(
            db.photoDao(),
            store,
            listOf("", "   ", "originals/a.jpg", "originals/a.jpg"),
        )

        assertTrue(!store.file("originals/a.jpg").exists())
        assertEquals("重复与空路径不该抛异常", 0, db.photoDao().refsTo("originals/a.jpg"))
    }
}
