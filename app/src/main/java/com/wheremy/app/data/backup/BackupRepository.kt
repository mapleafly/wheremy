package com.wheremy.app.data.backup

import androidx.room3.withWriteTransaction
import com.wheremy.app.data.db.DB_SCHEMA_VERSION
import com.wheremy.app.data.db.LocationNodeEntity
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份（`RULES.md` §12）。
 *
 * 三条不许动的：
 * 1. **导出时四张表在同一个事务里读**——`newDraft` 是"先写物品、再写照片"两条语句，
 *    夹在中间读会导出"所属物品不在包里"的照片行，那种包导入时被判成损坏；
 * 2. **导入顺序固定**：读包 → 整体校验 → 一个事务落库 → 事务成功之后才动磁盘上的照片；
 * 3. 🔒 **包里绝不出现 API key 或任何设置项**——这个类的构造参数里**刻意没有** `SettingsRepository`，
 *    导出通道物理上碰不到它。
 */
class BackupRepository(
    private val db: WheremyDatabase,
    private val photoStore: PhotoStore,
    private val appVersion: String,
    private val stagingDir: File,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    // ————————————————————————— 导出 —————————————————————————

    /** 往调用方打开的流里写一个完整的包。流的打开/关闭由界面层负责（打不开那个文件不算异常，算 null）。 */
    suspend fun exportTo(stream: OutputStream, at: Long = now()): BackupSummary = withContext(Dispatchers.IO) {
        // 同一个事务里读四张表（理由见类注释）
        val snapshot = db.withWriteTransaction {
            BackupData(
                locations = db.locationDao().all(),
                items = db.itemDao().all(),
                photos = db.photoDao().all(),
                tags = db.itemDao().allTagRows(),
            )
        }

        val written = HashSet<String>()
        var bytes = 0L
        // ⚠️ 计数流必须**同时把字节转发给调用方的流**：只加计数不写出去的版本会让导出包变成空的（踩过一次）
        val counting = object : OutputStream() {
            override fun write(b: Int) {
                bytes++
                stream.write(b)
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                bytes += len
                stream.write(b, off, len)
            }

            override fun flush() = stream.flush()

            // 不替调用方关流：谁打开的谁关（`ZipOutputStream.use` 只会走到这里）
            override fun close() = stream.flush()
        }
        ZipOutputStream(BufferedOutputStream(counting)).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
            zip.write(
                json.encodeToString(
                    BackupManifest(
                        schemaVersion = DB_SCHEMA_VERSION,
                        appVersion = appVersion,
                        exportedAt = at,
                    ),
                ).toByteArray(),
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(ENTRY_DATA))
            zip.write(json.encodeToString(snapshot).toByteArray())
            zip.closeEntry()

            // 照片：同一个路径只写一次（两件共用一张图时第二件指的是同一对文件）
            snapshot.photos.forEach { photo ->
                putOnce(zip, written, photo.filePath)
                putOnce(zip, written, photo.thumbPath)
            }
        }
        // 计数用的是"文件对"口径，与"完成"那句话说的同一批
        BackupSummary(
            locations = snapshot.locations.size,
            items = snapshot.items.size,
            photos = written.count { it.startsWith("${PhotoStore.DIR_ORIGINALS}/") },
            bytes = bytes,
        )
    }

    /** 读不到的图**跳过就行**：一张丢失的缩略图不该让整次导出失败（这一条决定了导入侧"缺文件要放行"）。 */
    private fun putOnce(zip: ZipOutputStream, written: MutableSet<String>, relPath: String) {
        if (!written.add(relPath)) return
        val file = photoStore.file(relPath)
        if (!file.isFile) return
        runCatching {
            zip.putNextEntry(ZipEntry("$PHOTO_PREFIX/$relPath"))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    // ————————————————————————— 导入 —————————————————————————

    suspend fun importFrom(stream: InputStream, mode: ImportMode): BackupSummary = withContext(Dispatchers.IO) {
        // 1 读包（此阶段一张表都还没写过）
        if (stagingDir.exists()) stagingDir.deleteRecursively()
        stagingDir.mkdirs()
        var keep: Set<String>
        val data: BackupData
        try {
            val bundle = readBundle(stream)
            keep = bundle.keep
            data = bundle.data
            validate(data)
        } catch (t: Throwable) {
            stagingDir.deleteRecursively()
            throw t
        }

        try {
            // 2 一个事务落库
            val inserted = runCatching {
                db.withWriteTransaction<BackupSummary> {
                    writeRows(data, mode)
                }
            }.getOrElse { t ->
                throw t.toBackupException()
            }

            // 3 事务成功之后才动磁盘上的照片
            if (mode == ImportMode.OVERWRITE) removeLocalPhotosNotIn(keep)
            moveStagedPhotos()
            BackupSummary(
                locations = inserted.locations,
                items = inserted.items,
                photos = inserted.photos,
                bytes = 0, // 导入路径不给字节数，界面据 bytes == 0 不显示这一段
            )
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private data class Bundle(val data: BackupData, val keep: Set<String>)

    private fun readBundle(stream: InputStream): Bundle {
        var manifest: BackupManifest? = null
        var data: BackupData? = null
        var manifestBroken = false
        var dataBroken = false
        val keep = HashSet<String>()

        ZipInputStream(BufferedInputStream(stream)).use { zip ->
            while (true) {
                val entry = try {
                    zip.nextEntry ?: break
                } catch (e: ZipException) {
                    throw BackupFormatException("这个 zip 读不出来，文件可能坏了")
                }
                when {
                    entry.name == ENTRY_MANIFEST -> runCatching {
                        manifest = json.decodeFromString<BackupManifest>(zip.readBytes().decodeToString())
                    }.onFailure { manifestBroken = true }

                    entry.name == ENTRY_DATA -> runCatching {
                        data = json.decodeFromString<BackupData>(zip.readBytes().decodeToString())
                    }.onFailure { dataBroken = true }

                    isPhotoEntry(entry.name) -> {
                        val rel = entry.name.removePrefix("$PHOTO_PREFIX/")
                        val target = File(stagingDir, rel)
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                        keep.add(rel)
                    }

                    // 目录条目与白名单外的条目：目录放行，别的文件名一律拒
                    !entry.isDirectory -> throw BackupFormatException("备份里有不合法的文件名：${entry.name}")
                }
                zip.closeEntry()
            }
        }

        val missing = buildList {
            if (manifest == null && !manifestBroken) add(ENTRY_MANIFEST)
            if (data == null && !dataBroken) add(ENTRY_DATA)
        }
        if (missing.isNotEmpty()) {
            throw BackupFormatException("这不是 Wheremy 的备份包：少了 ${missing.joinToString("、")}")
        }
        if (manifestBroken) throw BackupFormatException("备份的 manifest.json 读不出来")
        // 这一句是"包被截断"唯一的线索，要留着
        if (dataBroken) throw BackupFormatException("备份的 data.json 读不出来，文件可能被截断了")

        val m = manifest!!
        // 第五道拒（不在 validate 里）：此时一张表都还没写过，语义与"数据包不合格"不同
        if (m.schemaVersion > DB_SCHEMA_VERSION) {
            throw BackupFormatException(
                "这个备份来自更新的版本（schema ${m.schemaVersion} > $DB_SCHEMA_VERSION），先升级应用再导入",
            )
        }
        return Bundle(data!!, keep)
    }

    /** 四条拒（`RULES.md` §12.3）。**文件不在包里不拒**：导出本来就放过丢了的图。 */
    private fun validate(data: BackupData) {
        val locationIds = data.locations.mapTo(HashSet()) { it.id }
        val itemIds = data.items.mapTo(HashSet()) { it.id }

        data.items.forEach { item ->
            val locationId = item.locationId
            if (locationId != null && locationId !in locationIds) {
                throw BackupFormatException(
                    "「${item.name.ifBlank { "未命名物品" }}」所在的位置不在包里，备份不完整",
                )
            }
        }
        data.photos.forEach { photo ->
            val known = when (photo.ownerType) {
                "location" -> locationIds
                "item" -> itemIds
                else -> emptySet()
            }
            if (photo.ownerId !in known) {
                throw BackupFormatException("有照片找不到它所属的条目，备份不完整")
            }
            if (photo.filePath.isBlank() || photo.thumbPath.isBlank()) {
                throw BackupFormatException("有照片没写它对应的文件，备份不完整")
            }
        }
        data.tags.forEach { tag ->
            if (tag.itemId !in itemIds) {
                throw BackupFormatException("有标签挂在一件不在包里的物品上，备份不完整")
            }
        }
    }

    private suspend fun writeRows(data: BackupData, mode: ImportMode): BackupSummary {
        val locationDao = db.locationDao()
        val itemDao = db.itemDao()
        val photoDao = db.photoDao()

        if (mode == ImportMode.OVERWRITE) {
            photoDao.deleteAll()
            itemDao.deleteAll()
            itemDao.deleteAllTags()
            locationDao.deleteAll()
        }

        // MERGE：各表按自己的主键逐行比对，本机已有的原样保留
        val knownLocations = if (mode == ImportMode.MERGE) locationDao.all().mapTo(HashSet()) { it.id } else emptySet()
        val knownItems = if (mode == ImportMode.MERGE) itemDao.all().mapTo(HashSet()) { it.id } else emptySet()
        val knownPhotos = if (mode == ImportMode.MERGE) photoDao.all().mapTo(HashSet()) { it.id } else emptySet()
        val knownTags = if (mode == ImportMode.MERGE) {
            itemDao.allTagRows().mapTo(HashSet()) { it.itemId to it.tag }
        } else {
            emptySet()
        }

        val locations = data.locations.filter { it.id !in knownLocations }
        val items = data.items.filter { it.id !in knownItems }
        val photos = data.photos.filter { it.id !in knownPhotos }
        val tags = data.tags.filter { (it.itemId to it.tag) !in knownTags }

        // 位置按"父在前"插入：SQLite 逐条语句立刻检查外键，而包里位置的顺序不可控
        parentsFirst(locations).forEach { locationDao.upsert(it) }
        items.forEach { itemDao.upsert(it) }
        photos.forEach { photoDao.upsert(it) }
        // 标签表的主键是 (itemId, tag)
        if (tags.isNotEmpty()) itemDao.insertTags(tags)

        return BackupSummary(
            locations = locations.size,
            items = items.size,
            photos = photos.filter { it.ownerType == "item" }.size,
            bytes = 0,
        )
    }

    /**
     * 按"父节点先就位"排出插入顺序。
     *
     * 成环或父节点既不在包里也不在本机的节点会**留在原地交回**，让外键去拒——
     * 那种包整包失败并报"备份里有对不上的引用"。
     */
    private suspend fun parentsFirst(locations: List<LocationNodeEntity>): List<LocationNodeEntity> {
        val localIds = db.locationDao().all().mapTo(HashSet()) { it.id }
        val remaining = locations.toMutableList()
        val placed = HashSet<String>()
        val ordered = ArrayList<LocationNodeEntity>(locations.size)
        var progressed = true
        while (remaining.isNotEmpty() && progressed) {
            progressed = false
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val node = iterator.next()
                val parent = node.parentId
                if (parent == null || parent in placed || parent in localIds) {
                    ordered.add(node)
                    placed.add(node.id)
                    iterator.remove()
                    progressed = true
                }
            }
        }
        ordered.addAll(remaining)
        return ordered
    }

    /** 覆盖导入只删**没被新包声称**的文件（判据是"包里有没有这条字节"，不是"新库里有没有行引用它"）。 */
    private fun removeLocalPhotosNotIn(keep: Set<String>) {
        PHOTO_DIRS.forEach { dir ->
            File(photoStore.rootDir, dir).listFiles()?.forEach { file ->
                if (file.isFile && "$dir/${file.name}" !in keep) file.delete()
            }
        }
    }

    /** 单张照片搬不进去**不回滚库**（库是唯一真相源），代价是那件缩略图空白。 */
    private fun moveStagedPhotos() {
        PHOTO_DIRS.forEach { dir ->
            File(stagingDir, dir).listFiles()?.forEach { staged ->
                if (!staged.isFile) return@forEach
                runCatching {
                    val target = File(photoStore.rootDir, "$dir/${staged.name}")
                    target.parentFile?.mkdirs()
                    if (target.exists()) target.delete()
                    if (!staged.renameTo(target)) {
                        staged.copyTo(target, overwrite = true)
                        staged.delete()
                    }
                }
            }
        }
    }

    private fun Throwable.toBackupException(): BackupFormatException = if (this is BackupFormatException) {
        this
    } else {
        // 违反外键（成环/孤儿位置）走这里；文案指不出是哪个节点，所以笼统但要真
        BackupFormatException("备份里有对不上的引用，已取消导入，原来的数据没有动")
    }
}
