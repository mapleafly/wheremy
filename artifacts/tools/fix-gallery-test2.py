# 删掉带反斜杠的那个坏测试块（保留后面正确的那个）。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\test\java\com\wheremy\app\ui\gallery\GalleryViewModelTest.kt"
t = io.open(PATH, encoding="utf-8").read()

bad = re.search(r"\n    @Test\n    fun \\组合筛选.*?\n    \}\n", t, re.S)
if not bad:
    print("!! 没找到带反斜杠的坏块")
else:
    t = t[: bad.start()] + t[bad.end() :]
    io.open(PATH, "w", encoding="utf-8", newline="").write(t)
    print("已删掉坏块")
