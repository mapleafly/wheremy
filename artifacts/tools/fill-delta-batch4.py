# 三条"已批准进首版"的做完了：更新 DELTA 的对应行、PRD 的标记与待做队列。
import re

DELTA = r"D:\src\dsh\wheremy\docs\DELTA.md"
PRD = r"D:\src\dsh\wheremy\docs\PRD.md"

NEW_ROWS = {
    "FR-FND-07": "| `FR-FND-07` | 一次查询上限 300 条，超出静默截断 | 🟡 | ✅ | **补上了告知**：撞到上限时照片墙上多一行「只显示了最近更新的 300 条，可能还有更多：再加一个词，或者用筛选取个范围」。分页没做（也不需要：这个量级下缩小范围更快） | `GalleryViewModelTest.搜索撞到三百条上限时界面要说出来`（301 条撞上限 vs 只搜到一条不报）；`ItemRepository.SEARCH_LIMIT` 与 `Daos.kt` 的 `LIMIT 300` 同值 |\n",
    "FR-TRC-04": "| `FR-TRC-04` | 累计调用次数/花费展示 | ⬜ | ✅ | 设置页 AI 小节多一行「累计调用 N 次（含重试）· 服务端报的 tokens 合计 M · 费用按你的账单为准（本机不折算）」。🔒 **故意不折算成钱**：各家的价格表会变，本机算出来的金额迟早是错的，说错钱比不说更糟 | `SettingsRepositoryTest.每次尝试都记一笔，重试也算`（含重试、接口不报 tokens 时次数照涨）；真机设置页读到「累计调用 1 次 · tokens 合计 929」 |\n",
    "FR-TRC-05": "| `FR-TRC-05` | 留痕里加耗时 | 🟡 | ✅ | 痕里每条多了 `elapsedMs=`：排「超时 vs 模型慢」全靠它 | `AnalysisTraceTest.一次尝试一条，四条信息都在`；真机留痕 `elapsedMs=832`（那次模型 0.8 秒就回了） |\n",
}

with open(DELTA, encoding="utf-8") as f:
    lines = f.readlines()

out = []
replaced = 0
for line in lines:
    m = re.match(r"^\|\s*`(FR-[A-Z]+-\d+)`\s*\|", line)
    if m and m.group(1) in NEW_ROWS:
        out.append(NEW_ROWS[m.group(1)])
        replaced += 1
    else:
        out.append(line)

with open(DELTA, "w", encoding="utf-8", newline="") as f:
    f.writelines(out)
print("DELTA 行已替换：%d" % replaced)

# —— PRD：标题标记 + 待做队列 ——
with open(PRD, encoding="utf-8") as f:
    text = f.read()

marker_changes = [
    ("**FR-FND-07 一次查询上限 300 条，超出静默截断** 🟡", "**FR-FND-07 一次查询上限 300 条，超出静默截断** ✅（已补告知）"),
    ("**FR-TRC-04 累计调用次数/花费展示** ⬜", "**FR-TRC-04 累计调用次数/花费展示** ✅"),
    ("**FR-TRC-05 留痕里加耗时** 🟡", "**FR-TRC-05 留痕里加耗时** ✅"),
]
for old, new in marker_changes:
    if old not in text:
        print("!! PRD 标题没找到：%s" % old)
    text = text.replace(old, new)

# 待做队列：这三条做完就移走
for fid in ("FR-FND-07", "FR-TRC-04", "FR-TRC-05"):
    pattern = re.compile(r"^\| " + fid + r" \|.*\n", re.M)
    text, n = pattern.subn("", text)
    print("PRD §5 移走 %s：%d 行" % (fid, n))

# §5.1 里 TRC-05 那条也移走
pattern = re.compile(r"^\| FR-TRC-05 \|.*\n", re.M)
text, n = pattern.subn("", text)
print("PRD §5.1 移走 FR-TRC-05：%d 行" % n)

note = ("> **2026-10-09 追加**：用户批准进首版的三条已做完并从下表移走——`FR-FND-07`（搜索上限的告知）、\n"
        "> `FR-TRC-04`（累计调用次数与 tokens，**不折算成钱**）、`FR-TRC-05`（留痕加耗时）。\n"
        "> 剩 `FR-CFG-06`（词表自定义管理）还没做。\n")
anchor = "> ⚠️ **其中三条是用户已批准要进首版的**，本实现还没做：`FR-FND-07`（搜索上限的告知）、`FR-TRC-04`（调用次数/花费）、`FR-CFG-06`（词表自定义管理）。\n"
if anchor in text:
    text = text.replace(anchor, note)
    print("PRD §5 的前言已更新")
else:
    print("!! PRD §5 前言没找到，需手工看一眼")

with open(PRD, "w", encoding="utf-8", newline="") as f:
    f.write(text)
print("PRD 已更新")
