# 归档 FR-AI-11（方案②：筛得到也搜得到）+ FR-BAK-03（钉住设计）+ F45（字段版本缺省值的坑）
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
DOCS = r"D:\src\dsh\wheremy\docs"
DELTA = DOCS + r"\DELTA.md"
PRD = DOCS + r"\PRD.md"
PLAN = DOCS + r"\PLAN.md"
RULES = DOCS + r"\RULES.md"
CHANGELOG = DOCS + r"\CHANGELOG.md"

# —— DELTA：两条翻绿 ——
d = io.open(DELTA, encoding="utf-8").read()
ai11 = (
    "| `FR-AI-11` | 词表外的类别/季节：照单入库，但界面筛不到 | 🟡 | ✅ | **按 `RULES.md` §5.7 选了方案②**"
    "（保持入库、筛选与搜索都认自由值）：① `search_text` 纳入类别与季节（字段版本 2 → 3）；"
    "② 照片墙芯片云 = 生效词表 ∪ 库里真出现过的值（词表在前、自由值缀后）；"
    "③ 确认页芯片行带上「当前这个值」，词表外的也显示得出选中态。"
    "**顺带修掉一个真 bug（F45）**：`searchTextVersion` 的缺省值原本取当前 `FIELD_VERSION`，"
    "键不存在时闸门永远相等 → 字段版本升了对老库完全不生效 | `FreeValueFacetsTest` 6 例"
    "（自由值搜得到 / 筛得到 / 与预设值同筛 / 芯片云跟着数据长 / 没自由值时就是词表 / 字段版本是 3）；"
    "`SettingsRepositoryTest` 加 1 例钉住缺省值 0；真机：导入一个带「数码产品/冬季」的包 → "
    "滑动芯片行出现「数码产品」→ 点它墙上只剩「移动硬盘」；重启后 `search_text` 变成"
    "「移动硬盘 数码产品 冬季 1tb，蓝色外壳」 |\n"
)
bak03 = (
    "| `FR-BAK-03` | 导入失败时库整体回滚；照片文件层刻意不回滚 | 🟡 | ✅ | **是设计不是漏洞，已钉住**："
    "① 库四张表一个事务，校验失败换成一句中文；② 搬图在事务成功**之后**，单张失败被 `runCatching` 吞掉"
    "（库是唯一真相源，字节仍躺在用户的 zip 里）；③ 覆盖模式删多余照片也不在事务里——所以**被拒的包一张照片都不动**。"
    "完成那句话数的是**入库行数**，不代表同样多个文件真搬进去了 | `BackupAsymmetryTest` 2 例"
    "（搬图失败仍成功入库 / 被拒的包不动本机照片）；原有 `BackupRepositoryTest` 已覆盖库回滚与覆盖模式删文件 |\n"
)
d, n1 = re.subn(r"^\| `FR-AI-11` \|.*\n", ai11, d, flags=re.M)
d, n2 = re.subn(r"^\| `FR-BAK-03` \|.*\n", bak03, d, flags=re.M)
print("DELTA：FR-AI-11 %d 处、FR-BAK-03 %d 处" % (n1, n2))
io.open(DELTA, "w", encoding="utf-8", newline="").write(d)

# —— PLAN：F45 ——
p = io.open(PLAN, encoding="utf-8").read()
f45 = (
    "| F45 | **`searchTextVersion` 的缺省值取了当前 `FIELD_VERSION`，于是字段版本升了对老库完全不生效** | "
    "真机上发现：加了类别/季节（版本 2→3）之后重启，老数据的 `search_text` 里还是没有它们。"
    "原因：缺省值 = 当前版本 → 键不存在时闸门 `x != x` 恒假 → 永远不补算。"
    "**修法**：缺省 **0**（= 从没补算过），首次启动多跑一次 `rebuildSearchText`（它只写真的变了的行）。"
    "教训：**「缺省值等于当前值」这种写法会把「升版本」这件事悄悄吃掉**——闸门类的字段，缺省必须是"未知"而不是"最新" |\n"
)
m = re.search(r"^\| F44 \|.*\n", p, flags=re.M)
if m and "F45" not in p:
    p = p[: m.end()] + f45 + p[m.end() :]
    io.open(PLAN, "w", encoding="utf-8", newline="").write(p)
    print("PLAN 已追加 F45")
else:
    print("PLAN 跳过")

# —— PRD：两条的标记 + §5.1 表 ——
t = io.open(PRD, encoding="utf-8").read()
t = t.replace("**FR-AI-11 词表外的类别/季节：照单入库，但界面筛不到** 🟡",
              "**FR-AI-11 词表外的类别/季节：照单入库，且筛得到也搜得到** ✅", 1)
t = t.replace("**FR-BAK-03 导入失败时库整体回滚；照片文件层刻意不回滚** 🟡",
              "**FR-BAK-03 导入失败时库整体回滚；照片文件层刻意不回滚** ✅（设计，已钉住）", 1)
