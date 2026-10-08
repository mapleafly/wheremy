package com.wheremy.app.data.db

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Room 3 验证门：内存库能建、DAO 能写能读（PLAN.md §3.3 第 7 步）。
 * 这条跑通 = `androidx.room3:room3-runtime` + `room3-compiler`(KSP) + 本机工具链可用。
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseSmokeTest {
    @Test
    fun roomThreeCanCreateAndQueryAnInMemoryDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db =
            Room
                .inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
                .allowMainThreadQueries()
                .build()

        try {
            val dao = db.itemDao()
            // 能写能读就说明 schema 生成与 KSP 处理都过了
            dao.insert(ItemEntity(id = "1", name = "电钻", locationId = "drawer"))
            dao.insert(ItemEntity(id = "2", name = "护照", locationId = null))

            assertEquals(2, dao.count())
            assertEquals(listOf("电钻", "护照").sorted(), dao.all().map { it.name }.sorted())
        } finally {
            db.close()
        }
    }
}
