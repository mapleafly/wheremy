# 归档：无障碍深扫（FR-PRM-05 收口）+ F39（裁掉的芯片）+ CHANGELOG
import io
import re
import sys

# 控制台是 GBK：emoji 打不出来会直接抛 UnicodeEncodeError（上一版就是这么崩的）
sys.stdout.reconfigure(errors="replace")

DELTA = r"D:\src\dsh\wheremy\docs\DELTA.md"
PRD = r"D:\src\dsh\wheremy\docs\PRD.md"
PLAN = r"D:\src\dsh\wheremy\docs\PLAN.md"
CHANGELOG = r"D:\src\dsh\wheremy\docs\CHANGELOG.md"

# —— DELTA：FR-PRM-05 翻绿 ——
d = io.open(DELTA, encoding="utf-8").read()
new_row = (
    "| `FR-PRM-05` | 无障碍 | 🟡 | ✅ | **内容描述 + ATF 深扫都做完了**：格子图给物品名（空名给「未命名物品」）、"
    "详情照片给「物品照片」、位置缩略图给「<位置名>的位置照片」；"
    "**ATF（Android Accessibility Test Framework）深扫进自动化**——取景屏三态、照片墙（有物品）、确认页都在 "
    "`CheckLevel.Error` 档判红，**不需要设备、进得了 CI**；扫出来的真问题当场修掉（见 F39） | "
    "`AccessibilityScanTest`（2 例）/ `GalleryAccessibilityTest` / `ReviewAccessibilityTest`；"
    "真机 `uiautomator dump` 核过 `content-desc`。"
    "⚠️ 剩下**只有一条必须人工**：TalkBack 的**焦点次序**走查（ATF 那套规则不覆盖次序） |\n"
)
d, n = re.subn(r"^\| `FR-PRM-05` \|.*\n", new_row, d, flags=re.M)
print("DELTA FR-PRM-05 替换：%d" % n)
io.open(DELTA, "w", encoding="utf-8", newline="").write(d)

# —— PLAN：F39 ——
p = io.open(PLAN, encoding="utf-8").read()
f39 = (
    "| F39 | **横向滚动的芯片行里，被视口裁掉的那颗芯片被 ATF 判成「触摸目标只有 13dp」** | "
    "确认页的无障碍扫描抓出来的。判定实验：把类别词表缩到 2 项（不再溢出）→ 报警消失，"
    "确认是「按可见宽度量」而不是真做小了。**修法不是抑制检查，而是让芯片换行**"
    "（`Row` → `FlowRow`）：12 颗类别芯片分三行铺开，谁都不会被裁——对被裁掉一半的芯片，"
    "读屏与手抖的用户本来就不友好 |\n"
)
m = re.search(r"^\| F38 \|.*\n", p, flags=re.M)
assert m, "PLAN 里没找到 F38"
if "F39" in p:
    print("PLAN 里已经有 F39，跳过")
else:
    p = p[: m.end()] + f39 + p[m.end() :]
    io.open(PLAN, "w", encoding="utf-8", newline="").write(p)
    print("PLAN 已追加 F39")

# —— PRD：标记 + 待做队列 ——
t = io.open(PRD, encoding="utf-8").read()
for old, new in [
    ("**FR-PRM-05 无障碍** 🟡", "**FR-PRM-05 无障碍** ✅（ATF 深扫已自动化）"),
    ("**FR-PRM-05 无障碍补全** 🟡", "**FR-PRM-05 无障碍补全** ✅"),
]:
    if old in t:
        t = t.replace(old, new, 1)
        print("PRD 标题已改：%s" % old[:20])
t, n5 = re.subn(r"^\| FR-PRM-05 \|.*\n", "", t, flags=re.M)
t, n51 = re.subn(r"^\| FR-PRM-05 \|.*\n", "", t, flags=re.M)
print("PRD §5 移走 %d 行，§5.1 移走 %d 行" % (n5, n51))
t = t.replace(
    "> `FR-CFG-06`（四套词表自定义管理）**也做完了**：词表进 DataStore、设置页可改、提示词跟着用户那份走。\n"
    "> 至此**用户批准进首版的六条全部落地**。\n",
    "> `FR-CFG-06`（四套词表自定义管理）**也做完了**：词表进 DataStore、设置页可改、提示词跟着用户那份走。\n"
    "> `FR-PRM-05`（无障碍）**也做完了**：内容描述 + ATF 深扫（取景屏/照片墙/确认页，Error 档判红、进 CI）；\n"
    "> 只剩 TalkBack 的**焦点次序**走查必须人工。至此**用户批准进首版的六条全部落地**。\n",
)
io.open(PRD, "w", encoding="utf-8", newline="").write(t)
print("PRD 已更新")

# —— CHANGELOG：新一节插到最前 ——
c = io.open(CHANGELOG, encoding="utf-8").read()
section = """## 2026-10-10 · 无障碍深扫进自动化（`FR-PRM-05` 收口）+ 恢复数据到手机

**按用户要求恢复数据**：用**最近那份**导出包（`wheremy-backup-2026-10-09.zip`，12 条目 = 清单 + 数据 + 10 张图）
在**用户自己签名的 release 包**上走「设置 → 备份 → 覆盖导入」→ SAF 选包 →「覆盖导入完成 · 位置 3 个 · 物品 5 件 · 照片 5 张」，
照片墙随即显示出这些物品。**这一步顺带验证了 release（R8 + 正式签名）的完整恢复链路**。
（另两份 zip 仍在 `Download` 里：10-05 1.06 MB / 10-04 394 KB，需要时说一声。）

**无障碍深扫（`FR-PRM-05` 从 🟡 到 ✅）**：

- 用 **ATF**（Android Accessibility Test Framework）+ `roborazzi-accessibility-check` 跑在 JVM 上——
  **不需要设备、进得了 CI**；
- 三屏在 `CheckLevel.Error` 档**判红**：取景屏（`AccessibilityScanTest`）、照片墙有物品时（`GalleryAccessibilityTest`）、
  确认页（`ReviewAccessibilityTest`）；
- 扫出来的**真问题**（F39）：横向滚动的芯片行里，被视口裁掉的那颗芯片被 ATF 按**可见宽度**量成「触摸目标只有 13dp」。
  判定实验（把词表缩到 2 项 → 报警消失）确认了原因；**修法不是抑制检查，而是让芯片换行**（`Row` → `FlowRow`）：
  12 颗类别芯片分三行铺开，谁都不会被裁——对被裁掉一半的芯片，读屏与手抖的用户本来就不友好；
- 剩下**只有一条必须人工**：TalkBack 的**焦点次序**走查（ATF 的规则集不覆盖次序）。

**判绿双证**：**34 类 / 217 例**、`freshXml=34/34`、`failures=0 errors=0`、Spotless 通过；`assembleRelease` 一并过。

---

"""
anchor = c.index("## 2026-10-10 · 用户签了名")
c = c[:anchor] + section + c[anchor:]
io.open(CHANGELOG, "w", encoding="utf-8", newline="").write(c)
print("CHANGELOG 已插入新一节")
