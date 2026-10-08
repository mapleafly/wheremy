package com.wheremy.app.data.repo

import com.wheremy.app.data.db.PhotoDao
import com.wheremy.app.data.photos.PhotoStore

/**
 * 照片文件的唯一回收规则（`ARCHITECTURE.md` §5.2 / `RULES.md` §9.2）：
 *
 * **再没有任何照片行指着它，才把它从磁盘上删掉。**
 *
 * 调用契约：调用方必须**先删掉/改掉自己的行再进来**，否则 [PhotoDao.refsTo] 永远数到自己。
 * 物品侧与位置侧都必须走这一条，不许各自 `file.delete()`。
 */
internal suspend fun deleteUnreferencedPhotoFiles(
    photoDao: PhotoDao,
    photoStore: PhotoStore,
    paths: Collection<String>,
) {
    paths.filter { it.isNotBlank() }
        .distinct()
        .forEach { path ->
            if (photoDao.refsTo(path) == 0) photoStore.delete(path)
        }
}
