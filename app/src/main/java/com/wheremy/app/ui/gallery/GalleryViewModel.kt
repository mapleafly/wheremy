package com.wheremy.app.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheremy.app.data.repo.CATEGORIES
import com.wheremy.app.data.repo.Filters
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.ItemSummary
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.data.repo.SEASONS
import com.wheremy.app.data.repo.SortOrder
import com.wheremy.app.data.settings.SettingsRepository
import com.wheremy.app.ui.PendingDelete
import com.wheremy.app.ui.UiFormat
import com.wheremy.app.util.expireWindowEnd
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 「搬到」对话框的一个候选；默认那一条是「暂不归类，先进「待归类」」。 */
data class MoveOption(val id: String?, val label: String)

data class GalleryUiState(
    val query: String = "",
    val filters: Filters = Filters(),
    /** 待归类页 = 同一个屏幕的 `inboxMode`（`UI.md` §3.1） */
    val inboxMode: Boolean = false,
    val leadDays: Int = 3,
    val rows: List<ItemSummary> = emptyList(),
    val tags: List<String> = emptyList(),
    val moveOptions: List<MoveOption> = emptyList(),
    val locationOptions: List<MoveOption> = emptyList(),
    val menuFor: String? = null,
    val moveFor: String? = null,
    val pendingDelete: PendingDelete? = null,
    val loading: Boolean = true,
    /** 筛选芯片的生效词表（`FR-CFG-06`）：用户改过就用用户那份。 */
    val categories: List<String> = CATEGORIES,
    val seasons: List<String> = SEASONS,
    /** 这次是不是被 `LIMIT 300` 截了（FR-FND-07）：被截了就得说出来，不能假装这就是全部。 */
    val capped: Boolean = false,
) {
    /**
     * 空态第 1 句的判据（`UI.md` §3.4）：**有筛选条件时不能说"还没有物品"**。
     * 注意 `(!inboxMode && onlyInbox)` 那半句：待归类页自带的 `onlyInbox` 不算用户自己加的筛选。
     */
    val narrowing: Boolean
        get() = query.isNotBlank() ||
            filters.category != null ||
            filters.season != null ||
            filters.tag != null ||
            filters.expireUntil != null ||
            (!inboxMode && filters.onlyInbox)

    /** 筛选区第 2 层那颗芯片的文字：没选任何东西时是「更多筛选」。 */
    val moreFiltersLabel: String
        get() {
            val picked = buildList {
                if (filters.expireUntil != null) add("快到期/已过期")
                filters.season?.let { add("季节：$it") }
                filters.tag?.let { add("#$it") }
                // 排序不是"筛选"，但它是展开面板里选出来的——折起来时也得看得见（FR-FND-09）
                if (filters.sort != SortOrder.DEFAULT) add("排序：${filters.sort.label}")
            }
            return if (picked.isEmpty()) "更多筛选" else "更多筛选 · " + picked.joinToString(" ")
        }
}

