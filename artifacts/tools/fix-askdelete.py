# 让 askDelete 同步立起弹窗、异步补数字（否则按删除可能早于 pendingDeleteId 到位而静默失效）。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app\ui\tree\TreeViewModel.kt"
t = io.open(PATH, encoding="utf-8").read()

pattern = re.compile(r"    fun askDelete\(id: String\) \{.*?\n    \}\n", re.S)
new_body = '''    fun askDelete(id: String) {
        val name = _state.value.rows.firstOrNull { it.node.id == id }?.node?.name
            ?: _state.value.breadcrumb.lastOrNull()
            ?: return
        // 弹窗**立刻**立起来（id/名字同步写），"还剩什么"随后补上——
        // 反过来的话，用户（或测试）可能在数字到位之前就按了删除，那一下会静默失效
        _state.update {
            it.copy(
                menuFor = null,
                pendingDeleteId = id,
                pendingDeleteName = name,
                pendingDeleteChildCount = 0,
                pendingDeleteItemCount = 0,
            )
        }
        viewModelScope.launch {
            // 数清楚"里面还剩什么"（`FR-LOC-05`）：弹窗据此给"搬走"的快捷入口，
            // 而不是等用户按了删除再告诉他"不行"
            val all = locations.all()
            val tree = buildLocationTree(all)
            val children = tree.childrenOf(id).size
            val itemsHere = items.atLocation(id).size
            if (_state.value.pendingDeleteId != id) return@launch // 期间用户已经取消了
            _state.update { it.copy(pendingDeleteChildCount = children, pendingDeleteItemCount = itemsHere) }
        }
    }
'''
t2, n = pattern.subn(new_body, t, count=1)
print("askDelete 替换：%d 处" % n)
if n == 1:
    io.open(PATH, "w", encoding="utf-8", newline="").write(t2)
