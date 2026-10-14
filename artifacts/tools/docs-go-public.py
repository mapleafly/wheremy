# 转公开：文档同步（渠道 A 落地 + 公开仓库下 secret 的口径 + F52 结论）
import io
import sys

sys.stdout.reconfigure(errors="replace")
D = r"D:\src\dsh\wheremy\docs"
ok, fail = 0, []


def sub(path, old, new, label):
    global ok
    t = io.open(path, encoding="utf-8", newline="").read()
    if old not in t:
        fail.append(label)
        return
    io.open(path, "w", encoding="utf-8", newline="").write(t.replace(old, new, 1))
    ok += 1


# ---- 1) RELEASE §9.1：渠道已定 ----
sub(
    D + r"\RELEASE.md",
    "**现状**：0.1.0 按**自用侧载**签收（发布记录）。想给别人用是**下一步**，要补的不是一行命令，是这几件：",
    "**现状（2026-10-14 更新）**：0.1.0 按**自用侧载**签收（发布记录），**对外分发选渠道 A**"
    "「仓库转公开 + GitHub Releases」（用户 2026-10-14 定）。已做：仓库根目录补了面向陌生人的 `README.md`。\n"
    "**还差**：① 用户配四个 secret（`KEYSTORE.md` §13）② LICENSE 待用户定（不写＝保留所有权利，是保守默认）\n"
    "③ 配完 secret 后派发一次 `Release APK`，确认能出签名包；④ 打 `v0.1.0` 标签发第一个 Release。\n"
    "\n"
    "原计划要补的几件（存档）：",
    "RELEASE §9.1 现状",
)
sub(
    D + r"\RELEASE.md",
    "**建议**：先走 **B**（今天就能用，零风险）→ 想省事再考虑 **A**。",
    "**用户的选择（2026-10-14）**：走 **A**。转公开前做过一次扫描（见 `PLAN.md` F52）：\n"
    "全历史里**没有任何凭据**（口令、API key、私钥都没有；命中的全是代码里的属性名与 `KEYSTORE.md` 的模板占位符），\n"
    "tracked 文件里**没有** keystore / `keystore.properties` / 备份 zip / 演练产物，历史上也**从未**提交过它们。\n"
    "**公开后会可见的非凭据信息**（已告知用户，保留未删）：设备序列号 `9aa4b43e`（17 个文件）、\n"
    "Windows 用户名（6 个）、keystore 路径 `D:\\keys\\…`（6 个）、本机仓库路径（1 个）——\n"
    "这些不是凭据；要抹掉得**重写全部历史**（所有 commit 哈希都会变，发布记录里的引用随之失效），代价大于收益。",
    "RELEASE §9.1 渠道",
)

# ---- 2) KEYSTORE §13.3：公开仓库下的口径 ----
sub(
    D + r"\KEYSTORE.md",
    "- 配了 secret，**keystore 就不再只在你机器上**：它以加密形式存在 GitHub 的 secret 里，\n"
    "  仓库管理员可见、workflow 里能取到。仓库现在是**私有**的，风险可控，但这条要说清。",
    "- 配了 secret，**keystore 就不再只在你机器上**：它以加密形式存在 GitHub 的 secret 里，\n"
    "  仓库管理员可见、workflow 里能取到。\n"
    "- **仓库已于 2026-10-14 转为公开**（`RELEASE.md` §9.1 渠道 A）。公开仓库下这三条仍然成立，且都对我们有利：\n"
    "  ① secret 依然加密存放、**只在 workflow 里**能取到（公开的是仓库内容，不是 secret）；\n"
    "  ② **来自 fork 的 PR 拿不到 secret**（GitHub 默认行为）——陌生人 fork 后提 PR 也偷不到；\n"
    "  ③ 触发这个工作流需要**写权限**（手动 dispatch 与推标签都是），陌生人在公开仓库上做不到。\n"
    "  也就是说：公开仓库**不会**让别人拿到你的签名能力，只会让别人看到源码。",
    "KEYSTORE §13.3 公开仓库",
)

