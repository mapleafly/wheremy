# 归档 FR-OPR-06（批量操作）+ F43（工具条被 FAB 盖住）+ CHANGELOG
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
    "| `FR-OPR-06` | 批量操作（多选后搬移/删除/改类别） | ⬜ | ✅ | **做完了**：长按卡片进多选（长按的那张先选上），"
    "点卡片切换；贴底工具条「已选 N 件」+「搬到…」「改类别」「删除」「取消」；"
    "三件事都**逐件复用单件那条路**（`moveAll` / `deleteAll` / `setCategoryAll`），不另写一套 SQL——"
    "单件的口径（搬动时间戳、按引用计数删文件）已经在测试里钉死了；"
    "批量删除的文案把**两类照片分开数**（会跟着没的按文件张数、会留着的按记录件数）；"
    "搬完/删完/改完都给一句带件数的结果 | `BatchOperationsTest` 8 例"
    "（仓库三方法 + 多选切换 + 删除文案两类分开数 + 共用照片留/删 + 结果句）；"
    "真机：长按 → 已选 2 件 → 改类别「衣物」（库里 电钻 工具→衣物）、搬到「家/抽屉」"
    "（库里 深灰羽绒外套 衣柜→抽屉）、删除确认框文案正确（未真删，避免动用户数据） |\n"
)
d, n = re.subn(r"^\| `FR-OPR-06` \|.*\n", new_row, d, flags=re.M)
print("DELTA FR-OPR-06 替换：%d" % n)
io.open(DELTA, "w", encoding="utf-8", newline="").write(d)

# —— PLAN：F43 ——
p = io.open(PLAN, encoding="utf-8").read()
extra = (
    "| F43 | **多选工具条被右下角的 FAB 盖住「取消」** | 真机截图看出来的：四颗按钮一行在 411dp 宽屏上放不下，"
    "最右那颗正好在 FAB 底下。**做法**：按钮行改 `FlowRow`（放不下就换行）+ 工具条尾部留 88dp 给 FAB。"
    "教训：**布局问题要看截图**，dump 的 bounds 只能证明节点在，证明不了它没被别的层压住 |\n"
)
m = re.search(r"^\| F42 \|.*\n", p, flags=re.M)
if m and "F43" not in p:
    p = p[: m.end()] + extra + p[m.end() :]
    io.open(PLAN, "w", encoding="utf-8", newline="").write(p)
    print("PLAN 已追加 F43")
else:
    print("PLAN 跳过")

# —— PRD ——
t = io.open(PRD, encoding="utf-8").read()
m2 = re.search(r"^\*\*FR-OPR-06[^\n]*\*\* ⬜$", t, flags=re.M)
if m2:
    t = t[: m2.start()] + m2.group(0)[:-1] + "✅（长按多选：搬到/改类别/删除）" + t[m2.end() :]
    print("PRD 标题已改")
else:
    print("PRD 标题没匹配")
t, n5 = re.subn(r"^\| FR-OPR-06 \|.*\n", "", t, flags=re.M)
print("PRD §5 移走：%d 行" % n5)
t = t.replace(
    "表里剩下的每一条都与 `DELTA.md` §2 的矩阵一致（**✅ 75 条、🟡 2 条、⬜ 9 条、⛔ 1 条**，共 87 条 FR）。",
    "表里剩下的每一条都与 `DELTA.md` §2 的矩阵一致（**✅ 76 条、🟡 2 条、⬜ 8 条、⛔ 1 条**，共 87 条 FR）。",
)
io.open(PRD, "w", encoding="utf-8", newline="").write(t)
print("PRD 已更新")

# —— CHANGELOG ——
c = io.open(CHANGELOG, encoding="utf-8").read()
section = """## 2026-10-10 · 批量操作（`FR-OPR-06`）

**换季整理那条路通了**：长按一张卡片进多选 → 点其他卡片加选 → 贴底工具条上做三件事。

- **长按进多选**（长按的那张先选上），多选态里点卡片 = 选中/取消；贴底条写着「已选 N 件」；
- **搬到…** / **改类别** / **删除**，加一颗「取消」；改类别的清单读**生效词表**（与确认页那颗芯片同源）；
- 三件事都**逐件复用单件那条路**（`moveAll` / `deleteAll` / `setCategoryAll`），不另写一套 SQL——
  单件的口径（搬动时间戳、按引用计数删文件）已经在测试里钉死了，批量再写一遍就是抄两遍，迟早抄歪；
- **批量删除的文案把两类照片分开数**：会跟着没的按**文件张数**（一条文件没别人用才算），
  还有别的记录在用会留着的按**记录件数**（同一条文件可能被两件共用）——含糊说「照片可能被删」等于没说；
- 搬完/删完/改完都回一句带件数的结果（「已把 2 件搬到「家 / 抽屉」。」）。

**真机验证**：长按 → 已选 2 件 → 改类别「衣物」（库里核对：电钻 工具→衣物）→ 搬到「家 / 抽屉」
（库里核对：深灰羽绒外套 衣柜→抽屉）→ 删除确认框文案正确（**没有真删**，避免动用户数据；
共用照片的留/删语义由单测钉住：还有一件在用就必须留着，没人用了才删）。

**真机又抓到一个布局问题（已记进 `PLAN.md` §3.4 的 F43）**：工具条四颗按钮一行在 411dp 宽屏上放不下，
最右的「取消」正好被右下角的 **FAB** 盖住。改法：按钮行换 `FlowRow` + 工具条尾部留 88dp 给 FAB。
教训：**布局问题要看截图**——dump 的 bounds 只能证明节点在，证明不了它没被别的层压住。

**判绿双证**：**39 类 / 247 例**、`freshXml=39/39`、`failures=0 errors=0`、Spotless 通过；`assembleRelease` 一并过。

---

"""
anchor = c.index("## 2026-10-10 · 组合筛选")
c = c[:anchor] + section + c[anchor:]
io.open(CHANGELOG, "w", encoding="utf-8", newline="").write(c)
print("CHANGELOG 已插入")
