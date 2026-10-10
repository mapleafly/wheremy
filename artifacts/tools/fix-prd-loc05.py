# 把 PRD 里 FR-LOC-05 那一条的标记从 🟡 改成 ✅（上一版脚本的引号没对上）。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\docs\PRD.md"
t = io.open(PATH, encoding="utf-8").read()

old = '**FR-LOC-05 删除失败时给"把物品搬走"的快捷入口** 🟡'
new = (
    '**FR-LOC-05 删除失败时给"把物品搬走"的快捷入口** ✅\n'
    "删除弹窗一打开就数清「还有 N 件物品、M 个子位置」，并就地给两颗快捷入口："
    "「把 N 件物品搬到…」「把 M 个子位置移到…」；选完目标**自动重试删除**。"
    "真机验过：删「抽屉」（2 件物品）→ 搬到「家」→ 抽屉消失、两件物品挂到「家」下。"
)
if old in t:
    t = t.replace(old, new, 1)
    io.open(PATH, "w", encoding="utf-8", newline="").write(t)
    print("PRD FR-LOC-05 标记已改 ✅")
else:
    print("没匹配到，需要手工看一眼")
