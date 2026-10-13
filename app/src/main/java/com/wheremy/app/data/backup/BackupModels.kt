package com.wheremy.app.data.backup

import com.wheremy.app.data.db.ItemEntity
import com.wheremy.app.data.db.ItemPhotoEntity
import com.wheremy.app.data.db.ItemTagEntity
import com.wheremy.app.data.db.LocationNodeEntity
import kotlinx.serialization.Serializable

/**
 * **能读到的最高格式版本**（`ADR-27`）。
 *
 * - 明文包写 [PLAIN_FORMAT_VERSION]（= 1）：**将来任何版本都能导入**，这是「不加密导出」的承诺；
 * - 加密包写 2：里面有 `encryption` 段与 `data.enc`，认不出的旧应用必须**拒收**而不是乱读。
 */
const val BACKUP_FORMAT_VERSION = 2

/** 不加密导出时写进 manifest 的版本：永远是 1（旧应用也能导入）。 */
const val PLAIN_FORMAT_VERSION = 1

/** 包里的根文件名。加密包的负载叫 `data.enc`——**文件名本身也是给读的人看的线索**。 */
const val ENTRY_MANIFEST = "manifest.json"
const val ENTRY_DATA = "data.json"
const val ENTRY_DATA_ENC = "data.enc"

// 照片在包里的目录前缀：photos/originals/… 与 photos/thumbs/…
// ⚠️ 写在 KDoc 里时别写成 `originals/**`：那两个字符 `/*` 会开一个嵌套注释（踩过两次，见 PLAN §3.4 F13）
const val PHOTO_PREFIX = "photos"

/**
 * 加密段的参数（明文写在 `manifest.json` 里，**不含口令、不含密钥**）。
 * 读的人靠它知道「要不要问口令、用什么算法、几次迭代、salt 是什么」，以及先验哪个块。
 */
@Serializable
data class BackupEncryption(
    val algorithm: String = BackupCrypto.ALGORITHM,
    val kdf: String = BackupCrypto.KDF,
    val iterations: Int = BackupCrypto.ITERATIONS,
    val saltBase64: String,
    val verifierBase64: String,
)

@Serializable
data class BackupManifest(
    /** 明文包恒为 [PLAIN_FORMAT_VERSION]；加密包写 2。**别跟着 [BACKUP_FORMAT_VERSION] 走**（那是最高的可读版本）。 */
    val formatVersion: Int = PLAIN_FORMAT_VERSION,
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAt: Long,
    /** 加密段；**明文包是 null**（`ADR-27`）。 */
    val encryption: BackupEncryption? = null,
)

/** 四张表的行（顺序即插入顺序的一部分：位置要按父在前排好）。 */
@Serializable
data class BackupData(
    val locations: List<LocationNodeEntity> = emptyList(),
    val items: List<ItemEntity> = emptyList(),
    val photos: List<ItemPhotoEntity> = emptyList(),
    val tags: List<ItemTagEntity> = emptyList(),
)

/** 导入方式（`PRD.md` FR-BAK-02）。 */
enum class ImportMode { MERGE, OVERWRITE }

/** 一次导出/导入的账：**文件对数**与"完成那句话"数的是同一批（`RULES.md` §12.1）。 */
data class BackupSummary(val locations: Int, val items: Int, val photos: Int, val bytes: Long)

/** 包本身的问题：**它的 message 是要给用户看的中文**，会被原样贴到界面上（`RULES.md` §16）。 */
open class BackupFormatException(message: String) : Exception(message)

/**
 * 这个包加密了、但这次没给口令（`ADR-27`）。
 *
 * 单独一个类型是为了让界面能**只对这一种失败**弹口令输入框：其余失败照旧报错，
 * 不能把「包坏了」也当成「要口令」（`RULES.md` §12.5 判据 3）。
 */
class BackupPasswordRequired : BackupFormatException("这个备份包加密了，需要口令才能导入")

/** 口令不对：**不是**文件坏了，也不是导入失败（同一个包换个口令还能用）。 */
class BackupWrongPassword : BackupFormatException("口令不对")

/** 1000 进制、整除截断（`UiFormat.formatBytes` 的口径；`bytes == 0` 时界面不显示这一段）。 */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "${bytes / 1_000_000_000} GB"
    bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
    bytes >= 1_000 -> "${bytes / 1_000} kB"
    else -> "$bytes B"
}

/**
 * 解包路径白名单（`RULES.md` §12.3）🔒 **安全边界，不许放宽**：
 * 只认 `PhotoStore` 的两个目录、名字必须是**恰好一层**且字符集受限、不许含 `..`。
 * 解一个来路不明的 zip 时，`..` 或绝对路径会写到应用外面去。
 */
fun isPhotoEntry(name: String): Boolean {
    val prefix = "$PHOTO_PREFIX/"
    if (!name.startsWith(prefix)) return false
    val tail = name.removePrefix(prefix)
    if (tail.contains("..")) return false
    if (!tail.matches(Regex("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+"))) return false
    return tail.substringBefore('/') in PHOTO_DIRS
}

val PHOTO_DIRS = listOf("originals", "thumbs")
