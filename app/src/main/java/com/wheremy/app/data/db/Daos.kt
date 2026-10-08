package com.wheremy.app.data.db

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Upsert

/**
 * 三个 DAO 的取数契约（`ARCHITECTURE.md` §4.3）。
 *
 * 设计选择：**树在内存里建**（全量 `all()`），所以位置表的查询不做递归 SQL。
 */

@Dao
interface LocationDao {

    @Upsert
    suspend fun upsert(node: LocationNodeEntity)

    @Upsert
    suspend fun upsertAll(nodes: List<LocationNodeEntity>)

    @Query("SELECT * FROM location_node")
    suspend fun all(): List<LocationNodeEntity>

    @Query("SELECT * FROM location_node WHERE id = :id")
    suspend fun byId(id: String): LocationNodeEntity?

    @Query("DELETE FROM location_node")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(node: LocationNodeEntity)
}

@Dao
interface ItemDao {

    @Upsert
    suspend fun upsert(item: ItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<ItemEntity>)

    @Query("DELETE FROM item")
    suspend fun deleteAll()

    @Query("SELECT * FROM item WHERE id = :id")
    suspend fun byId(id: String): ItemEntity?

    /** 全量（照片墙在空 query 时走这条）。 */
    @Query("SELECT * FROM item ORDER BY updatedAt DESC")
    suspend fun all(): List<ItemEntity>

    @Delete
    suspend fun delete(item: ItemEntity)

    @Query("DELETE FROM item WHERE id = :id")
    suspend fun deleteById(id: String)

    // —— 标签：整批替换（先 clear 再 insert），所以这里不给单条 update ——

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<ItemTagEntity>)

    @Query("DELETE FROM item_tag WHERE itemId = :itemId")
    suspend fun clearTags(itemId: String)

    @Query("SELECT tag FROM item_tag WHERE itemId = :itemId ORDER BY tag")
    suspend fun tagsFor(itemId: String): List<String>

    @Query("SELECT * FROM item_tag")
    suspend fun allTagRows(): List<ItemTagEntity>

    @Query("DELETE FROM item_tag")
    suspend fun deleteAllTags()

    @Query("SELECT DISTINCT tag FROM item_tag ORDER BY tag COLLATE NOCASE")
    suspend fun allTags(): List<String>

    /**
     * 全文搜索。[searchText] 写入时已小写化，pattern 由调用方小写化后拼 `%…%`。
     * **300 是硬上限**（`RULES.md` §10.2）：截断发生在打分之前，界面必须把这件事说出来（FR-FND-07）。
     */
    @Query("SELECT * FROM item WHERE searchText LIKE :pattern ORDER BY updatedAt DESC LIMIT 300")
    suspend fun searchLike(pattern: String): List<ItemEntity>

    /** 待归类：没有位置，或还是草稿。 */
    @Query(
        "SELECT * FROM item WHERE locationId IS NULL OR status = 'DRAFT' ORDER BY updatedAt DESC",
    )
    suspend fun inbox(): List<ItemEntity>

    @Query("SELECT * FROM item WHERE locationId = :locationId ORDER BY updatedAt DESC")
    suspend fun atLocation(locationId: String): List<ItemEntity>

    @Query("SELECT * FROM item WHERE status = :status ORDER BY updatedAt DESC")
    suspend fun byStatus(status: ItemStatus): List<ItemEntity>

    @Query("SELECT COUNT(*) FROM item WHERE locationId = :locationId")
    suspend fun countAt(locationId: String): Int

    /**
     * 搬移物品：**同一个时间戳写 `lastMovedAt` 与 `updatedAt`**。
     * 两个值不一致的话，"刚搬到哪"和"最近动过"会互相打架（`RULES.md` §3.2）。
     */
    @Query(
        "UPDATE item SET locationId = :locationId, lastMovedAt = :movedAt, updatedAt = :movedAt " +
            "WHERE id = :itemId",
    )
    suspend fun moveTo(itemId: String, locationId: String?, movedAt: Long)

    /** 标签筛选认的是 `item_tag` 里的行，**不是** `searchText` 里的字面；大小写不敏感。 */
    @Query("SELECT itemId FROM item_tag WHERE LOWER(tag) = LOWER(:tag)")
    suspend fun itemIdsWithTag(tag: String): List<String>
}

/** `otherOwnersOnPath` 的一行：某类主人还有几个（`ARCHITECTURE.md` §4.3）。 */
data class PhotoOwnerCount(val ownerType: String, val owners: Int)

@Dao
interface PhotoDao {

    @Upsert
    suspend fun upsert(photo: ItemPhotoEntity)

    @Upsert
    suspend fun upsertAll(photos: List<ItemPhotoEntity>)

    @Query(
        "SELECT * FROM item_photo WHERE ownerType = :ownerType AND ownerId = :ownerId " +
            "ORDER BY sortOrder",
    )
    suspend fun forOwner(ownerType: String, ownerId: String): List<ItemPhotoEntity>

    @Query("DELETE FROM item_photo WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun deleteForOwner(ownerType: String, ownerId: String)

    @Query("SELECT * FROM item_photo")
    suspend fun all(): List<ItemPhotoEntity>

    @Query("DELETE FROM item_photo")
    suspend fun deleteAll()

    /**
     * 还有多少行指着这个路径。
     *
     * ⚠️ **调用方必须先删掉自己的行再问**，否则永远数到自己——这是"照片会不会跟着没"
     * 唯一的判据（`RULES.md` §9.2）。原图与缩略图各一列，但两个目录的文件名互不重合，数一处就够。
     */
    @Query("SELECT COUNT(*) FROM item_photo WHERE filePath = :path OR thumbPath = :path")
    suspend fun refsTo(path: String): Int

    /**
     * 除自己之外，还有哪些主人指着这个路径（按 ownerType 分组数 `DISTINCT ownerId`）。
     * 删除确认框那句"照片会不会跟着没"靠它。
     */
    @Query(
        "SELECT ownerType, COUNT(DISTINCT ownerId) AS owners FROM item_photo " +
            "WHERE (filePath = :path OR thumbPath = :path) " +
            "AND NOT (ownerType = :ownerType AND ownerId = :ownerId) GROUP BY ownerType",
    )
    suspend fun otherOwnersOnPath(ownerType: String, ownerId: String, path: String): List<PhotoOwnerCount>
}
