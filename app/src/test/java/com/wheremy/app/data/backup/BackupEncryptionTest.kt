package com.wheremy.app.data.backup

import android.content.Context
import androidx.room3.Room
import androidx.room3.withWriteTransaction
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.DB_SCHEMA_VERSION
import com.wheremy.app.data.db.ItemEntity
import com.wheremy.app.data.db.ItemPhotoEntity
import com.wheremy.app.data.db.ItemStatus
import com.wheremy.app.data.db.LocationNodeEntity
import com.wheremy.app.data.db.PhotoOwner
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.testJpeg
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份加密（`FR-BAK-09` / `ADR-27` / `RULES.md` §12.5）。
 *
 * 这一批先写**能红的两条**——它们不需要新 API，用手工造的包就能钉住「旧逻辑遇到 v2 会怎样」：
 * 1. 加密包被喂进只认明文的读取器：今天会报「少了 data.json」（**驴唇不对马嘴的一句话**，
 *    用户看到会以为包坏了，其实只是没输口令）；
 * 2. `formatVersion = 2` 的包：今天**会被静默导入**——这最危险，未来格式被旧代码按老规矩读进去。
 *
 * 还有一条**当绿**的：明文 v1 包必须永远能导入（`ADR-27` 的不变量）。
 */
@RunWith(RobolectricTestRunner::class)
class BackupEncryptionTest {

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
        repo = BackupRepository(
            db = db,
            photoStore = store,
            appVersion = "1.0.0-test",
            stagingDir = File(store.rootDir, ".incoming"),
        )
    }

    @After
    fun tearDown() = db.close()

    /** 手工造包：manifest 原文可控，`data.json` 可省（加密包就是省掉它的那种）。 */
    private fun buildPack(
        manifestText: String,
        data: BackupData? = null,
        extra: Map<String, ByteArray> = emptyMap(),
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
            zip.write(manifestText.toByteArray())
            zip.closeEntry()
            if (data != null) {
                zip.putNextEntry(ZipEntry(ENTRY_DATA))
                zip.write(json.encodeToString(data).toByteArray())
                zip.closeEntry()
            }
            extra.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** 用**现有**的三参 API 导入（这一批只钉消息对不对，不看异常类型）。 */
    private fun importResult(bytes: ByteArray): Result<BackupSummary> = runBlocking {
        runCatching { repo.importFrom(ByteArrayInputStream(bytes), ImportMode.MERGE) }
    }

    /** 带口令的导入（加密包）。 */
    private fun importBytes(bytes: ByteArray, password: String?): BackupSummary = runBlocking {
        repo.importFrom(ByteArrayInputStream(bytes), ImportMode.MERGE, password)
    }

    private val emptyData = BackupData()

    private val encryptedManifest =
        """{"formatVersion":2,"schemaVersion":$DB_SCHEMA_VERSION,"appVersion":"x","exportedAt":1,""" +
            """"encryption":{"algorithm":"AES-256-GCM","kdf":"PBKDF2-HMAC-SHA256","iterations":210000,""" +
            """"saltBase64":"AAAA","verifierBase64":"BBBB"}}"""

    @Test
    fun `加密包没给口令时说清楚是要口令，而不是说包少了文件`() {
        val pack = buildPack(
            manifestText = encryptedManifest,
            data = null,
            extra = mapOf("data.enc" to ByteArray(40) { 7 }),
        )

        val message = importResult(pack).exceptionOrNull()?.message.orEmpty()

        assertTrue(
            "要说「这个备份包加密了、请给口令」，而不是「少了 data.json」——实际 = $message",
            message.contains("加密") || message.contains("口令"),
        )
    }

    @Test
    fun `比能读的格式更新的包要被拒，而不是被按老规矩读进去`() {
        // 3 = 比本实现能读的最高版本（BACKUP_FORMAT_VERSION）还新：这种包必须拒收
        val pack = buildPack(
            manifestText = """{"formatVersion":3,"schemaVersion":$DB_SCHEMA_VERSION,"appVersion":"x","exportedAt":1}""",
            data = emptyData,
        )

        val message = importResult(pack).exceptionOrNull()?.message.orEmpty()

        assertTrue(
            "要说「来自更新的版本，先升级应用」——实际 = $message",
            message.contains("更新"),
        )
    }

    @Test
    fun `明文 v1 包永远能导入（加密不是把老包作废）`() {
        val pack = buildPack(
            manifestText = json.encodeToString(
                BackupManifest(schemaVersion = DB_SCHEMA_VERSION, appVersion = "x", exportedAt = 1),
            ),
            data = emptyData,
        )

        val summary = importResult(pack).getOrThrow()

        assertEquals("空包导进来就是 0、0、0", 0, summary.items)
        assertEquals(0, summary.locations)
        assertEquals(0, summary.photos)
    }

    // —— 以下钉加密本身（口令对了/错了/被改过/包里没有口令）——

    private val password = "correct horse battery"

    /** 塞一条有内容的库：1 个位置 + 1 件物品（名字用来验「密文里搜不到」）+ 1 张照片。 */
    private fun seed() = runBlocking {
        db.locationDao().upsert(
            LocationNodeEntity(
                id = "home",
                parentId = null,
                name = "家",
                kind = "房间",
                sortOrder = 0,
                createdAt = 1_000,
                updatedAt = 1_000,
            ),
        )
        db.itemDao().upsert(
            ItemEntity(
                id = "drill",
                name = "博世电钻",
                category = "工具",
                seasons = null,
                color = null,
                brand = null,
                material = null,
                aliases = null,
                description = null,
                notes = null,
                quantity = 1,
                locationId = "home",
                status = ItemStatus.CONFIRMED,
                expiresAt = null,
                expireKind = null,
                expireNotifiedAt = null,
                searchText = "博世电钻",
                createdAt = 1_000,
                updatedAt = 1_000,
                lastMovedAt = null,
            ),
        )
        val stored = store.write(testJpeg())
        db.photoDao().upsert(
            ItemPhotoEntity(
                id = "p1",
                ownerType = PhotoOwner.ITEM,
                ownerId = "drill",
                filePath = stored.originalPath,
                thumbPath = stored.thumbPath,
                sortOrder = 0,
                createdAt = 1_000,
            ),
        )
        Unit
    }

    private fun exportBytes(pwd: String?): ByteArray {
        val out = ByteArrayOutputStream()
        runBlocking { repo.exportTo(out, at = 42L, password = pwd) }
        return out.toByteArray()
    }

    private fun wipe() = runBlocking {
        db.withWriteTransaction {
            db.photoDao().deleteAll()
            db.itemDao().deleteAll()
            db.itemDao().deleteAllTags()
            db.locationDao().deleteAll()
        }
        store.rootDir.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
    }

    @Test
    fun `加密往返：口令对了内容原样回来，而包里搜不到物品名`() {
        seed()
        val name = "博世电钻"

        val plainPack = exportBytes(null)
        val encPack = exportBytes(password)

        // 比的是**解压后的负载**（zip 条目是 deflate 的，直接搜原始字节两边都搜不到——那样这条断言就是假绿）
        assertTrue(
            "明文包的负载里本来就该有物品名（这条是前置）",
            String(entryBytes(plainPack, ENTRY_DATA)!!, Charsets.UTF_8).contains(name),
        )
        assertTrue(
            "加密包的负载里不该搜得到物品名",
            !String(entryBytes(encPack, ENTRY_DATA_ENC)!!, Charsets.UTF_8).contains(name),
        )

        wipe()
        val summary = importBytes(encPack, password)

        assertEquals("1 件物品", 1, summary.items)
        assertEquals("1 个位置", 1, summary.locations)
        assertEquals("1 张照片", 1, summary.photos)
        runBlocking {
            assertEquals(name, db.itemDao().byId("drill")!!.name)
            val photo = db.photoDao().all().single()
            assertTrue("照片文件要真的解密搬回来", store.file(photo.filePath).isFile)
        }
    }

    /** 取某个条目的原始字节（找不到返回 null）。 */
    private fun entryBytes(pack: ByteArray, entryName: String): ByteArray? {
        ZipInputStream(ByteArrayInputStream(pack)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val bytes = zip.readBytes()
                if (entry.name == entryName) return bytes
                zip.closeEntry()
            }
        }
        return null
    }

    @Test
    fun `口令不对时立刻说是口令不对，且一个字节都不写`() {
        seed()
        val pack = exportBytes(password)
        wipe()

        val failure = runCatching { importBytes(pack, "wrong password") }.exceptionOrNull()

        assertTrue("要能分辨出是口令不对", failure is BackupWrongPassword)
        assertEquals("口令不对就该一个字都不写", 0, runBlocking { db.itemDao().all() }.size)
    }

    @Test
    fun `没给口令时是"要口令"，不是"包坏了"`() {
        seed()
        val pack = exportBytes(password)
        wipe()

        val failure = runCatching { importBytes(pack, null) }.exceptionOrNull()

        assertTrue("要能分辨出是需要口令", failure is BackupPasswordRequired)
    }

    @Test
    fun `密文被改一个字节就整体拒收`() {
        seed()
        val pack = exportBytes(password)
        wipe()

        val tampered = flipOneByteInDataEnc(pack)
        val failure = runCatching { importBytes(tampered, password) }.exceptionOrNull()

        assertTrue("认证标签不对必须拒", failure is BackupFormatException)
        assertEquals("拒了就不能留下半截数据", 0, runBlocking { db.itemDao().all() }.size)
    }

    @Test
    fun `包里没有口令、也没有任何设置项`() {
        seed()
        val pack = exportBytes(password)

        val names = mutableListOf<String>()
        var manifestText = ""
        ZipInputStream(ByteArrayInputStream(pack)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names += entry.name
                if (entry.name == ENTRY_MANIFEST) manifestText = zip.readBytes().decodeToString()
                zip.closeEntry()
            }
        }

        assertTrue(
            "条目名只有 manifest / data.enc / 照片",
            names.all {
                it == ENTRY_MANIFEST ||
                    it == ENTRY_DATA_ENC ||
                    isPhotoEntry(it)
            },
        )
        assertTrue("manifest 里不该出现口令原文（FR-BAK-04 不许放松）", !manifestText.contains(password))
        assertTrue("manifest 里要有 encryption 段（读的人靠它知道要问口令）", manifestText.contains("encryption"))
    }

    /** 把 `data.enc` 里的一个字节翻个个儿（模拟"文件被改过"）。 */
    private fun flipOneByteInDataEnc(pack: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            ZipInputStream(ByteArrayInputStream(pack)).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    var bytes = input.readBytes()
                    if (entry.name == ENTRY_DATA_ENC) {
                        bytes = bytes.copyOf()
                        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x01).toByte()
                    }
                    zip.putNextEntry(ZipEntry(entry.name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }
}
