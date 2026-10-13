# 修 §10.6 的签名摘要 + 修模板里过期的硬编码数字 + RELEASE §8 指向已填好的发布记录
import io
import sys

sys.stdout.reconfigure(errors="replace")
R = r"D:\src\dsh\wheremy\docs\RELEASE.md"
T = r"D:\src\dsh\wheremy\docs\templates\release-checklist.md"
ok, fail = 0, []


def sub(path, old, new, label):
    global ok
    t = io.open(path, encoding="utf-8").read()
    if old not in t:
        fail.append(label)
        return
    io.open(path, "w", encoding="utf-8", newline="").write(t.replace(old, new, 1))
    ok += 1


# 1) §10.6 的证书摘要写错了（B070DD26 → 8118b97d），附证据链
sub(R,
    "用**用户自己的 keystore** 签名（`apksigner` 的 SHA-256 `B070DD26…`）、**2.62 MB**；",
    "用**用户自己的 keystore** 签名（`apksigner` 证书摘要 `8118b97d…`）、**2.62 MB**；"
    "〔2026-10-13 更正：本行原先写 `B070DD26…`，是错的。依据有两条：① 2026-10-10 做 ⭐FR-RLS-01 时，"
    "`apksigner verify` 与 `signingReport` 两处互相印证同一证书 `8118b97d…`（见 `CHANGELOG.md` 那节）；"
    "② 2026-10-13 用 `adb install -r` 覆盖安装到当时机器上那个包**成功**——签名不同会直接失败，"
    "所以 10-12 装的那个包与今天这个**同一个证书**。签名摘要是发布记录的必填项，写错会误导排障，故按证据改正。〕、**2.62 MB**；",
    "§10.6 签名摘要")

# 2) 模板里的过期硬编码数字 → 留空（模板是空白表单，写死数字会误导）
sub(T,
    "| G1 单测全绿双证 | `BUILD SUCCESSFUL` + XML 份数 ____ / 39 + `tests=481 failures=0 errors=0` | |",
    "| G1 单测全绿双证 | `BUILD SUCCESSFUL` + XML 份数 ____ + 自数 `tests=____ failures=0 errors=0`（**数字填本轮实数**） | |",
    "模板 G1")
sub(T,
    "| G3 覆盖对账 | 正向 ____ 行；反向 39 类 / 481 例 | |",
    "| G3 覆盖对账 | 正向 ____ 行（`DELTA.md` §2 矩阵）；反向 ____ 类 / ____ 例（**填本轮实数**） | |",
    "模板 G3")

# 3) §8 那条"复制一份填"处，指向已经填好的这份
sub(R,
    "模板 `templates/release-checklist.md`：复制一份填，建议文件名 `release-<versionName>-<日期>.md`；**别把填好的记录放回 `templates/`**",
    "模板 `templates/release-checklist.md`：复制一份填，建议文件名 `release-<versionName>-<日期>.md`；**别把填好的记录放回 `templates/`**。"
    "**0.1.0 已经填好一份：`docs/release-0.1.0-2026-10-13.md`**（照它抄一遍就知道每格该写什么）",
    "§8 指向已填记录")

print("成功 %d 处" % ok)
for f in fail:
    print("  未匹配：" + f)
