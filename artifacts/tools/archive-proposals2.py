# PRD 的两个 ⬜ 条目指向提案 + CHANGELOG（引号一律用「」，别再触发那个陷阱）
import io
import sys

sys.stdout.reconfigure(errors="replace")
P = r"D:\src\dsh\wheremy\docs\PRD.md"
C = r"D:\src\dsh\wheremy\docs\CHANGELOG.md"
ok, fail = 0, []


def sub(path, old, new, label):
    global ok
    t = io.open(path, encoding="utf-8", newline="").read()
    if old not in t:
        fail.append(label)
        return
    io.open(path, "w", encoding="utf-8", newline="").write(t.replace(old, new, 1))
    ok += 1


sub(P,
    "要动的是多图 UI、引用计数与备份去重三条。",
    "要动的是多图 UI、引用计数与备份去重三条。\n"
    "**2026-10-13 已写提案等用户拍板：`proposals/FR-SRC-08-多图.md`**——实测底子比原先估计的厚"
    "（表本来就是多行、详情页已在渲染列表、备份已按路径去重），真正要写的是「加/删照片」那条竖切，"
    "加上修两处只看封面那一张的引用计数（`ItemRepository.kt:485`、`:504`）；代价从「大」下修为「中」。",
    "PRD FR-SRC-08")
sub(P,
    "代价：要引入口令派生与加密流，并且**必须能处理",
    "**2026-10-13 已写提案等用户拍板：`proposals/FR-BAK-09-备份加密.md`**——里面把威胁模型写实了"
    "（包里是照片 + 物品名/位置树/备注/到期日，等于一份「家里有什么、放在哪」的清单；"
    "**自动导出每天在跑，目录一旦落进云同步范围就是静默外传**），给了 5 个备选"
    "（含「只加提示不做加密」与「用设备密钥加密 → ❌ 换机就解不开」），"
    "并把「忘了口令」定死成 5 条（无后门也不留恢复密钥、但损失有界因为原库还在手机上、导入先验口令、"
    "口令只在本机记住、不做找回）。\n"
    "代价：要引入口令派生与加密流，并且**必须能处理",
    "PRD FR-BAK-09")

section = """## 2026-10-13 · 两个待拍板的功能写成了提案（FR-SRC-08 多图、FR-BAK-09 备份加密）

用户要求「把问题和备选项讲清楚，再让我决定」。于是按 `templates/feature-proposal.md` 各填一份，
放在 `docs/proposals/`（模板规定的路径），**两份都还没开工**——等拍板。

**这一轮最有价值的两件事，是「核实过的代价」与「写实的威胁模型」**：

- `FR-SRC-08` 原估**大**，实测底子厚得多：`item_photo` 本来就是多行带 `sortOrder`（**不动 schema、没有 Migration**）、
  `photosOf` 返回列表、**详情页已经在渲染照片列表**、备份 `putOnce` **已按路径去重**。
  真正要写的是「加/删照片」那条竖切 + 修两处只看封面那一张的引用计数（`ItemRepository.kt:485`、`:504`）
  ——否则多图之后会出现「该删的文件没删」或「删了别人还在用的文件」。代价下修为**中**。
- `FR-BAK-09` 的触发条件（「用户把 zip 放进网盘/共享目录」）在**自动导出打开之后很容易被动成立**：
  每天一份明文包落在用户选的目录，只要那目录在相册备份/网盘同步范围内，就是**静默外传**。
  包里是什么也核实了：照片 + `data.json`（物品名/位置树/备注/到期日）——等于「家里有什么、放在哪、值不值钱」。
  提案给了 5 个备选（A 只加提示 / B 全包加密 / C 随机强口令 / D 只加密 JSON → ❌ 等于没加密 /
  E 设备密钥 → ❌ 换机解不开），并把「忘了口令」定死成 5 条（无后门也不留恢复密钥、但损失有界因为原库还在手机上、
  导入先验口令、口令只在本机记住、不做找回）。建议 **B（口令用 C 的方式生成）**。

**顺手修了 `docs/README.md` 的两处**：文档地图补上 `proposals/` 与 `release-<版本>-<日期>.md` 两行；
并修掉 §2 表格里 `RELEASE.md` 那行**重复粘贴了半句**的错乱（那行原本把同一内容贴了两遍）。

---

"""
t = io.open(C, encoding="utf-8", newline="").read()
j = t.index("---\n\n") + len("---\n\n")
io.open(C, "w", encoding="utf-8", newline="").write(t[:j] + section + t[j:])
print("CHANGELOG 已插入；sub 成功 %d 处" % ok)
for f in fail:
    print("  未匹配：" + f)
