package com.wheremy.app.data.backup

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.repo.ItemDraft
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.settings.testSettings
import com.wheremy.app.data.testJpeg
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.time.ZoneId

/**
 * 定时自动导出（`FR-BAK-08`）。
 *
 * 落点用**假 sink**：SAF 的 `ContentResolver` 在 JVM 上不好造，而这里要钉的是
 * 「写什么、名字对不对、失败说不说人话、状态记没记」——那些才是会出错的地方。
 *
 * 真机上再验一遍"选目录 → 立刻导出 → 文件真的躺在那个目录里"（见 `CHANGELOG`）。
 */
@RunWith(RobolectricTestRunner::class)
class AutoExporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var store: PhotoStore
    private lateinit var backup: BackupRepository
    private lateinit var settings: com.wheremy.app.data.settings.SettingsRepository

    /** 固定时钟：2026-10-10 09:30（东八区）——文件名里那串日期要能对上。 */
    private val fixedAt = 1_791_595_800_000L
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = PhotoStore(File(tmp.root, "photos"))
        backup = BackupRepository(
            db = db,
            photoStore = store,
            appVersion = "0.1.0-test",
            stagingDir = File(store.rootDir, ".incoming"),
        )
        settings = testSettings()
    }

    @After
    fun tearDown() = db.close()

    /** 记下写了什么、写了几次；可以指定"写就抛"。 */
    private class FakeSink(private val failWith: String? = null) : BackupSink {
        val writes = mutableListOf<Pair<String, ByteArray>>()
        override suspend fun write(bytes: ByteArray, fileName: String): String {
            failWith?.let { error(it) }
            writes += fileName to bytes
            return fileName
        }
    }

    private fun exporter(sink: BackupSink?): AutoExporter = AutoExporter(
        backup = backup,
        sinkProvider = { sink },
        settings = settings,
        now = { fixedAt },
        zone = zone,
    )

    private fun seedOneItem() = runBlocking {
        val items = ItemRepository(db, store)
        val id = items.newDraft(testJpeg())
        items.confirm(ItemDraft(id = id, name = "电钻"))
    }

    @Test
    fun `没配落点时跳过，并说清要选保存位置`() {
        val message = runBlocking { exporter(null).runOnce() }

        assertTrue("要说人话：$message", message.contains("保存位置"))
        val saved = runBlocking { settings.current() }
        assertEquals("结果要落盘（设置页那一行读它）", fixedAt, saved.autoExportLastAt)
        assertTrue(saved.autoExportLastResult.contains("保存位置"))
    }

    @Test
    fun `配了落点就写一份，文件名带本地日期`() {
        seedOneItem()
        val sink = FakeSink()
        val message = runBlocking { exporter(sink).runOnce() }

        assertEquals(1, sink.writes.size)
        val (name, bytes) = sink.writes.single()
        assertEquals("wheremy-auto-2026-10-10.zip", name)
        assertTrue("是个 zip（PK 头）", bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte())
        assertTrue("结果句里要有文件名：$message", message.contains(name))
        assertEquals(
            name,
            runBlocking {
                settings.current().autoExportLastResult
            }.let { if (it.contains(name)) name else "" },
        )
    }

    @Test
    fun `写失败也要说出来，不能静默`() {
        seedOneItem()
        val message = runBlocking { exporter(FakeSink(failWith = "磁盘满了")).runOnce() }

        assertTrue("要带原因：$message", message.contains("导出失败") && message.contains("磁盘满了"))
        assertTrue(runBlocking { settings.current().autoExportLastResult }.contains("导出失败"))
    }

    @Test
    fun `导出的包能原样导回来（与手动导出同一条链）`() {
        seedOneItem()
        val sink = FakeSink()
        runBlocking { exporter(sink).runOnce() }
        val (_, bytes) = sink.writes.single()

        // 覆盖导入（先清空再按包重建）——能回来就说明这份自动导出的包是完整的
        val imported = runBlocking {
            backup.importFrom(ByteArrayInputStream(bytes), ImportMode.OVERWRITE)
        }
        assertEquals(1, imported.items)
        assertEquals(1, imported.photos)
    }

    @Test
    fun `落点前缀与保留份数是一处常量`() {
        assertEquals("wheremy-auto-", TreeDocumentSink.PREFIX)
        assertEquals(7, TreeDocumentSink.KEEP)
        assertNull("默认没配落点", runBlocking { settings.current() }.autoExportUri.ifBlank { null })
    }
}
