# FR-OPR-06 第二步：VM 多选态与动作。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app\ui\gallery\GalleryViewModel.kt"
t = io.open(PATH, encoding="utf-8").read()

# —— 状态字段 ——
anchor = "    val pendingDelete: PendingDelete? = null,"
fields = anchor + '''
    /**
     * 多选态（`FR-OPR-06`）：**非空 = 正在选**（长按卡片进入），空集 = 普通模式。
     *
     * 用"集合是否为空"当开关，不另加一个 boolean——两个字段就可能互相矛盾（说在选、其实没选）。
     */
    val selectedIds: Set<String> = emptySet(),
    val moveSelectionOpen: Boolean = false,
    val categorySelectionOpen: Boolean = false,
    val pendingBatchDelete: BatchDelete? = null,
    /** 一句话结果（搬了几件、删了几件），屏幕弹完就清。 */
    val notice: String? = null,'''
assert anchor in t, "没找到 pendingDelete 字段"
t = t.replace(anchor, fields, 1)

# —— 动作：挂在类尾（move 之后）——
tail_anchor = """    /** 搬到：交给仓库（它自己判断"有名字就顺手确认"）。 */
    fun move(itemId: String, locationId: String?) {
        viewModelScope.launch {
            items.move(itemId, locationId)
            _state.update { it.copy(moveFor = null) }
            refresh()
        }
    }
}"""
batch_actions = '''    /** 搬到：交给仓库（它自己判断"有名字就顺手确认"）。 */
    fun move(itemId: String, locationId: String?) {
        viewModelScope.launch {
            items.move(itemId, locationId)
            _state.update { it.copy(moveFor = null) }
            refresh()
        }
    }

    // —— 批量操作（`FR-OPR-06`）——

    /** 长按一张卡片：进入多选态，并且**这一张先选上**（用户长按就是想选它）。 */
    fun startSelection(itemId: String) =
        _state.update { it.copy(selectedIds = setOf(itemId), menuFor = null, moveFor = null) }

    /** 多选态里点卡片：选中/取消这一张。取消到一张不剩 = 退出多选态。 */
    fun toggleSelection(itemId: String) = _state.update { it.copy(selectedIds = it.selectedIds.toggled(itemId)) }

    fun clearSelection() = _state.update {
        it.copy(
            selectedIds = emptySet(),
            moveSelectionOpen = false,
            categorySelectionOpen = false,
            pendingBatchDelete = null,
        )
    }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    /** 批量搬到…：位置清单与单件那条路同一份口径（含根层级）。 */
    fun askMoveSelection() {
        viewModelScope.launch {
            val all = locations.all()
            val tree = com.wheremy.app.domain.buildLocationTree(all)
            val options = all.map { MoveOption(it.id, tree.breadcrumb(it.id)) }.sortedBy { it.label }
            _state.update { it.copy(moveSelectionOpen = true, locationOptions = options) }
        }
    }

    fun cancelMoveSelection() = _state.update { it.copy(moveSelectionOpen = false) }

    fun confirmMoveSelection(locationId: String?) {
        val ids = _state.value.selectedIds.toList()
        if (ids.isEmpty()) return
        val where = _state.value.locationOptions.firstOrNull { it.id == locationId }?.label ?: "根层级"
        viewModelScope.launch {
            val moved = items.moveAll(ids, locationId)
            _state.update {
                it.copy(
                    moveSelectionOpen = false,
                    selectedIds = emptySet(),
                    notice = "已把 $moved 件搬到「$where」。",
                )
            }
            refresh()
        }
    }

    /**
     * 批量删除：**先把每一件的照片归属都问一遍**，再把两类照片分开数——
     * 会跟着没的、还有别的记录在用会留着的（`RULES.md` §9.3 的批量版）。
     */
    fun askDeleteSelection() {
        val ids = _state.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val deletions = ids.map { items.photoDeletionOf(it) }
            val toDelete = deletions.count { it.hasPhoto && it.otherItems == 0 && it.otherOwners == 0 }
            val kept = deletions.count { it.hasPhoto && (it.otherItems > 0 || it.otherOwners > 0) }
            _state.update {
                it.copy(pendingBatchDelete = BatchDelete(ids, UiFormat.deleteWarningOfBatch(ids.size, toDelete, kept)))
            }
        }
    }

    fun confirmDeleteSelection() {
        val pending = _state.value.pendingBatchDelete ?: return
        viewModelScope.launch {
            val deleted = items.deleteAll(pending.itemIds)
            _state.update {
                it.copy(pendingBatchDelete = null, selectedIds = emptySet(), notice = "已删除 $deleted 件。")
            }
            refresh()
        }
    }

    fun cancelDeleteSelection() = _state.update { it.copy(pendingBatchDelete = null) }

    /** 批量改类别：类别清单读**生效词表**（`FR-CFG-06`），与确认页那颗芯片同源。 */
    fun askCategorySelection() = _state.update { it.copy(categorySelectionOpen = true) }

    fun cancelCategorySelection() = _state.update { it.copy(categorySelectionOpen = false) }

    fun confirmCategorySelection(category: String?) {
        val ids = _state.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val changed = items.setCategoryAll(ids, category)
            _state.update {
                it.copy(
                    categorySelectionOpen = false,
                    selectedIds = emptySet(),
                    notice = if (category == null) "已把 $changed 件的类别清空。" else "已把 $changed 件的类别改成「$category」。",
                )
            }
            refresh()
        }
    }
}'''
assert tail_anchor in t, "没找到 move 的收尾"
t = t.replace(tail_anchor, batch_actions, 1)

# —— import ——
if "import com.wheremy.app.ui.BatchDelete" not in t:
    t = t.replace("import com.wheremy.app.ui.PendingDelete", "import com.wheremy.app.ui.BatchDelete\nimport com.wheremy.app.ui.PendingDelete", 1)
if "import com.wheremy.app.ui.UiFormat" not in t:
    t = t.replace("import com.wheremy.app.ui.PendingDelete", "import com.wheremy.app.ui.PendingDelete\nimport com.wheremy.app.ui.UiFormat", 1)

io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("VM 已加多选态与六个动作")
