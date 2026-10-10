# 补齐 GalleryScreen 缺的 5 个 import。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app\ui\gallery\GalleryScreen.kt"
t = io.open(PATH, encoding="utf-8").read()

need = [
    "import androidx.compose.foundation.shape.CircleShape",
    "import androidx.compose.material3.OutlinedButton",
    "import androidx.compose.material3.SnackbarHost",
    "import androidx.compose.material3.SnackbarHostState",
    "import androidx.compose.material3.Surface",
]
# 全部插在 `import androidx.compose.material3.Scaffold` 之前，随后由 spotless 排序
anchor = "import androidx.compose.material3.Scaffold"
missing = [i for i in need if i not in t]
if missing:
    t = t.replace(anchor, "\n".join(missing) + "\n" + anchor, 1)
    io.open(PATH, "w", encoding="utf-8", newline="").write(t)
    print("已补：%s" % ", ".join(m.split('.')[-1] for m in missing))
else:
    print("没有缺的 import")
