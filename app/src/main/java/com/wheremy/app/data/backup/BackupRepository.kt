package com.wheremy.app.data.backup

import androidx.room3.withWriteTransaction
import com.wheremy.app.data.db.DB_SCHEMA_VERSION
import com.wheremy.app.data.db.LocationNodeEntity
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.crypto.spec.SecretKeySpec

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

    /**
     * 往调用方打开的流里写一个完整的包。流的打开/关闭由界面层负责（打不开那个文件不算异常，算 null）。
     *
     * [password] 为 null 时写**明文 v1 包**（`formatVersion = 1`，旧应用也能导入）；
     * 给了口令就写**加密 v2 包**（`ADR-27`）：manifest 明文 + `data.enc` + 照片条目全加密。
     */
    suspend fun exportTo(stream: OutputStream, at: Long = now(), password: String? = null): BackupSummary =
        withContext(Dispatchers.IO) {
            // 同一个事务里读四张表（理由见类注释）
            val snapshot = db.withWriteTransaction {
                BackupData(
                    locations = db.locationDao().all(),
                    items = db.itemDao().all(),
                    photos = db.photoDao().all(),
                    tags = db.itemDao().allTagRows(),
                )
            }

            // 口令 → 密钥。salt 与 verifier 明文写在 manifest 里（**口令本身绝不进包**）
            val salt = if (password != null) BackupCrypto.newSalt() else null
            val key = if (password != null && salt != null) {
                BackupCrypto.deriveKey(password, salt, BackupCrypto.ITERATIONS)
            } else {
                null
            }
            val encryption = if (key != null && salt != null) {
                BackupEncryption(
                    saltBase64 = BackupCrypto.toBase64(salt),
                    verifierBase64 = BackupCrypto.toBase64(BackupCrypto.makeVerifier(key)),
                )
            } else {
                null
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
                            formatVersion = if (encryption != null) BACKUP_FORMAT_VERSION else PLAIN_FORMAT_VERSION,
                            schemaVersion = DB_SCHEMA_VERSION,
                            appVersion = appVersion,
                            exportedAt = at,
                            encryption = encryption,
                        ),
                    ).toByteArray(),
                )
                zip.closeEntry()

                if (key == null) {
                    zip.putNextEntry(ZipEntry(ENTRY_DATA))
                    zip.write(json.encodeToString(snapshot).toByteArray())
                    zip.closeEntry()
                } else {
                    zip.putNextEntry(ZipEntry(ENTRY_DATA_ENC))
                    zip.write(BackupCrypto.seal(key, json.encodeToString(snapshot).toByteArray(), ENTRY_DATA_ENC))
                    zip.closeEntry()
                }

                // 照片：同一个路径只写一次（两件共用一张图时第二件指的是同一对文件）
                snapshot.photos.forEach { photo ->
                    if (key == null) {
                        putOnce(zip, written, photo.filePath)
                        putOnce(zip, written, photo.thumbPath)
                    } else {
                        putSealed(zip, written, photo.filePath, key)
                        putSealed(zip, written, photo.thumbPath, key)
                    }
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

    /** 加密的同一件事：**条目名当 AAD**，于是把密文换个名字粘过去会验不过。 */
    private fun putSealed(zip: ZipOutputStream, written: MutableSet<String>, relPath: String, key: SecretKeySpec) {
        if (!written.add(relPath)) return
        val file = photoStore.file(relPath)
        if (!file.isFile) return
        runCatching {
            val entryName = "$PHOTO_PREFIX/$relPath"
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(BackupCrypto.seal(key, file.readBytes(), entryName))
            zip.closeEntry()
        }
    }

    // ————————————————————————— 导入 —————————————————————————

    /**
     * 读一个包并落库。
     *
     * [password] 只在**加密包**上需要（`ADR-27`）；明文包传 null 照旧。
     * 加密包没给口令抛 [BackupPasswordRequired]、口令不对抛 [BackupWrongPassword]——
     * 这两种都发生在"一张表都还没写"之前，界面可以拿它决定"弹口令框"还是"报错"。
     */
    suspend fun importFrom(stream: InputStream, mode: ImportMode, password: String? = null): BackupSummary =
        withContext(Dispatchers.IO) {
            // 1 读包（此阶段一张表都还没写过）
            if (stagingDir.exists()) stagingDir.deleteRecursively()
            stagingDir.mkdirs()
            var keep: Set<String>
            val data: BackupData
            try {
                // 先整包落盘：**manifest 要先读出来才知道要不要口令**，而 SAF 给的流不能回头（`ADR-27`）
                val pack = File(stagingDir, "pack.zip")
                stream.use { input -> pack.outputStream().use { input.copyTo(it) } }

                // 顺序与重构前一致：**先报"缺哪个"，再报"解不开"**（`ARCHITECTURE.md` §10.3、既有测试都钉着）
                val names = entryNamesOf(pack)
                checkMissing(names)

                val manifest = readManifest(pack)
                if (manifest.formatVersion > BACKUP_FORMAT_VERSION) {
                    throw BackupFormatException("这个备份来自更新的版本，先升级应用再导入")
                }
                val key = manifest.encryption?.let { enc -> keyFor(enc, password) }

                val bundle = readEntries(pack, key)
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

    /** 包里的条目名（先看有没有缺东西，再谈解不解得开）。 */
    private fun entryNamesOf(pack: File): Set<String> = try {
        ZipFile(pack).use { zip ->
            zip.entries().iterator().asSequence().mapTo(HashSet()) { it.name }
        }
    } catch (e: ZipException) {
        throw BackupFormatException("这个 zip 读不出来，文件可能坏了")
    }

    /**
     * 缺哪个报哪个（`RULES.md` §12.3）：**只报第一个缺的**（`ARCHITECTURE.md` §10.3 的口径）。
     * 加密包的负载叫 `data.enc`，所以两个名字有一个在就不算缺。
     */
    private fun checkMissing(names: Set<String>) {
        val missing = buildList {
            if (ENTRY_MANIFEST !in names) add(ENTRY_MANIFEST)
            if (ENTRY_DATA !in names && ENTRY_DATA_ENC !in names) add(ENTRY_DATA)
        }
        if (missing.isNotEmpty()) {
            throw BackupFormatException("这不是 Wheremy 的备份包：少了 ${missing.first()}")
        }
    }

    /** 口令 → 密钥。**没说口令就说清楚要口令，口令不对就说口令不对**——都不说成"文件坏了"。 */
    private fun keyFor(enc: BackupEncryption, password: String?): SecretKeySpec {
        if (password == null) throw BackupPasswordRequired()
        val key = BackupCrypto.deriveKey(password, BackupCrypto.fromBase64(enc.saltBase64), enc.iterations)
        if (!BackupCrypto.verifierOk(key, BackupCrypto.fromBase64(enc.verifierBase64))) throw BackupWrongPassword()
        return key
    }

    /** 读 manifest：**它永远明文**，因为读的人要先知道"这个包要不要口令"（`ADR-27`）。 */
    private fun readManifest(pack: File): BackupManifest {
        val text = try {
            ZipFile(pack).use { zip ->
                val entry = zip.getEntry(ENTRY_MANIFEST)
                    ?: throw BackupFormatException("这不是 Wheremy 的备份包：少了 $ENTRY_MANIFEST")
                zip.getInputStream(entry).use { it.readBytes().decodeToString() }
            }
        } catch (e: ZipException) {
            throw BackupFormatException("这个 zip 读不出来，文件可能坏了")
        }
        val manifest = runCatching { json.decodeFromString<BackupManifest>(text) }
            .getOrElse { throw BackupFormatException("备份的 manifest.json 读不出来") }
        // 第五道拒（不在 validate 里）：此时一张表都还没写过，语义与"数据包不合格"不同
        if (manifest.schemaVersion > DB_SCHEMA_VERSION) {
            throw BackupFormatException(
                "这个备份来自更新的版本（schema ${manifest.schemaVersion} > $DB_SCHEMA_VERSION），先升级应用再导入",
            )
        }
        return manifest
    }

    /** 读负载（明文直读、加密包逐条目解密）+ 把照片落到 staging。 */
    private fun readEntries(pack: File, key: SecretKeySpec?): Bundle {
        var data: BackupData? = null
        var dataBroken = false
        val keep = HashSet<String>()

        try {
            ZipFile(pack).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    when {
                        entry.name == ENTRY_MANIFEST -> Unit // 上面读过了

                        entry.name == ENTRY_DATA -> {
                            if (key != null) {
                                // 加密包里混进明文负载：与其猜，不如拒——这多半是有人把两个包拼错了
                                throw BackupFormatException("这个加密包里混进了明文的内容，导入已取消")
                            }
                            runCatching {
                                data = json.decodeFromString<BackupData>(
                                    zip.getInputStream(entry).use { it.readBytes().decodeToString() },
                                )
                            }.onFailure { dataBroken = true }
                        }

                        entry.name == ENTRY_DATA_ENC -> {
                            val k = key ?: throw BackupPasswordRequired()
                            // 解密失败（被改过/没口令）要原样报出来，不能被下面的 JSON 解析吞掉
                            val plain = BackupCrypto.open(
                                k,
                                zip.getInputStream(entry).use {
                                    it.readBytes()
                                },
                                ENTRY_DATA_ENC,
                            )
                            runCatching { data = json.decodeFromString<BackupData>(plain.decodeToString()) }
                                .onFailure { dataBroken = true }
                        }

                        isPhotoEntry(entry.name) -> {
                            val rel = entry.name.removePrefix("$PHOTO_PREFIX/")
                            val target = File(stagingDir, rel)
                            target.parentFile?.mkdirs()
                            val payload = zip.getInputStream(entry).use { it.readBytes() }
                            val bytes = if (key == null) payload else BackupCrypto.open(key, payload, entry.name)
                            target.outputStream().use { it.write(bytes) }
                            keep.add(rel)
                        }

                        // 目录条目与白名单外的条目：目录放行，别的文件名一律拒
                        !entry.isDirectory -> throw BackupFormatException("备份里有不合法的文件名：${entry.name}")
                    }
                }
            }
        } catch (e: ZipException) {
            throw BackupFormatException("这个 zip 读不出来，文件可能坏了")
        }

        if (data == null && !dataBroken) {
            val expected = if (key == null) ENTRY_DATA else ENTRY_DATA_ENC
            throw BackupFormatException("这不是 Wheremy 的备份包：少了 $expected")
        }
        // 这一句是"包被截断"唯一的线索，要留着
        if (dataBroken) throw BackupFormatException("备份的 data.json 读不出来，文件可能被截断了")
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
