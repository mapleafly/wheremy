# 修 RELEASE §9 编号（两个 11）+ 把"判绿规则"里的例数改成随批次增长 + 当前值。
# 纪律：要匹配原文里已有的 ASCII 双引号时，Python 字符串用单引号定界；
#       要写入的新文案里的中文引语一律用「」，绝不往字符串里写 ASCII 双引号。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
DOCS = r"D:\src\dsh\wheremy\docs"
RELEASE = DOCS + r"\RELEASE.md"
TESTING = DOCS + r"\TESTING.md"
DEVELOPMENT = DOCS + r"\DEVELOPMENT.md"
DELTA = DOCS + r"\DELTA.md"
PROCESS = DOCS + r"\PROCESS.md"

# —— RELEASE §9：先改内容，再整体重排编号 ——
r = io.open(RELEASE, encoding="utf-8").read()
m9 = re.search(r"^## 9\. .*$", r, flags=re.M)
m10 = re.search(r"^## 10\. .*$", r, flags=re.M)
assert m9 and m10 and m10.start() > m9.start(), "没找到 §9/§10 的边界"
block = r[m9.start():m10.start()]

lines = block.split("\n")
fixed = []
for line in lines:
    m = re.match(r'^(\d+)\. \[ \] (.*)$', line)
    if not m:
        fixed.append(line)
        continue
    body = m.group(2)
    if "自己数出" in body:
        body = (
            '自己数出 `tests=261 failures=0 errors=0`——**不认 exit code**（G1）。'
            "（数字随批次增长，当前 42 类 / 261 例，2026-10-10；"
            "`pwsh -File artifacts/tools/ci-local.ps1 -Cold` 会替你跑完四道门并报合计。）"
        )
    elif "覆盖对账反向" in body:
        body = "覆盖对账反向：**42 个类 / 261 例**（G3；数字随批次增长）。"
    fixed.append("{}. [ ] {}".format(m.group(1), body))

counter = 0
renumbered = []
for line in fixed:
    m = re.match(r"^(\d+)\. \[ \] (.*)$", line)
    if m:
        counter += 1
        renumbered.append("{}. [ ] {}".format(counter, m.group(2)))
    else:
        renumbered.append(line)
print("RELEASE §9 重排为 %d 条" % counter)
r = r[: m9.start()] + "\n".join(renumbered) + r[m10.start():]
io.open(RELEASE, "w", encoding="utf-8", newline="").write(r)

# —— TESTING §6 判绿规则 ——
t = io.open(TESTING, encoding="utf-8").read()
old = "**判绿**：`fresh == 39` **且** `tests == 481` 且 `failures == 0` 且 `errors == 0`。少一条都不算绿。"
new = (
    "**判绿**：`fresh == 本轮类数` **且** `tests == 本轮例数` 且 `failures == 0` 且 `errors == 0`。少一条都不算绿。\n"
    "> 数字随批次增长：**当前 = 42 类 / 261 例**（2026-10-10）。判据是「fresh 等于本轮 XML 份数」，不是某个固定数字。"
)
if old in t:
    t = t.replace(old, new, 1)
    print("TESTING §6 判绿规则已改")
else:
    print("!! TESTING 判绿规则没匹配")
io.open(TESTING, "w", encoding="utf-8", newline="").write(t)

# —— DEVELOPMENT：三处判绿口径（原文里带 ASCII 引号，用单引号字符串匹配）——
d = io.open(DEVELOPMENT, encoding="utf-8").read()
pairs = [
    (
        '# 5) 判绿：不看 exit code，看"新鲜的 39 份 XML + 自己数出来的 tests/failures/errors"',
        "# 5) 判绿：不看 exit code，看「新鲜的 XML 份数（当前 42 份）+ 自己数出来的 tests/failures/errors」",
    ),
    (
        '| 5 | **39 份新鲜 XML** 且 `failures=0 errors=0` | 陈旧 XML 是"假绿"的头号来源（`REBUILD.md` §1.1） |',
        "| 5 | **本轮份数的新鲜 XML**（当前 42 份）且 `failures=0 errors=0` | 陈旧 XML 是「假绿」的头号来源（`REBUILD.md` §1.1） |",
    ),
    (
        '- [ ] **测试全绿，且是这一轮的绿**：39 份 XML 新鲜 + `failures=0 errors=0`（§2.2），不是"昨天跑过"。',
        "- [ ] **测试全绿，且是这一轮的绿**：本轮份数（当前 42 份）的 XML 新鲜 + `failures=0 errors=0`（§2.2），不是「昨天跑过」。",
    ),
]
for old, new in pairs:
    if old in d:
        d = d.replace(old, new, 1)
        print("DEVELOPMENT 已改一处")
    else:
        print("!! DEVELOPMENT 没匹配：%s" % old[:40])
io.open(DEVELOPMENT, "w", encoding="utf-8", newline="").write(d)

# —— DELTA 的对比行：把「当前」从 P0 基线更新到现在 ——
dl = io.open(DELTA, encoding="utf-8").read()
old_row = "| 测试分层 | JVM + Robolectric 39 类 / 481 例 | 分层相同；当前 4 类 / 6 例（P0 基线），**例数目标不少于 481** |"
new_row = "| 测试分层 | JVM + Robolectric 39 类 / 481 例 | 分层相同；**P0 基线 4 类 / 6 例 → 现在 42 类 / 261 例**（2026-10-10） |"
if old_row in dl:
    dl = dl.replace(old_row, new_row, 1)
    print("DELTA 对比行已更新")
else:
    print("!! DELTA 对比行没匹配")
io.open(DELTA, "w", encoding="utf-8", newline="").write(dl)

# —— PROCESS G3 行：数字已带「随批次增长」注，顺手把数字更新 ——
p = io.open(PROCESS, encoding="utf-8").read()
old_p = "反向：`@Test` 类数 = 39、总数 = 481（数字随批次增长"
new_p = "反向：`@Test` 类数 = 42、总数 = 261（数字随批次增长"
if old_p in p:
    p = p.replace(old_p, new_p, 1)
    print("PROCESS G3 行已更新")
else:
    print("!! PROCESS G3 行没匹配")
io.open(PROCESS, "w", encoding="utf-8", newline="").write(p)
