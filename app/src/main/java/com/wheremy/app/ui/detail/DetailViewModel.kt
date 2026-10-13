package com.wheremy.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.ItemSummary
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.domain.buildLocationTree
import com.wheremy.app.ui.PendingDelete
import com.wheremy.app.ui.UiFormat
import com.wheremy.app.ui.gallery.MoveOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 详情页里的一张照片：行 id（删的时候要）+ 绝对路径（画的时候要）——`ADR-26`。 */
data class DetailPhoto(val id: String, val path: String)

data class DetailUiState(
    val loading: Boolean = true,
    /** 「没有这条记录」——**与"读的时候抛了"分开**：那是两件事，不能都说成"没有" */
    val missing: Boolean = false,
    val failed: Boolean = false,
    val summary: ItemSummary? = null,
    /** 这件物品的全部照片，按 `sortOrder`（**第 0 张就是封面**，`RULES.md` §3.4） */
    val photos: List<DetailPhoto> = emptyList(),
    /** 正在等确认删掉的那一张（null = 没有弹确认框） */
    val pendingRemove: DetailPhoto? = null,
    val moveOptions: List<MoveOption> = emptyList(),
    val pendingDelete: PendingDelete? = null,
    val moving: Boolean = false,
    val deleted: Boolean = false,
    val notice: String? = null,
) {
    /** 照片的**绝对路径**（库里存的是 PhotoStore 的相对路径）。渲染用。 */
    val photoPaths: List<String> get() = photos.map { it.path }

    /** 还能不能再加一张（到 9 张上限就置灰，`RULES.md` §3.4 📐）。 */
    val canAddPhoto: Boolean get() = photos.size < ItemRepository.MAX_PHOTOS_PER_ITEM
}

/**
 * 详情页（`UI.md` §5）。
 *
 * 两条契约要记牢：
 * - 顶栏那三颗（编辑/搬到/删除）**全挂在 `summary != null`** 上——不许"按钮常驻、点了再报错"；
 * - **删完不 reload**：库里已经没有这一行，没有可 load 的东西；只置 `deleted = true`，退屏交给界面。
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val items: ItemRepository,
    private val locations: LocationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var itemId: String? = null

    /** 与 `GalleryViewModel` 同一条规矩：过期的刷新结果直接丢（W6）。 */
    private var loadSeq = 0

    fun load(id: String) {
        itemId = id
        val seq = ++loadSeq
        _state.update { it.copy(loading = true, missing = false, failed = false) }
        viewModelScope.launch {
            runCatching {
                val entity = items.get(id)
                if (entity == null) {
                    null
                } else {
                    val summary = items.summaries(listOf(entity)).single()
                    val photos = photosOf(id)
                    summary to photos
                }
            }.onSuccess { result ->
                if (seq != loadSeq) return@onSuccess
                _state.update {
                    it.copy(
                        loading = false,
                        missing = result == null,
                        summary = result?.first,
                        photos = result?.second.orEmpty(),
                    )
                }
            }.onFailure {
                if (seq != loadSeq) return@onFailure
                _state.update { it.copy(loading = false, failed = true, summary = null) }
            }
        }
    }

    fun askDelete() {
        val id = itemId ?: return
        viewModelScope.launch {
            val deletion = items.photoDeletionOf(id)
            _state.update {
                it.copy(pendingDelete = PendingDelete(id, UiFormat.deleteWarningOf(deletion)))
            }
        }
    }

    fun cancelDelete() = _state.update { it.copy(pendingDelete = null) }

    /** 删除成功只置 `deleted`；**不 reload**（库里已经没有这一行）。 */
    fun confirmDelete() {
        val id = itemId ?: return
        viewModelScope.launch {
            runCatching { items.delete(id) }
                .onSuccess { _state.update { it.copy(pendingDelete = null, deleted = true) } }
                .onFailure { t -> _state.update { it.copy(notice = t.message ?: "删除失败") } }
        }
    }

    fun consumeDeleted() = _state.update { it.copy(deleted = false) }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    // —— 多图（`FR-SRC-08` / `ADR-26` / `UI.md` §5.9）——

    /**
     * 给这件物品**再加一张**（外壳的 `deliverPhoto` 唯一收口把字节送到这里）。
     *
     * 到上限时仓储返回 `false`：**一个字节都没落盘**，这里只说一句 🗣「一件物品最多 9 张照片」。
     */
    fun addPhoto(jpeg: ByteArray) {
        val id = itemId ?: return
        viewModelScope.launch {
            runCatching { items.addPhotoToItem(id, jpeg) }
                .onSuccess { added ->
                    if (added) {
                        refreshAfterPhotoChange()
                    } else {
                        _state.update {
                            it.copy(
                                notice = "一件物品最多 ${ItemRepository.MAX_PHOTOS_PER_ITEM} 张照片",
                            )
                        }
                    }
                }
                .onFailure { t -> _state.update { it.copy(notice = t.message ?: "加照片失败") } }
        }
    }

    fun askRemovePhoto(photo: DetailPhoto) = _state.update { it.copy(pendingRemove = photo) }

    fun cancelRemovePhoto() = _state.update { it.copy(pendingRemove = null) }

    /** 删掉某一张：仓储那边**先删行再按引用计数回收文件**（别人还在用就留着）。 */
    fun confirmRemovePhoto() {
        val photo = _state.value.pendingRemove ?: return
        viewModelScope.launch {
            runCatching { items.removePhoto(photo.id) }
                .onSuccess { refreshAfterPhotoChange() }
                .onFailure { t ->
                    _state.update { it.copy(pendingRemove = null, notice = t.message ?: "删照片失败") }
                }
        }
    }

    /** 这件物品的全部照片（行 id + 绝对路径），按 `sortOrder`。 */
    private suspend fun photosOf(id: String): List<DetailPhoto> = items.photosOf(id).map { row ->
        DetailPhoto(row.id, items.photoStore.file(row.filePath).absolutePath)
    }

    /**
     * 加/删照片之后**静默重读**（照片与摘要），**不置 `loading`**——
     * 置了会让整屏闪一下"读取中"，而这里只是同一件物品的照片变了。
     */
    private fun refreshAfterPhotoChange() {
        val id = itemId ?: return
        viewModelScope.launch {
            val entity = items.get(id) ?: return@launch
            val summary = runCatching { items.summaries(listOf(entity)).single() }.getOrNull()
            val photos = runCatching { photosOf(id) }.getOrDefault(emptyList())
            _state.update {
                it.copy(summary = summary ?: it.summary, photos = photos, pendingRemove = null)
            }
        }
    }

    fun askMove() {
        viewModelScope.launch {
            val all = locations.all()
            val tree = buildLocationTree(all)
            val options = all.map { MoveOption(it.id, tree.breadcrumb(it.id)) }.sortedBy { it.label }
            _state.update { it.copy(moving = true, moveOptions = options) }
        }
    }

    fun cancelMove() = _state.update { it.copy(moving = false) }

    fun move(locationId: String?) {
        val id = itemId ?: return
        viewModelScope.launch {
            items.move(id, locationId)
            _state.update { it.copy(moving = false) }
            load(id) // 搬完要重读：位置行与标签都要刷新
        }
    }
}
