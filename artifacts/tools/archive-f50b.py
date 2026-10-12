# 记 F50（TalkBack 走查第二轮的三条发现）+ CHANGELOG
# 注意：本文件里的中文引语一律用「」，不要出现 ASCII 双引号（否则解析期就崩）。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
D = r"D:\src\dsh\wheremy\docs"
PLAN = D + r"\PLAN.md"
CHANGELOG = D + r"\CHANGELOG.md"

p = io.open(PLAN, encoding="utf-8").read()
f50 = (
    "| F50 | **TalkBack 走查第二轮抓到三条**（开关与标签分离／名称没念「必填」／切屏后焦点留在底栏） | "
    "① **设置页的开关**：`Switch` 与标签是两个焦点，且两处版式不一致（一处开关在左、一处标签在左），"
    "读屏只念「开/关」不知道是哪个开关 → 按官方模式把**整行**做成一个可切换项"
    "（`Modifier.toggleable(role = Role.Switch)` + `Switch(onCheckedChange = null)`），并统一成「标签在左、开关在右」；"
    "② **确认页的「名称 *」**：星号读屏读不出来，用户听到的没有「必填」 → 加 "
    "`Modifier.semantics { contentDescription = 名称，必填 }`（视觉不变，且仍是可输入框）；"
    "③ **切屏后焦点仍停在底栏**：这是 Android 对底部导航的**标准行为**，不是缺陷（原先的清单预期写错了）→ "
    "给各屏标题加 `heading()`、给屏根加 `paneTitle`，让读屏知道「这是哪一屏」；"
    "⚠️ 一开始用 `announceForAccessibility` 播报，被编译器指出**已在 Android 16 弃用**，"
    "官方说明是「无障碍服务可能直接忽略这种事件、应当用语义描述界面」，遂改用语义方案。"
    "**教训**：无障碍问题要断言**合并后的语义树**（Compose 测试的 `hasText(...) and isToggleable()`），"
    "`adb uiautomator dump` 看的是原始视图树、会误导 |\n"
)
if "F50" not in p:
    m = re.search(r"^\| F49 \|.*\n", p, flags=re.M)
    if m:
        p = p[: m.end()] + f50 + p[m.end() :]
        print("PLAN 已追加 F50")
    else:
        print("!! 没找到 F49 行")
io.open(PLAN, "w", encoding="utf-8", newline="").write(p)

c = io.open(CHANGELOG, encoding="utf-8").read()
section = """## 2026-10-12 · TalkBack 走查第二轮：改掉开关／必填／切屏三处（F50）

用户按 `templates/talkback-walkthrough.md` 走第二轮，报了三条，都成立（其中第三条是我们清单的预期写错了）：

| 反馈 | 结论 | 改法 |
|---|---|---|
| 设置页开关与标签是**两个**焦点，且有时标签在前、有时在后，「读到开关不知道是哪个」 | 真问题 | 按官方模式把**整行**做成一个可切换项：`Modifier.toggleable(role = Role.Switch)` + `Switch(onCheckedChange = null)`；顺手统一成「标签在左、开关在右」 |
| 确认页「名称」**没念出「必填」** | 真问题 | 视觉仍是「名称 *」，加 `semantics { contentDescription = 名称，必填 }`；星号本来就读不出来 |
| 双击底栏「位置」进页后，焦点没移到标题 | 清单预期错了 | 焦点停在底栏标签是 Android 对底部导航的**标准行为**；改法不是去抢焦点，而是让读屏知道「在哪一屏」：各屏标题加 `heading()`、屏根加 `paneTitle` |

**过程中的一个纠正**：先用 `announceForAccessibility` 播报标题，编译器指出它**在 Android 16 已弃用**——
官方弃用说明写着「无障碍服务**可能直接忽略**这类播报事件，应当改用**语义描述界面**」，
所以换成 `paneTitle` 语义（非弃用，且是专为「窗格／屏幕」设计的）。

**验证方式也纠正了一次**：一开始用 `adb shell uiautomator dump` 看开关节点，看到「可勾选但无文字」
就以为没修好——其实 `uiautomator dump` 看的是**原始视图树**，读屏读的是**合并后的语义树**，两者不是一棵树。
改用 Compose 测试断言（`hasText(标签) and isToggleable()` 命中的正是读屏会念的那一个节点），
并固化成 **CI 回归测试**：

- 新增 `SettingsAccessibilityTest`（3 条）：两个开关各自「标签与开关是同一个节点」、标签在语义树里只出现一次；
- `ReviewAccessibilityTest` 加 1 条：名称框的 `contentDescription` 是「名称，必填」**且**仍是可输入框。

测试从 **42 类 / 261 例** 长到 **43 类 / 265 例**，本地 CI 四道门全绿。

**清单同步改对**（`templates/talkback-walkthrough.md`）：位置页标题改成真实的「全部位置」、
写明「切屏后焦点在底栏是标准行为」、名称那条写明「应念出「名称，必填」」、开关那条改成「标签和开关是同一个焦点」。

---

"""
anchor = c.index("## 2026-10-12 · 修掉 TalkBack 走查单里写错的系统路径")
c = c[:anchor] + section + c[anchor:]
io.open(CHANGELOG, "w", encoding="utf-8", newline="").write(c)
print("CHANGELOG 已插入")
