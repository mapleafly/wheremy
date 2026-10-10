# 修 CHANGELOG 的抬头与全仓库对"仓库根 CHANGELOG.md"的失效引用（那个文件不存在）。
import glob
import io
import os
import re

ROOT = r"D:\src\dsh\wheremy"

# 1) docs/CHANGELOG.md 抬头改成实话
p = os.path.join(ROOT, "docs", "CHANGELOG.md")
t = io.open(p, encoding="utf-8").read()
old_head = "**这是本文档集自己的日志**，不是软件版本的发布记录——软件版本记录在仓库根 `CHANGELOG.md`（见 `RELEASE.md` §8）。"
new_head = (
    "**这是本工作区唯一的变更日志**：软件的阶段进展（P0…P6、每批功能与修复）与文档集的改动都记在这里，"
    "按日期倒序、每批一节。⚠️ 2026-10-10 更正：本文件此前写着「软件版本的发布记录在仓库根 `CHANGELOG.md`」——"
    "**那个文件从来不存在**，工作区里只有这一份；`RELEASE.md` §8.1 与本文的指路已按事实改掉。"
)
if old_head in t:
    t = t.replace(old_head, new_head, 1)
    io.open(p, "w", encoding="utf-8", newline="").write(t)
    print("docs/CHANGELOG.md 抬头已更正")
else:
    print("抬头没匹配（可能已改过）")

# 2) 全仓库：把"仓库根 `CHANGELOG.md`"这类失效指路改成 docs/CHANGELOG.md
changed = []
for path in glob.glob(os.path.join(ROOT, "docs", "*.md")):
    text = io.open(path, encoding="utf-8").read()
    before = text
    text = text.replace("仓库根 `CHANGELOG.md`", "`docs/CHANGELOG.md`")
    text = text.replace("仓库根的 `CHANGELOG.md`", "`docs/CHANGELOG.md`")
    text = text.replace("仓库根 CHANGELOG.md", "docs/CHANGELOG.md")
    if text != before:
        io.open(path, "w", encoding="utf-8", newline="").write(text)
        changed.append(os.path.basename(path))
print("改了指路的文档：%s" % (", ".join(changed) or "（无）"))

# 3) 复查还有没有指向仓库根 CHANGELOG 的说法
left = []
for path in glob.glob(os.path.join(ROOT, "docs", "*.md")):
    for i, line in enumerate(io.open(path, encoding="utf-8"), 1):
        if "仓库根" in line and "CHANGELOG" in line:
            left.append("%s:%d" % (os.path.basename(path), i))
print("仍提到「仓库根 + CHANGELOG」的行：%s" % (", ".join(left) or "（无）"))
