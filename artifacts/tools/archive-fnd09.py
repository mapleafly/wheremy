# 归档 FR-FND-09（结果排序）+ CHANGELOG
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
DELTA = r"D:\src\dsh\wheremy\docs\DELTA.md"
PRD = r"D:\src\dsh\wheremy\docs\PRD.md"
CHANGELOG = r"D:\src\dsh\wheremy\docs\CHANGELOG.md"

# —— DELTA ——
d = io.open(DELTA, encoding="utf-8").read()
new_row = (
    "| `FR-FND-09` | 「最近动过」排序 | ⬜ | ✅ | **四档排序**：默认 / 最近动过 / 最近更新 / 名称。"
    "🔒 **默认那一档一个字都没改**（空查询仍按插入序 `RULES.md` §10.1、有查询仍按相关度 `SearchRanker`），"
    "其余三档是用户显式选的、选了就以排序为准；只改顺序不改集合；折叠起来时标签上写着「排序：X」 | "
    "`SortOrderTest` 6 例（三种排序 + **两条默认行为的防回归** + 排序与筛选同用）；"
    "真机：展开面板点「排序：名称」→ 墙上顺序按字符串序重排，折叠标签变成「更多筛选 · 排序：名称」 |\n"
)
d, n = re.subn(r"^\| `FR-FND-09` \|.*\n", new_row, d, flags=re.M)
print("DELTA FR-FND-09 替换：%d" % n)
io.open(DELTA, "w", encoding="utf-8", newline="").write(d)

# —— PRD：标题 + 待做队列 ——
t = io.open(PRD, encoding="utf-8").read()
m = re.search(r"^\*\*FR-FND-09[^\n]*\*\* ⬜$", t, flags=re.M)
if m:
    t = t[: m.start()] + m.group(0)[:-1] + "✅（四档：默认/最近动过/最近更新/名称）" + t[m.end() :]
    print("PRD 标题已改")
else:
    print("PRD 标题没匹配，待手工确认")
t, n5 = re.subn(r"^\| FR-FND-09 \|.*\n", "", t, flags=re.M)
print("PRD §5 移走：%d 行" % n5)
t = t.replace(
    "表里剩下的每一条都与 `DELTA.md` §2 的矩阵一致（**✅ 72 条、🟡 2 条、⬜ 12 条、⛔ 1 条**，共 87 条 FR）。",
    "表里剩下的每一条都与 `DELTA.md` §2 的矩阵一致（**✅ 73 条、🟡 2 条、⬜ 11 条、⛔ 1 条**，共 87 条 FR）。",
)
io.open(PRD, "w", encoding="utf-8", newline="").write(t)
print("PRD 已更新")

# —— CHANGELOG ——
c = io.open(CHANGELOG, encoding="utf-8").read()
section = """## 2026-10-10 · 结果排序四档（`FR-FND-09`）+ 删位置就地搬走（`FR-LOC-05`）

**`FR-FND-09` 结果排序**：筛选面板里多一行四颗芯片——**默认 / 最近动过 / 最近更新 / 名称**。

- 🔒 **默认那一档一个字都没改**：空查询仍按插入序（`RULES.md` §10.1 的 `all()` 没有 ORDER BY）、有查询仍按相关度（`SearchRanker`）。
  加排序最容易顺手把默认行为改掉，而那是锁死的契约——所以 `SortOrderTest` 里有**两条专门防这个回归**的用例；
- 其余三档是用户**显式**选的，选了就以排序为准；**只改顺序、不改集合**；再点当前那颗 = 回默认；
- 折叠起来时第 2 层芯片上写着「更多筛选 · 排序：名称」，不会出现"顺序变了但看不出为什么"；
- 真机：展开面板点「排序：名称」→ 墙上顺序按**字符串序**（不是拼音序）重排，折叠标签同步变化。

**判绿双证**：**36 类 / 227 例**、`freshXml=36/36`、`failures=0 errors=0`、Spotless 通过；`assembleRelease` 一并过。

**顺带记一条工具**：`artifacts/tools/pull-db.ps1`——核手机上的库必须**连 `-wal` 一起拉**，
只拉主库会看到旧快照（当天因此误判过一次"搬走没生效"）。

---

"""
anchor = c.index("## 2026-10-10 · 无障碍深扫进自动化")
c = c[:anchor] + section + c[anchor:]
io.open(CHANGELOG, "w", encoding="utf-8", newline="").write(c)
print("CHANGELOG 已插入")
