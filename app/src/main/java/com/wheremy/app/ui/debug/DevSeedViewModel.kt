package com.wheremy.app.ui.debug

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheremy.app.data.repo.ItemDraft
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.util.localStartOfDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import javax.inject.Inject

/**
 * 开发期的演示数据（`--es dev seed`）：造几件**合成照片**的物品，让照片墙有东西可看。
 *
 * 为什么不直接用相册里的照片：那是用户的私人数据，一旦进了库就有了拷贝与派生文件。
 * 演示数据用固定名字 + 一颗 `自检种子` 标签标记，可以一键清掉（[clearSeeds]）。
 */
@HiltViewModel
class DevSeedViewModel @Inject constructor(
    private val items: ItemRepository,
    private val locations: LocationRepository,
) : ViewModel() {

    private val _status = MutableStateFlow("准备生成演示数据…")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    fun seed() {
        viewModelScope.launch {
            try {
                clearSeeds()
                val today = LocalDate.now()
                // 幂等：已经有的位置直接复用。**不能直接 add**——同名同级会被拒，
                // 而那时 clearSeeds() 已经把上一次的种子删了，结果就是"删了旧的、没建新的"（踩过一次）
                val home = ensureLocation(parentId = null, name = "家", kind = "房间")
                val wardrobe = ensureLocation(parentId = home, name = "衣柜", kind = "衣柜")
                val drawer = ensureLocation(parentId = home, name = "抽屉", kind = "抽屉")

                val seeds = listOf(
                    Seed("深灰羽绒外套", "衣物", "冬", "深灰", "Uniqlo", wardrobe, null),
                    Seed("电钻", "工具", null, "蓝", "博世", drawer, null),
                    Seed("牛奶", "食品", null, "白", null, null, today.plusDays(2)),
                    Seed("护照", "文档", null, "暗红", null, drawer, today.plusDays(400)),
                    Seed("过期电池", "数码", null, "银", null, null, today.minusDays(3)),
                )

                seeds.forEachIndexed { index, seed ->
                    val id = items.newDraft(jpegOf(seed.name, index))
                    items.confirm(
                        ItemDraft(
                            id = id,
                            name = seed.name,
                            category = seed.category,
                            seasons = listOfNotNull(seed.season),
                            color = seed.color,
                            brand = seed.brand,
                            locationId = seed.locationId,
                            expiresAt = seed.expiresAt?.let { localStartOfDay(it) },
                            expireKind = if (seed.expiresAt != null) "保质期" else null,
                            tags = listOf("自检种子"),
                        ),
                    )
                }
                _status.value = "已生成 ${seeds.size} 件演示物品（都很干净：合成照片 + 自检种子 标签）"
                _done.value = true
            } catch (t: Throwable) {
                _status.value = "生成失败：${t::class.simpleName}: ${t.message}"
            }
        }
    }

    /** 把之前的种子清掉（按标签找，不动用户的任何一件）。 */
    private suspend fun clearSeeds() {
        items.rankedSearch("", com.wheremy.app.data.repo.Filters(tag = "自检种子")).forEach {
            items.delete(it.id)
        }
    }

    /** 已经有同名同级位置就复用，避免"同级重名"把 seed 跑挂。 */
    private suspend fun ensureLocation(parentId: String?, name: String, kind: String?): String =
        locations.all().firstOrNull { it.parentId == parentId && it.name == name }?.id
            ?: locations.add(parentId = parentId, name = name, kind = kind)

    private data class Seed(
        val name: String,
        val category: String?,
        val season: String?,
        val color: String?,
        val brand: String?,
        val locationId: String?,
        val expiresAt: LocalDate?,
    )

    /** 造一张有辨识度的合成 JPEG：底色随名字变，中间写名字的前两个字。 */
    private fun jpegOf(name: String, index: Int): ByteArray {
        val width = 800
        val height = 600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val palette = intArrayOf(
            Color.rgb(0x2E, 0x6B, 0x64),
            Color.rgb(0xB5, 0x7A, 0x1F),
            Color.rgb(0x7A, 0x3E, 0x3E),
            Color.rgb(0x3E, 0x4E, 0x7A),
            Color.rgb(0x4E, 0x6B, 0x3E),
        )
        Canvas(bitmap).apply {
            drawColor(palette[index % palette.size])
            drawText(
                name.take(3),
                width / 2f - 150f,
                height / 2f + 20f,
                Paint().apply {
                    color = Color.WHITE
                    textSize = 96f
                    isAntiAlias = true
                },
            )
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        return out.toByteArray()
    }
}
