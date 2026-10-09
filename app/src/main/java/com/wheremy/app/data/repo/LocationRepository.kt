package com.wheremy.app.data.repo

import androidx.room3.withWriteTransaction
import com.wheremy.app.data.db.ItemPhotoEntity
import com.wheremy.app.data.db.LocationNodeEntity
import com.wheremy.app.data.db.PhotoOwner
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.photos.StoredPhoto
import com.wheremy.app.domain.LocationTree
import com.wheremy.app.domain.buildLocationTree
import com.wheremy.app.domain.canDeleteLocation
import com.wheremy.app.domain.toDomain
import java.util.UUID

/** 位置删不掉时抛它——界面据此决定说哪句话（`RULES.md` §8.5）。 */
class LocationNotEmptyException(val childCount: Int, val itemCount: Int) : IllegalStateException("这个位置里还有东西")

/**
 * 位置仓库（`ARCHITECTURE.md` §6.2）。
 *
 * 三条容易踩的规则：
 * - **同级重排不是交换 `sortOrder`**，而是按显示次序给整条同级重排号（重复值下"交换"等于没换）；
 * - `changeParent` 有三道 require：不能移到它自己、目标不存在、**不能移到它的子位置下面**；
 * - 一个位置只留一张照片：`setPhoto` 是**整类清掉再写一行**，且先要求位置行还在。
 */
