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

    /**
     * 全量（照片墙在空 query 时走这条）。
     *
     * 🔒 **故意没有 `ORDER BY`**（`RULES.md` §10.1）：SQLite 按 rowid 升序返回 = 插入先后，
     * 而 `@Upsert` 按主键原地更新、不改 rowid，所以**编辑一件物品不会把它挪到前面**。
     * 想当然加上 `ORDER BY updatedAt DESC` 会改掉用户已经习惯的卡片次序，
     * 也会让"屏幕上第 N 格是谁"这种对账失效（`REBUILD.md` §7 C 组的事故就靠这个次序核对）。
     */
    @Query("SELECT * FROM item")
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

    /**
     * 到期取数窗口（`RULES.md` §11.2）。**下界不设**：过期很久但从没说过的也要进来。
     *
     * 三个分支各挡一种骚扰：
     * - `expireNotifiedAt IS NULL`：进窗口后的第一次；
     * - `expireNotifiedAt < windowStart AND expiresAt ∈ [windowStart, todayEnd)`："提前提醒过、今天就是到期日"再允许说一次；
     * - 过期且已说过的**不再进来**（否则 `ORDER BY expiresAt` 会让它们长期占满 LIMIT 50）。
     *
     * ⚠️ 第二分支的三个条件缺一不可：旧的写法只要求"早于本次窗口起点"，会把"最多两次"变成"每天一次"。
     */
    @Query(
        "SELECT * FROM item WHERE expiresAt IS NOT NULL AND expiresAt <= :windowEnd " +
            "AND (expireNotifiedAt IS NULL " +
            "OR (expireNotifiedAt < :windowStart AND expiresAt >= :windowStart AND expiresAt < :todayEnd)) " +
            "ORDER BY expiresAt LIMIT 50",
    )
    suspend fun expiringBy(windowStart: Long, todayEnd: Long, windowEnd: Long): List<ItemEntity>

    /**
     * 只给设置页那个调试按钮：**放宽的是"今天说过几次"**（与 [expiringBy] 比，少的就是那条
     * `expireNotifiedAt < windowStart`），但**不是把沉默的旧账翻回来**——过期很久且已说过的仍然不进来。
     */
    @Query(
        "SELECT * FROM item WHERE expiresAt IS NOT NULL AND expiresAt <= :windowEnd " +
            "AND (expiresAt >= :windowStart OR expireNotifiedAt IS NULL) " +
            "ORDER BY expiresAt LIMIT 50",
    )
    suspend fun expiringIgnoringNotified(windowStart: Long, windowEnd: Long): List<ItemEntity>

    @Query("UPDATE item SET expireNotifiedAt = :notifiedAt WHERE id IN (:ids)")
    suspend fun markExpireNotified(ids: List<String>, notifiedAt: Long)
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
