# 收尾：把三处"两份 CHANGELOG"的说法改成事实（工作区只有 docs/CHANGELOG.md 一份）。
import io
import sys

sys.stdout.reconfigure(errors="replace")
ROOT = r"D:\src\dsh\wheremy\docs"

# 1) PROJECT.md 第 6 行
p = ROOT + r"\PROJECT.md"
t = io.open(p, encoding="utf-8").read()
old = '| 6 | 版本记录 | `CHANGELOG.md`（**仓库根**的软件版本记录；与 `docs/CHANGELOG.md` 这份"文档集日志"是两份，见 `RELEASE.md` §8） | 每次发布一行 |'
new = '| 6 | 版本记录 | `docs/CHANGELOG.md`（**工作区唯一一份**变更日志：软件版本记录与文档集改动都在里面，按日期倒序分节，见 `RELEASE.md` §8） | 每次发布一行 |'
print("PROJECT.md 改到：%s" % (old in t))
t = t.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="").write(t)

# 2) RELEASE.md §8.1 标题与那一段
p = ROOT + r"\RELEASE.md"
t = io.open(p, encoding="utf-8").read()
t = t.replace("### 8.1 `CHANGELOG.md`（仓库根）", "### 8.1 `docs/CHANGELOG.md`（工作区唯一一份）", 1)
old_para = "**先分清两份同名文件**：**代码`docs/CHANGELOG.md`** = 软件版本记录（每一版给用户的变化、已知问题），发布负责人写；**`docs/CHANGELOG.md`** = **本文档集**的变更记录（谁在哪一轮改了哪份文档），改文档的人写。两者不是一回事。"
new_para = (
    "⚠️ 2026-10-10 更正：本节此前写「先分清两份同名文件：仓库根那份记软件版本、`docs/` 那份记文档集改动」——"
    "**工作区里只有 `docs/CHANGELOG.md` 一份**，根目录那份从来不存在。现在这一份同时承担两件事："
    "**软件版本记录**（每一版给用户的变化、已知问题，发布负责人写）与**文档集改动**（谁在哪一轮改了哪份文档），"
    "按日期倒序、每批一节；发布那一节另起标题（见 §8.2）。"
)
print("RELEASE.md 段落改到：%s" % (old_para in t))
t = t.replace(old_para, new_para, 1)
io.open(p, "w", encoding="utf-8", newline="").write(t)

# 3) PLAN.md 工作区树里那行
p = ROOT + r"\PLAN.md"
t = io.open(p, encoding="utf-8").read()
old_line = "└── CHANGELOG.md                  # 软件版本记录（与 docs/CHANGELOG.md 分工见 RELEASE.md §8）"
new_line = "└── （没有根目录 CHANGELOG.md：唯一一份在 docs/CHANGELOG.md，见 RELEASE.md §8.1）"
print("PLAN.md 树那行改到：%s" % (old_line in t))
t = t.replace(old_line, new_line, 1)
io.open(p, "w", encoding="utf-8", newline="").write(t)

# 复查
import glob, os
left = []
for path in glob.glob(ROOT + r"\*.md"):
    for i, line in enumerate(io.open(path, encoding="utf-8"), 1):
        if "仓库根" in line and "CHANGELOG" in line:
            left.append("%s:%d" % (os.path.basename(path), i))
print("仍提「仓库根 + CHANGELOG」：%s" % (", ".join(left) or "（无）"))
