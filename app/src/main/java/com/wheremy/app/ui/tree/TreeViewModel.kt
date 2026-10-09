package com.wheremy.app.ui.tree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.ItemSummary
import com.wheremy.app.data.repo.LOCATION_KINDS
import com.wheremy.app.data.repo.LocationNotEmptyException
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.data.settings.SettingsRepository
import com.wheremy.app.domain.LocationNode
import com.wheremy.app.domain.buildLocationTree
import com.wheremy.app.ui.gallery.MoveOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 一行子位置要显示的全部（`UI.md` §4.3）。 */
data class LocationRowUi(
    val node: LocationNode,
    val directCount: Int,
    val subtreeCount: Int,
    val hasChildren: Boolean,
    /** **只有真记过照片才给**——留个灰框等于在说"这里有过一张图" */
    val thumbPath: String?,
)

data class TreeUiState(
    val loading: Boolean = true,
    val currentParentId: String? = null,
    val breadcrumb: List<String> = emptyList(),
    val rows: List<LocationRowUi> = emptyList(),
    val itemsHere: List<ItemSummary> = emptyList(),
    /** 当前位置自己的那张参照图（绝对路径） */
    val locationPhotoPath: String? = null,
    val kinds: List<String> = LOCATION_KINDS,
    val menuFor: String? = null,
    val moveFor: String? = null,
    val moveOptions: List<MoveOption> = emptyList(),
    /** 新建（`editId == null`）或编辑某个节点 */
    val editOpen: Boolean = false,
    val editId: String? = null,
    val pendingDeleteId: String? = null,
    val pendingDeleteName: String? = null,
    val notice: String? = null,
) {
    val isRoot: Boolean get() = currentParentId == null

    /** 空态两句（`UI.md` §4.2）：根层与子层不一样。 */
    val emptyText: String
        get() = if (isRoot) {
            "还没有位置。点右上角「新建」建第一个，例如「家」「主卧」「衣柜」。"
        } else {
            "这个位置还是空的，点「新建」加个子位置，或去照片墙录入物品。"
        }

    val isEmpty: Boolean get() = rows.isEmpty() && itemsHere.isEmpty()
}

/**
 * 位置页（`UI.md` §4）。
 * 树规则全在 `LocationRepository`/`LocationTree` 里（同级重排、防环、删除门槛），这里只管状态。
 */
