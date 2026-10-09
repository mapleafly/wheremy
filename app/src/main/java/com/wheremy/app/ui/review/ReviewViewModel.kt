package com.wheremy.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheremy.app.data.ai.AnalysisOutcome
import com.wheremy.app.data.ai.ApiPhotoAnalyzer
import com.wheremy.app.data.ai.FailureReason
import com.wheremy.app.data.ai.Prompt
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.domain.CaptureFlow
import com.wheremy.app.domain.LocationPath
import com.wheremy.app.domain.SuggestLocationUseCase
import com.wheremy.app.domain.buildLocationTree
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 确认页的状态机（`RULES.md` §1.3–§1.5、§6）。
 *
 * 四本账（`notStored`/`leftBehind`/`alreadySaved`/`unsaved`）在 `ReviewUiState` 上，
 * **不许合并**——合成一个数就等于对着已经确认的物品说"它还要去待归类"。
 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val items: ItemRepository,
    private val locations: LocationRepository,
    private val captureFlow: CaptureFlow,
    private val analyzer: ApiPhotoAnalyzer,
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    private val _locationOptions = MutableStateFlow<List<LocationOption>>(emptyList())

    /** 「选择位置」对话框的候选：全部节点（含根——人可以把它直接放进「家」）。 */
    val locationOptions: StateFlow<List<LocationOption>> = _locationOptions.asStateFlow()

    fun loadLocationOptions() {
        viewModelScope.launch {
            val all = locations.all()
            val tree = buildLocationTree(all)
            _locationOptions.value = all
                .map { LocationOption(it.id, tree.breadcrumb(it.id)) }
                .sortedBy { it.path }
        }
    }

    /** 打开这一屏：把刚落库的那件读成槽位，然后**顺手分析一次**（`UI.md` §6.4）。 */
    fun start(itemId: String) {
        viewModelScope.launch {
            val draft = items.draftOf(itemId)
            val slot = ReviewSlot(
                itemId = itemId,
                draft = draft,
                // 存**绝对路径**给界面用：缩略图在 PhotoStore 的相对路径下
                thumbPath = items.thumbPathOf(itemId)?.let { items.photoStore.file(it).absolutePath },
                locationPath = locationPathOf(draft.locationId),
            )
            _state.update { it.copy(slots = listOf(slot), selected = 0, finished = false) }
            analyze(itemId)
        }
    }

    /** 「重新分析」：与进门那次走**同一段**逻辑（照片不在库里就说明下一步）。 */
    fun retryAnalysis() {
        val itemId = _state.value.current?.itemId ?: return
        viewModelScope.launch {
            if (items.photoBytesOf(itemId) == null && items.takePendingAnalysis() == null) {
                _state.update { it.copy(analyzing = false, failure = null, notice = "找不到这张照片了，重新选一张试试") }
                return@launch
            }
            analyze(itemId)
        }
    }

    /**
     * 分析一次并回填（`RULES.md` §5.2–§5.6、§13）。
     *
     * 三件事按顺序做：
     * 1. 提示词**算一次**，发出去的那份与留痕里的那份是同一串字；
     * 2. 位置建议只在空着的时候填，三句话由界面用 `guessOutcome` 说（§5.3/§5.4）；
     * 3. AI 回填**不标记** `touched`——否则一次回填会把整表标成"用户动过"（§5.2）。
     */
    private suspend fun analyze(itemId: String) {
        val jpeg = items.takePendingAnalysis() ?: items.photoBytesOf(itemId)
        if (jpeg == null) {
            _state.update { it.copy(analyzing = false, notice = "找不到这张照片了，重新选一张试试") }
            return
        }
        _state.update {
            it.copy(analyzing = true, failure = null, guess = null, guessApplied = false, placeAlreadyPicked = false)
        }

        val all = locations.all()
        val tree = buildLocationTree(all)
        val selectable = all
            .filter { it.parentId != null }
            .map { node ->
                LocationPath(
                    id = node.id,
                    path = tree.breadcrumb(node.id),
                    name = node.name,
                    kind = node.kind,
                    depth = tree.pathOf(node.id).size - 1,
                )
            }
        val prompt = Prompt.build(selectable.map { it.path })

        when (val outcome = analyzer.analyze(jpeg, prompt)) {
            is AnalysisOutcome.Failure ->
                _state.update { it.copy(analyzing = false, failure = outcome.reason) }

            is AnalysisOutcome.Success -> {
                val first = outcome.items.firstOrNull()
                if (first == null) {
                    _state.update { it.copy(analyzing = false, failure = FailureReason.NOTHING_FOUND) }
                    return
                }
                val current = items.get(itemId)
                val alreadyPicked = current?.locationId != null
                val matchId = SuggestLocationUseCase.suggest(selectable, first.containerGuess)
                items.applyAnalysis(itemId, first, matchId)
                val guessPath = matchId?.let { id -> selectable.firstOrNull { it.id == id }?.path }

                // 重读一遍库里的行：界面显示的就是真落库的那份
                val refreshed = items.draftOf(itemId)
                _state.update { state ->
                    state.copy(
                        analyzing = false,
                        failure = null,
                        slots = state.slots.map {
                            if (it.itemId == itemId) {
                                it.copy(draft = refreshed, locationPath = locationPathOf(refreshed.locationId))
                            } else {
                                it
                            }
                        },
                        guess = guessPath,
                        guessApplied = guessPath != null && !alreadyPicked,
                        placeAlreadyPicked = alreadyPicked,
                    )
                }
            }
        }
    }

    // —— 字段编辑：每一个都记进 `touched`（空壳判定要用它）——

    fun setName(value: String) = edit("name") { it.copy(name = value) }

    fun setCategory(value: String?) = edit("category") { it.copy(category = value) }

    fun toggleSeason(value: String) = edit("seasons") {
        it.copy(seasons = it.seasons.toggle(value))
    }

    fun setColor(value: String) = edit("color") { it.copy(color = value) }

    fun setMaterial(value: String) = edit("material") { it.copy(material = value) }

    fun setBrand(value: String) = edit("brand") { it.copy(brand = value) }

    fun setAliases(value: List<String>) = edit("aliases") { it.copy(aliases = value) }

    /** 「标签（点一下删除）」的另一半：有就摘、没有就加。 */
    fun toggleTag(value: String) = edit("tags") {
        val tag = value.trim()
        it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag)
    }

    fun setDescription(value: String) = edit("description") { it.copy(description = value) }

    fun setNotes(value: String) = edit("notes") { it.copy(notes = value) }

    fun setQuantity(value: Int) = edit("quantity") { it.copy(quantity = value.coerceAtLeast(1)) }

    fun setLocation(locationId: String?) = edit("locationId") { it.copy(locationId = locationId) }

    fun setExpire(expiresAt: Long?, kind: String?) = edit("expiresAt") {
        it.copy(expiresAt = expiresAt, expireKind = kind)
    }

    // —— 三个动作 ——

    /** 「保存这一件」：确认入库。一批只有一件时关页，多件时只说一句并跳到下一件。 */
    fun save() {
        val snapshot = _state.value
        val cur = snapshot.current ?: return
        viewModelScope.launch {
            runCatching { items.confirm(cur.draft) }
                .onSuccess {
                    _state.update { st ->
                        st.withSlot(
                            cur.copy(saved = true, locationPath = locationPathOf(cur.draft.locationId)),
                        )
                    }
                    if (snapshot.slots.size == 1) {
                        finish()
                    } else {
                        // 关页等于替用户把芯片上其余没保存的件扔了
                        _state.update { it.copy(notice = "已保存这一件") }
                        advance()
                    }
                }
                .onFailure { t -> _state.update { it.copy(notice = t.message ?: "保存失败") } }
        }
    }

    /** 「稍后处理」：写字段但退回 DRAFT。**位置不动**（在仓库里）。 */
    fun keepForLater() {
        val snapshot = _state.value
        val cur = snapshot.current ?: return
        viewModelScope.launch {
            runCatching { items.keepAsDraft(cur.draft) }
                .onSuccess {
                    _state.update { st -> st.withSlot(cur.copy(saved = true)) }
                    if (snapshot.slots.size == 1) {
                        finish()
                    } else {
                        _state.update { it.copy(notice = "这一件先放回待归类了") }
                        advance()
                    }
                }
                .onFailure { t -> _state.update { it.copy(notice = t.message ?: "保存失败") } }
        }
    }

    /** 「不要这件」：只对**没落库**的件有效，且不能摘掉最后一件（`RULES.md` §6.5）。 */
    fun discardCurrent() {
        _state.update { st ->
            val cur = st.current ?: return@update st
            if (cur.itemId != null || st.slots.size < 2) return@update st
            val rest = st.slots.toMutableList().also { it.removeAt(st.selected) }
            st.copy(
                slots = rest,
                selected = st.selected.coerceAtMost(rest.lastIndex),
                notice = "「${cur.draft.name.ifBlank { "这一件" }}」没保存，库里不会有它",
            )
        }
    }

    /** ✕ 与系统返回键都走这里（`RULES.md` §6.1）。 */
    fun requestExit() {
        val st = _state.value
        val nothingStored = st.slots.none { it.itemId != null }
        val nothingToLose = st.notStored == 0 && st.slots.none { it.touched.isNotEmpty() }
        if (nothingStored || nothingToLose) closeWithoutAsking() else _state.update { it.copy(confirmExit = true) }
    }

    fun cancelExit() = _state.update { it.copy(confirmExit = false) }

    /** 「不保存，直接离开」。 */
    fun exitWithoutSaving() = _state.update { it.copy(confirmExit = false, finished = true) }

    /** 「保存改动并留下」——**就是那颗「稍后处理」**，不许新造第二种语义。 */
    fun exitKeepingEdits() {
        val cur = _state.value.current ?: return exitWithoutSaving()
        viewModelScope.launch {
            runCatching { items.keepAsDraft(cur.draft) }
                .onSuccess { _state.update { it.copy(confirmExit = false, finished = true) } }
                .onFailure { t -> _state.update { it.copy(notice = t.message ?: "保存失败") } }
        }
    }

    fun consumeFinished() = _state.update { it.copy(finished = false) }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    /** 换一张照片：走仓库的三步顺序，回来刷新槽位上的缩略图。 */
    fun replacePhoto(jpeg: ByteArray) {
        val cur = _state.value.current ?: return
        val itemId = cur.itemId ?: return
        viewModelScope.launch {
            runCatching { captureFlow.replacePhoto(itemId, jpeg) }
                .onSuccess { draft ->
                    _state.update { st ->
                        st.withSlot(
                            cur.copy(
                                draft = draft,
                                thumbPath = items.thumbPathOf(itemId)
                                    ?.let { items.photoStore.file(it).absolutePath },
                            ),
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(notice = t.message ?: "换照片失败") } }
        }
    }

    // —— 私有 ——

    /**
     * 关页但不问（`RULES.md` §6.4）：先把空壳连照片一起摘掉，**再**置 `finished`。
     * 「不保存」说的是"别存我的改动"，不是"允许这里多一件没内容的物品"。
     */
    private fun closeWithoutAsking() {
        val shells = _state.value.slots.filter { it.isThrowawayShell }.mapNotNull { it.itemId }
        viewModelScope.launch {
            // 📌 P3 起这里还要先 analysis?.cancel()：不取消在飞的采集，会出现"退出时没东西可删、
            //    删完了行才落库"的幽灵记录。P2 阶段还没有在飞的分析。
            shells.forEach { items.delete(it) }
            _state.update { it.copy(confirmExit = false, finished = true) }
        }
    }

    private fun finish() = _state.update { it.copy(finished = true) }

    private fun advance() {
        _state.update { st ->
            val next = st.slots.indexOfFirst { !it.saved }
            st.copy(selected = if (next >= 0) next else st.selected)
        }
    }

    private fun edit(
        field: String,
        transform: (com.wheremy.app.data.repo.ItemDraft) -> com.wheremy.app.data.repo.ItemDraft,
    ) {
        _state.update { st ->
            val cur = st.current ?: return@update st
            val updated = cur.copy(draft = transform(cur.draft), touched = cur.touched + field)
            st.withSlot(updated).copy(notice = null)
        }
    }

    private suspend fun locationPathOf(locationId: String?): String? {
        if (locationId == null) return null
        val tree = buildLocationTree(locations.all())
        return tree.node(locationId)?.let { tree.breadcrumb(it.id) }
    }

    private fun ReviewUiState.withSlot(slot: ReviewSlot): ReviewUiState =
        copy(slots = slots.toMutableList().also { it[selected] = slot })

    private fun List<String>.toggle(value: String): List<String> = if (value in this) this - value else this + value
}
