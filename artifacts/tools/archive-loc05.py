# 归档 FR-LOC-05（删位置时就地搬走）+ 顺手留一个"连 WAL 一起拉库"的小工具说明。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
DELTA = r"D:\src\dsh\wheremy\docs\DELTA.md"
PRD = r"D:\src\dsh\wheremy\docs\PRD.md"

# —— DELTA：FR-LOC-05 翻绿 ——
d = io.open(DELTA, encoding="utf-8").read()
new_row = (
    "| `FR-LOC-05` | 删除失败时给「把物品搬走」的快捷入口 | 🟡 | ✅ | **做完了**：删除弹窗一打开就数清"
    "「还有 N 件物品、M 个子位置」，并就地给两颗快捷入口——「把 N 件物品搬到…」「把 M 个子位置移到…」；"
    "选完目标**自动重试删除**（搬走的唯一目的就是删掉它，让用户再点一次是多余的）；"
    "子位置的目标清单排除自己与自己的子树（防环） | `TreeMoveOutTest` 4 例"
    "（弹窗即见现状 / 搬物品后自动删 / 两段都搬完才删 / 子位置不能进自己子树）；"
    "真机：删「抽屉」（2 件物品）→ 弹窗写「还有 2 件物品、0 个子位置」→「把 2 件物品搬到…」→ 选「家」→ "
    "抽屉消失、电钻与护照挂到「家」下（连 `-wal` 一起拉库核对） |\n"
)
d, n = re.subn(r"^\| `FR-LOC-05` \|.*\n", new_row, d, flags=re.M)
print("DELTA FR-LOC-05 替换：%d" % n)
io.open(DELTA, "w", encoding="utf-8", newline="").write(d)

# —— PRD：标记 + 两处待做队列 ——
t = io.open(PRD, encoding="utf-8").read()
for old, new in [
    ("**FR-LOC-05 删除失败时给「把物品搬走」的快捷入口** 🟡", "**FR-LOC-05 删除失败时给「把物品搬走」的快捷入口** ✅"),
    ("**FR-LOC-05 删除失败时给“把物品搬走”的快捷入口** 🟡", "**FR-LOC-05 删除失败时给“把物品搬走”的快捷入口** ✅"),
]:
    if old in t:
        t = t.replace(old, new, 1)
        print("PRD 标题已改")
t, n5 = re.subn(r"^\| FR-LOC-05 \|.*\n", "", t, flags=re.M)
t, n51 = re.subn(r"^\| FR-LOC-05 \|.*\n", "", t, flags=re.M)
print("PRD 待做队列移走：§5 %d 行 / §5.1 %d 行" % (n5, n51))
t = t.replace(
    "> `FR-PRM-05`（无障碍）**也做完了**：内容描述 + ATF 深扫（取景屏/照片墙/确认页，Error 档判红、进 CI）；\n"
    "> 只剩 TalkBack 的**焦点次序**走查必须人工。至此**用户批准进首版的六条全部落地**。\n",
    "> `FR-PRM-05`（无障碍）**也做完了**：内容描述 + ATF 深扫（取景屏/照片墙/确认页，Error 档判红、进 CI）；\n"
    "> 只剩 TalkBack 的**焦点次序**走查必须人工。至此**用户批准进首版的六条全部落地**。\n"
    "> 之后又清掉一条 🟡：`FR-LOC-05`（删位置时**就地**把物品/子位置搬走，选完自动重试删除）。\n",
)
t = t.replace(
    "表里剩下的每一条都与 `DELTA.md` §2 的矩阵一致（**✅ 70 条、🟡 4 条、⬜ 12 条、⛔ 1 条**，共 87 条 FR）。",
    "表里剩下的每一条都与 `DELTA.md` §2 的矩阵一致（**✅ 72 条、🟡 2 条、⬜ 12 条、⛔ 1 条**，共 87 条 FR）。",
)
io.open(PRD, "w", encoding="utf-8", newline="").write(t)
print("PRD 已更新")