t = re.sub(r"^\| FR-AI-11 \|.*\n", "", t, flags=re.M)
t = re.sub(r"^\| FR-BAK-03 \|.*\n", "", t, flags=re.M)
t = t.replace(
    "表里剩下的每一条都与 `DELTA.md` §2 的矩阵一致（**✅ 77 条、🟡 2 条、⬜ 7 条、⛔ 1 条**，共 87 条 FR）。",
    "表里剩下的每一条都与 `DELTA.md` §2 的矩阵一致（**✅ 79 条、🟡 0 条、⬜ 7 条、⛔ 1 条**，共 87 条 FR）。",
)
io.open(PRD, "w", encoding="utf-8", newline="").write(t)
print("PRD 已更新")

# —— RULES §5.7：写成"选了②、怎么做" ——
r = io.open(RULES, encoding="utf-8").read()
old_head = "### 5.7 词表外的类别/季节：照单入库，但界面筛不到 🔒（重做时必须选一边）"
new_head = "### 5.7 词表外的类别/季节：照单入库，且筛得到也搜得到 🔒（本实现选了②，2026-10-10）"
if old_head in r:
    r = r.replace(old_head, new_head, 1)
    marker = "现在代码是第 ② 半（入库）+ 第 ① 半的界面（只给预设芯片），所以两边都不彻底。"
    replacement = (
        "**本实现选了②并做完**（2026-10-10）：① `search_text` 纳入 `category` 与 `seasons`，"
        "字段版本 2 → 3（`SearchTextBuilder`）；② 照片墙芯片云 = 生效词表 ∪ **库里真出现过的值**"
        "（`GalleryViewModel.mergeFacets`，词表在前、自由值缀在后，一眼看得出哪些是词表外的）；"
        "③ 确认页的分类/季节芯片行带上「当前这个值」，所以模型回了词表外的值也显示得出选中态。"
        "⚠️ 与 §10.3 一起改的，两处必须同进同退。"
    )
    if marker in r:
        r = r.replace(marker, replacement, 1)
    else:
        print("  (RULES 5.7 的收尾句没匹配，只改了标题)")
    io.open(RULES, "w", encoding="utf-8", newline="").write(r)
    print("RULES §5.7 已改")
else:
    print("RULES §5.7 标题没匹配")

# —— CHANGELOG ——
c = io.open(CHANGELOG, encoding="utf-8").read()
section = """## 2026-10-10 · 词表外的类别/季节（`FR-AI-11`）+ 备份那条不对称（`FR-BAK-03`）+ 修 F45

**`FR-AI-11` 按 `RULES.md` §5.7 选了方案②**（保持入库、筛选与搜索都认自由值）——不丢模型的答案：

- `search_text` 纳入**类别与季节**，字段版本 2 → 3（启动时自动补算老数据）；
- 照片墙**芯片云 = 生效词表 ∪ 库里真出现过的值**（词表在前、自由值缀在后，一眼看得出哪些是词表外的）；
- 确认页的分类/季节芯片行带上「当前这个值」，所以模型回了「数码产品」也显示得出选中态。

**真机验证**：造了一个带「数码产品 / 冬季」的包导入 → 滑动芯片行出现「数码产品」→ 点它墙上只剩
「移动硬盘」（**筛得到**）；重启后 `search_text` 变成「移动硬盘 数码产品 冬季 1tb，蓝色外壳」（**搜得到**）。

**顺带修掉一个真 bug（F45）**：`searchTextVersion` 的**缺省值原本取了当前的 `FIELD_VERSION`**——
键不存在时闸门 `x != x` 恒假，于是**字段版本升了对老库完全不生效**（这就是为什么第一次重启后
`search_text` 里还是没有类别）。改成缺省 **0**（= 从没补算过）后，重启立刻补算 ✓。
教训：闸门类字段的缺省必须是「未知」，不能是「最新」。

**`FR-BAK-03` 是设计不是漏洞，现在被钉住了**（`BackupAsymmetryTest` 2 例）：

- 搬图失败**不回滚库**：库里有行、磁盘上没文件 → 缩略图空白，导入仍算成功；
- **被拒的包一张本机照片都不动**：删多余照片发生在事务成功之后，所以校验失败时本机照片原封不动；
- 完成那句话数的是**入库行数**，不代表同样多个文件真搬进去了（界面口径）。

**判绿双证**：**42 类 / 261 例**、`freshXml=42/42`、`failures=0 errors=0`、Spotless 通过；`assembleRelease` 一并过。

---

"""
anchor = c.index("## 2026-10-10 · 无照片的手填条目")
c = c[:anchor] + section + c[anchor:]
io.open(CHANGELOG, "w", encoding="utf-8", newline="").write(c)
print("CHANGELOG 已插入")
