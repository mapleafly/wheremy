# 把 autoExportUriLine / autoExportLine 从 VM 挪进 SettingsUiState（与 leadDaysLine/visionStateLine 一致），
# 并修 setAutoExportEnabled 里对状态字段的引用。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app\ui\settings\SettingsViewModel.kt"
t = io.open(PATH, encoding="utf-8").read()

# 1) 从 VM 里剪掉这两段
pattern = re.compile(
    r"    /\*\* 保存位置那行.*?\n    val autoExportUriLine.*?\n\n    /\*\* 上次结果那行.*?\n    val autoExportLine.*?\n(?=\n)",
    re.S,
)
m = pattern.search(t)
if not m:
    print("没找到要挪的两段，需手工看一眼")
    sys.exit(1)
block = m.group(0)
t = t[: m.start()] + t[m.end() :]

# 2) 去掉 VM 专属的 `state.value.` 之类的差异（这里原本就是纯状态字段，直接用）
block = block.replace("    /**", "    /**", 1)

# 3) 插进状态类：放在 autoExportLastResult 之后
anchor = '    val autoExportLastResult: String = "",'
assert anchor in t, "状态类里没找到 autoExportLastResult"
t = t.replace(anchor, anchor + "\n" + block.rstrip() + "\n", 1)

# 4) setAutoExportEnabled 里的引用
t = t.replace(
    "if (enabled && autoExportUri.isBlank()) {",
    "if (enabled && state.value.autoExportUri.isBlank()) {",
    1,
)

io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("已挪进 SettingsUiState")
