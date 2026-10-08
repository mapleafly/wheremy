package com.wheremy.app.data

import com.wheremy.app.data.db.ItemEntity
import com.wheremy.app.data.db.ItemPhotoEntity
import com.wheremy.app.data.db.ItemStatus
import com.wheremy.app.data.db.LocationNodeEntity
import com.wheremy.app.data.db.PhotoOwner

/**
 * 测试数据的工厂：四张表的字段都多，逐个写会把手写错。
 * 只需覆盖关心的字段，其余给默认值——但**默认值必须合法**（比如 `searchText` 不能是 null）。
 */
internal fun item(
    id: String = "i-1",
    name: String = "电钻",
    category: String? = null,
    seasons: String? = null,
    color: String? = null,
    brand: String? = null,
    material: String? = null,
    aliases: String? = null,
    description: String? = null,
    notes: String? = null,
    quantity: Int = 1,
    locationId: String? = null,
    status: ItemStatus = ItemStatus.DRAFT,
    expiresAt: Long? = null,
    expireKind: String? = null,
    expireNotifiedAt: Long? = null,
    searchText: String = name.lowercase(),
    createdAt: Long = 0L,
    updatedAt: Long = 0L,
    lastMovedAt: Long? = null,
) = ItemEntity(
    id = id,
    name = name,
    category = category,
    seasons = seasons,
    color = color,
    brand = brand,
    material = material,
    aliases = aliases,
    description = description,
    notes = notes,
    quantity = quantity,
    locationId = locationId,
    status = status,
    expiresAt = expiresAt,
    expireKind = expireKind,
    expireNotifiedAt = expireNotifiedAt,
    searchText = searchText,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastMovedAt = lastMovedAt,
)

internal fun location(
    id: String = "l-1",
    parentId: String? = null,
    name: String = "主卧",
    kind: String? = "房间",
    sortOrder: Int = 0,
    createdAt: Long = 0L,
    updatedAt: Long = 0L,
) = LocationNodeEntity(
    id = id,
    parentId = parentId,
    name = name,
    kind = kind,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun photo(
    id: String = "p-1",
    ownerType: String = PhotoOwner.ITEM,
    ownerId: String = "i-1",
    filePath: String = "originals/a.jpg",
    thumbPath: String = "thumbs/a.jpg",
    sortOrder: Int = 0,
    createdAt: Long = 0L,
) = ItemPhotoEntity(
    id = id,
    ownerType = ownerType,
    ownerId = ownerId,
    filePath = filePath,
    thumbPath = thumbPath,
    sortOrder = sortOrder,
    createdAt = createdAt,
)
