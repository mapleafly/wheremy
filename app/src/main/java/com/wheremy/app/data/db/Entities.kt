package com.wheremy.app.data.db

import androidx.room3.ColumnTypeConverter
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 四张表（`ARCHITECTURE.md` §4.2）。
 *
 * 🔒 **这一层的字段名、类型与顺序与参照实现逐字段一致**，因为备份包直接序列化这些 entity、
 * 且导入时按各表主键逐行比对（`RULES.md` §12）——改字段名等于让旧备份包导不进来。
 * 四个 entity 全部 `@Serializable`：备份不额外定义 DTO。
 */

/** 物品状态机（`RULES.md` §2）：只有两档，`name` 直接落库，改名等于改数据。 */
@Serializable
enum class ItemStatus { DRAFT, CONFIRMED }

/** 照片的主人可以是物品或位置（`ARCHITECTURE.md` §4.2 多态外键）。 */
object PhotoOwner {
    const val ITEM = "item"
    const val LOCATION = "location"
}

/**
 * `ItemStatus` ↔ `String`（落库用 `name`，不用 ordinal）。
 *
 * ⚠️ Room 3 的注解与 2.x 不同名：2.x 是 `@TypeConverter` / `@TypeConverters`，
 * 3.x 改成 **`@ColumnTypeConverter` / `@ColumnTypeConverters`**（`androidx.room3` 里没有 `TypeConverter`）。
 */
class ItemStatusConverter {
    @ColumnTypeConverter
    fun fromStatus(value: ItemStatus): String = value.name

    @ColumnTypeConverter
    fun toStatus(value: String): ItemStatus = ItemStatus.valueOf(value)
}

@Entity(
    tableName = "location_node",
    foreignKeys = [
        ForeignKey(
            entity = LocationNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("parentId")],
)
@Serializable
data class LocationNodeEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val kind: String?,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "item",
    foreignKeys = [
        ForeignKey(
            entity = LocationNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("locationId"), Index("status"), Index("expiresAt")],
)
@Serializable
data class ItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String?,
    /** CSV；与 [aliases] 一起，数组字段的编解码只在仓库层的 `splitList()`/`csv()` 里做 */
    val seasons: String?,
    val color: String?,
    val brand: String?,
    val material: String?,
    val aliases: String?,
    val description: String?,
    val notes: String?,
    val quantity: Int,
    val locationId: String?,
    val status: ItemStatus,
    val expiresAt: Long?,
    val expireKind: String?,
    val expireNotifiedAt: Long?,
    /** 派生列：写入时由 [SearchTextBuilder] 算出来，**不参与备份的语义判断**（`RULES.md` §10.3） */
    val searchText: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMovedAt: Long?,
)

@Entity(tableName = "item_photo", indices = [Index("ownerType", "ownerId")])
@Serializable
data class ItemPhotoEntity(
    @PrimaryKey val id: String,
    /** [PhotoOwner.ITEM] 或 [PhotoOwner.LOCATION] */
    val ownerType: String,
    val ownerId: String,
    val filePath: String,
    val thumbPath: String,
    val sortOrder: Int,
    val createdAt: Long,
)

@Entity(tableName = "item_tag", primaryKeys = ["itemId", "tag"], indices = [Index("tag")])
@Serializable
data class ItemTagEntity(val itemId: String, val tag: String)
