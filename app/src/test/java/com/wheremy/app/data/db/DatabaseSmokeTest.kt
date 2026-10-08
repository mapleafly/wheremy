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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 四张表能建、能写、能读（P1 出口判据之一）。
 * 顺带当 Room 3 这条线的回归：schema 生成失败会在 `build()` 就炸。
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseSmokeTest {

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
    fun `四张表都能写入并读回`() = runBlocking {
        db.locationDao().upsert(location(id = "room", name = "主卧"))
        db.itemDao().upsert(
            item(id = "drill", name = "电钻", locationId = "room", status = ItemStatus.CONFIRMED),
        )
        db.photoDao().upsert(photo(ownerId = "drill"))
        db.itemDao().insertTags(listOf(ItemTagEntity(itemId = "drill", tag = "工具")))

        assertEquals(1, db.locationDao().all().size)
        assertEquals("电钻", db.itemDao().atLocation("room").single().name)
        assertEquals(1, db.photoDao().forOwner(PhotoOwner.ITEM, "drill").size)
        assertEquals(listOf("工具"), db.itemDao().tagsFor("drill"))
    }

    @Test
    fun `删除位置会把物品的位置置空而不是删掉物品`() = runBlocking {
        val drawer = location(id = "drawer", name = "抽屉")
        db.locationDao().upsert(drawer)
        db.itemDao().upsert(item(id = "passport", name = "护照", locationId = "drawer"))

        db.locationDao().delete(drawer)

        val after = db.itemDao().byStatus(ItemStatus.DRAFT).single()
        assertEquals("passport", after.id)
        assertNull("FK 是 SET_NULL：物品还在，只是没了位置", after.locationId)
    }

    @Test
    fun `外键是真的在生效`() {
        // 位置不存在时插物品必须当场被拒——"照片/物品找不到主人"这类脏数据要在写到库里的那一刻挡住，
        // 而不是等展示时才发现（RULES.md §12.3 的读侧校验是第二道，不是第一道）
        runBlocking {
            assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                runBlocking {
                    db.itemDao().upsert(item(id = "ghost", name = "幽灵", locationId = "不存在的抽屉"))
                }
            }
        }
    }

    @Test
    fun `schema 版本与库名是对外承诺`() {
        // 备份 manifest 写的是这个号；改名等于让旧备份包导不进来
        assertEquals(1, DB_SCHEMA_VERSION)
        assertEquals("wheremy.db", WheremyDatabase.NAME)
    }
}
