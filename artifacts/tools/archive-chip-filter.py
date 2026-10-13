# 把「只看待归类」换成 FilterChip 这件事写进 UI.md / PRD.md / CHANGELOG
import io
import sys

sys.stdout.reconfigure(errors="replace")
D = r"D:\src\dsh\wheremy\docs"
ok = 0


def sub(path, old, new, label):
    global ok
    t = io.open(path, encoding="utf-8").read()
    if old not in t:
        print("  !! 没匹配：" + label)
        return
    io.open(path, "w", encoding="utf-8", newline="").write(t.replace(old, new, 1))
    print("  已改：" + label)
    ok += 1


# —— UI.md §3.1 ——
sub(D + r"\UI.md",
    "- actions：只在**非** inbox 模式给一颗「只看待归类」芯片（`:84-88`）。"
    "待归类页自己不再给这颗（它是这一页的定义，见 `RULES.md` §1.1）。",
    "- actions：只在**非** inbox 模式给一颗「只看待归类」芯片（`:121-125`）。"
    "待归类页自己不再给这颗（它是这一页的定义，见 `RULES.md` §1.1）。"
    "**2026-10-13 调整**：这颗芯片从 `AssistChip` 换成 `FilterChip(selected = state.filters.onlyInbox)` —— "
    "它是**开关行为**，`FilterChip` 自带 `selected` 语义（读屏念「只看待归类，已选中」），"
    "文案不再随状态变（原先靠「只看待归类 · 开」这种改文案的写法），也和这一屏其余筛选芯片同一种控件。"
    "两种状态的截图基线见 `app/src/test/screenshots/gallery-wall.png` 与 `gallery-wall-only-inbox.png`。",
    "UI.md §3.1 actions")

# —— PRD.md FR-FND-04 ——
sub(D + r"\PRD.md",
    "顶栏 `GalleryScreen.kt:88` 「只看待归类」芯片在两个视图里语义一致。",
    "顶栏 `GalleryScreen.kt:121` 「只看待归类」芯片在两个视图里语义一致；"
    "2026-10-13 起它是 `FilterChip`，**选中状态由组件的 `selected` 语义表达**（读屏念「已选中」），不再靠改文案。",
    "PRD.md FR-FND-04")

# —— CHANGELOG：在审计那节末尾补"已采纳建议并改了" ——
sub(D + r"\CHANGELOG.md",
    "**结论**：除设置页那两处开关外，**没有第二处",
    "**改动（用户批准后）**：「只看待归类」已从 `AssistChip` 换成 `FilterChip(selected = …)`（2026-10-13）——"
    "读屏改念「只看待归类，已选中」，文案不再随状态变，且与其余 17 颗筛选芯片同一种控件。"
    "顺带补了**照片墙的截图基线**（原先最常用的一屏反而没有基线）："
    "`gallery-wall.png`（关）与 `gallery-wall-only-inbox.png`（开），"
    "并新增 `GalleryScreenshotTest` 钉住这两种状态。\n\n"
    "**结论**：除设置页那两处开关外，**没有第二处",
    "CHANGELOG 审计节补改动")

print("共改 %d 处" % ok)
