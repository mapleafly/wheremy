# 把「全应用可切换控件审计」的结论写进 CHANGELOG（F50 那节）与 PLAN F50
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
D = r"D:\src\dsh\wheremy\docs"
PLAN = D + r"\PLAN.md"
CHANGELOG = D + r"\CHANGELOG.md"

# ---- CHANGELOG：在 F50 那节末尾追加"全应用审计" ----
c = io.open(CHANGELOG, encoding="utf-8").read()
anchor = "不是形式主义。）"
audit = anchor + """

### 全应用可切换控件审计（同一批做的）

用户追问「其他页面的开关和标签也检查是否有这个问题」，于是把全应用的**可切换类**控件过了一遍
（`Switch` / `Checkbox` / `RadioButton` / `toggleable` / `selectable` / `FilterChip` / `AssistChip`）：

| 控件 | 位置 | 结论 |
|---|---|---|
| `Switch` | 只有 2 处，都在设置页 | **就是报告的那两处，已修**（整行 `toggleable` + `Switch(onCheckedChange = null)`） |
| `FilterChip` | 照片墙的分类/季节/标签/排序/更多筛选、确认页的分类/季节、设置页的提前天数、位置页的位置类型 | ✅ **没问题**：材料组件自带 `selected` 语义，读屏会念「已选中」 |
| `AssistChip`（「只看待归类」） | 照片墙顶栏 | ✅ **可接受**：它是开关行为但用不了 `selected` 语义，于是**把状态写进文案**（「只看待归类」/「只看待归类 · 开」），文字与点击在同一个节点。要更规范可换成 `FilterChip`（会带勾选态、文案不再变），但那会改视觉与截图基线，**暂不动** |
| `AssistChip`（标签） | 确认页的「标签（点一下删除）」 | ✅ **是按钮不是开关**：点一下删掉该标签，文字与点击同节点；该做什么由上方那行标题交代 |
| `Checkbox` / `RadioButton` / `selectable` | **全应用没有** | —— |

**结论**：除设置页那两处开关外，**没有第二处"控件与标签分家"**；其余可切换控件要么是自带选中语义的
`FilterChip`，要么是文案里带状态、文字与点击同节点的芯片。

审计不是靠肉眼看的，而是写成了断言（读屏那棵树）：照片墙那条查「只看待归类是一个可点节点」+
「分类芯片可选中且暴露状态」；确认页加了「分类芯片可选中」+「标签芯片是可点节点」；
设置页加了「提前天数的预设芯片带选中状态」。**测试 265 → 268 例**，本地四道门全绿。"""
if anchor in c:
    c = c.replace(anchor, audit, 1)
    io.open(CHANGELOG, "w", encoding="utf-8", newline="").write(c)
    print("CHANGELOG 已追加审计结论")
else:
    print("!! CHANGELOG 没找到锚点")

# ---- PLAN F50：补一句审计结论 ----
p = io.open(PLAN, encoding="utf-8").read()
old = "`adb uiautomator dump` 看的是原始视图树、会误导 |"
new = (
    "`adb uiautomator dump` 看的是原始视图树、会误导。"
    "**全应用审计（用户追问「其他页面」后做的）**：可切换类控件只有 2 处 `Switch`（都在设置页，已修）；"
    "其余全是 `FilterChip`（自带选中语义）或文案里带状态的芯片，**没有第二处「控件与标签分家」**；"
    "审计写成了断言（照片墙/确认页/设置页各加），测试 265 → 268 例 |"
)
if old in p:
    p = p.replace(old, new, 1)
    io.open(PLAN, "w", encoding="utf-8", newline="").write(p)
    print("PLAN F50 已补审计结论")
else:
    print("!! PLAN 没找到锚点")