# ---- 3) PLAN F52：处理结论 ----
sub(
    D + r"\PLAN.md",
    "| F52 | **文档里有几处「不该公开」的信息**（设备序列号 `9aa4b43e`、keystore 路径 `D:\\keys\\…`、本机路径 `D:\\src\\dsh\\wheremy`）✗ 现在仓库是私有的，没影响；**一旦走「仓库转公开」这条路（`RELEASE.md` §9.1 渠道 A）就得先扫一遍** | 2026-10-14 记下，等用户选渠道时处理 |",
    "| F52 | **文档里有几处「不该公开」的信息**（设备序列号 `9aa4b43e`、Windows 用户名、keystore 路径 `D:\\keys\\…`、本机路径 `D:\\src\\dsh\\wheremy`）✗ 私有仓库时无影响 | "
    "**2026-10-14 已处理（用户选渠道 A：转公开）**：① 扫了**全历史**，凭据（口令 / API key / 私钥）**零命中**"
    "（命中的 9 条全是代码里的属性名与 `KEYSTORE.md` 的模板占位符）；② tracked 文件与历史里**没有** keystore / "
    "`keystore.properties` / 备份 zip / 演练产物；③ 补了面向陌生人的根 `README.md`；④ 上述个人标识**保留未删**——"
    "它们不是凭据，而抹掉要重写全部历史（commit 哈希全变、发布记录里的引用失效），代价大于收益；用户若要再处理，说一声即可 |",
    "PLAN F52",
)

# ---- 4) CHANGELOG ----
section = """## 2026-10-14 · 仓库转公开（渠道 A）：先扫一遍，再翻开关

用户 2026-10-14 定：对外分发走**渠道 A（仓库转公开 + GitHub Releases）**。转公开不可逆，所以先做扫描再动手。

**扫描结论（关键的一条）**：全历史里**没有任何凭据**——口令、API key、私钥全部零命中
（`storePassword=` 那几条命中的是代码里的属性名与 `KEYSTORE.md` 的模板占位符 `<你的库口令>`）；
tracked 文件与**历史里**都没有 keystore、`keystore.properties`、备份 zip、演练产物。

**公开后会可见的"非凭据"信息**（已告知用户，保留未删）：设备序列号 `9aa4b43e`（17 个文件）、
Windows 用户名（6 个）、keystore 路径 `D:\\keys\\…`（6 个）、本机仓库路径（1 个）。
抹掉它们要**重写全部历史**（commit 哈希全变、发布记录里的引用失效），代价大于收益，所以保留；
用户若要再处理，随时可以做。

**补的短板**：仓库根目录原先**没有 README**（陌生人进来看到一片空），补了一份面向外人的
`README.md`：它是什么、能做什么、怎么构建、文档索引、隐私说明（权限只有三个、不做云备份、数据只在本机）。
**LICENSE 待用户定**——不写＝保留所有权利，对公开仓库来说是保守且安全的默认。

**文档同步**：`RELEASE.md` §9.1 记下渠道 A 与剩下的四件（配 secret → 定 LICENSE → 派发一次出签名包 → 打标签发 Release）；
`KEYSTORE.md` §13.3 改成公开仓库的口径（secret 仍加密、**fork 的 PR 拿不到 secret**、
触发工作流需要写权限——公开仓库不会让别人拿到签名能力，只会看到源码）；`PLAN.md` F52 结案。

---

"""
C = D + r"\CHANGELOG.md"
t = io.open(C, encoding="utf-8", newline="").read()
j = t.index("---\n\n") + len("---\n\n")
io.open(C, "w", encoding="utf-8", newline="").write(t[:j] + section + t[j:])

print("成功 %d 处" % ok)
for f in fail:
    print("  未匹配：" + f)
