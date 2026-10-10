# 补 import。
import io
import sys

sys.stdout.reconfigure(errors="replace")
SRC = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app"

def add(path, pairs):
    p = SRC + "\\" + path
    t = io.open(p, encoding="utf-8").read()
    for imp, anchor in pairs:
        if imp in t:
            print("  已有 %s" % imp.split(".")[-1])
            continue
        if anchor in t:
            t = t.replace(anchor, imp + "\n" + anchor, 1)
            print("  已补 %s" % imp.split(".")[-1])
        else:
            print("  !! 锚点缺失，要手工补 %s" % imp)
    io.open(p, "w", encoding="utf-8", newline="").write(t)

print("WheremyRoot:")
add(r"ui\WheremyRoot.kt", [
    ("import androidx.compose.foundation.layout.Arrangement", "import androidx.compose.foundation.layout.Box"),
    ("import androidx.compose.foundation.layout.Column", "import androidx.compose.foundation.layout.Box"),
])
print("ReviewScreen:")
add(r"ui\review\ReviewScreen.kt", [
    ("import androidx.compose.foundation.background", "import androidx.compose.foundation.layout.Arrangement"),
])
