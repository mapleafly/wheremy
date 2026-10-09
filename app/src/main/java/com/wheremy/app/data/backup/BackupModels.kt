package com.wheremy.app.data.backup

import com.wheremy.app.data.db.ItemEntity
import com.wheremy.app.data.db.ItemPhotoEntity
import com.wheremy.app.data.db.ItemTagEntity
import com.wheremy.app.data.db.LocationNodeEntity
import kotlinx.serialization.Serializable

/** 备份格式版本（与 Room schema 解耦：导出的是四表行序列化成的 JSON，不是 `.db`）。 */
const val BACKUP_FORMAT_VERSION = 1

/** 包里的两个根文件名。 */
const val ENTRY_MANIFEST = "manifest.json"
const val ENTRY_DATA = "data.json"

// 照片在包里的目录前缀：photos/originals/… 与 photos/thumbs/…
// ⚠️ 写在 KDoc 里时别写成 `originals/**`：那两个字符 `/*` 会开一个嵌套注释（踩过两次，见 PLAN §3.4 F13）
const val PHOTO_PREFIX = "photos"

@Serializable
data class BackupManifest(
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAt: Long,
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
class BackupFormatException(message: String) : Exception(message)

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
