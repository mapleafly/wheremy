# 归档 FR-FND-08（组合筛选）+ F42（uiautomator dump 会留旧文件）+ CHANGELOG
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
DELTA = r"D:\src\dsh\wheremy\docs\DELTA.md"
PRD = r"D:\src\dsh\wheremy\docs\PRD.md"
PLAN = r"D:\src\dsh\wheremy\docs\PLAN.md"
CHANGELOG = r"D:\src\dsh\wheremy\docs\CHANGELOG.md"

# —— DELTA ——
d = io.open(DELTA, encoding="utf-8").read()
new_row = (
    "| `FR-FND-08` | 多标签 / 多类别组合筛选 | ⬜ | ✅ | **同类内"或"、跨类"与"**：`Filters` 三个字段改成集合"
    "（`categories` / `seasons` / `tags`），标签在 SQL 层取"带任一标签的物品 id"再并集，"
    "类别与季节走内存谓词；芯片改成多选（再点一次只去掉那一个）；折叠时标签写「季节：冬、秋」「#冬装 #自检种子」 | "
    "`FilterCombinationTest` 6 例（同类或 / 跨类与 / 两标签或 / 标签+类别与 / 季节多选 / 空集合不筛 / 与查询词同用）；"
    "`GalleryViewModelTest` 加 1 例钉住连点与再点；真机：点「衣物」只剩外套，再点「数码」变成"
    "「外套 + 过期电池」（并集） |\n"
)
d, n = re.subn(r"^\| `FR-FND-08` \|.*\n", new_row, d, flags=re.M)
print("DELTA FR-FND-08 替换：%d" % n)
io.open(DELTA, "w", encoding="utf-8", newline="").write(d)

# —— PLAN：F42 ——
p = io.open(PLAN, encoding="utf-8").read()
extra = (
    "| F42 | **`uiautomator dump` 失败时会保留上一次的文件，直接读就会看到旧界面** | "
    "真机自动化里踩过：点了「衣物」却读到点击前的界面，一度误判"多选没生效"（其实是逻辑对的）。"
    "**做法**：`artifacts/tools/ui.ps1` —— 先 `rm` 掉 dump 文件、再 dump、再 pull，并核对文件是刚写的；"
    "取屏/点按/截图都走它 |\n"
)
m = re.search(r"^\| F41 \|.*\n", p, flags=re.M)
if m and "F42" not in p:
    p = p[: m.end()] + extra + p[m.end() :]
    io.open(PLAN, "w", encoding="utf-8", newline="").write(p)
    print("PLAN 已追加 F42")
else:
    print("PLAN 跳过")

# —— PRD ——
t = io.open(PRD, encoding="utf-8").read()
m2 = re.search(r"^\*\*FR-FND-08[^\n]*\*\* ⬜$", t, flags=re.M)
if m2:
    t = t[: m2.start()] + m2.group(0)[:-1] + "✅（同类内「或」、跨类「与」）" + t[m2.end() :]
    print("PRD 标题已改")
else:
    print("PRD 标题没匹配")
t, n5 = re.subn(r"^\| FR-FND-08 \|.*\n", "", t, flags=re.M)
print("PRD §5 移走：%d 行" % n5)
t = t.replace(
    "表里剩下的每一条都与 `DELTA.md` §2 的矩阵一致（**✅ 74 条、🟡 2 条、⬜ 10 条、⛔ 1 条**，共 87 条 FR）。",
    "表里剩下的每一条都与 `DELTA.md` §2 的矩阵一致（**✅ 75 条、🟡 2 条、⬜ 9 条、⛔ 1 条**，共 87 条 FR）。",
)
io.open(PRD, "w", encoding="utf-8", newline="").write(t)
print("PRD 已更新")

# —— CHANGELOG ——
c = io.open(CHANGELOG, encoding="utf-8").read()
section = """## 2026-10-10 · 组合筛选（`FR-FND-08`）

**筛选芯片改成多选**：同一类里选多个是**或**，不同类之间是**与**。

- 例：「衣物 + 数码」= 衣物**或**数码；「衣物 + 冬」= 衣物**且**冬；标签同理（「#冬装 + #自检种子」是或）；
- 再点一次只去掉**那一个**，不是清空这一类；
- 折叠起来时标签写清选了什么：「更多筛选 · 季节：冬、秋 #冬装 #自检种子」；
- 实现：`Filters` 三个字段改成集合；标签走 SQL 取"带任一标签的物品 id"再并集，类别与季节走内存谓词；
- **真机**：点「衣物」→ 墙上只剩深灰羽绒外套；再点「数码」→ 变成「深灰羽绒外套 + 过期电池」（并集）✓。

**顺带一条工具教训（已记进 `PLAN.md` §3.4 的 F42）**：
`uiautomator dump` **失败时会保留上一次的文件**，直接读就会看到旧界面——我因此一度误判"多选没生效"（其实逻辑是对的，
单测先证明了这一点）。现在真机取屏统一走 `artifacts/tools/ui.ps1`：先删 dump 文件、再 dump、再 pull，并核对是刚写的。

**判绿双证**：**38 类 / 239 例**、`freshXml=38/38`、`failures=0 errors=0`、Spotless 通过；`assembleRelease` 一并过。

---

"""
anchor = c.index("## 2026-10-10 · 定时自动导出")
c = c[:anchor] + section + c[anchor:]
io.open(CHANGELOG, "w", encoding="utf-8", newline="").write(c)
print("CHANGELOG 已插入")
