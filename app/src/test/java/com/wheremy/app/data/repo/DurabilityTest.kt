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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * **耐久性**：写进去的东西，关掉再打开必须还在（真机上踩到过一次，见 `PLAN.md` §3.4 F18）。
 *
 * 内存库测不出这件事：同一连接的可见性会让"没提交的数据"看起来一切正常，
 * 进程一杀就全没了。所以这一支必须用**磁盘库 + 关掉再重开**。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DurabilityTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var db: WheremyDatabase? = null

    @After
    fun tearDown() {
        db?.close()
    }

    private fun open(name: String): WheremyDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(tmp.root, name)
        return Room.databaseBuilder(context, WheremyDatabase::class.java, file.absolutePath).build()
    }

    private fun <T> reopen(name: String, block: suspend (WheremyDatabase) -> T): T {
        db?.close()
        val fresh = open(name).also { db = it }
        return runBlocking { block(fresh) }
    }

    @Test
    fun `事务里的写入关掉应用也还在`() {
        val store = PhotoStore(File(tmp.root, "photos"))
        val name = "durable.db"

        val first = open(name).also { db = it }
        val repo = ItemRepository(first, store)
        val itemId = runBlocking {
            val id = repo.newDraft(testJpeg())
            repo.confirm(ItemDraft(id = id, name = "电钻", tags = listOf("工具")))
            id
        }

        // 换一个连接看：没提交的话这里就是 0 行
        reopen(name) { fresh ->
            assertEquals("物品行必须已经落盘", 1, runBlocking { fresh.itemDao().all() }.size)
            assertEquals("标签行也要落盘", listOf("工具"), runBlocking { fresh.itemDao().tagsFor(itemId) })
            assertEquals("照片行也要落盘", 1, runBlocking { fresh.photoDao().all() }.size)
        }
    }

    @Test
    fun `不在事务里的写入也要落盘`() {
        val name = "plain.db"
        val first = open(name).also { db = it }
        runBlocking {
            first.locationDao().upsert(
                com.wheremy.app.data.location(id = "home", name = "家", kind = "房间"),
            )
        }

        reopen(name) { fresh ->
            assertEquals(1, runBlocking { fresh.locationDao().all() }.size)
        }
    }

    @Test
    fun `关掉再开一次数据不会重复也不会丢`() {
        val store = PhotoStore(File(tmp.root, "photos"))
        val name = "twice.db"
        val first = open(name).also { db = it }
        val repo = ItemRepository(first, store)
        runBlocking {
            val id = repo.newDraft(testJpeg())
            repo.confirm(ItemDraft(id = id, name = "护照"))
        }

        reopen(name) { fresh ->
            val repo2 = ItemRepository(fresh, store)
            runBlocking {
                val id = repo2.newDraft(testJpeg())
                repo2.confirm(ItemDraft(id = id, name = "电钻"))
            }
        }

        reopen(name) { fresh ->
            assertEquals(2, runBlocking { fresh.itemDao().all() }.size)
        }
    }
}
