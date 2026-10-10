# 把多选工具条挪进 Column：用"卡片调用点 → 网格收尾 → when 收尾 → Column 收尾"这段做锚点。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app\ui\gallery\GalleryScreen.kt"
t = io.open(PATH, encoding="utf-8").read()

# 1) 剪出工具条块
m = re.search(r"\n        // —— 多选工具条（`FR-OPR-06`）.*?\n        \}\n", t, re.S)
assert m, "没找到工具条块"
block = m.group(0)
t = t[: m.start()] + "\n" + t[m.end() :]

# 2) 锚点：卡片调用点之后的四层收尾（items → grid → when → Column）
anchor = """                            onMenu = { vm.openMenu(summary.item.id) },
                        )
                    }
                }
            }
        }
"""
assert anchor in t, "没找到网格/Column 的收尾锚点"

# 3) 把工具条缩进 +4 插进 when 与 Column 收尾之间
indented = "\n".join(("    " + line) if line.strip() else line for line in block.strip("\n").split("\n"))
new = """                            onMenu = { vm.openMenu(summary.item.id) },
                        )
                    }
                }
            }

""" + indented + """
        }
"""
t = t.replace(anchor, new, 1)
io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("已把工具条挪进 Column（缩进 +4）")
