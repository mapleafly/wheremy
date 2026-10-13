# CHANGELOG：把「照片墙截图基线」改成真实结果（试了、跨平台不一致、撤掉）
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\docs\CHANGELOG.md"
t = io.open(PATH, encoding="utf-8").read()

old = ("顺带补了**照片墙的截图基线**（原先最常用的一屏反而没有基线）："
       "`gallery-wall.png`（关）与 `gallery-wall-only-inbox.png`（开），"
       "并新增 `GalleryScreenshotTest` 钉住这两种状态。")
new = ("**试过给照片墙补截图基线，最后撤掉了**（如实记一笔）：原先最常用的一屏确实没有基线"
       "（只有相机 3 张 + 设计系统 2 张），于是新写了 `GalleryWallScreenshotTest`，"
       "在本机录了 `gallery-wall.png`（3 件、芯片未选中）与 `gallery-wall-only-inbox.png`（筛掉 1 件、芯片选中）"
       "两张——本机看外观是对的。但**真 CI 上两张都报 `is changed`（像素不一致）**："
       "相机与设计系统那 5 张跨 Windows/Linux 是一致，这两张不是（怀疑与列表/芯片的入场动画帧时机有关）。"
       "与其为一张「外观」基线去追跨平台像素差，不如把力气花在**行为**上——"
       "那颗芯片的行为已由「芯片的标签与状态在同一个无障碍节点上」这条语义断言钉住"
       "（可选中、点一下变已选中、文案不变）。照片墙的**真机**截图按需存到 `artifacts/screenshots/`，"
       "不进 CI 比对（那里还存着别的真机证据）。")

if old in t:
    t = t.replace(old, new, 1)
    io.open(PATH, "w", encoding="utf-8", newline="").write(t)
    print("CHANGELOG 已改成真实结果")
else:
    print("!! 没匹配到那段，实际是：")
    for i, line in enumerate(t.split("\n"), 1):
        if "照片墙的截图基线" in line:
            print("  L%d: %s" % (i, line[:120]))