class LocationRepository(
    private val db: WheremyDatabase,
    /** 界面层要拿它把相对路径换成绝对路径（与 `ItemRepository` 同一做法） */
    val photoStore: PhotoStore,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val locationDao = db.locationDao()
    private val itemDao = db.itemDao()
    private val photoDao = db.photoDao()

    suspend fun tree(): LocationTree = buildLocationTree(locationDao.all())

    suspend fun all(): List<LocationNodeEntity> = locationDao.all()

    suspend fun byId(id: String): LocationNodeEntity? = locationDao.byId(id)

    suspend fun add(parentId: String?, name: String, kind: String?): String {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "位置名称不能为空" }

        val siblings = locationDao.all().filter { it.parentId == parentId }
        require(siblings.none { it.name == clean }) { "同一层级下已有「$clean」" }

        val timestamp = now()
        val id = UUID.randomUUID().toString()
        locationDao.upsert(
            LocationNodeEntity(
                id = id,
                parentId = parentId,
                name = clean,
                kind = kind?.trim()?.ifBlank { null },
                sortOrder = (siblings.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        return id
    }

    /** **名字与类型一次读改写**：分两笔写会在"改名的同时改类型"时丢掉一笔。 */
    suspend fun update(id: String, name: String, kind: String?) {
        val node = locationDao.byId(id) ?: error("位置不存在：$id")
        val clean = name.trim()
        require(clean.isNotEmpty()) { "位置名称不能为空" }

        val siblings = locationDao.all().filter { it.parentId == node.parentId && it.id != id }
        require(siblings.none { it.name == clean }) { "同一层级下已有「$clean」" }

        locationDao.upsert(
            node.copy(
                name = clean,
                kind = kind?.trim()?.ifBlank { null },
                updatedAt = now(),
            ),
        )
    }

    suspend fun changeParent(id: String, newParentId: String?) {
        val all = locationDao.all()
        val node = all.firstOrNull { it.id == id } ?: error("位置不存在：$id")

        require(newParentId != id) { "不能把位置移到它自己下面" }
        if (newParentId != null) {
            require(all.any { it.id == newParentId }) { "目标位置不存在" }
        }
        val descendants = buildLocationTree(all).descendantIds(id)
        require(newParentId == null || newParentId !in descendants) {
            "不能把位置移到它的子位置下面"
        }

        val siblings = all.filter { it.parentId == newParentId && it.id != id }
        locationDao.upsert(
            node.copy(
                parentId = newParentId,
                sortOrder = (siblings.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                updatedAt = now(),
            ),
        )
    }

    suspend fun itemCount(id: String): Int = itemDao.countAt(id)

    /** 含子位置的物品总数（`descendantIds` 含自己，所以直接求和）。 */
    suspend fun subtreeItemCount(id: String): Int {
        val tree = tree()
        return tree.descendantIds(id).sumOf { itemDao.countAt(it) }
    }

    suspend fun photoOf(id: String): ItemPhotoEntity? = photoDao.forOwner(PhotoOwner.LOCATION, id).firstOrNull()

    /** 给位置挂一张参照图：位置必须在、整类清掉再写一行、失败删新文件、最后回收旧文件。 */
    suspend fun setPhoto(ownerId: String, jpeg: ByteArray) {
        require(locationDao.byId(ownerId) != null) { "这个位置已经不在了，照片没处可挂" }

        val old = photoDao.forOwner(PhotoOwner.LOCATION, ownerId)
        val stored = photoStore.write(jpeg)
        try {
            db.withWriteTransaction<Unit> {
                photoDao.deleteForOwner(PhotoOwner.LOCATION, ownerId)
                photoDao.upsert(
                    ItemPhotoEntity(
                        id = UUID.randomUUID().toString(),
                        ownerType = PhotoOwner.LOCATION,
                        ownerId = ownerId,
                        filePath = stored.originalPath,
                        thumbPath = stored.thumbPath,
                        sortOrder = 0,
                        createdAt = now(),
                    ),
                )
            }
        } catch (t: Throwable) {
            photoStore.delete(stored.originalPath)
            photoStore.delete(stored.thumbPath)
            throw t
        }
        deleteUnreferencedPhotoFiles(
            photoDao,
            photoStore,
            old.flatMap { listOf(it.filePath, it.thumbPath) },
        )
    }

    /** 没有照片时是**空操作**（不是错误）。 */
    suspend fun clearPhoto(id: String) {
        val old = photoDao.forOwner(PhotoOwner.LOCATION, id)
        if (old.isEmpty()) return
        db.withWriteTransaction { photoDao.deleteForOwner(PhotoOwner.LOCATION, id) }
        deleteUnreferencedPhotoFiles(
            photoDao,
            photoStore,
            old.flatMap { listOf(it.filePath, it.thumbPath) },
        )
    }

    /**
     * 同层上移/下移。**按显示次序给整条同级重排号**——重复的 `sortOrder` 下"交换"等于没换。
     * 到边界返回 `false`（界面据此把箭头置灰）。
     */
    suspend fun shiftWithinParent(id: String, up: Boolean): Boolean {
        val all = locationDao.all()
        val node = all.firstOrNull { it.id == id } ?: return false
        val siblings = buildLocationTree(all).childrenOf(node.parentId).toMutableList()
        val index = siblings.indexOfFirst { it.id == id }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in siblings.indices) return false

        siblings.removeAt(index)
        siblings.add(target, node.toDomain())

        val timestamp = now()
        db.withWriteTransaction {
            siblings.forEachIndexed { order, sibling ->
                val entity = all.first { it.id == sibling.id }
                if (entity.sortOrder != order) {
                    locationDao.upsert(entity.copy(sortOrder = order, updatedAt = timestamp))
                }
            }
        }
        return true
    }

    /** 删除门槛只看**子位置数 + 本层物品数**；删之前先把它的照片清掉。 */
    suspend fun delete(id: String) {
        val all = locationDao.all()
        val node = all.firstOrNull { it.id == id } ?: return
        val children = buildLocationTree(all).childrenOf(id)
        val items = itemDao.countAt(id)
        if (!canDeleteLocation(children, items)) {
            throw LocationNotEmptyException(childCount = children.size, itemCount = items)
        }
        clearPhoto(id)
        locationDao.delete(node)
    }
}

/** 位置类型词表：**输入框允许填别的词**，这里只是预置芯片的顺序。 */
val LOCATION_KINDS = listOf("房间", "衣柜", "抽屉", "收纳盒", "箱子", "挂钩", "货架", "其他")

/** 给测试与界面用的便捷扩展：把 `StoredPhoto` 摊成两条相对路径。 */
internal fun StoredPhoto.paths(): List<String> = listOf(originalPath, thumbPath)
