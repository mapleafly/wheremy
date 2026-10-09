package com.wheremy.app.data.repo

import androidx.room3.withWriteTransaction
import com.wheremy.app.data.db.ItemEntity
import com.wheremy.app.data.db.ItemPhotoEntity
import com.wheremy.app.data.db.ItemStatus
import com.wheremy.app.data.db.ItemTagEntity
import com.wheremy.app.data.db.PhotoOwner
import com.wheremy.app.data.db.SearchTextBuilder
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.photos.StoredPhoto
import com.wheremy.app.domain.SearchRanker
import com.wheremy.app.domain.buildLocationTree
import java.util.UUID

/**
 * 物品仓库（`ARCHITECTURE.md` §6.1）。
 *
 * 跨层不变量都在这里收口：
 * - **数组字段的编解码只有一处**：`seasons`/`aliases` 存 CSV、`tags` 存 `item_tag` 行；空列表 → **null 而不是空串**；
 * - 所有字符串字段 `trim()` 后空串归一成 `null`（否则"清掉了类别"会变成库里一个空串，搜索与筛选跟着分叉）；
 * - `quantity` 在这一层 `coerceAtLeast(1)`；
 * - **照片与物品行同生同死**：写文件先、写库后，库失败就把文件扫干净。
 */
class ItemRepository(
    private val db: WheremyDatabase,
    val photoStore: PhotoStore,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val itemDao = db.itemDao()
    private val photoDao = db.photoDao()

    private var pendingAnalysis: ByteArray? = null

    /** 本次 `newDraft`/`swapPhoto` 产出的 1024 分析图，**取一次即清**（`CaptureFlow` 用它省掉二次编码）。 */
    fun takePendingAnalysis(): ByteArray? = pendingAnalysis.also { pendingAnalysis = null }

    /** 选图/拍照进来的第一站：写两个文件 + 一个事务写两行。 */
    suspend fun newDraft(jpeg: ByteArray): String {
        val stored = photoStore.write(jpeg)
        val id = UUID.randomUUID().toString()
        try {
            // 显式 <Unit>：try/catch 里的 lambda 返回值会被推断成 Nothing，
            // 不写清楚就报 "Return type mismatch: expected 'Nothing', actual 'Unit'"
            db.withWriteTransaction<Unit> {
                itemDao.upsert(blankItem(id))
                photoDao.upsert(stored.toRow(ownerId = id))
            }
        } catch (t: Throwable) {
            // 此刻库里没有任何一行认得这两个路径，谁写出来谁扫干净
            photoStore.delete(stored.originalPath)
            photoStore.delete(stored.thumbPath)
            pendingAnalysis = null
            throw t
        }
        pendingAnalysis = stored.analysisJpeg
        return id
    }

    /** 同一张照的第二件起：**一个新文件都不写**，只多两行（共用字节靠引用计数活着）。 */
    suspend fun newDraftSharingPhoto(sourceItemId: String): String {
        val source = photoDao.forOwner(PhotoOwner.ITEM, sourceItemId).firstOrNull()
            ?: error("源物品没有照片")
        val id = UUID.randomUUID().toString()
        db.withWriteTransaction {
            itemDao.upsert(blankItem(id))
            photoDao.upsert(
                source.copy(id = UUID.randomUUID().toString(), ownerId = id, sortOrder = 0),
            )
        }
        return id
    }

    /** 换一张：**先写新文件 → 再改行 → 最后按引用计数删旧文件**。 */
    suspend fun swapPhoto(itemId: String, jpeg: ByteArray) {
        val old = photoDao.forOwner(PhotoOwner.ITEM, itemId)
        val stored = photoStore.write(jpeg)
        try {
            db.withWriteTransaction<Unit> {
                photoDao.deleteForOwner(PhotoOwner.ITEM, itemId)
                photoDao.upsert(stored.toRow(ownerId = itemId))
            }
        } catch (t: Throwable) {
            // 失败时旧图还在，继续用；新写的两个文件扫掉
            photoStore.delete(stored.originalPath)
            photoStore.delete(stored.thumbPath)
            pendingAnalysis = null
            throw t
        }
        pendingAnalysis = stored.analysisJpeg
        deleteUnreferencedPhotoFiles(
            photoDao,
            photoStore,
            old.flatMap { listOf(it.filePath, it.thumbPath) },
        )
    }

    suspend fun get(itemId: String): ItemEntity? = itemDao.byId(itemId)

    /**
     * 把 AI 认出来的字段写进这一件（`RULES.md` §5.2/§5.3）。
     *
     * 两条纪律：
     * - **只写 AI 给得出的字段**：为 null 的一律不动，别把用户手打的字抹成空；
     * - 位置**只在空着的时候填**（`draft.locationId == null`），已经选好位置绝不覆盖；
     *   界面上另外用 [LocationGuessOutcome] 三句话把"为什么没改"说清楚。
     *
     * ⚠️ `touched` 是界面的事：这条路径**不**标记"用户动过"，否则一次 AI 回填会把整表标成手改过（§5.2）。
     */
    suspend fun applyAnalysis(
        itemId: String,
        item: com.wheremy.app.data.ai.AnalysisItem,
        suggestedLocationId: String?,
    ) {
        val entity = itemDao.byId(itemId) ?: return
        val existingTags = itemDao.tagsFor(itemId)
        val merged = entity.copy(
            name = item.name?.takeIf { it.isNotBlank() } ?: entity.name,
            category = item.category?.takeIf { it.isNotBlank() } ?: entity.category,
            seasons = item.seasons.takeIf { it.isNotEmpty() }?.let { csv(it) } ?: entity.seasons,
            color = item.color?.takeIf { it.isNotBlank() } ?: entity.color,
            material = item.material?.takeIf { it.isNotBlank() } ?: entity.material,
            brand = item.brand?.takeIf { it.isNotBlank() } ?: entity.brand,
            aliases = item.aliases.takeIf { it.isNotEmpty() }?.let { csv(it) } ?: entity.aliases,
            description = item.description?.takeIf { it.isNotBlank() } ?: entity.description,
            // 已经选好位置的绝不覆盖
            locationId = entity.locationId ?: suggestedLocationId,
            updatedAt = now(),
        )
        // 派生列要跟着重算（`RULES.md` §10.3：`search_text` 是写入时算出来的）
        val tags = item.tags.takeIf { it.isNotEmpty() } ?: existingTags
        val row = merged.copy(
            searchText = SearchTextBuilder.build(
                name = merged.name,
                brand = merged.brand,
                color = merged.color,
                material = merged.material,
                aliases = merged.aliases,
                description = merged.description,
                notes = merged.notes,
                tags = tags,
            ),
        )
        db.withWriteTransaction {
            itemDao.upsert(row)
            if (item.tags.isNotEmpty()) writeTags(itemId, item.tags.distinct())
        }
    }

    suspend fun draftOf(itemId: String): ItemDraft {
        val item = itemDao.byId(itemId) ?: error("物品不存在：$itemId")
        return ItemDraft(
            id = item.id,
            name = item.name,
            category = item.category,
            seasons = splitList(item.seasons),
            color = item.color,
            brand = item.brand,
            material = item.material,
            aliases = splitList(item.aliases),
            description = item.description,
            notes = item.notes,
            quantity = item.quantity,
            locationId = item.locationId,
            expiresAt = item.expiresAt,
            expireKind = item.expireKind,
            tags = itemDao.tagsFor(itemId),
        )
    }

    /** 确认入库：名称必须非空；顺手算 `lastMovedAt` 与 `searchText`，并整批替换标签。 */
    suspend fun confirm(draft: ItemDraft) {
        val name = draft.name.trim()
        require(name.isNotEmpty()) { "名称不能为空" }

        val timestamp = now()
        val current = itemDao.byId(draft.id)
        val fields = fieldsOf(draft, name)
        val lastMovedAt = when {
            current == null -> timestamp
            current.locationId != fields.locationId -> timestamp
            else -> current.lastMovedAt
        }
        val row = ItemEntity(
            id = draft.id,
            name = name,
            category = fields.category,
            seasons = fields.seasons,
            color = fields.color,
            brand = fields.brand,
            material = fields.material,
            aliases = fields.aliases,
            description = fields.description,
            notes = fields.notes,
            quantity = fields.quantity,
            locationId = fields.locationId,
            status = ItemStatus.CONFIRMED,
            expiresAt = fields.expiresAt,
            expireKind = fields.expireKind,
            expireNotifiedAt = reArm(current?.expiresAt, current?.expireNotifiedAt, fields.expiresAt),
            searchText = searchTextOf(draft, name),
            createdAt = current?.createdAt ?: timestamp,
            updatedAt = timestamp,
            lastMovedAt = lastMovedAt,
        )
        db.withWriteTransaction {
            itemDao.upsert(row)
            writeTags(draft.id, draft.tags)
        }
    }

    /** 「稍后处理」：写字段 + 翻回 DRAFT。**位置不动**（待归类的谓词本来就是 `OR`）。 */
    suspend fun keepAsDraft(draft: ItemDraft) {
        val current = itemDao.byId(draft.id) ?: error("物品不存在：${draft.id}")
        val name = draft.name.trim().ifEmpty { current.name }
        val fields = fieldsOf(draft, name)
        val timestamp = now()
        val row = current.copy(
            name = name,
            category = fields.category,
            seasons = fields.seasons,
            color = fields.color,
            brand = fields.brand,
            material = fields.material,
            aliases = fields.aliases,
            description = fields.description,
            notes = fields.notes,
            quantity = fields.quantity,
            status = ItemStatus.DRAFT,
            expiresAt = fields.expiresAt,
            expireKind = fields.expireKind,
            expireNotifiedAt = reArm(current.expiresAt, current.expireNotifiedAt, fields.expiresAt),
            searchText = searchTextOf(draft, name),
            updatedAt = timestamp,
        )
        db.withWriteTransaction {
            itemDao.upsert(row)
            writeTags(draft.id, draft.tags)
        }
    }

    /** 搬到某个位置。**有名字才顺手确认**：空名转 CONFIRMED 会让它从收件箱消失且搜不到。 */
    suspend fun move(itemId: String, locationId: String?) {
        val item = itemDao.byId(itemId) ?: error("物品不存在：$itemId")
        val timestamp = now()
        db.withWriteTransaction {
            itemDao.moveTo(itemId, locationId, timestamp)
            if (item.name.trim().isNotEmpty() && item.status != ItemStatus.CONFIRMED) {
                itemDao.upsert(
                    item.copy(
                        locationId = locationId,
                        status = ItemStatus.CONFIRMED,
                        lastMovedAt = timestamp,
                        updatedAt = timestamp,
                    ),
                )
            }
        }
    }

    /** 全文搜索 + 五个筛选（AND）。**标签筛选认 `item_tag` 行**，不是 `search_text` 里的字面。 */
    suspend fun rankedSearch(query: String, filters: Filters = Filters()): List<ItemEntity> {
        val q = query.trim().lowercase()
        val base = if (q.isEmpty()) itemDao.all() else itemDao.searchLike("%$q%")
        val tagOk = if (filters.tag.isNullOrBlank()) {
            base
        } else {
            val ids = itemDao.itemIdsWithTag(filters.tag).toSet()
            base.filter { it.id in ids }
        }
        val filtered = tagOk.filter { matches(it, filters) }
        return if (q.isEmpty()) filtered else SearchRanker.rank(filtered, q)
    }

    suspend fun inbox(): List<ItemEntity> = itemDao.inbox()

    suspend fun atLocation(locationId: String): List<ItemEntity> = itemDao.atLocation(locationId)

    /**
     * 筛选芯片那一排的标签。
     *
     * SQL 是 `SELECT DISTINCT tag … ORDER BY tag COLLATE NOCASE`（BINARY 序），而 `DISTINCT` **分大小写**，
     * 所以仓库层再补一次 `distinctBy { lowercase() }`：库里同时有 "MacBook" 与 "macbook" 时只留一颗芯片，
     * 点下去两件都出来（`itemIdsWithTag` 用 `LOWER` 比）。
     * ⚠️ 卡片上列出的标签走 `tagsFor`，**不做大小写合并**——那两处口径别统一成一处。
     */
    suspend fun allTags(): List<String> = itemDao.allTags().distinctBy { it.lowercase() }

    suspend fun photosOf(itemId: String): List<ItemPhotoEntity> = photoDao.forOwner(PhotoOwner.ITEM, itemId)

    suspend fun thumbPathOf(itemId: String): String? =
        photoDao.forOwner(PhotoOwner.ITEM, itemId).firstOrNull()?.thumbPath

    /** 读的是 **1600 原图**：冷启动之后"重新分析"唯一可用的来源。 */
    suspend fun photoBytesOf(itemId: String): ByteArray? {
        val path = photoDao.forOwner(PhotoOwner.ITEM, itemId).firstOrNull()?.filePath ?: return null
        val file = photoStore.file(path)
        return if (file.exists()) file.readBytes() else null
    }

    /** 列表页一行要的全部。⚠️ **N+1 查询**（每件各查标签与缩略图），调用方要有 seq 护栏。 */
    suspend fun summaries(items: List<ItemEntity>): List<ItemSummary> {
        val tree = buildLocationTree(db.locationDao().all())
        return items.map { item ->
            ItemSummary(
                item = item,
                tags = itemDao.tagsFor(item.id),
                thumbPath = thumbPathOf(item.id),
                locationPath = item.locationId?.let { tree.breadcrumb(it) },
            )
        }
    }

    /** 全表补算派生列，**只在内容真变了才写**（不为一次启动把整库 `updatedAt` 搅一遍）。 */
    suspend fun rebuildSearchText(): Int {
        var changed = 0
        db.withWriteTransaction {
            itemDao.all().forEach { item ->
                val text = SearchTextBuilder.build(
                    name = item.name,
                    brand = item.brand,
                    color = item.color,
                    material = item.material,
                    aliases = item.aliases,
                    description = item.description,
                    notes = item.notes,
                    tags = itemDao.tagsFor(item.id),
                )
                if (text != item.searchText) {
                    itemDao.upsert(item.copy(searchText = text))
                    changed++
                }
            }
        }
        return changed
    }

    /**
     * 删除确认框要的三个数（`RULES.md` §9.3）。
     * 原图与缩略图**两条路径都要问**，每类主人取 **max 而不是 sum**——
     * 问的是"最多还有几个人共用"，不是"几个路径上各有几个人"。
     */
    suspend fun photoDeletionOf(itemId: String): PhotoDeletion {
        val photo = photoDao.forOwner(PhotoOwner.ITEM, itemId).firstOrNull()
            ?: return PhotoDeletion(hasPhoto = false, otherItems = 0, otherOwners = 0)

        var otherItems = 0
        var otherOwners = 0
        listOf(photo.filePath, photo.thumbPath).filter { it.isNotBlank() }.forEach { path ->
            photoDao.otherOwnersOnPath(PhotoOwner.ITEM, itemId, path).forEach { row ->
                if (row.ownerType == PhotoOwner.ITEM) {
                    otherItems = maxOf(otherItems, row.owners)
                } else {
                    otherOwners = maxOf(otherOwners, row.owners)
                }
            }
        }
        return PhotoDeletion(hasPhoto = true, otherItems = otherItems, otherOwners = otherOwners)
    }

    /** 删除：**先删三处行，再按引用计数删文件**。顺序反了会留下无人认领的字节。 */
    suspend fun delete(itemId: String) {
        val photo = photoDao.forOwner(PhotoOwner.ITEM, itemId).firstOrNull()
        db.withWriteTransaction {
            itemDao.deleteById(itemId)
            itemDao.clearTags(itemId)
            photoDao.deleteForOwner(PhotoOwner.ITEM, itemId)
        }
        deleteUnreferencedPhotoFiles(
            photoDao,
            photoStore,
            listOfNotNull(photo?.filePath, photo?.thumbPath),
        )
    }

    // —— 私有收口 ——

    /** `expiresAt` 变了就清 `expireNotifiedAt`（重新武装：改过到期日就该再提醒一次）。 */
    private fun reArm(oldExpiresAt: Long?, oldNotifiedAt: Long?, newExpiresAt: Long?): Long? =
        if (oldExpiresAt == newExpiresAt) oldNotifiedAt else null

    private fun blankItem(id: String): ItemEntity {
        val timestamp = now()
        return ItemEntity(
            id = id,
            name = "",
            category = null,
            seasons = null,
            color = null,
            brand = null,
            material = null,
            aliases = null,
            description = null,
            notes = null,
            quantity = 1,
            locationId = null,
            status = ItemStatus.DRAFT,
            expiresAt = null,
            expireKind = null,
            expireNotifiedAt = null,
            searchText = "",
            createdAt = timestamp,
            updatedAt = timestamp,
            lastMovedAt = null,
        )
    }

    private suspend fun writeTags(itemId: String, tags: List<String>) {
        itemDao.clearTags(itemId)
        val rows = tags.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { ItemTagEntity(itemId = itemId, tag = it) }
        if (rows.isNotEmpty()) itemDao.insertTags(rows)
    }

    /** draft → 落库字段：trim、空串归一成 null、`quantity` 至少 1、空列表 → null。 */
    private fun fieldsOf(draft: ItemDraft, name: String) = Fields(
        name = name,
        category = draft.category.normalized(),
        seasons = csv(draft.seasons),
        color = draft.color.normalized(),
        brand = draft.brand.normalized(),
        material = draft.material.normalized(),
        aliases = csv(draft.aliases),
        description = draft.description.normalized(),
        notes = draft.notes.normalized(),
        quantity = draft.quantity.coerceAtLeast(1),
        locationId = draft.locationId,
        expiresAt = draft.expiresAt,
        expireKind = draft.expireKind.normalized(),
    )

    private fun searchTextOf(draft: ItemDraft, name: String): String = SearchTextBuilder.build(
        name = name,
        brand = draft.brand.normalized(),
        color = draft.color.normalized(),
        material = draft.material.normalized(),
        aliases = csv(draft.aliases),
        description = draft.description.normalized(),
        notes = draft.notes.normalized(),
        tags = draft.tags.map { it.trim() }.filter { it.isNotEmpty() },
    )

    /** 五个筛选项的 AND 组合。 */
    private fun matches(item: ItemEntity, filters: Filters): Boolean {
        if (filters.category != null && item.category != filters.category) return false
        if (filters.season != null && filters.season !in splitList(item.seasons)) return false
        if (filters.onlyInbox && !(item.locationId == null || item.status == ItemStatus.DRAFT)) return false
        if (filters.expireUntil != null) {
            val expiresAt = item.expiresAt ?: return false
            if (expiresAt > filters.expireUntil) return false
        }
        return true
    }

    private fun String?.normalized(): String? = this?.trim()?.ifBlank { null }

    /** 空列表 → **null**（不是空串）：否则"清掉季节"会在库里变成 `","`。 */
    private fun csv(values: List<String>): String? {
        val cleaned = values.map { it.trim() }.filter { it.isNotEmpty() }
        return cleaned.takeIf { it.isNotEmpty() }?.joinToString(",")
    }

    private fun splitList(value: String?): List<String> =
        value?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun StoredPhoto.toRow(ownerId: String) = ItemPhotoEntity(
        id = UUID.randomUUID().toString(),
        ownerType = PhotoOwner.ITEM,
        ownerId = ownerId,
        filePath = originalPath,
        thumbPath = thumbPath,
        sortOrder = 0,
        createdAt = now(),
    )

    private data class Fields(
        val name: String,
        val category: String?,
        val seasons: String?,
        val color: String?,
        val brand: String?,
        val material: String?,
        val aliases: String?,
        val description: String?,
        val notes: String?,
        val quantity: Int,
        val locationId: String?,
        val expiresAt: Long?,
        val expireKind: String?,
    )
}
