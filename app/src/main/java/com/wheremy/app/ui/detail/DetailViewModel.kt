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

data class DetailUiState(
    val loading: Boolean = true,
    /** 「没有这条记录」——**与"读的时候抛了"分开**：那是两件事，不能都说成"没有" */
    val missing: Boolean = false,
    val failed: Boolean = false,
    val summary: ItemSummary? = null,
    /** 照片的**绝对路径**（库里存的是 PhotoStore 的相对路径） */
    val photoPaths: List<String> = emptyList(),
    val moveOptions: List<MoveOption> = emptyList(),
    val pendingDelete: PendingDelete? = null,
    val moving: Boolean = false,
    val deleted: Boolean = false,
    val notice: String? = null,
)

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
                    val photos = items.photosOf(id).map { items.photoStore.file(it.filePath).absolutePath }
                    summary to photos
                }
            }.onSuccess { result ->
                if (seq != loadSeq) return@onSuccess
                _state.update {
                    it.copy(
                        loading = false,
                        missing = result == null,
                        summary = result?.first,
                        photoPaths = result?.second.orEmpty(),
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
