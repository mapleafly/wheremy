# 修 TreeMoveOutTest 里两处"等得太早"的等待条件（状态先翻、refresh 后到）。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\test\java\com\wheremy\app\ui\tree\TreeMoveOutTest.kt"
t = io.open(PATH, encoding="utf-8").read()

pairs = [
    (
        '        val stillBlocked = awaitState(vm, "子位置还挡着") { it.pendingDeleteId == drawer && it.pendingDeleteChildCount == 1 }',
        '        val stillBlocked = awaitState(vm, "物品数归零、子位置还挡着") {\n'
        '            it.pendingDeleteId == drawer && it.pendingDeleteItemCount == 0 && it.pendingDeleteChildCount == 1\n'
        '        }',
    ),
    (
        '        val after = awaitState(vm, "搬完并自动删掉") { it.pendingDeleteId == null }',
        '        // 等 refresh 也落地：pendingDeleteId 先翻，行列表随后才更新\n'
        '        val after = awaitState(vm, "搬完并自动删掉") { it.pendingDeleteId == null && it.rows.isEmpty() }',
    ),
]

for old, new in pairs:
    if old in t:
        t = t.replace(old, new, 1)
        print("改了：%s" % old.strip()[:40])
    else:
        print("没匹配：%s" % old.strip()[:40])

io.open(PATH, "w", encoding="utf-8", newline="").write(t)
