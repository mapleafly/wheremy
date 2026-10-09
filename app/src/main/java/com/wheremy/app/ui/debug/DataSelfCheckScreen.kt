package com.wheremy.app.ui.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.exifinterface.media.ExifInterface
import androidx.room3.Room
import com.wheremy.app.data.ai.AnalysisTrace
import com.wheremy.app.data.ai.ApiPhotoAnalyzer
import com.wheremy.app.data.db.ItemEntity
import com.wheremy.app.data.db.ItemPhotoEntity
import com.wheremy.app.data.db.ItemStatus
import com.wheremy.app.data.db.ItemTagEntity
import com.wheremy.app.data.db.LocationNodeEntity
import com.wheremy.app.data.db.PhotoOwner
import com.wheremy.app.data.db.SearchTextBuilder
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.data.settings.SettingsRepository
import com.wheremy.app.domain.CaptureFlow
import com.wheremy.app.ui.review.ReviewUiState
import com.wheremy.app.ui.review.ReviewViewModel
import com.wheremy.app.ui.theme.WheremySpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * P1 的数据层自检屏（**开发期脚手架，P2 起会被真正的照片墙取代**）。
 *
 * 为什么要在真机上跑一遍：`RULES.md` 的 P1 出口判据里有一条是"EXIF 真机正确"。
 * Robolectric 的 `BitmapFactory` 与 SQLite 都是影子实现，方向烤制、外键约束、中文 LIKE
 * 这三件事只有在真机的 Android 运行时上跑过才算数。
 *
 * 自检**不碰用户数据**：数据库用独立的 `selfcheck.db`、照片写进 `cacheDir`，跑完删掉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSelfCheckScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf(listOf("自检中…")) }

    LaunchedEffect(Unit) {
        lines = runCatching { runDataSelfCheck(context) }
            .getOrElse { listOf("❌ 自检崩了：${it::class.simpleName}: ${it.message}") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据层自检（P1）") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(WheremySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WheremySpacing.sm),
        ) {
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("selfcheck_line"),
                )
            }
        }
    }
}

