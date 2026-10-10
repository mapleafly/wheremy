# 把 KEYSTORE.md 接进文档体系：RELEASE.md 的 §3.2 / §6.3 / §6.2、docs/README.md 的文档地图。
import io

RELEASE = r"D:\src\dsh\wheremy\docs\RELEASE.md"
README = r"D:\src\dsh\wheremy\docs\README.md"

r = io.open(RELEASE, encoding="utf-8").read()

old_32 = "**本实现的情况**：⬜ **未完成，且需要用户参与**——keystore 的口令与保管是用户的决定，代理不生成、不代管。首次正式交付前必须做掉这一条。"
new_32 = (
    "**本实现的情况**：⬜ **未完成，且需要用户参与**——keystore 的口令与保管是用户的决定，代理不生成、不代管。\n"
    "📖 **操作手册在 `KEYSTORE.md`**：从「生成 → 三份备份 → 写 `keystore.properties`（仓库外）→ 接进构建 → "
    "三条验证命令 → 装机两个坑 → 换密钥不可逆」一条条写清，并附本次在本机用一把**一次性**密钥跑通整条链的实测记录。"
    "构建侧已经就绪：`app/build.gradle.kts` 会按顺序找 `<仓库父目录>/keystore.properties` 与 "
    "`~/.wheremy/keystore.properties`，**找不到就退回调试签名并在出 release 包时喊一声**——"
    "所以你把文件放好之后，不需要改任何代码。"
)
assert old_32 in r, "RELEASE §3.2 的锚点没找到"
r = r.replace(old_32, new_32, 1)

old_63 = "### 6.3 侧载步骤"
new_63 = (
    "### 6.3 侧载步骤\n\n"
    "⚠️ **先读 `KEYSTORE.md` §7 的两个坑**：release 的包名 `com.wheremy.app` 与参照实现那个应用**同名**"
    "（覆盖安装会失败，强装要先卸载并丢数据），以及 `versionCode` 只能涨不能降。"
)
assert old_63 in r, "RELEASE §6.3 锚点没找到"
r = r.replace(old_63, new_63, 1)

old_62 = "| 签名 | 自动 debug 签名 | **也暂时是 debug 签名**，只够冒烟；正式交付要换 keystore（⭐FR-RLS-01，§3.2） |"
new_62 = "| 签名 | 自动 debug 签名 | 有 `keystore.properties`（仓库外）就用你自己的 keystore，没有就暂时用 debug 签名（⭐FR-RLS-01，§3.2；做法见 `KEYSTORE.md`） |"
if old_62 in r:
    r = r.replace(old_62, new_62, 1)
    print("§6.2 那一行也改了")
else:
    print("§6.2 那一行没匹配（可能已改过）")

io.open(RELEASE, "w", encoding="utf-8", newline="").write(r)
print("RELEASE.md 已更新")

t = io.open(README, encoding="utf-8").read()
anchor = "| `RELEASE.md` |"
idx = t.find(anchor)
if idx == -1:
    print("README 的文档地图里没找到 RELEASE.md 行，需手工加一行")
else:
    line_end = t.find("\n", idx)
    line = t[idx:line_end]
    if "KEYSTORE.md" not in t:
        new_line = (
            line
            + "\n| `KEYSTORE.md` | **怎么生成与保管 release 密钥**：一条条命令、三份备份、"
            "`keystore.properties` 的坑、三条验证命令、装机两个坑、换密钥不可逆 | "
            "发布门禁与回滚（→`RELEASE.md`） | 操作手册（⭐FR-RLS-01） |"
        )
        t = t[:line_end] + new_line + t[line_end:]
        io.open(README, "w", encoding="utf-8", newline="").write(t)
        print("README 文档地图已加 KEYSTORE.md")
    else:
        print("README 里已经提过 KEYSTORE.md")
