# 修归档脚本里 Python 字符串内的 ASCII 双引号（改成「」）——这个坑今天踩了第三次了。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\artifacts\tools\archive-ai11-bak03.py"
t = io.open(PATH, encoding="utf-8").read()

# F45 那段里的 ASCII 引号
t = t.replace('"缺省值等于当前值"这种写法会把"升版本"这件事悄悄吃掉', "「缺省值等于当前值」这种写法会把「升版本」这件事悄悄吃掉")
t = t.replace('**"缺省值等于当前值"这种写法', "**「缺省值等于当前值」这种写法")

# 兜底：把中文语境里成对出现的 ASCII 引号换成「」
lines = t.split("\n")
fixed = []
for line in lines:
    if line.count('"') > 2 and ("「" not in line) and ("| F45" in line or "教训" in line):
        # 只处理明显是中文引号的那几行
        parts = line.split('"')
        if len(parts) % 2 == 1:
            rebuilt = parts[0]
            for i in range(1, len(parts), 2):
                rebuilt += "「" + parts[i] + "」" + (parts[i + 1] if i + 1 < len(parts) else "")
            line = rebuilt
    fixed.append(line)
t = "\n".join(fixed)
io.open(PATH, "w", encoding="utf-8", newline="").write(t)

# 立刻验证语法
import py_compile
try:
    py_compile.compile(PATH, doraise=True)
    print("语法 OK")
except Exception as e:
    print("仍有语法错：%s" % e)
