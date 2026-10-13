# 走查通过后：更新 PROJECT 发布行 + CHANGELOG
import io
import sys

sys.stdout.reconfigure(errors="replace")
D = r"D:\src\dsh\wheremy\docs"
ok, fail = 0, []


def sub(path, old, new, label):
    global ok
    t = io.open(path, encoding="utf-8").read()
    if old not in t:
        fail.append(label)
        return
    io.open(path, "w", encoding="utf-8", newline="").write(t.replace(old, new, 1))
    ok += 1


# ---- PROJECT：发布行 ----
sub(D + r"\PROJECT.md",
    "| 发布 | **本轮已具备交付条件**：⭐FR-RLS-01 已完成（用户 keystore）、**真机人工清单七条 2026-10-12 全部通过**（`RELEASE.md` §10.7）；未做的只剩**无障碍专项**（TalkBack 焦点次序走查，第 22 条）与那 7 条带触发条件的 `⬜`。`RELEASE.md` §3 是就绪清单 |",
    "| 发布 | **两条人工签收都已通过**：真机人工清单七条 **2026-10-12 全过**（`RELEASE.md` §10.6）；"
    "**TalkBack 焦点次序走查 2026-10-13 通过**（§10.7，两轮：第一轮抓出 4 处问题都已修）。"
    "⭐FR-RLS-01 已完成（用户 keystore）。剩下的只有那 7 条带触发条件的 `⬜` 与发布清单 §9 里的常规条目。`RELEASE.md` §3 是就绪清单 |",
    "PROJECT 发布行")

# ---- CHANGELOG ----
CH = D + r"\CHANGELOG.md"
t = io.open(CH, encoding="utf-8").read()
section = """## 2026-10-13 · TalkBack 焦点次序走查**通过**（无障碍专项签收）+「只看待归类」换成 FilterChip

用户本人按 `docs/templates/talkback-walkthrough.md` 走完 7 屏，回 🗣「**走查通过**」。
这是 `FR-PRM-05` 里**唯一必须人工**的一条（ATF 只能查「有没有名字、触摸目标够不够大」，次序只能靠人听）。

**这是两轮的结果**（第一轮 2026-10-12 抓出 4 处，都已修并复验）：

| 用户报的原话 | 判定 | 处置 |
|---|---|---|
| 「talkback 的设置我完全找不到」 | **我们的文档写错了路径**（HyperOS 3 没有「设置 → 更多设置 → 无障碍」） | 真机逐屏翻出真实路径并重写走查单（F49） |
| 「焦点还在底栏…没有先读标题」 | **清单预期写错了**（焦点停在底栏是标准行为；且标题是「全部位置」不是「位置」） | 各屏加 `heading()` + `paneTitle`；清单写明这是标准行为（F50 ③） |
| 「名称没念出必填」 | 真问题（视觉是「名称 *」，星号读屏读不出来） | 加 `semantics { contentDescription = "名称，必填" }`（F50 ②） |
| 「开关和标签是分离的」 | 真问题 | 整行 `toggleable(role = Role.Switch)` + `Switch(onCheckedChange = null)`（F50 ①） |

**顺带**：用户追问「其他页面也检查」后做了全应用审计——可切换控件只有 2 处 `Switch`（都在设置页，已修）；
其余是自带选中语义的 `FilterChip`；唯一靠改文案表达状态的「只看待归类」经用户批准也换成 `FilterChip`。
审计写成断言固化（`SettingsAccessibilityTest` 3 条 + 照片墙/确认页/设置页各若干条）。

**签收记录**：`RELEASE.md` §10.7（含环境快照：2.62 MB、SHA-256 `05D0EBD1…`、用户 keystore、提交 `3ae596a`）；
§9 第 22 条已勾。**顺手修了 §9 一处重复编号**（此前插入这条时与「迁移演练」撞号，现已 1–30 连续）
与 §10 的小节顺序（10.6/10.7/10.8 归位）。

---

"""
anchor = t.index("## 2026-10-13 · ") if "## 2026-10-13 · " in t else None
if anchor is not None:
    t = t[:anchor] + section + t[anchor:]
    io.open(CH, "w", encoding="utf-8", newline="").write(t)
    print("CHANGELOG 已插入（在已有的 2026-10-13 节之前）")
else:
    # 没有 10-13 的节就插在文件头的分隔线之后
    marker = "---\n\n"
    i = t.index(marker) + len(marker)
    t = t[:i] + section + t[i:]
    io.open(CH, "w", encoding="utf-8", newline="").write(t)
    print("CHANGELOG 已插入（文件头之后）")

print("成功 %d 处" % ok)
for f in fail:
    print("  未匹配：" + f)