private suspend fun runDataSelfCheck(context: Context): List<String> = withContext(Dispatchers.IO) {
    val out = mutableListOf<String>()
    val now = System.currentTimeMillis()

    out += runP2SelfCheck(context)

    // —— ① 真机 SQLite：中文搜索 / 标签筛选 / 引用计数（用独立库文件，跑完删掉）——
    val selfCheckDbFile = context.getDatabasePath("selfcheck.db")
    val db = Room.databaseBuilder(context, WheremyDatabase::class.java, "selfcheck.db").build()
    try {
        val locationDao = db.locationDao()
        val itemDao = db.itemDao()
        val photoDao = db.photoDao()

        locationDao.upsert(
            LocationNodeEntity(
                id = "sc-loc",
                parentId = null,
                name = "自检抽屉",
                kind = "抽屉",
                sortOrder = 0,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val coatName = "深灰羽绒外套"
        itemDao.upsert(
            ItemEntity(
                id = "sc-item",
                name = coatName,
                category = null,
                seasons = null,
                color = "深灰",
                brand = "Uniqlo",
                material = "羽绒",
                aliases = null,
                description = null,
                notes = null,
                quantity = 1,
                locationId = "sc-loc",
                status = ItemStatus.CONFIRMED,
                expiresAt = null,
                expireKind = null,
                expireNotifiedAt = null,
                searchText = SearchTextBuilder.build(
                    name = coatName,
                    brand = "Uniqlo",
                    color = "深灰",
                    material = "羽绒",
                    aliases = null,
                    description = null,
                    notes = null,
                    tags = listOf("冬装"),
                ),
                createdAt = now,
                updatedAt = now,
                lastMovedAt = null,
            ),
        )
        itemDao.clearTags("sc-item")
        itemDao.insertTags(listOf(ItemTagEntity(itemId = "sc-item", tag = "冬装")))

        val hits = itemDao.searchLike("%外套%")
        out += if (hits.any { it.id == "sc-item" }) {
            "✅ 中文搜索命中「外套」：${hits.size} 条（真机 SQLite）"
        } else {
            "❌ 中文搜索没命中"
        }
        out += if (itemDao.itemIdsWithTag("冬装").contains("sc-item")) {
            "✅ 标签筛选命中（大小写不敏感）"
        } else {
            "❌ 标签筛选没命中"
        }
        out += if (itemDao.inbox().any { it.id == "sc-item" }) {
            "❌ 已归位物品不该出现在待归类"
        } else {
            "✅ 归位后不在待归类里"
        }

        // 引用计数：两条行共用一份字节 → 删掉一条后文件仍不能被回收
        listOf("sc-owner-1", "sc-owner-2").forEachIndexed { index, owner ->
            photoDao.upsert(
                ItemPhotoEntity(
                    id = "sc-photo-$index",
                    ownerType = PhotoOwner.ITEM,
                    ownerId = owner,
                    filePath = "originals/selfcheck.jpg",
                    thumbPath = "thumbs/selfcheck.jpg",
                    sortOrder = 0,
                    createdAt = now,
                ),
            )
        }
        val refsBefore = photoDao.refsTo("originals/selfcheck.jpg")
        photoDao.deleteForOwner(PhotoOwner.ITEM, "sc-owner-1")
        val refsAfter = photoDao.refsTo("originals/selfcheck.jpg")
        val others = photoDao.otherOwnersOnPath(PhotoOwner.ITEM, "sc-owner-2", "originals/selfcheck.jpg")
        out += if (refsBefore == 2 && refsAfter == 1) {
            "✅ 引用计数：共用时 $refsBefore → 删一条后 $refsAfter"
        } else {
            "❌ 引用计数不对：$refsBefore → $refsAfter"
        }
        out += "✅ 删除前能看到除自己之外还有 ${others.sumOf { it.owners }} 个主人指着这份字节"
    } finally {
        db.close()
        selfCheckDbFile.delete()
    }

    // —— ② 真机图片管线：EXIF 方向烤进像素 ——
    val store = PhotoStore(File(context.cacheDir, "selfcheck-photos"))
    val written = mutableListOf<String>()
    try {
        val jpeg = jpegWithOrientation(800, 400, ExifInterface.ORIENTATION_ROTATE_90)
        val stored = store.write(jpeg)
        written += stored.originalPath
        written += stored.thumbPath

        val baked = BitmapFactory.decodeFile(store.file(stored.originalPath).absolutePath)
        out += if (baked != null && baked.width == 400 && baked.height == 800) {
            "✅ EXIF 方向已烤进像素：800×400 存出来是 ${baked.width}×${baked.height}"
        } else {
            "❌ EXIF 方向没烤进去：${baked?.width}×${baked?.height}"
        }
        baked?.recycle()

        val thumb = BitmapFactory.decodeFile(store.file(stored.thumbPath).absolutePath)
        val thumbEdge = maxOf(thumb?.width ?: 0, thumb?.height ?: 0)
        out += if (thumbEdge in 1..PhotoStore.THUMB_EDGE) {
            "✅ 缩略图长边 $thumbEdge ≤ ${PhotoStore.THUMB_EDGE}"
        } else {
            "❌ 缩略图长边不对：$thumbEdge"
        }
        thumb?.recycle()

        val analysis = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(stored.analysisJpeg, 0, stored.analysisJpeg.size, analysis)
        out += "✅ 分析图只在内存里：${analysis.outWidth}×${analysis.outHeight}，${stored.analysisJpeg.size / 1024} KB"
    } finally {
        written.forEach { store.delete(it) }
        File(context.cacheDir, "selfcheck-photos").deleteRecursively()
    }

    out += "— 以上全部在真机上跑完，用户数据未被触碰（独立库 + cacheDir，已清理）"
    out
}

/**
 * P2 闭环自检（**用合成照片，不碰相册里的私人照片**）：
 * 选图落库 → 确认页状态机 → 保存/稍后处理 → 待归类 → 空壳回收，全部在真机运行时上跑。
 * 用独立库与 `cacheDir`，跑完把行与文件都清掉。
 */
private suspend fun runP2SelfCheck(context: Context): List<String> {
    val out = mutableListOf<String>()
    val dbFile = context.getDatabasePath("selfcheck-p2.db")
    val db = Room.databaseBuilder(context, WheremyDatabase::class.java, "selfcheck-p2.db").build()
    val store = PhotoStore(File(context.cacheDir, "selfcheck-p2-photos"))
    val items = ItemRepository(db, store)
    val locations = LocationRepository(db, store)
    val flow = CaptureFlow(items)
    // 自检**不连网**：配置给 null，于是 AI 那条路直接走 NOTHING_FOUND/NO_CONFIG 分支
    val selfCheckSettings = SettingsRepository(MemoryPreferencesStore())
    val analyzer = ApiPhotoAnalyzer({ null }, AnalysisTrace(File(context.cacheDir, "selfcheck-ai-trace")))

    try {
        // ① 选图那一刻就落库
        val itemId = flow.capture(jpegWithOrientation(800, 400, ExifInterface.ORIENTATION_NORMAL)) {}.itemId
        val stored = items.get(itemId)
        val photos = items.photosOf(itemId)
        out += if (stored != null && stored.status == ItemStatus.DRAFT && photos.size == 1) {
            "✅ 选图即落库：DRAFT + 照片行（${photos.single().thumbPath}）"
        } else {
            "❌ 选图没落库：item=${stored?.status} photos=${photos.size}"
        }
        out += if (items.inbox().any { it.id == itemId }) "✅ 未保存的件出现在待归类" else "❌ 待归类里没有它"

        // ② 确认页状态机：改名 → 保存
        val vm = ReviewViewModel(items, locations, flow, analyzer, selfCheckSettings)
        vm.start(itemId)
        awaitState(vm) { it.current != null }
        val canSaveBefore = vm.state.value.canSave
        vm.setName("自检电钻")
        vm.setQuantity(2)
        vm.save()
        awaitState(vm) { it.finished }
        val confirmed = items.get(itemId)
        out += if (canSaveBefore && confirmed?.status == ItemStatus.CONFIRMED && confirmed.name == "自检电钻") {
            "✅ 确认页保存：DRAFT → CONFIRMED，名称与数量写进去了（searchText=${confirmed.searchText.take(20)}）"
        } else {
            "❌ 保存没生效：status=${confirmed?.status} name=${confirmed?.name}"
        }
        // 确认 ≠ 已归位：没选位置时它仍在待归类（谓词是「没位置 或 还是草稿」）
        out += if (items.inbox().any { it.id == itemId }) {
            "✅ 确认但没选位置：仍在待归类（确认 ≠ 已归位）"
        } else {
            "❌ 没选位置却离开了待归类：谓词写错了"
        }

        // 选了位置再确认，这才该离开待归类
        val home = locations.add(parentId = null, name = "自检家", kind = "房间")
        val placedId = flow.capture(jpegWithOrientation(700, 350, ExifInterface.ORIENTATION_NORMAL)) {}.itemId
        val vmPlaced = ReviewViewModel(items, locations, flow, analyzer, selfCheckSettings)
        vmPlaced.start(placedId)
        awaitState(vmPlaced) { it.current != null }
        vmPlaced.setName("自检电钻二号")
        vmPlaced.setLocation(home)
        vmPlaced.save()
        awaitState(vmPlaced) { it.finished }
        val placed = items.get(placedId)
        out += if (placed?.status == ItemStatus.CONFIRMED && items.inbox().none { it.id == placedId }) {
            "✅ 选了位置再确认：离开待归类（${items.summaries(listOf(placed)).single().locationPath}）"
        } else {
            "❌ 归位后仍在待归类：status=${placed?.status} location=${placed?.locationId}"
        }

        // ③ 稍后处理：写字段但留在待归类
        val laterId = flow.capture(jpegWithOrientation(600, 300, ExifInterface.ORIENTATION_NORMAL)) {}.itemId
        val vm2 = ReviewViewModel(items, locations, flow, analyzer, selfCheckSettings)
        vm2.start(laterId)
        awaitState(vm2) { it.current != null }
        vm2.setName("自检护照")
        vm2.keepForLater()
        awaitState(vm2) { it.finished }
        val later = items.get(laterId)
        out +=
            if (later?.status == ItemStatus.DRAFT && later.name == "自检护照" && items.inbox().any { it.id == laterId }) {
                "✅ 稍后处理：字段写了、状态还是 DRAFT、留在待归类"
            } else {
                "❌ 稍后处理不对：status=${later?.status} name=${later?.name}"
            }

        // ④ 空壳回收：什么都没动就退出 → 行与文件都不留
        val shellId = flow.capture(jpegWithOrientation(500, 500, ExifInterface.ORIENTATION_NORMAL)) {}.itemId
        val shellPath = items.photosOf(shellId).single().filePath
        val vm3 = ReviewViewModel(items, locations, flow, analyzer, selfCheckSettings)
        vm3.start(shellId)
        awaitState(vm3) { it.current != null }
        vm3.requestExit()
        awaitState(vm3) { it.finished }
        out += if (items.get(shellId) == null && !store.file(shellPath).exists()) {
            "✅ 空壳回收：行与照片文件都清掉了（引用计数那条规则）"
        } else {
            "❌ 空壳没清干净：行=${items.get(shellId) != null} 文件=${store.file(shellPath).exists()}"
        }
    } finally {
        db.close()
        dbFile.delete()
        File(context.cacheDir, "selfcheck-p2-photos").deleteRecursively()
    }
    return out
}

/** 等 VM 的异步动作落地（真机上 Room 的查询在别的线程上完成）。 */
private suspend fun awaitState(vm: ReviewViewModel, predicate: (ReviewUiState) -> Boolean) {
    repeat(200) {
        if (predicate(vm.state.value)) return
        kotlinx.coroutines.delay(25)
    }
}

/** 造一张带方向标签的 JPEG：左半边涂红，方便人眼分辨朝向。 */
private fun jpegWithOrientation(width: Int, height: Int, orientation: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
        drawColor(Color.WHITE)
        drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = Color.RED })
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    bitmap.recycle()

    val carrier = File.createTempFile("selfcheck", ".jpg")
    carrier.writeBytes(out.toByteArray())
    ExifInterface(carrier.absolutePath).apply {
        setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        saveAttributes()
    }
    val bytes = carrier.readBytes()
    carrier.delete()
    return bytes
}

/** 自检用的内存版 DataStore：**不碰**真偏好文件（跟测试里那个同一个理由，见 F23）。 */
private class MemoryPreferencesStore :
    androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(
        androidx.datastore.preferences.core.emptyPreferences(),
    )
    override val data: kotlinx.coroutines.flow.Flow<androidx.datastore.preferences.core.Preferences> = state
    override suspend fun updateData(
        transform: suspend (
            androidx.datastore.preferences.core.Preferences,
        ) -> androidx.datastore.preferences.core.Preferences,
    ): androidx.datastore.preferences.core.Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
