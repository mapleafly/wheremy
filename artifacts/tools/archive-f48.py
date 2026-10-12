# 记录 F48（微信输入法 × adjustResize 导致键盘画不出来）+ CHANGELOG
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
DOCS = r"D:\src\dsh\wheremy\docs"
PLAN = DOCS + r"\PLAN.md"
CHANGELOG = DOCS + r"\CHANGELOG.md"
ARCH = DOCS + r"\ARCHITECTURE.md"

# —— PLAN §3.4 追加 F48 ——
p = io.open(PLAN, encoding="utf-8").read()
f48 = (
    "| F48 | **微信输入法 + `adjustResize` → 键盘画不出来，导致填不了名称、存不进去** | "
    "真机走查第 3 步卡住：输入框能聚焦、系统也报 `mInputShown=true`，但键盘完全不出现"
    "（`mInputMethodWindowVisibleHeight=-43`）。对照实验（同机同版本，只改这一项）："
    "微信+`adjustResize` 不显示（-43）／微信+`adjustPan` 正常（864）／"
    "搜狗+`adjustResize` 正常（885）／搜狗+`adjustPan` 正常（885）／"
    "微信+系统设置（它自己就是 `adjustPan`）正常（864）。"
    "即「微信输入法 × `adjustResize`」这个组合坏，换 `adjustPan` 两边都正常。"
    "**修法**：清单里 `windowSoftInputMode` 改成 `adjustPan`，并在清单里写明不要改回去。"
    "代价是键盘弹出时整窗上移而非缩放；要回到缩放语义得走官方对 targetSdk 35+ 的建议"
    "（Compose 里 `Modifier.imePadding()` 消费 `WindowInsets.ime`），并重录截图基线。"
    "**教训：这类问题只能真机走查发现——本机自动化（Robolectric 截图、单测）永远看不到键盘** |\n"
)
if "F48" not in p:
    m = re.search(r"^\| F47 \|.*\n", p, flags=re.M)
    if m:
        p = p[: m.end()] + f48 + p[m.end() :]
        print("PLAN 已追加 F48")
    else:
        print("!! 没找到 F47 行")
else:
    print("PLAN 已有 F48")
io.open(PLAN, "w", encoding="utf-8", newline="").write(p)

# —— CHANGELOG 新一节 ——
c = io.open(CHANGELOG, encoding="utf-8").read()
section = """## 2026-10-12 · 真机走查抓到「键盘弹不出来」并修掉（F48）

**现象**：功能验收第 3 步（存进去一件）卡住——点「名称」输入框能聚焦、光标在闪，但**键盘不出现**，
于是填不了名称、也存不进去。第 1、2 步（打开、拍照）都通过。

**排查**：先排除 TalkBack（关掉后仍然复现），再在同一台机器上做对照实验，只改一个变量：

| 组合 | 键盘 | 可见高度 |
|---|---|---|
| 微信输入法 + 系统设置 | ✅ | 864 |
| 微信输入法 + 本应用（`adjustResize`） | ❌ | **-43** |
| 搜狗输入法 + 本应用（`adjustResize`） | ✅ | 885 |
| **微信输入法 + 本应用（`adjustPan`）** | ✅ | 864 |
| 搜狗输入法 + 本应用（`adjustPan`） | ✅ | 885 |

系统状态一直报 `mInputShown=true`（"键盘已显示"），只有 `mInputMethodWindowVisibleHeight`
是负值、截图里也确实没有键盘——所以**不能只看系统标志位**，必须截图。

**修法**：`AndroidManifest.xml` 的 `windowSoftInputMode` 从 `adjustResize` 改成 `adjustPan`，
并在清单里写清为什么不能改回去。系统设置自己也用 `adjustPan`，算是同机型的先例。

**代价与后路**：键盘弹出时整窗上移而不是缩放。本应用需要打字的输入框很少且都靠近上半屏
（名称/搜索/位置名/设置项），实测可用。将来若要回到"缩放"语义，正确做法不是改回
`adjustResize`，而是按官方对 targetSdk 35+ 的建议在 Compose 里消费 `WindowInsets.ime`
（`Modifier.imePadding()`），那时要连截图基线一起重录。

**顺带确认**：这一条**不是** TalkBack 的问题——关掉 TalkBack 一样复现；也没有为了迁就 TalkBack
改任何界面或交互（按"TalkBack 不能影响正常使用"的原则）。

---

"""
anchor = c.index("## 2026-10-10 · CI 首次真跑全绿")
c = c[:anchor] + section + c[anchor:]
io.open(CHANGELOG, "w", encoding="utf-8", newline="").write(c)
print("CHANGELOG 已插入")

# —— ARCHITECTURE：如果里面写了 windowSoftInputMode，就同步 ——
a = io.open(ARCH, encoding="utf-8").read()
if "adjustResize" in a:
    a = a.replace("adjustResize", "adjustPan")
    io.open(ARCH, "w", encoding="utf-8", newline="").write(a)
    print("ARCHITECTURE 里的 adjustResize 已同步为 adjustPan")
else:
    print("ARCHITECTURE 没提 windowSoftInputMode（无需改）")
