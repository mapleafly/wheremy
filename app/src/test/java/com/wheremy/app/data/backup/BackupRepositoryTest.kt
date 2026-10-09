package com.wheremy.app.data.backup

import android.content.Context
import androidx.room3.Room
import androidx.room3.withWriteTransaction
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.DB_SCHEMA_VERSION
import com.wheremy.app.data.db.ItemEntity
import com.wheremy.app.data.db.ItemPhotoEntity
import com.wheremy.app.data.db.ItemStatus
import com.wheremy.app.data.db.ItemTagEntity
import com.wheremy.app.data.db.LocationNodeEntity
import com.wheremy.app.data.db.PhotoOwner
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.testJpeg
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 备份（`RULES.md` §12 / `PRD.md` FR-BAK-01…07）。
 *
 * 这一支要钉的是"**坏包不许留下半个库**"与"**文件缺了要放行**"这两条看起来相反、其实互补的规则。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var store: PhotoStore
    private lateinit var repo: BackupRepository

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = PhotoStore(File(tmp.root, "photos"))
        repo = makeRepo()
    }

    @After
    fun tearDown() = db.close()

    private fun makeRepo(): BackupRepository = BackupRepository(
        db = db,
        photoStore = store,
        appVersion = "1.0.0-test",
        stagingDir = File(store.rootDir, ".incoming"),
    )

    // —— 造数据 ——

    private fun location(id: String, parentId: String? = null, name: String = id, sort: Int = 0) = LocationNodeEntity(
        id = id,
        parentId = parentId,
        name = name,
        kind = "房间",
        sortOrder = sort,
        createdAt = 1_000,
        updatedAt = 1_000,
    )

    private fun item(id: String, name: String, locationId: String? = null) = ItemEntity(
        id = id,
        name = name,
        category = "衣物",
        seasons = "冬",
        color = "深灰",
        brand = null,
        material = null,
        aliases = null,
        description = null,
        notes = null,
        quantity = 1,
        locationId = locationId,
        status = ItemStatus.CONFIRMED,
        expiresAt = null,
        expireKind = null,
        expireNotifiedAt = null,
        searchText = name.lowercase(),
        createdAt = 1_000,
        updatedAt = 2_000,
        lastMovedAt = null,
    )

    /** 写一对照片文件并把行插进库；返回行。 */
    private fun addPhoto(ownerType: String, ownerId: String, photoId: String): ItemPhotoEntity = runBlocking {
        val stored = store.write(testJpeg())
        val row = ItemPhotoEntity(
            id = photoId,
            ownerType = ownerType,
            ownerId = ownerId,
            filePath = stored.originalPath,
            thumbPath = stored.thumbPath,
            sortOrder = 0,
            createdAt = 1_000,
        )
        db.photoDao().upsert(row)
        row
    }

    private fun seed() = runBlocking {
        db.withWriteTransaction {
            db.locationDao().upsert(location("home", null, "家", 0))
            db.locationDao().upsert(location("wardrobe", "home", "衣柜", 1))
            db.itemDao().upsert(item("coat", "羽绒外套", "wardrobe"))
            db.itemDao().upsert(item("drill", "电钻", "home"))
            db.itemDao().insertTags(listOf(ItemTagEntity("coat", "冬装"), ItemTagEntity("coat", "衣物")))
        }
        addPhoto(PhotoOwner.ITEM, "coat", "p1")
        Unit
    }

    private fun exportBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        runBlocking { repo.exportTo(out, at = 42L) }
        return out.toByteArray()
    }

    private fun import(bytes: ByteArray, mode: ImportMode = ImportMode.MERGE): BackupSummary =
        runBlocking { repo.importFrom(ByteArrayInputStream(bytes), mode) }

    // —— 造包（可以故意造坏的）——

    private fun buildPack(
        manifest: BackupManifest? = BackupManifest(schemaVersion = DB_SCHEMA_VERSION, appVersion = "x", exportedAt = 1),
        data: BackupData? = null,
        extraFiles: Map<String, ByteArray> = emptyMap(),
        extraEntries: Map<String, ByteArray> = emptyMap(),
        manifestText: String? = null,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            if (manifestText != null) {
                zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
                zip.write(manifestText.toByteArray())
                zip.closeEntry()
            } else if (manifest != null) {
                zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
                zip.write(json.encodeToString(manifest).toByteArray())
                zip.closeEntry()
            }
            if (data != null) {
                zip.putNextEntry(ZipEntry(ENTRY_DATA))
                zip.write(json.encodeToString(data).toByteArray())
                zip.closeEntry()
            }
            extraFiles.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            extraEntries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    // —— 1 往返 ——

    @Test
    fun `导出再导入能整包回来`() {
        seed()
        val bytes = exportBytes()

        // 清空本机，模拟换机
        runBlocking {
            db.withWriteTransaction {
                db.photoDao().deleteAll()
                db.itemDao().deleteAll()
                db.itemDao().deleteAllTags()
                db.locationDao().deleteAll()
            }
        }
        store.rootDir.listFiles()?.filter { it.isFile }?.forEach { it.delete() }

        val summary = import(bytes)

        assertEquals(2, summary.locations)
        assertEquals(2, summary.items)
        assertEquals(1, summary.photos)
        assertEquals("导入路径不给字节数", 0, summary.bytes)

        runBlocking {
            assertEquals(2, db.locationDao().all().size)
            assertEquals(2, db.itemDao().all().size)
            assertEquals(2, db.itemDao().allTagRows().size)
            val coat = db.itemDao().byId("coat")!!
            assertEquals("衣柜", db.locationDao().byId(coat.locationId!!)!!.name)
            val photo = db.photoDao().all().single()
            assertTrue("照片文件要真的搬回来", store.file(photo.filePath).isFile)
            assertTrue(store.file(photo.thumbPath).isFile)
        }
    }

    @Test
    fun `导出按文件对计数，共用一张图只算一次`() {
        seed()
        // 两件共用同一对文件（同一 filePath/thumbPath）：包里只写一次，计数也只算一次
        val shared = runBlocking { db.photoDao().all().single() }
        runBlocking {
            db.photoDao().upsert(shared.copy(id = "p2", ownerId = "drill"))
        }
        assertEquals("库里确实是两行", 2, runBlocking { db.photoDao().all() }.size)

        val out = ByteArrayOutputStream()
        val summary = runBlocking { repo.exportTo(out, at = 42L) }

        assertEquals("计的是文件对，不是行数", 1, summary.photos)
        assertTrue("字节数要真的报出来", summary.bytes > 0)
        assertEquals("1000 进制、整除截断", "1 MB", formatBytes(1_999_999))
    }

    @Test
    fun `读不到的图跳过就行`() {
        seed()
        val row = runBlocking { db.photoDao().all().single() }
        store.file(row.thumbPath).delete() // 缩略图丢了

        val bytes = exportBytes()
        val summary = import(bytes, ImportMode.OVERWRITE)

        assertEquals("丢的那张不算进计数", 1, summary.photos)
        assertNotNull(runBlocking { db.photoDao().all().firstOrNull() })
    }

    // —— 2 四条拒 + 一条放行 ——

    @Test
    fun `拒一：物品的位置不在包里`() {
        val data = BackupData(
            locations = emptyList(),
            items = listOf(item("coat", "", locationId = "ghost")),
        )
        val failure = runCatching { import(buildPack(data = data)) }.exceptionOrNull()

        assertTrue(failure is BackupFormatException)
        assertEquals("「未命名物品」所在的位置不在包里，备份不完整", failure!!.message)
    }

    @Test
    fun `拒二：照片找不到所属条目`() {
        val data = BackupData(
            items = listOf(item("coat", "外套")),
            photos = listOf(
                ItemPhotoEntity("p1", PhotoOwner.ITEM, "nobody", "originals/a.jpg", "thumbs/a.jpg", 0, 1),
            ),
        )
        val failure = runCatching { import(buildPack(data = data)) }.exceptionOrNull()

        assertEquals("有照片找不到它所属的条目，备份不完整", failure!!.message)
    }

    @Test
    fun `拒三：照片没写文件路径`() {
        val data = BackupData(
            items = listOf(item("coat", "外套")),
            photos = listOf(ItemPhotoEntity("p1", PhotoOwner.ITEM, "coat", "", "", 0, 1)),
        )
        val failure = runCatching { import(buildPack(data = data)) }.exceptionOrNull()

        assertEquals("有照片没写它对应的文件，备份不完整", failure!!.message)
    }

    @Test
    fun `拒四：标签挂在不在包里的物品上`() {
        val data = BackupData(
            items = listOf(item("coat", "外套")),
            tags = listOf(ItemTagEntity("ghost", "冬装")),
        )
        val failure = runCatching { import(buildPack(data = data)) }.exceptionOrNull()

        assertEquals("有标签挂在一件不在包里的物品上，备份不完整", failure!!.message)
    }

    @Test
    fun `放行：包里写了路径但 zip 里没有那个文件`() {
        // 导出本来就放过丢了的图；否则一台已经丢了文件的机器连自己的备份都恢复不了
        val data = BackupData(
            items = listOf(item("coat", "外套")),
            photos = listOf(
                ItemPhotoEntity("p1", PhotoOwner.ITEM, "coat", "originals/gone.jpg", "thumbs/gone.jpg", 0, 1),
            ),
        )

        val summary = import(buildPack(data = data))

        assertEquals(1, summary.items)
        assertNotNull("行照样要进库（界面按没有照片显示）", runBlocking { db.photoDao().all().firstOrNull() })
    }

    @Test
    fun `白名单外的文件名一律拒`() {
        val data = BackupData(items = listOf(item("coat", "外套")))
        listOf(
            "photos/originals/../../evil.txt",
            "photos/other/a.jpg",
            "photos/originals/nested/deep/a.jpg",
            "evil.txt",
            "photos/originals/a b.jpg",
        ).forEach { bad ->
            val failure = runCatching {
                import(buildPack(data = data, extraEntries = mapOf(bad to byteArrayOf(1))))
            }.exceptionOrNull()
            assertEquals("应当拒：$bad", "备份里有不合法的文件名：$bad", failure!!.message)
        }
    }

    // —— 3 缺件与版本 ——

    @Test
    fun `少了 manifest 或 data 会说缺了哪个`() {
        val onlyData = runCatching { import(buildPack(manifest = null, data = BackupData())) }.exceptionOrNull()
        assertEquals("这不是 Wheremy 的备份包：少了 manifest.json", onlyData!!.message)

        val onlyManifest = runCatching { import(buildPack(manifestText = "{}", data = null)) }.exceptionOrNull()
        assertEquals("这不是 Wheremy 的备份包：少了 data.json", onlyManifest!!.message)
    }

    @Test
    fun `解不开的两个文件各有各的说法`() {
        val badManifest = runCatching {
            import(buildPack(manifestText = "not json", data = BackupData()))
        }.exceptionOrNull()
        assertEquals("备份的 manifest.json 读不出来", badManifest!!.message)

        val goodManifest = BackupManifest(schemaVersion = DB_SCHEMA_VERSION, appVersion = "x", exportedAt = 1)
        val badData = buildPack(manifest = goodManifest, manifestText = null, data = null, extraEntries = emptyMap())
        // data.json 存在但读不出来：手工拼一个坏 JSON
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
            zip.write(json.encodeToString(goodManifest).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(ENTRY_DATA))
            zip.write("{ 截断了".toByteArray())
            zip.closeEntry()
        }
        assertTrue(badData.isNotEmpty())
        val failure = runCatching { import(out.toByteArray()) }.exceptionOrNull()
        assertEquals("备份的 data.json 读不出来，文件可能被截断了", failure!!.message)
    }

    @Test
    fun `schema 更高直接拒`() {
        val pack = buildPack(
            manifest = BackupManifest(schemaVersion = DB_SCHEMA_VERSION + 1, appVersion = "9", exportedAt = 1),
            data = BackupData(),
        )
        val failure = runCatching { import(pack) }.exceptionOrNull()

        assertTrue(failure!!.message!!.startsWith("这个备份来自更新的版本"))
        assertTrue(failure.message!!.endsWith("先升级应用再导入"))
        assertTrue(failure.message!!.contains("schema ${DB_SCHEMA_VERSION + 1} > $DB_SCHEMA_VERSION"))
    }

    // —— 4 MERGE / OVERWRITE ——

    @Test
    fun `MERGE 保留本机已有的行`() {
        seed()
        val bytes = exportBytes()
        // 本机把外套改名了，合并导入不该把它改回去
        runBlocking {
            val coat = db.itemDao().byId("coat")!!
            db.itemDao().upsert(coat.copy(name = "本机改过的名字"))
        }

        val summary = import(bytes, ImportMode.MERGE)

        assertEquals("已存在的行按主键跳过", 0, summary.items)
        assertEquals("本机改过的名字", runBlocking { db.itemDao().byId("coat")!!.name })
    }

    @Test
    fun `OVERWRITE 清空本机多余的数据与照片`() {
        seed()
        val bytes = exportBytes()
        // 本机多出一件 + 一张不在包里的照片文件
        runBlocking { db.itemDao().upsert(item("extra", "本机多出来的")) }
        val stranger = File(store.rootDir, "originals/stranger.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(testJpeg())
        }
        assertTrue(stranger.isFile)

        val summary = import(bytes, ImportMode.OVERWRITE)

        assertEquals(2, summary.items)
        assertNull("本机多出来的那件要被清掉", runBlocking { db.itemDao().byId("extra") })
        assertFalse("没被新包声称的文件要删掉", stranger.isFile)
    }

    @Test
    fun `外键对不上时整包拒且原来的数据没动`() {
        seed()
        val before = runBlocking { db.itemDao().all() }.size
        // 孤儿位置：父节点既不在包里也不在本机
        val data = BackupData(
            locations = listOf(location("orphan", "ghost-parent", "孤儿")),
            items = emptyList(),
        )

        val failure = runCatching { import(buildPack(data = data), ImportMode.OVERWRITE) }.exceptionOrNull()

        assertEquals("备份里有对不上的引用，已取消导入，原来的数据没有动", failure!!.message)
        assertEquals("原来的数据没有动", before, runBlocking { db.itemDao().all() }.size)
        assertEquals(2, runBlocking { db.locationDao().all().size })
    }

    @Test
    fun `导入之后暂存目录不留东西`() {
        seed()
        val bytes = exportBytes()

        import(bytes, ImportMode.OVERWRITE)

        assertFalse("`.incoming` 进门先删、finally 再删", File(store.rootDir, ".incoming").exists())
    }
}