@HiltViewModel
class TreeViewModel @Inject constructor(
    private val locations: LocationRepository,
    private val items: ItemRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TreeUiState())
    val state: StateFlow<TreeUiState> = _state.asStateFlow()

    private var refreshSeq = 0

    init {
        refresh()
        // 位置类型的候选读生效词表（FR-CFG-06）
        viewModelScope.launch {
            settings.changes.collect { saved ->
                _state.update { it.copy(kinds = saved.locationKinds.ifEmpty { LOCATION_KINDS }) }
            }
        }
    }

    /** 每次进入本页（含从详情页返回）都要刷新：详情页可能刚搬走或删掉物品。 */
    fun refresh() {
        val seq = ++refreshSeq
        val parentId = _state.value.currentParentId
        viewModelScope.launch {
            val all = locations.all()
            val tree = buildLocationTree(all)
            val children = tree.childrenOf(parentId)
            val rows = children.map { node ->
                val direct = locations.itemCount(node.id)
                val subtree = locations.subtreeItemCount(node.id)
                LocationRowUi(
                    node = node,
                    directCount = direct,
                    subtreeCount = subtree,
                    hasChildren = tree.childrenOf(node.id).isNotEmpty(),
                    thumbPath = locations.photoOf(node.id)
                        ?.thumbPath
                        ?.let { locations.photoStore.file(it).absolutePath },
                )
            }
            val itemsHere = parentId?.let { items.atLocation(it) } ?: emptyList()
            val summaries = items.summaries(itemsHere).map { summary ->
                summary.copy(thumbPath = summary.thumbPath?.let { items.photoStore.file(it).absolutePath })
            }
            val locationPhoto = parentId?.let { locations.photoOf(it) }
                ?.filePath
                ?.let { locations.photoStore.file(it).absolutePath }
            if (seq != refreshSeq) return@launch
            _state.update {
                it.copy(
                    loading = false,
                    breadcrumb = parentId?.let { id -> tree.pathOf(id).map { node -> node.name } }.orEmpty(),
                    rows = rows,
                    itemsHere = summaries,
                    locationPhotoPath = locationPhoto,
                )
            }
        }
    }

    fun open(parentId: String?) {
        _state.update { it.copy(currentParentId = parentId, loading = true) }
        refresh()
    }

    /** 「上一层」= 回父节点（面包屑本身就是标题，所以退一层只有这颗按钮与系统返回两条路）。 */
    fun up() {
        val parentId = _state.value.currentParentId ?: return
        viewModelScope.launch {
            val all = locations.all()
            val parent = all.firstOrNull { it.id == parentId }?.parentId
            open(parent)
        }
    }

    fun openMenu(id: String) = _state.update { it.copy(menuFor = id) }

    fun closeMenu() = _state.update { it.copy(menuFor = null) }

    /** 同级上移/下移：**边界那两行不给方向按钮**（点了什么都没发生比少一个按钮更糟）。 */
    fun shift(id: String, up: Boolean) {
        viewModelScope.launch {
            locations.shiftWithinParent(id, up)
            _state.update { it.copy(menuFor = null) }
            refresh()
        }
    }

    // —— 新建 / 编辑 ——

    fun startCreate() = _state.update { it.copy(editOpen = true, editId = null) }

    fun startEdit(id: String) = _state.update { it.copy(editOpen = true, editId = id, menuFor = null) }

    fun cancelEdit() = _state.update { it.copy(editOpen = false, editId = null) }

    /**
     * 空名字**按钮就是按不动的**（`UI.md` §4.7 的 ⚠️：参照实现两处做法不同，重做时取后者）。
     */
    fun saveEdit(name: String, kind: String?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val editId = _state.value.editId
            runCatching {
                if (editId == null) {
                    locations.add(parentId = _state.value.currentParentId, name = name, kind = kind)
                } else {
                    locations.update(editId, name = name, kind = kind)
                }
            }.onSuccess {
                _state.update { it.copy(editOpen = false, editId = null) }
                refresh()
            }.onFailure { t -> _state.update { it.copy(notice = t.message ?: "保存失败") } }
        }
    }

    // —— 删除 ——

    fun askDelete(id: String) {
        val name = _state.value.rows.firstOrNull { it.node.id == id }?.node?.name
            ?: _state.value.breadcrumb.lastOrNull()
            ?: return
        _state.update { it.copy(menuFor = null, pendingDeleteId = id, pendingDeleteName = name) }
    }

    fun cancelDelete() = _state.update { it.copy(pendingDeleteId = null, pendingDeleteName = null) }

    fun confirmDelete() {
        val id = _state.value.pendingDeleteId ?: return
        viewModelScope.launch {
            runCatching { locations.delete(id) }
                .onSuccess {
                    _state.update { it.copy(pendingDeleteId = null, pendingDeleteName = null) }
                    refresh()
                }
                .onFailure { t ->
                    // 门槛在数据层；这里是"刚好被人搬进东西"的兜底，说清还剩什么
                    val message = when (t) {
                        is LocationNotEmptyException ->
                            "里面还有 ${t.childCount} 个子位置、${t.itemCount} 件物品，先搬走再删。"
                        else -> t.message ?: "删除失败"
                    }
                    _state.update { it.copy(pendingDeleteId = null, pendingDeleteName = null, notice = message) }
                }
        }
    }

    // —— 移动到… ——

    fun askMove(id: String) {
        viewModelScope.launch {
            val all = locations.all()
            val tree = buildLocationTree(all)
            val self = tree.descendantIds(id)
            val options = all
                .filter { it.id !in self } // 不能移到它自己或它的子位置下面
                .map { MoveOption(it.id, tree.breadcrumb(it.id)) }
                .sortedBy { it.label }
            _state.update { it.copy(menuFor = null, moveFor = id, moveOptions = options) }
        }
    }

    fun cancelMove() = _state.update { it.copy(moveFor = null) }

    fun move(id: String, newParentId: String?) {
        viewModelScope.launch {
            runCatching { locations.changeParent(id, newParentId) }
                .onSuccess {
                    _state.update { it.copy(moveFor = null) }
                    refresh()
                }
                .onFailure { t -> _state.update { it.copy(moveFor = null, notice = t.message ?: "移动失败") } }
        }
    }

    // —— 位置照片 ——

    fun setPhoto(jpeg: ByteArray) {
        val id = _state.value.currentParentId ?: return
        viewModelScope.launch {
            runCatching { locations.setPhoto(id, jpeg) }
                .onSuccess { refresh() }
                .onFailure { t -> _state.update { it.copy(notice = t.message ?: "照片没存下来") } }
        }
    }

    fun clearPhoto() {
        val id = _state.value.currentParentId ?: return
        viewModelScope.launch {
            locations.clearPhoto(id)
            refresh()
        }
    }

    fun consumeNotice() = _state.update { it.copy(notice = null) }
}
