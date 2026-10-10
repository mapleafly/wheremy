# CI 收尾的文档更新：PLAN 的 CI 行 + RELEASE §9 的 CI 首跑 + 修过期数字 + CHANGELOG
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
DOCS = r"D:\src\dsh\wheremy\docs"
PLAN = DOCS + r"\PLAN.md"
RELEASE = DOCS + r"\RELEASE.md"
CHANGELOG = DOCS + r"\CHANGELOG.md"

# —— PLAN：CI 那一行 ——
p = io.open(PLAN, encoding="utf-8").read()
old_row = "| CI | GitHub Actions：`checkout@v7` + `setup-java@v6` + `gradle/actions/setup-gradle@v6` | — | 第一天就要绿：`spotlessCheck` + `testDebugUnitTest`（`.github/workflows/ci.yml`，**尚未在真 GitHub 上跑过**） |"
new_row = (
    "| CI | GitHub Actions：`checkout@v6` + `setup-java@v5` + `gradle/actions/setup-gradle@v4` "
    "+ `upload-artifact@v4` | — | 四道门：`spotlessCheck` → `testDebugUnitTest` → `verifyRoborazziDebug` → `assembleRelease`"
    "（`.github/workflows/ci.yml`）。**2026-10-10 修正**：原写的 `checkout@v7`/`setup-java@v6` 这两个大版本**不存在**"
    "（核对到的最新是 v6/v5），写错版本号会让第一次跑直接红；同时补了 `concurrency` 与最小 `permissions`。"
    "**内容已本地等价跑通**（`artifacts/tools/ci-local.ps1 -Cold`：clean + 禁缓存 + 强制重跑，四步全绿）；"
    "**真 GitHub 上仍未跑过**——本工作区没有 git 远端，建仓库与首次推送必须由人做（命令见 `RELEASE.md` §9 第 10 条） |"
)
if old_row in p:
    p = p.replace(old_row, new_row, 1)
    print("PLAN 的 CI 行已更新")
else:
    print("!! PLAN 的 CI 行没匹配")

# 在 CI 行后面补一句"本地怎么验"
marker = "### 3.3 Phase 0 的工具链验证门（spike，半天）"
note = (
    "> **CI 怎么在本地验**（没有远端也能知道会不会红）：\n"
    "> `pwsh -File artifacts/tools/ci-local.ps1 -Cold` —— 与 `ci.yml` 同序同参跑四道门，"
    "`-Cold` 会 clean + 禁构建缓存 + 强制重跑（最接近 runner 的干净状态），末尾给判决与测试合计。\n"
    "> 2026-10-10 实测：`spotlessCheck` OK / `testDebugUnitTest` OK（96s）/ `verifyRoborazziDebug` OK（86s）"
    "/ 42 份 XML / 261 例 / 0 失败。\n\n"
)
if "CI 怎么在本地验" not in p:
    p = p.replace(marker, note + marker, 1)
    print("PLAN 已补本地验证说明")
io.open(PLAN, "w", encoding="utf-8", newline="").write(p)

# —— RELEASE §9：加 CI 首跑那一条 + 修过期数字 ——
r = io.open(RELEASE, encoding="utf-8").read()
r = r.replace(
    "9. [ ] 数 XML：`app/build/test-results/testDebugUnitTest/*.xml` **39 份**，mtime 全部晚于本轮起跑时刻（G1）。",
    "9. [ ] 数 XML：`app/build/test-results/testDebugUnitTest/*.xml` **42 份**，mtime 全部晚于本轮起跑时刻（G1）。"
    "（也可直接 `pwsh -File artifacts/tools/ci-local.ps1 -Cold`：它会替你把四道门跑完并报测试合计。）",
    1,
)
ci_item = (
    "10. [ ] **CI 首次真跑**（只在第一次或换了远端时做）：本工作区**没有 git 远端**，所以这一步必须由人做——\n"
    "    ```\n"
    "    # 1) 在 GitHub 上建一个空仓库（不要勾 README/.gitignore）\n"
    "    git remote add origin git@github.com:<你的账号>/<仓库名>.git\n"
    "    git push -u origin main\n"
    "    # 2) 去 Actions 页看这次 run：四道门（spotlessCheck → testDebugUnitTest →\n"
    "    #    verifyRoborazziDebug → assembleRelease）应当全绿；红了先看「打印环境」那一步\n"
    "    ```\n"
    "    跑通之后把结论记进 `PLAN.md` §3.3 的 CI 行（把「仍未跑过」划掉、写上日期与 run 链接）。\n"
    "    本地等价验证见 `PLAN.md` §3.3 的说明与 `artifacts/tools/ci-local.ps1`。\n"
)
if "CI 首次真跑" not in r:
    anchor = "10. [ ] "
    # 找 §9 里的第 10 条（原本存在的下一条），把它整体后移
    m = re.search(r"^10\. \[ \] .*$", r, flags=re.M)
    if m:
        r = r[: m.start()] + ci_item + "11. [ ] " + m.group(0)[len("10. [ ] ") :] + r[m.end() :]
    else:
        # 没有第 10 条：挂在第 9 条之后
        idx = r.index("9. [ ] 数 XML")
        line_end = r.index("\n", idx) + 1
        r = r[:line_end] + ci_item + r[line_end:]
    print("RELEASE §9 已加 CI 首跑")
else:
    print("RELEASE §9 已有 CI 首跑")
io.open(RELEASE, "w", encoding="utf-8", newline="").write(r)

# —— CHANGELOG ——
c = io.open(CHANGELOG, encoding="utf-8").read()
section = """## 2026-10-10 · CI 收口：修不存在的 action 版本 + 本地等价验证 + 首跑步骤交给人工

**为什么这一轮不是"CI 真跑"**：本工作区**没有 git 远端**（`git remote -v` 为空），
GitHub Actions 跑不了；建仓库与首次推送**必须由人做**（凭据在你手里）。所以这一轮做的是"把 CI 变成可信的"：

- **修掉两个不存在的 action 大版本**：原写的 `actions/checkout@v7` 与 `actions/setup-java@v6` 都**不存在**
  （核对到的最新分别是 **v6 / v5**，v5.3.0 已发布）。写错版本号会让第一次跑**直接红**。
  `gradle/actions/setup-gradle` 保守取 **v4**、`upload-artifact` 保持 **v4**；
- 补两条 GitHub 官方推荐：`concurrency`（同分支后推取消前一次）与最小 `permissions: contents: read`；
- 加一步「打印环境」（`java -version` + `./gradlew --version`）——第一次真跑红了靠它定位；
- **新增 `artifacts/tools/ci-local.ps1`**：与 `ci.yml` **同序同参**跑四道门，末尾给判决与测试合计。
  `-Cold` = clean + 禁构建缓存 + 强制重跑，最接近 runner 的干净状态。
  **实测**：`spotlessCheck` OK / `testDebugUnitTest` OK（96s）/ `verifyRoborazziDebug` OK（86s）/ 42 份 XML / 261 例 / 0 失败 ✓
- `RELEASE.md` §9 加了第 10 条：**CI 首次真跑的三行命令**（建仓库 → `git remote add` → `push`），
  跑通后把结论记回 `PLAN.md` §3.3；顺手修掉 §9 里过期的「39 份 XML」（现在是 42 份）。

---

"""
anchor = c.index("## 2026-10-10 · 词表外的类别/季节")
c = c[:anchor] + section + c[anchor:]
io.open(CHANGELOG, "w", encoding="utf-8", newline="").write(c)
print("CHANGELOG 已插入")
