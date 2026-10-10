# 归档 FR-SRC-09（无照片手填）+ F44（测试函数名不能含 ASCII 双引号）+ CHANGELOG
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
    "| `FR-SRC-09` | 无照片的手填条目 | ⬜ | ✅ | **做完了**：来源弹窗多了「不拍照，手动填」"
    "（只在**新建**时出现——换照片时那条没有意义）；`newBlankDraft()` 只不写 `item_photo` 那一行，"
    "其余与拍照那条完全一样，所以确认入库/检索/删除/待归类**都不用为它另开一条路**；"
    "确认页给「没有照片（手动填的条目）」占位 +「加一张照片」按钮，状态行改成"
    "「没有照片，不跑识别——下面的字段自己填就行。」（**不再说**「找不到这张照片了」——"
    "手动填的条目不是照片丢了） | `BlankDraftTest` 5 例（草稿没有照片行也没有待分析照片 / "
    "照样能确认入库并被搜到 / 删它的文案不牵扯照片 / 确认页打开它**不跑分析也不说照片丢了** / "
    "有照片那条路照样会跑分析）；真机：FAB → 不拍照手动填 → 确认页三处文案正确 → "
    "库里新建 `status=DRAFT`、**照片行数 0** → 待归类页显示成「未命名」格子 |\n"
)
d, n = re.subn(r"^\| `FR-SRC-09` \|.*\n", new_row, d, flags=re.M)
print("DELTA FR-SRC-09 替换：%d" % n)
io.open(DELTA, "w", encoding="utf-8", newline="").write(d)

# —— PLAN：F44 ——
p = io.open(PLAN, encoding="utf-8").read()
extra = (
    "| F44 | **Kotlin 测试函数名里不能有 ASCII 双引号** | 编译出的 `.class` 文件名会带上函数名（lambda 那个合成类），"
    "而 Windows 文件名不允许 `\"` → 报 `InvalidPathException: Illegal char <\">`。"
    "**做法**：测试名里一律用「」（这条与 Python 脚本里不能用 ASCII 引号是同一条纪律）。"
    "真机侧另一条同源限制见 F41（微信输入法 + 小鹤双拼，`adb input text` 打不出字） |\n"
)
m = re.search(r"^\| F43 \|.*\n", p, flags=re.M)
if m and "F44" not in p:
    p = p[: m.end()] + extra + p[m.end() :]
    io.open(PLAN, "w", encoding="utf-8", newline="").write(p)
    print("PLAN 已追加 F44")
else:
    print("PLAN 跳过")

# —— PRD ——
t = io.open(PRD, encoding="utf-8").read()
m2 = re.search(r"^\*\*FR-SRC-09[^\n]*\*\* ⬜$", t, flags=re.M)
if m2:
    t = t[: m2.start()] + m2.group(0)[:-1] + "✅（不拍照，手动填）" + t[m2.end() :]
    print("PRD 标题已改")
else:
    print("PRD 标题没匹配")
t, n5 = re.subn(r"^\| FR-SRC-09 \|.*\n", "", t, flags=re.M)
print("PRD §5 移走：%d 行" % n5)
t = t.replace(
    "表里剩下的每一条都与 `DELTA.md` §2 的矩阵一致（**✅ 76 条、🟡 2 条、⬜ 8 条、⛔ 1 条**，共 87 条 FR）。",
    "表里剩下的每一条都与 `DELTA.md` §2 的矩阵一致（**✅ 77 条、🟡 2 条、⬜ 7 条、⛔ 1 条**，共 87 条 FR）。",
)
io.open(PRD, "w", encoding="utf-8", newline="").write(t)
print("PRD 已更新")

# —— CHANGELOG ——
c = io.open(CHANGELOG, encoding="utf-8").read()
section = """## 2026-10-10 · 无照片的手填条目（`FR-SRC-09`）

**不想拍的东西也能记一条**：「身份证放在抽屉第二格」这类，以前只能靠拍一张纸。

- 来源弹窗多了第三个选项 **「不拍照，手动填」**——只在**新建**时出现，换照片时不给（那条记录本来就有照片）；
- 实现上只做了一件事：`newBlankDraft()` **不写 `item_photo` 那一行**。其余（空名称、`DRAFT`、进待归类）
  与拍照那条完全一样，所以**确认入库、检索、删除、待归类都不用为它另开一条路**；
- 确认页：给「没有照片（手动填的条目）」占位 + 「加一张照片」按钮；
- **AI 那条线要特别处理**：手动填的条目不是「照片丢了」，所以进门**不跑分析**、也不弹
  「找不到这张照片了」——改成一句「没有照片，不跑识别——下面的字段自己填就行。」。

**真机验证**：FAB → 来源弹窗三个选项都在 → 「不拍照，手动填」→ 确认页三处文案正确 →
库里新建一条 `status=DRAFT`、**照片行数 0** 的记录 → 待归类页把它显示成「未命名」格子。
（「在输入框里打字保存」这一步受设备输入法限制没能自动化：**微信输入法 + 小鹤双拼**会把
`adb input text` 的字母吞掉——保存那条路已由 `BlankDraftTest` 的单测覆盖。）

**判绿双证**：**40 类 / 252 例**、`freshXml=40/40`、`failures=0 errors=0`、Spotless 通过；`assembleRelease` 一并过。

---

"""
anchor = c.index("## 2026-10-10 · 批量操作")
c = c[:anchor] + section + c[anchor:]
io.open(CHANGELOG, "w", encoding="utf-8", newline="").write(c)
print("CHANGELOG 已插入")
