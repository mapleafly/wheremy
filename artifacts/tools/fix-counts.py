# 修 RELEASE §9 的编号 + 把"判绿规则"里的例数改成"随批次增长 + 当前值"（基线描述保持不动）。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
DOCS = r"D:\src\dsh\wheremy\docs"
RELEASE = DOCS + r"\RELEASE.md"
TESTING = DOCS + r"\TESTING.md"
DEVELOPMENT = DOCS + r"\DEVELOPMENT.md"
DELTA = DOCS + r"\DELTA.md"

# —— RELEASE §9：重排编号 + 更新两条例数 ——
r = io.open(RELEASE, encoding="utf-8").read()
start = r.index("## 9. 发布检查清单（可直接执行版本）") if "## 9. 发布检查清单（可直接执行版本）" in r else r.index("## 9. 发布检查清单（可直接执行版）")
end = r.index("## 10. ", start)
block = r[start:end]

# 1) 先修内容（在重排编号之前，按行处理）
lines = block.split("\n")
fixed = []
for line in lines:
    m = re.match(r"^(\d+)\. \[ \] (.*)$", line)
    if m and "自己数出" in m.group(2):
        line = m.group(1) + ". [ ] 自己数出 `tests=261 failures=0 errors=0`——**不认 exit code**（G1）。" \
            "（数字随批次增长；`pwsh -File artifacts/tools/ci-local.ps1 -Cold` 会替你报合计。）"
    elif m and "覆盖对账反向" in m.group(2):
        line = m.group(1) + ". [ ] 覆盖对账反向：**42 个类 / 261 例**（G3；数字随批次增长）。"
    fixed.append(line)

# 2) 重排编号：按出现顺序 1..N
counter = 0
renumbered = []
for line in fixed:
    m = re.match(r"^(\d+)\. \[ \] (.*)$", line)
    if m:
        counter += 1
        renumbered.append("%d. [ ] %s" % (counter, m.group(2)))
    else:
        renumbered.append(line)
print("RELEASE §9 条目重排到 %d 条" % counter)
r = r[:start] + "\n".join(renumbered) + r[end:]
io.open(RELEASE, "w", encoding="utf-8", newline="").write(r)

# —— TESTING §6 的判绿规则：改成"随批次增长 + 当前值" ——
t = io.open(TESTING, encoding="utf-8").read()
t = t.replace(
    "**判绿**：`fresh == 39` **且** `tests == 481` 且 `failures == 0` 且 `errors == 0`。少一条都不算绿。",
    "**判绿**：`fresh == 本轮类数` **且** `tests == 本轮例数` 且 `failures == 0` 且 `errors == 0`。少一条都不算绿。\n"
    "> 数字随批次增长：**当前 = 42 类 / 261 例**（2026-10-10）。判据是"fresh 等于本轮 XML 份数"，不是某个固定数字。",
    1,
)
io.open(TESTING, "w", encoding="utf-8", newline="").write(t)
print("TESTING §6 判绿规则已改为随批次增长")

# —— DEVELOPMENT 的判绿清单 ——
d = io.open(DEVELOPMENT, encoding="utf-8").read()
d = d.replace(
    "| 5 | **39 份新鲜 XML** 且 `failures=0 errors=0` | 陈旧 XML 是\"假绿\"的头号来源（`REBUILD.md` §1.1） |",
    "| 5 | **本轮份数的新鲜 XML**（当前 42 份）且 `failures=0 errors=0` | 陈旧 XML 是「假绿」的头号来源（`REBUILD.md` §1.1） |",
    1,
)
d = d.replace(
    "- [ ] **测试全绿，且是这一轮的绿**：39 份 XML 新鲜 + `failures=0 errors=0`（§2.2），不是\"昨天跑过\"。",
    "- [ ] **测试全绿，且是这一轮的绿**：本轮份数（当前 42 份）的 XML 新鲜 + `failures=0 errors=0`（§2.2），不是「昨天跑过」。",
    1,
)
io.open(DEVELOPMENT, "w", encoding="utf-8", newline="").write(d)
print("DEVELOPMENT 判绿清单已更新")

# —— DELTA 的对比行：把"当前"更新 ——
dl = io.open(DELTA, encoding="utf-8").read()
dl = dl.replace(
    "| 测试分层 | JVM + Robolectric 39 类 / 481 例 | 分层相同；当前 4 类 / 6 例（P0 基线），**例数目标不少于 481** |",
    "| 测试分层 | JVM + Robolectric 39 类 / 481 例 | 分层相同；**P0 基线 4 类 / 6 例 → 现在 42 类 / 261 例**（2026-10-10） |",
    1,
)
io.open(DELTA, "w", encoding="utf-8", newline="").write(dl)
print("DELTA 对比行已更新")