/**
 * 照片墙 / 待归类（`UI.md` §3、`RULES.md` §10）。
 * 搜索是三段式的：SQL 取候选 → 谓词过滤 → 内存打分，仓库层一人做完，这里只管状态。
 */
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val items: ItemRepository,
    private val locations: LocationRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GalleryUiState())
    val state: StateFlow<GalleryUiState> = _state.asStateFlow()

    /**
     * 刷新的序号守卫（`DELTA.md` 的 W6：参照实现缺这个，我们从第一天就加）。
     *
     * 没有它就会出现：VM 构造时那次刷新与用户刚点的筛选**同时**在飞，
     * 先发的后落地 → 屏幕上是没筛过的结果，而芯片显示"已选"。
     */
    private var refreshSeq = 0

    init {
        refresh()
        // 筛选芯片读生效词表（FR-CFG-06）：改完词表回到照片墙就该看到新芯片
        viewModelScope.launch {
            settings.changes.collect { saved ->
                _state.update {
                    it.copy(
                        categories = saved.categories.ifEmpty { CATEGORIES },
                        seasons = saved.seasons.ifEmpty { SEASONS },
                    )
                }
            }
        }
    }

    fun refresh() {
        val seq = ++refreshSeq
        viewModelScope.launch {
            val current = _state.value
            val outcome = items.search(current.query, current.filters)
            val rows = items.summaries(outcome.rows).map { summary ->
                // 缩略图给界面的是绝对路径（库里存的是 PhotoStore 的相对路径）
                summary.copy(thumbPath = summary.thumbPath?.let { items.photoStore.file(it).absolutePath })
            }
            val tags = items.allTags()
            if (seq != refreshSeq) return@launch // 有更新的刷新在飞：这次的结果已经过期，丢掉
            _state.update { it.copy(rows = rows, tags = tags, loading = false, capped = outcome.capped) }
        }
    }

    /**
     * 待归类页就是同一个屏幕的 `inboxMode`（`UI.md` §3.1）。
     * ⚠️ 光把标题改掉不算：**收件箱谓词要真的开**（`locationId IS NULL OR status = 'DRAFT'`），
     * 否则"待归类"页会把全库都列出来——第一版就是这么错的。
     */
    fun setInboxMode(on: Boolean) {
        val current = _state.value
        if (current.inboxMode == on && current.filters.onlyInbox == on) return
        _state.update { it.copy(inboxMode = on, filters = it.filters.copy(onlyInbox = on)) }
        refresh()
    }

    /**
     * 从到期通知点进来（`RULES.md` §11.4）。
     *
     * 🔒 **清掉 query 与 category/season/tag**：用户从通知点进来要的是"这些到期的"，不是上一屏的筛选。
     */
    fun showExpiringSoonOnly() {
        _state.update {
            it.copy(
                query = "",
                filters = Filters(expireUntil = expireWindowEnd(it.leadDays)),
            )
        }
        refresh()
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        refresh()
    }

    fun toggleCategory(category: String) {
        _state.update {
            it.copy(filters = it.filters.copy(category = if (it.filters.category == category) null else category))
        }
        refresh()
    }

    /**
     * 结果排序（`FR-FND-09`）。**只改顺序、不改集合**，也不动查询与筛选。
     * 再点一次当前那颗 = 回到「默认」。
     */
    fun setSort(sort: SortOrder) {
        val next = if (_state.value.filters.sort == sort) SortOrder.DEFAULT else sort
        _state.update { it.copy(filters = it.filters.copy(sort = next)) }
        refresh()
    }

    fun toggleSeason(season: String) {
        _state.update {
            it.copy(filters = it.filters.copy(season = if (it.filters.season == season) null else season))
        }
        refresh()
    }

    fun toggleTag(tag: String) {
        _state.update {
            it.copy(filters = it.filters.copy(tag = if (it.filters.tag == tag) null else tag))
        }
        refresh()
    }

    /** 「快到期/已过期」：上界走唯一来源 [expireWindowEnd]（已过期的同样落进来）。 */
    fun toggleExpiring() {
        _state.update {
            val on = it.filters.expireUntil != null
            it.copy(
                filters = it.filters.copy(
                    expireUntil = if (on) null else expireWindowEnd(it.leadDays),
                ),
            )
        }
        refresh()
    }

    fun toggleOnlyInbox() {
        _state.update { it.copy(filters = it.filters.copy(onlyInbox = !it.filters.onlyInbox)) }
        refresh()
    }

    fun clearFilters() {
        _state.update { it.copy(query = "", filters = Filters(onlyInbox = it.inboxMode)) }
        refresh()
    }

    // —— 单元菜单（⋮）——

    fun openMenu(itemId: String) = _state.update { it.copy(menuFor = itemId) }

    fun closeMenu() = _state.update { it.copy(menuFor = null, moveFor = null) }

    /**
     * 删除：**先问数据层"这件的照片还有谁在用"，再决定说哪句话**（`RULES.md` §9.3）。
     * 那句话与 id 一起存进状态，弹框期间列表刷新也不会说错。
     */
    fun askDelete(itemId: String) {
        viewModelScope.launch {
            val deletion = items.photoDeletionOf(itemId)
            _state.update {
                it.copy(
                    menuFor = null,
                    pendingDelete = PendingDelete(itemId, UiFormat.deleteWarningOf(deletion)),
                )
            }
        }
    }

    fun confirmDelete() {
        val pending = _state.value.pendingDelete ?: return
        viewModelScope.launch {
            items.delete(pending.itemId)
            _state.update { it.copy(pendingDelete = null) }
            refresh()
        }
    }

    fun cancelDelete() = _state.update { it.copy(pendingDelete = null) }

    fun askMove(itemId: String) {
        viewModelScope.launch {
            val all = locations.all()
            val tree = com.wheremy.app.domain.buildLocationTree(all)
            val options = all.map { MoveOption(it.id, tree.breadcrumb(it.id)) }.sortedBy { it.label }
            _state.update {
                it.copy(menuFor = null, moveFor = itemId, locationOptions = options)
            }
        }
    }

    fun cancelMove() = _state.update { it.copy(moveFor = null) }

    /** 搬到：交给仓库（它自己判断"有名字就顺手确认"）。 */
    fun move(itemId: String, locationId: String?) {
        viewModelScope.launch {
            items.move(itemId, locationId)
            _state.update { it.copy(moveFor = null) }
            refresh()
        }
    }
}
