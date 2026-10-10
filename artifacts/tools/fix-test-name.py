# 测试函数名里不能用 ASCII 双引号：编译出的 .class 文件名会含 "，Windows 上非法。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\test\java\com\wheremy\app\ui\review\BlankDraftTest.kt"
t = io.open(PATH, encoding="utf-8").read()

old = 'fun `确认页打开手填条目：不跑分析，也不说"照片丢了"`() {'
new = 'fun `确认页打开手填条目：不跑分析，也不说照片丢了`() {'
if old in t:
    t = t.replace(old, new, 1)
    print("已改名")
else:
    # 兜底：把所有反引号函数名里的 ASCII 双引号换掉
    import re
    t2, n = re.subn(r"(`[^`]*?) \"([^`]*?`)", lambda m: m.group(0), t)
    print("没直接匹配，先看看实际文本")
    for line in t.split("\n"):
        if "fun `" in line and '"' in line:
            print("  含引号的测试名：%s" % line.strip())
            t = t.replace(line, line.replace('"', ""), 1)
    io.open(PATH, "w", encoding="utf-8", newline="").write(t)
    print("已按行清理")
    sys.exit(0)

io.open(PATH, "w", encoding="utf-8", newline="").write(t)
