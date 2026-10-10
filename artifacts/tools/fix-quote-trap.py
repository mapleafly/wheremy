# 修 fix-release-signing-docs.py 里 new3 的 ASCII 引号（Python 语法会断）。
# 教训：这个坑今天第 5 次了——脚本里凡是中文引语，一律用「」。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\artifacts\tools\fix-release-signing-docs.py"
t = io.open(PATH, encoding="utf-8").read()

old = '六条"怎么算做完"逐条核过'
new = "六条「怎么算做完」逐条核过"
if old in t:
    t = t.replace(old, new, 1)
    io.open(PATH, "w", encoding="utf-8", newline="").write(t)
    print("已修 new3 的引号")
else:
    print("没找到，扫描整份脚本里可疑的 ASCII 引号：")
    for i, line in enumerate(t.split("\n"), 1):
        # 粗筛：含中文的行里出现成对的 ASCII 引号
        if any('\u4e00' <= ch <= '\u9fff' for ch in line) and line.count('"') >= 2 and 'old' not in line and 'new' not in line:
            print("  L%d: %s" % (i, line.strip()[:90]))
