# 把「播报标题」换成 paneTitle 语义（官方对弃用 announceForAccessibility 的建议：用语义描述界面）。
import io
import sys

sys.stdout.reconfigure(errors="replace")
BASE = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app\ui"
FILES = [
    BASE + r"\gallery\GalleryScreen.kt",
    BASE + r"\tree\TreeScreen.kt",
    BASE + r"\settings\SettingsScreen.kt",
    BASE + r"\review\ReviewScreen.kt",
]

for path in FILES:
    t = io.open(path, encoding="utf-8").read()
    before = t
    name = path.split("\\")[-1]

    # 1) 去掉调用与 import
    t = t.replace("\n    AnnounceScreenTitle(title)\n", "\n")
    t = t.replace("import com.wheremy.app.ui.AnnounceScreenTitle\n", "")

    # 2) 补 paneTitle 的 import
    anchor = "import androidx.compose.ui.semantics.heading\n"
    if anchor in t and "semantics.paneTitle" not in t:
        t = t.replace(anchor, anchor + "import androidx.compose.ui.semantics.paneTitle\n", 1)

    # 3) 根 Scaffold 上挂 paneTitle（读屏据此知道「这是哪一屏」）
    t = t.replace(
        "    Scaffold(\n",
        "    Scaffold(\n        modifier = Modifier.semantics { paneTitle = title },\n",
        1,
    )

    # 4) 注释里「播报」改成 paneTitle
    t = t.replace("// 标题只写一份：TopAppBar 与读屏播报共用（F50）",
                  "// 标题只写一份：TopAppBar 与读屏的 paneTitle 共用（F50）")

    if t != before:
        io.open(path, "w", encoding="utf-8", newline="").write(t)
        print("  已改：%s" % name)
    else:
        print("  !! 没改动：%s" % name)
