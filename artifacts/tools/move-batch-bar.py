# 把多选工具条从 Column 外面挪进 Column 里（原来插在 Column 之后，成了兄弟节点 → 叠在顶部且被盖住）。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app\ui\gallery\GalleryScreen.kt"
t = io.open(PATH, encoding="utf-8").read()

# 1) 剪出工具条整块（从注释到它的 if 收尾）
m = re.search(r"\n        // —— 多选工具条（`FR-OPR-06`）.*?\n        \}\n", t, re.S)
if not m:
    print("!! 没找到工具条块")
    sys.exit(1)
block = m.group(0)
t = t[: m.start()] + "\n" + t[m.end() :]
print("已剪出工具条块（%d 行）" % block.count("\n"))

# 2) 找 Column 的收尾：`when {` 块结束后的那个 `}`（在 Scaffold content 里）
#    定位方式：找 `            }\n        }\n\n    }` 这种形状不可靠，改用"工具条原来所在位置往前第一个 `        }`"
idx = t.index("\n\n    }\n\n    // 批量搬到（FR-OPR-06）") if "\n\n    }\n\n    // 批量搬到（FR-OPR-06）" in t else -1
if idx < 0:
    print("!! 没找到 Scaffold content 的收尾锚点")
    sys.exit(1)

# 在 idx 之前插入工具条（此时 idx 指向 `    }` 前的空行，即 Column 收尾 `        }` 之后）
# 更稳的做法：把块插到"最后一个 `        }`"之前
col_end = t.rindex("        }\n", 0, idx)
t = t[:col_end] + block.lstrip("\n") + t[col_end:]
io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("已把工具条插进 Column 内（Column 收尾之前）")
