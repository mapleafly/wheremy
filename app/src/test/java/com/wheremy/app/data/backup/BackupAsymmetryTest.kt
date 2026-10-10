package com.wheremy.app.data.backup

import android.content.Context
import androidx.room3.Room
import androidx.room3.withWriteTransaction
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.DB_SCHEMA_VERSION
import com.wheremy.app.data.db.ItemPhotoEntity
import com.wheremy.app.data.db.ItemStatus
import com.wheremy.app.data.db.PhotoOwner
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.testJpeg
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * `FR-BAK-03`：**库这一层回滚，照片文件层刻意不回滚**——把这条"不对称"钉住。
 *
 * 为什么值得单独钉：它是**设计选择**而不是漏掉的 bug（`BackupRepository.kt` 的注释写了理由：
 * 库是唯一真相源，搬不进去的那几个字节仍完好躺在用户选的 zip 里）。设计选择最怕的不是被推翻，
 * 而是被后来的人当成 bug"顺手修好"——那样会把"库与文件谁说了算"这件事搅乱。
 *
 * 三条一起钉：
 * 1. 搬图失败**不回滚库**：库里有行、磁盘上没文件 → 缩略图空白，导入仍算成功；
 * 2. **被拒的包一张照片都不动**：删多余照片发生在事务成功之后，所以校验失败时本机照片原封不动；
 * 3. 完成那句话数的是**入库行数**，不代表同样多个文件真搬进去了（界面口径，见 `RELEASE.md`）。
 */
@RunWith(RobolectricTestRunner::class)
class BackupAsymmetryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var store: PhotoStore
    private lateinit var repo: BackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = PhotoStore(File(tmp.root, "photos"))
        repo = BackupRepository(
            db = db,
            photoStore = store,
            appVersion = "1.0.0-test",
            stagingDir = File(store.rootDir, ".incoming"),
        )
    }

    @After
    fun tearDown() = db.close()

    /** 造一件带照片的物品，返回那份导出的包。 */
    private fun packWithOnePhoto(): ByteArray = runBlocking {
        val stored = store.write(testJpeg())
        db.withWriteTransaction {
            db.itemDao().upsert(
                com.wheremy.app.data.db.ItemEntity(
                    id = "coat",
                    name = "羽绒外套",
                    category = "衣物",
                    seasons = "冬",
                    color = null,
                    brand = null,
                    material = null,
                    aliases = null,
                    description = null,
                    notes = null,
                    quantity = 1,
                    locationId = null,
                    status = ItemStatus.CONFIRMED,
                    expiresAt = null,
                    expireKind = null,
                    expireNotifiedAt = null,
                    searchText = "羽绒外套",
                    createdAt = 1_000,
                    updatedAt = 1_000,
                    lastMovedAt = null,
                ),
            )
            db.photoDao().upsert(
                ItemPhotoEntity(
                    id = "p1",
                    ownerType = PhotoOwner.ITEM,
                    ownerId = "coat",
                    filePath = stored.originalPath,
                    thumbPath = stored.thumbPath,
                    sortOrder = 0,
                    createdAt = 1_000,
                ),
            )
        }
        val out = ByteArrayOutputStream()
        repo.exportTo(out, at = 42L)
        out.toByteArray()
    }

    private fun wipeLocal() = runBlocking {
        db.withWriteTransaction {
            db.photoDao().deleteAll()
            db.itemDao().all().forEach { db.itemDao().deleteById(it.id) }
        }
        store.rootDir.listFiles()?.forEach { it.deleteRecursively() }
        Unit
    }

    @Test
    fun `搬图失败不回滚库：库里有行、磁盘上没文件，导入仍算成功`() {
        val bytes = packWithOnePhoto()
        wipeLocal()
        // 让"搬图"注定失败：把 originals 那一级做成**文件**，mkdirs 与 copyTo 都会失败
        val originals = File(store.rootDir, PhotoStore.DIR_ORIGINALS)
        originals.mkdirs()
        originals.deleteRecursively()
        originals.writeText("我不是目录")

        val summary = runBlocking { repo.importFrom(ByteArrayInputStream(bytes), ImportMode.MERGE) }

        assertEquals("库这一层照常提交", 1, summary.items)
        assertNotNull("行在", runBlocking { db.itemDao().byId("coat") })
        val row = runBlocking { db.photoDao().forOwner(PhotoOwner.ITEM, "coat").firstOrNull() }
        assertNotNull("照片行也在（这就是「缩略图空白」的来源）", row)
        assertFalse("但文件确实没搬进去", store.file(row!!.filePath).exists())
    }

    @Test
    fun `被拒的包一张本机照片都不动：删文件发生在事务成功之后`() {
        packWithOnePhoto()
        // 本机留一张"包里没有"的照片：覆盖模式若走到删文件那一步，它就会被删掉
        val localOnly = runBlocking { store.write(testJpeg()) }
        val localFile = store.file(localOnly.originalPath)
        assertTrue("先确认这张多余的照片在", localFile.exists())

        // 造一个外键对不上的包（物品的位置不在包里）→ 事务里就会抛
        val bad = brokenPack()
        val failed = runCatching { runBlocking { repo.importFrom(ByteArrayInputStream(bad), ImportMode.OVERWRITE) } }

        assertTrue("要被拒", failed.isFailure)
        assertTrue("**多余的照片还在**——删文件那一步没机会跑", localFile.exists())
        assertEquals("本机数据没被动", 1, runBlocking { db.itemDao().all().size })
    }

    /** 外键对不上的包：物品指着一个包里没有的位置。 */
    private fun brokenPack(): ByteArray {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
            zip.write(
                (
                    """{"formatVersion":1,"schemaVersion":$DB_SCHEMA_VERSION,""" +
                        """"appVersion":"x","exportedAt":1}"""
                    ).toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("data.json"))
            zip.write(
                """
                {
                  "locations": [],
                  "items": [
                    {
                      "id": "ghost", "name": "孤儿", "category": null, "seasons": null, "color": null,
                      "brand": null, "material": null, "aliases": null, "description": null, "notes": null,
                      "quantity": 1, "locationId": "nope", "status": "CONFIRMED", "expiresAt": null,
                      "expireKind": null, "expireNotifiedAt": null, "searchText": "孤儿",
                      "createdAt": 1, "updatedAt": 1, "lastMovedAt": null
                    }
                  ],
                  "photos": []
                }
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
        }
        return out.toByteArray()
    }
}
