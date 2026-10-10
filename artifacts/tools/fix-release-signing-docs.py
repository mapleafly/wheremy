# 按事实修 RELEASE.md 里过期的签名判断 + §8.1 的自相矛盾 + §10 历史记录的"后续更新"提示
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\docs\RELEASE.md"
t = io.open(PATH, encoding="utf-8").read()

# —— 1) §2.1：分发方式那一行 + "只有 debug 包能侧载"那段 ——
old1 = "| 分发方式 | 侧载 APK | `PRD.md` §5 ⭐FR-RLS-01 |"
new1 = "| 分发方式 | 侧载 APK（**已用用户自己的 keystore 签名**，见 §3.2 与 `KEYSTORE.md`） | `PRD.md` §5 ⭐FR-RLS-01 |"
if old1 in t:
    t = t.replace(old1, new1, 1)
    print("§2.1 分发方式行已改")
else:
    print("!! §2.1 分发方式行没匹配")

old2 = ("**没有 release 签名 ⇒ 现在能侧载出去的只有 debug 包。** 这不是推断：`gradle :app:signingReport` 今天对 release 变体"
        "打印的是 `Config: null`、`Store: null`、`Alias: null`（本机实跑）。这也正是 ⭐FR-RLS-01 的全部内容"
        "（`PRD.md` §5，触发条件写着\"侧载给别人用时\"）。")
new2 = ("**2026-10-10 更新：⭐FR-RLS-01 已由用户完成。** 历史：在那之前 release 变体确实没有签名配置"
        "（`gradle :app:signingReport` 对 release 打印 `Config: null`），当时能侧载出去的只有 debug 包；"
        "现在用户自己生成了 keystore 并放好 `keystore.properties`（仓库外），release 包已用它签名——"
        "`signingReport` 是 `Config: release`、`apksigner verify --print-certs` 核过签名者、真机装的是正式包。"
        "做法与三个备份见 `KEYSTORE.md`。")
if old2 in t:
    t = t.replace(old2, new2, 1)
    print("§2.1 段落已改")
else:
    print("!! §2.1 段落没匹配")

# —— 2) §3.2 的状态：⬜ → ✅ ——
old3 = "**本实现的情况**：⬜ **未完成，且需要用户参与**——keystore 的口令与保管是用户的决定，代理不生成、不代管。"
new3 = ("**本实现的情况**：✅ **已完成（2026-10-10，由用户自己做）**——用户生成了 keystore"
        "（`D:\\keys\\wheremy-release.jks`，别名 `wheremy-release`）并在仓库外放好 `keystore.properties`；"
        "六条「怎么算做完」逐条核过：`signingReport` 是 `Config: release`（不再 null）、"
        "`apksigner verify --print-certs` 的 SHA-256 与 keytool 一致、有效期到 2054、`git status` 干净、"
        "仓库里没有 keystore 文件。**口令始终在用户手里**，本文与 `KEYSTORE.md` 只记位置不记口令。")
if old3 in t:
    t = t.replace(old3, new3, 1)
    print("§3.2 状态已改为完成")
else:
    print("!! §3.2 状态没匹配")

# —— 3) §8.1 的自相矛盾：不要再去建仓库根那份 ——
old4 = "仓库根现在**还没有**这份文件（`git ls-files` 里没有），发布的第一次就要建。格式照仓库既有的提交风格——中文一句话说清\"改了什么以及代价\"（`REBUILD.md` §9）："
new4 = ("⚠️ 2026-10-10 更正：本节此前写「仓库根现在还没有这份文件，发布的第一次就要建」——**不要建第二份**。"
        "发布说明直接写进 `docs/CHANGELOG.md` 的新一节（标题用 `## <版本号> — <日期>（versionCode N）`），"
        "与其他节按日期倒序排在一起。格式照仓库既有的提交风格——中文一句话说清\"改了什么以及代价\"（`REBUILD.md` §9）：")
if old4 in t:
    t = t.replace(old4, new4, 1)
    print("§8.1 已消除自相矛盾")
else:
    print("!! §8.1 没匹配")

old5 = "## 0.1.0 — <日期>（versionCode 1）"
new5 = "## <版本号> — <日期>（versionCode N）"
if old5 in t:
    t = t.replace(old5, new5, 1)
    print("§8.1 模板标题已通用化")

old6 = "- 改 schema 会清空本机数据（⭐FR-DB-01 未落地）；该包未做 release 签名（⭐FR-RLS-01 未落地）。"
new6 = "- 只写**这一版真实存在的**已知问题；已经解决的就不要再列（例如 release 签名已于 2026-10-10 由用户完成）。"
if old6 in t:
    t = t.replace(old6, new6, 1)
    print("§8.1 模板的已知问题行已改")

# —— 4) §10 历史记录加"后续更新"提示 ——
old7 = """> 这一节是**本实现**（不是参照实现）的发布记录。逐条对 §9 的 28 项；没做的那几项写清为什么、什么时候做。
> 判据一律是**机器产生的证据**，不是"跑过了"。"""
new7 = """> 这一节是**本实现**（不是参照实现）的发布记录。逐条对 §9 的 28 项；没做的那几项写清为什么、什么时候做。
> 判据一律是**机器产生的证据**，不是"跑过了"。
>
> ⚠️ **后续更新（2026-10-10）**：这一节是 **2026-10-09 当时**的签收快照，**内容按当时的事实保留、不回改**。
> 其中「⭐FR-RLS-01（release 签名）未完成」这一条**后来已由用户完成**（见 §3.2 与 `KEYSTORE.md`），
> 所以本节表格里凡写「调试签名」的地方都只代表 10-09 那天；**看当前状态请以 §3.2 与 `docs/CHANGELOG.md` 为准**。"""
if old7 in t:
    t = t.replace(old7, new7, 1)
    print("§10 已加后续更新提示")
else:
    print("!! §10 头部没匹配")

io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("完成")
