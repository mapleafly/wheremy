# 在 RULES.md §5.6 补一句 FR-CFG-06 的说明（词表可改 → 提示词写生效的那份）。
import io

PATH = r"D:\src\dsh\wheremy\docs\RULES.md"
ANCHOR = "- 字段行的取值：`name` 必填要口语叫法（:24）、`category` 12 选 1（:25）"
ADD = (
    "- 🔗 **`FR-CFG-06`（本实现）**：类别与季节词表**可以被用户改**（设置页 →「词表」），"
    "所以提示词里写的是**生效的那份**（`Prompt.build(categories, seasons, paths)`）——"
    "「12 选 1」在用户改过之后就是「N 选 1」。不跟的话模型会回词表外的值，"
    "芯片行上显示不出选中态（§5.7 那个偏差就是这么来的）。"
)

text = io.open(PATH, encoding="utf-8").read()
if ANCHOR not in text:
    print("锚点没找到，需手工看一眼")
elif "FR-CFG-06" in text.split(ANCHOR)[0][-400:] + text.split(ANCHOR)[1][:400]:
    print("已经补过了")
else:
    text = text.replace(ANCHOR, ADD + "\n" + ANCHOR, 1)
    io.open(PATH, "w", encoding="utf-8", newline="").write(text)
    print("RULES §5.6 已补 FR-CFG-06 说明")
