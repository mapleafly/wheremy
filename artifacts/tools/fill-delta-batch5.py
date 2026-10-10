# FR-CFG-06 做完：DELTA 那行翻绿 + PRD 标记与待做队列同步。
import io
import re

DELTA = r"D:\src\dsh\wheremy\docs\DELTA.md"
PRD = r"D:\src\dsh\wheremy\docs\PRD.md"

NEW_ROW = ("| `FR-CFG-06` | 类别/季节/位置类型的自定义管理 | ⬜ | ✅ | **四套词表都进了 DataStore**（类别/季节/到期类型/位置类型）：空 = 用代码里的预设，"
           "所以「没改过」和「改回默认」是同一件事；写入侧清洗（去空白/丢空项/去重/封顶 24 项）；"
           "设置页新增「词表」一节。🔒 **改词表不动已有数据**（`RULES.md` §5.7 的白名单本来就不校验），"
           "🔗 **提示词跟着用户那份走**——不跟的话模型会回一堆词表外的值，芯片行显示不出选中态 | "
           "`SettingsRepositoryTest` 新增 5 例（默认空/清洗/封顶/清空回预设/恢复默认）；"
           "`AiLogicTest.用户改过词表时提示词用用户那份`；真机：设成 Anime/Model 后照片墙与确认页芯片都跟着变，"
           "**留痕里发出去的提示词是「从这 2 个里选一个：Anime Model」**，复位后回到衣物/数码 |\n")

text = io.open(DELTA, encoding="utf-8").read()
text, n = re.subn(r"^\| `FR-CFG-06` \|.*\n", NEW_ROW, text, flags=re.M)
print("DELTA FR-CFG-06 替换：%d 行" % n)
io.open(DELTA, "w", encoding="utf-8", newline="").write(text)

prd = io.open(PRD, encoding="utf-8").read()
for old, new in [
    ("**FR-CFG-06 类别/季节/位置类型的自定义管理** ⬜", "**FR-CFG-06 类别/季节/位置类型的自定义管理** ✅（四套词表进 DataStore）"),
    ("**FR-CFG-06 四套预设词表可自定义** ⬜", "**FR-CFG-06 四套预设词表可自定义** ✅"),
]:
    if old in prd:
        prd = prd.replace(old, new)
        print("PRD 标题已改：%s" % old[:24])

# 待做队列里移走
prd, n = re.subn(r"^\| FR-CFG-06 \|.*\n", "", prd, flags=re.M)
print("PRD §5 移走 FR-CFG-06：%d 行" % n)

prd = prd.replace(
    "> 剩 `FR-CFG-06`（词表自定义管理）还没做。\n",
    "> `FR-CFG-06`（四套词表自定义管理）**也做完了**：词表进 DataStore、设置页可改、提示词跟着用户那份走。\n"
    "> 至此**用户批准进首版的六条全部落地**。\n",
)
prd = prd.replace(
    "表里剩下的每一条都与 `DELTA.md` §2 的矩阵一致（**✅ 69 条、🟡 4 条、⬜ 13 条、⛔ 1 条**，共 87 条 FR）。",
    "表里剩下的每一条都与 `DELTA.md` §2 的矩阵一致（**✅ 70 条、🟡 4 条、⬜ 12 条、⛔ 1 条**，共 87 条 FR）。",
)
io.open(PRD, "w", encoding="utf-8", newline="").write(prd)
print("PRD 已更新")
