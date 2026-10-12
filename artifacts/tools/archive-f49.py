# 记 F49（文档里的 TalkBack 路径在 HyperOS 3 上不存在）+ CHANGELOG
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
D = r"D:\src\dsh\wheremy\docs"
PLAN = D + r"\PLAN.md"
CHANGELOG = D + r"\CHANGELOG.md"

# —— PLAN F49 ——
p = io.open(PLAN, encoding="utf-8").read()
f49 = (
    "| F49 | **文档里写的「设置 → 更多设置 → 无障碍」在 HyperOS 3 上不存在** | "
    "用户按 `templates/talkback-walkthrough.md` 找 TalkBack，**完全找不到**。"
    "在真机上一层层翻出真实路径：HyperOS 3.0.309.0（小米 13）里这一项叫 **「辅助功能」**、"
    "在设置首页**往下滑一屏**才看得到（「未成年人模式」与「更多设置」之间），点进去分"
    "**通用 / 视觉 / 听觉 / 肢体**四个页签，TalkBack 在 **视觉 → 屏幕阅读器** 里；"
    "开关叫 **使用「TalkBack」**，旁边还有 **「TalkBack」快捷方式**（打开后同时按两个音量键即可开/关）。"
    "**教训**：系统设置路径这类「说明书」必须**在目标机型上逐屏核过**再写，"
    "凭通用印象写出来的路径等于让用户白找 |\n"
)
if "F49" not in p:
    m = re.search(r"^\| F48 \|.*\n", p, flags=re.M)
    if m:
        p = p[: m.end()] + f49 + p[m.end() :]
        print("PLAN 已追加 F49")
    else:
        print("!! 没找到 F48 行")
else:
    print("PLAN 已有 F49")
io.open(PLAN, "w", encoding="utf-8", newline="").write(p)

# —— CHANGELOG ——
c = io.open(CHANGELOG, encoding="utf-8").read()
section = """## 2026-10-12 · 修掉 TalkBack 走查单里写错的系统路径（F49，真机逐屏核过）

**用户反馈**：按 `docs/templates/talkback-walkthrough.md` 去找 TalkBack，**完全找不到**。

**根因**：那节写的是「设置 → 更多设置 → 无障碍 → TalkBack」——**HyperOS 3 上不存在这条路径**
（我凭通用印象写的，没在目标机型上核过）。

**在真机（小米 13 / HyperOS OS3.0.309.0.WMCCNXM / Android 16）上翻出来的真实路径**：

1. **设置** → 首页**往下滑一屏** → 在「未成年人模式」与「更多设置」之间点 **辅助功能**
   （⚠️ 不叫「无障碍」；设置页顶部搜索框直接搜 `TalkBack` 也能跳到）；
2. 顶部页签 **通用 / 视觉 / 听觉 / 肢体** → 点 **视觉**；
3. **屏幕阅读器** 组里点 **TalkBack**；
4. 点开关 **使用「TalkBack」** → 首次有教程；
5. 同一页有 **「TalkBack」快捷方式** 开关——打开后**同时按住两个音量键几秒**即可开/关（走查建议先开它）。

**用法与关法**（系统页面上自己写的，逐字抄进清单）：左右滑动移动焦点、**点按两次激活**、
**两根手指拖动滚动页面**；关掉 = 点开关（出现外框）→ 点按两次 → 确认消息里点「停止」→ 点按两次。
调语速在 TalkBack 那一页 → **设置 → 音频 → 文字转语音**。

**顺带发现**：机器上当时**开着的是「无障碍功能菜单」**（`AccessibilityMenuService`，会放一个无障碍悬浮按钮），
TalkBack 是关的；清单里加了「顺手检查 + 怎么关掉它」。

**清单改动**：`docs/templates/talkback-walkthrough.md` 的「怎么打开 TalkBack」整节重写为上面这条
**逐字可点**的路径（含「原来那条在 HyperOS 3 上不存在」的更正说明），并在「怎么用这张单子」里补上
"单击只是选中、要点按两次「与」两指滚动"两条交互前提。

---

"""
anchor = c.index("## 2026-10-12 · 真机人工清单七条")
c = c[:anchor] + section + c[anchor:]
io.open(CHANGELOG, "w", encoding="utf-8", newline="").write(c)
print("CHANGELOG 已插入")
