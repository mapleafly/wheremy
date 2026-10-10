# 修复：把 autoExportUriLine / autoExportLine 从 data class 的**参数表**里挪进类体。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app\ui\settings\SettingsViewModel.kt"
t = io.open(PATH, encoding="utf-8").read()

# 1) 把参数表里那两段派生属性整块剪出来（从「保存位置那行」的 KDoc 到 autoExportBusy 之前）
pattern = re.compile(
    r"    /\*\* 保存位置那行.*?\n(?:.*?\n)*?        \}\n\n    /\*\* 上次结果那行.*?\n(?:.*?\n)*?        \}\n\n",
)
m = pattern.search(t)
if not m:
    print("!! 没匹配到要剪的块，需要手工处理")
    sys.exit(1)
block = m.group(0)
t = t[: m.start()] + t[m.end() :]
print("已剪出 %d 行" % block.count("\n"))

# 2) 插进类体：data class 的 `) {` 之后
anchor = re.search(r"^\) \{\n", t, flags=re.M)
if not anchor:
    print("!! 没找到 data class 的 ') {' 收尾")
    sys.exit(1)
insert_at = anchor.end()
t = t[:insert_at] + block + t[insert_at :]

io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("已插入类体")
