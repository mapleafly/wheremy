# 归档 FR-BAK-08（定时自动导出）+ F40/F41（真机环境两条坑）+ CHANGELOG
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
    "| `FR-BAK-08` | 定时自动导出 | ⬜ | ✅ | **每天一份**：设置页开关 + 「选择保存位置」"
    "（SAF `OpenDocumentTree`，**当场持久化授权**，不然重启后打不开）+ 「立刻导出一份」；"
    "WorkManager 周期任务 1 天、唯一名 `wheremy-auto-export`、`KEEP` 不重复排队，**开关在 worker 里判**"
    "（这样设置里开关一下立刻生效，也不用管取消）；文件名 `wheremy-auto-<本地日期>.zip`，"
    "只留最近 7 份（认前缀，绝不碰同目录里别的文件）；**成功失败都写进设置页那一行** | "
    "`AutoExporterTest` 5 例（没配落点要说明 / 名字带日期且是 zip / 写失败要说原因 / "
    "**导出的包能原样导回来** / 常量一致）；真机：选 `/sdcard/wheremy-backup` → 「立刻导出一份」→ "
    "`wheremy-auto-2026-10-10.zip`（43 KB）真躺在那个目录里，设置页显示「上次（10月10日 10:33）：已导出 …」；"
    "WorkManager 库里 `AutoExportWorker` 周期 86,400,000 ms（连 `-wal` 拉库核对） |\n"
)
d, n = re.subn(r"^\| `FR-BAK-08` \|.*\n", new_row, d, flags=re.M)
print("DELTA FR-BAK-08 替换：%d" % n)
io.open(DELTA, "w", encoding="utf-8", newline="").write(d)

# —— PLAN：F40 / F41（真机环境）——
p = io.open(PLAN, encoding="utf-8").read()
extra = (
    "| F40 | **MIUI/HyperOS 的 SAF 目录选择器不让选存储根目录与 `Download`**（「无法使用此文件夹／为保护您的隐私」） | "
    "自动导出要用户选一个落点，而真机上根目录和 `Download` 都被系统拒了。**做法**：先用 `adb shell mkdir` "
    "建一个自己的目录（`/sdcard/wheremy-backup`）再在选目录器里选它——**用户侧同样适用**："
    "在选目录器里点「新建文件夹」建一个自己的文件夹即可 |\n"
    "| F41 | **真机输入法是微信输入法 + 小鹤双拼，`adb shell input text` 打不出英文** | "
    "双拼会把字母键转成汉字，所以往「新建文件夹」对话框里 `input text wheremy-backup` 得到的是「－」。"
    "**做法**：真机自动化**不用输入法打字**——要文件/目录就先 `adb shell` 建好，再在界面里点选；"
    "万不得已要打字，先 `adb shell ime list -s` 切到 `com.android.adbkeyboard` 之类的无输入法，或改走 `content` 命令 |\n"
)
m = re.search(r"^\| F39 \|.*\n", p, flags=re.M)
if m and "F40" not in p:
    p = p[: m.end()] + extra + p[m.end() :]
    io.open(PLAN, "w", encoding="utf-8", newline="").write(p)
    print("PLAN 已追加 F40/F41")
else:
    print("PLAN 跳过（已有 F40 或没找到 F39）")

# —— PRD ——
t = io.open(PRD, encoding="utf-8").read()
m2 = re.search(r"^\*\*FR-BAK-08[^\n]*\*\* ⬜$", t, flags=re.M)
if m2:
    t = t[: m2.start()] + m2.group(0)[:-1] + "✅（每天一份 + 立刻一份；WorkManager 周期 1 天）" + t[m2.end() :]
    print("PRD 标题已改")
else:
    print("PRD 标题没匹配")
t, n5 = re.subn(r"^\| FR-BAK-08 \|.*\n", "", t, flags=re.M)
print("PRD §5 移走：%d 行" % n5)
t = t.replace(
    "表里剩下的每一条都与 `DELTA.md` §2 的矩阵一致（**✅ 73 条、🟡 2 条、⬜ 11 条、⛔ 1 条**，共 87 条 FR）。",
    "表里剩下的每一条都与 `DELTA.md` §2 的矩阵一致（**✅ 74 条、🟡 2 条、⬜ 10 条、⛔ 1 条**，共 87 条 FR）。",
)
io.open(PRD, "w", encoding="utf-8", newline="").write(t)
print("PRD 已更新")

# —— CHANGELOG ——
c = io.open(CHANGELOG, encoding="utf-8").read()
section = """## 2026-10-10 · 定时自动导出（`FR-BAK-08`）

**每天自动写一份包到你选的文件夹**，不用记得手动导出。

- 设置页「备份」里多了：开关「每天自动导出一份」、「选择保存位置」、「立刻导出一份」，
  以及两行实话——**保存位置**（解码后显示成 `wheremy-backup`，不是 `%3A` 那种怪东西）与**上次结果**；
- 落点走 SAF：`OpenDocumentTree` 选目录，**当场 `takePersistableUriPermission`**——
  不持久化的话重启后这个 URI 就打不开了，而"每天自动导出"恰恰要跨重启；
- WorkManager 周期任务：1 天、唯一名 `wheremy-auto-export`、`KEEP`（不重复排队）。
  **开关在 worker 里判**，不是在入队时判——周期任务一旦入队就一直在，这样设置里开关一下立刻生效，
  也不用管"关掉时要不要取消"；
- 文件名 `wheremy-auto-<本地日期>.zip`，**只留最近 7 份**（认前缀，绝不碰同目录里别的文件）；
- **失败要说出来**：自动导出失败而用户不知道，等于没有备份——无论成败都写进设置页那一行。

**真机验证**（debug 包）：选 `/sdcard/wheremy-backup` → 授权 → 「立刻导出一份」→
`/sdcard/wheremy-backup/wheremy-auto-2026-10-10.zip`（44,290 字节）真的躺在那里；
设置页显示「上次（10月10日 10:33）：已导出 wheremy-auto-2026-10-10.zip（43 KB）」；
WorkManager 库里 `AutoExportWorker` 周期 86,400,000 ms（**连 `-wal` 一起拉库**核对）。

**真机环境两条坑（已记进 `PLAN.md` §3.4）**：

- **F40**：MIUI/HyperOS 的 SAF 选择器**不让选存储根目录与 `Download`**（「无法使用此文件夹／为保护您的隐私」）。
  先用 `adb shell mkdir` 建 `/sdcard/wheremy-backup` 再选它；用户侧同理——在选目录器里「新建文件夹」建自己的目录；
- **F41**：真机输入法是**微信输入法 + 小鹤双拼**，`adb shell input text` 打不出英文（字母被双拼转成汉字，
  所以"新建文件夹"里得到的是「－」）。真机自动化**不再用输入法打字**：要目录/文件就先 `adb shell` 建好再点选。

**判绿双证**：**37 类 / 232 例**、`freshXml=37/37`、`failures=0 errors=0`、Spotless 通过；`assembleRelease` 一并过。

---

"""
anchor = c.index("## 2026-10-10 · 结果排序四档")
c = c[:anchor] + section + c[anchor:]
io.open(CHANGELOG, "w", encoding="utf-8", newline="").write(c)
print("CHANGELOG 已插入")
