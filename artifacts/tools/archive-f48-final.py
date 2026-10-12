# 把 F48 的"修法"从 adjustPan 更正为最终方案（不声明 + imePadding），并补 UI.md 的记录。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
DOCS = r"D:\src\dsh\wheremy\docs"
PLAN = DOCS + r"\PLAN.md"
CHANGELOG = DOCS + r"\CHANGELOG.md"
UI = DOCS + r"\UI.md"

# —— PLAN F48：整行替换 ——
p = io.open(PLAN, encoding="utf-8").read()
new_f48 = (
    "| F48 | **微信输入法 + `windowSoftInputMode=\"adjustResize\"` → 键盘画不出来，填不了名称、存不进去** | "
    "真机走查第 3 步卡住：输入框能聚焦、系统也报 `mInputShown=true`，但键盘完全不出现"
    "（`mInputMethodWindowVisibleHeight=-43`，截图里没有键盘）。对照实验（同机同版本，只改这一项，都截图核对）："
    "微信+`adjustResize` 不显示（-43）／微信+**不声明** 正常（864）／微信+`adjustPan` 正常（864）／"
    "搜狗+三者都正常（885）→ 坏的是「微信输入法 × `adjustResize`」这个组合。"
    "**修法**：清单里**不再声明** `windowSoftInputMode`（targetSdk 36 平台强制 edge-to-edge，"
    "官方建议就是别靠它缩放窗口），改为在 Compose 里消费 IME inset——"
    "`ReviewScreen` / `SettingsScreen` 的可滚动 Column 加 `Modifier.imePadding()`（放在 `verticalScroll` 之前，"
    "缩的是滚动视口，于是 Compose 会把正在编辑的那一格自动滚进可视区）。"
    "真机复核：名称框、靠下的 颜色/材质、设置页 y≈2010 的接口地址框，都能停在键盘上方。"
    "**附带发现**：`UI.md` §7 本来就要求设置页用 `imePadding`（参照实现有、我们没实现），"
    "所以这同时是一处**规格偏差**的补齐。"
    "**教训：这类问题只能真机走查发现——Robolectric 截图与单测都看不到键盘** |\n"
)
m = re.search(r"^\| F48 \|.*\n", p, flags=re.M)
if m:
    p = p[: m.start()] + new_f48 + p[m.end() :]
    print("PLAN F48 已更正为最终方案")
else:
    print("!! 没找到 F48 行")
io.open(PLAN, "w", encoding="utf-8", newline="").write(p)

# —— CHANGELOG：把"修法/代价与后路"两段换成最终方案 ——
c = io.open(CHANGELOG, encoding="utf-8").read()
old_fix = """**修法**：`AndroidManifest.xml` 的 `windowSoftInputMode` 从 `adjustResize` 改成 `adjustPan`，
并在清单里写清为什么不能改回去。系统设置自己也用 `adjustPan`，算是同机型的先例。

**代价与后路**：键盘弹出时整窗上移而不是缩放。本应用需要打字的输入框很少且都靠近上半屏
（名称/搜索/位置名/设置项），实测可用。将来若要回到"缩放"语义，正确做法不是改回
`adjustResize`，而是按官方对 targetSdk 35+ 的建议在 Compose 里消费 `WindowInsets.ime`
（`Modifier.imePadding()`），那时要连截图基线一起重录。"""
new_fix = """**修法**（按官方对 targetSdk 35+ 的建议，不是"绕过去"）：

1. `AndroidManifest.xml` 里**不再声明** `windowSoftInputMode`（原来是 `adjustResize`）——
   平台已强制 edge-to-edge，官方就是要求别靠它缩放窗口；
2. 需要打字的可滚动屏加 `Modifier.imePadding()`（`ReviewScreen` / `SettingsScreen`，放在
   `verticalScroll` **之前**：缩的是滚动视口，于是 Compose 会把正在编辑的那一格自动滚进可视区）。

**真机复核**：名称框 ✓；靠下的「颜色/材质」✓；设置页 y≈2010 的「接口地址」框点下去后
**自动滚到键盘上方完整可见** ✓（没有 `imePadding` 时键盘只是盖在内容上，靠下的输入框会被挡住）。

**附带补上一处规格偏差**：`UI.md` §7 本来就写着设置页要 `verticalScroll + imePadding`
（参照实现有、我们之前没实现），这次一并补齐，并在 `UI.md` 里把两屏都记上。

**顺带证实**：文字**能**进到框里（走查过程中名称框里真的出现了字符），所以"填不进去"纯粹是
键盘没出来导致的。"""
if old_fix in c:
    c = c.replace(old_fix, new_fix, 1)
    print("CHANGELOG 修法段已更正")
else:
    print("!! CHANGELOG 修法段没匹配")
io.open(CHANGELOG, "w", encoding="utf-8", newline="").write(c)

# —— UI.md：把设置页那行标注为"已实现"，并给确认页补一行 ——
u = io.open(UI, encoding="utf-8").read()
old_settings = "整屏 `verticalScroll` + `imePadding`（`:129-132`）——多了 AI 这一节之后一屏装不下，键盘弹起来也得能滚到正在编辑的那一格。三节按次序："
new_settings = ("整屏 `verticalScroll` + `imePadding`（`:129-132`）——多了 AI 这一节之后一屏装不下，键盘弹起来也得能滚到正在编辑的那一格。"
                "**2026-10-12 补齐**：`imePadding` 放在 `verticalScroll` 之前；此前漏了它，"
                "真机上键盘会把靠下的输入框盖住（见 `PLAN.md` F48）。三节按次序：")
if old_settings in u:
    u = u.replace(old_settings, new_settings, 1)
    print("UI.md 设置页那行已标注补齐")
else:
    print("!! UI.md 设置页那行没匹配")

# 确认页：找到它的标题行，补一条 imePadding 说明（若还没写）
if "imePadding" not in u.split("## 7. 设置页")[0]:
    m2 = re.search(r"^## 6\..*$", u, flags=re.M)
    if m2:
        note = ("\n\n整屏 `verticalScroll` + `imePadding`（`ReviewScreen.kt:134` 一带）——字段多、一屏装不下；"
                "`imePadding` 放在 `verticalScroll` 之前，键盘弹起时可视区缩到键盘上方，"
                "Compose 会把正在编辑的那一格自动滚进来（2026-10-12 真机验证，见 `PLAN.md` F48）。")
        u = u[: m2.end()] + note + u[m2.end() :]
        print("UI.md 确认页已补 imePadding 说明")
    else:
        print("!! 没找到 UI.md 的确认页标题")
io.open(UI, "w", encoding="utf-8", newline="").write(u)
