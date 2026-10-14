# 发布管道打通后的文档收口：README（下载+许可）、RELEASE §9.1/§10.10、发布记录、归档 README、CHANGELOG
import io
import sys

sys.stdout.reconfigure(errors="replace")
W = r"D:\src\dsh\wheremy"
D = W + r"\docs"
ok, fail = 0, []

PUB_SHA = "dd8a1aa22febfc262b48676d2b3ebca39c10a522116290af2e1c6455831c27eb"
LOCAL_SHA = "05005413a2ef85014724e6e9bb99a11f3e0f50aea603bdd0d8d80d27f3e3ddd5"
CERT = "8118b97d588e43c91a77540154fda366749e0111f1bafd2765ee7a47676b8369"


def sub(path, old, new, label):
    global ok
    t = io.open(path, encoding="utf-8", newline="").read()
    if old not in t:
        fail.append(label)
        return
    io.open(path, "w", encoding="utf-8", newline="").write(t.replace(old, new, 1))
    ok += 1


# ---- 1) README：下载 + 许可 ----
sub(
    W + r"\README.md",
    "## 怎么构建",
    """## 下载安装

**发布包在这里：[Releases](https://github.com/mapleafly/wheremy/releases)**（APK + `SHA256SUMS.txt`）。
装之前可以自己核一遍：`sha256sum -c SHA256SUMS.txt`（Windows：`Get-FileHash .\\wheremy-0.1.0-release.apk -Algorithm SHA256`）。

包是用发布者自己的 keystore 签的（证书 SHA-256 `""" + CERT + """`）——
这意味着**它没法覆盖安装别人签名的同名应用**，也意味着**别人签的包盖不了它**。

## 怎么构建""",
    "README 下载段",
)
sub(
    W + r"\README.md",
    "## 隐私",
    """## 许可

**源码公开，但保留所有权利**（没有 LICENSE 文件＝默认状态）：你可以阅读、克隆、自己构建来用，
但**不得复制再发布**（包括改名后重新分发 APK）。想要别的授权方式，开个 issue 谈。

## 隐私""",
    "README 许可段",
)

# ---- 2) RELEASE §9.1：管道已打通 ----
sub(
    D + r"\RELEASE.md",
    "**还差**：① 用户配四个 secret（`KEYSTORE.md` §13）② LICENSE 待用户定（不写＝保留所有权利，是保守默认）\n"
    "③ 配完 secret 后派发一次 `Release APK`，确认能出签名包；④ 打 `v0.1.0` 标签发第一个 Release。\n",
    "**2026-10-14 已全部打通**（用户配好三个 secret 后）：\n\n"
    "| 步 | 证据 |\n|---|---|\n"
    "| ① 三个 secret 配好 | `gh secret list` 可见 `KEYSTORE_BASE64` / `KEYSTORE_KEY_PASSWORD` / `KEYSTORE_STORE_PASSWORD` |\n"
    "| ② 手动派发一次出签名包 | run `38506495467` 全绿；产物 `wheremy-0.1.0-release.apk` 2,763,109 字节，"
    "SHA-256 `57b424af…`，证书 `" + CERT[:16] + "…` |\n"
    "| ③ 该包装真机冒烟 | 装到小米 13 上跑起来，设置页「导出到文件」与「自动导出会用这台手机上记住的口令加密。」都在 |\n"
    "| ④ 打标签发 Release | `v0.1.0` → run `38506779304` 全绿（含「发 Release」步）→ "
    "https://github.com/mapleafly/wheremy/releases/tag/v0.1.0 |\n"
    "| ⑤ 发布物自查 | 从 Release 下载回来重算：APK SHA-256 `" + PUB_SHA + "` 与 `SHA256SUMS.txt` **一致** ✓；证书仍是发布者那张 ✓ |\n"
    "\n"
    "**途中修掉的一个真 bug**：run `38506064573` 卡在「验签」步——runner 的 apksigner 是 build-tools 37，"
    "输出格式是 `V2 Signer: certificate SHA-256 digest:`，而断言按本机 36 的 `Signer #1 certificate SHA-256 digest:` 写的，"
    "取到空值 → 误判失败（保险丝拦住了，没发出去；日志里的指纹其实是对的）。已改成两种格式都认，"
    "并在解析为空时单独报错，免得下次把「解析失败」误报成「证书不对」。\n"
    "\n"
    "**LICENSE**：用户 2026-10-14 决定**不加**（＝保留所有权利）。`README.md` 里写明了这条，"
    "免得外人以为公开＝随便拿去再发布。\n",
    "RELEASE §9.1 打通",
)

# ---- 3) RELEASE §10.10：补一行"对外发布的包" ----
sub(
    D + r"\RELEASE.md",
    "| debug APK | `app-debug.apk`",
    "| **对外发布的包**（GitHub Releases） | `wheremy-0.1.0-release.apk`，2,763,109 字节，SHA-256 `"
    + PUB_SHA.upper()
    + "` —— 由 **CI** 从标签 `v0.1.0` 构建并签名（run `38506779304`），"
    "与上面那个本地归档包**同代码不同哈希**（APK 不可字节复现，见下面的坑①）；`SHA256SUMS.txt` 与它一致 ✓ |\n"
    "| debug APK | `app-debug.apk`",
    "RELEASE §10.10 发布包行",
)

# ---- 4) 发布记录：补一行分发物 ----
sub(
    D + r"\release-0.1.0-2026-10-13.md",
    "| 归档位置 |",
    "| **对外发布物**（GitHub Releases） | `wheremy-0.1.0-release.apk`，2,763,109 字节，SHA-256 `"
    + PUB_SHA
    + "`；由 CI 从标签 `v0.1.0` 构建并签名（run `38506779304`，全绿），随 `SHA256SUMS.txt` 一起挂在 "
    "https://github.com/mapleafly/wheremy/releases/tag/v0.1.0 —— 与本地归档包同代码、不同哈希（APK 不可字节复现） |\n"
    "| 归档位置 |",
    "发布记录 分发物",
)

# ---- 5) 归档 README ----
sub(
    W + r"\artifacts\release\0.1.0\README.md",
    "## 这个包被验证到什么程度",
    "## 还有第二个包：对外发布的那一个\n\n"
    "GitHub Releases 上挂着 `wheremy-0.1.0-release.apk`（SHA-256 `" + PUB_SHA + "`，2,763,109 字节），\n"
    "那是 **CI 从标签 `v0.1.0` 构建并签名**的（run `38506779304`），**与本目录这个包同代码、不同哈希**\n"
    "（APK 不可字节复现，见下面坑①）。对外给别人用走那个；本目录这个留着当「装机验收过的那一个」。\n\n"
    "## 这个包被验证到什么程度",
    "归档 README 说明",
)

# ---- 6) CHANGELOG ----
section = """## 2026-10-14 · 发布管道打通：CI 出签名包 → 真机冒烟 → Release 上线

用户配好三个 secret 之后，整条路当天打通（证据都在 `RELEASE.md` §9.1）：

| 步 | 结果 |
|---|---|
| 手动派发 | run `38506495467` **全绿**：还原 keystore → `assembleRelease` → **验签指纹通过** → 算哈希 → 上传产物 |
| 真机冒烟 | 把 CI 产出的包装到小米 13 上：跑起来、设置页「导出到文件」与「自动导出会用这台手机上记住的口令加密。」都在 |
| 打标签 | `v0.1.0` → run `38506779304` 全绿（含「发 Release」步）→ https://github.com/mapleafly/wheremy/releases/tag/v0.1.0 |
| 发布物自查 | 从 Release 下载回来重算：APK SHA-256 `dd8a1aa2…` 与 `SHA256SUMS.txt` **一致** ✓；证书仍是发布者那张 `8118b97d…` ✓ |

**途中修掉一个真 bug**：第一次派发（run `38506064573`）卡在「验签」步——runner 的 apksigner 是
**build-tools 37**，输出格式是 `V2 Signer: certificate SHA-256 digest:`，而我的断言按本机 36 的
`Signer #1 certificate SHA-256 digest:` 写的，`grep` 取到空值 → 误判失败。保险丝拦住了（**没有**发出任何包），
而且日志里打印的指纹其实是对的。已改成两种格式都认，并在解析为空时单独报错——免得下次把「解析失败」误报成「证书不对」。

**顺带实证了文档里那条**「APK 不是字节可复现的」：同一份代码三次构建三个哈希
（本地 `05005413…` / run 38506495467 `57b424af…` / 发布 `dd8a1aa2…`），大小都是 2,763,109 字节。

**LICENSE**：用户 2026-10-14 决定**不加**（保留所有权利）；`README.md` 补了「下载安装」与「许可」两节，
写明公开的是源码、APK 可以给朋友装，但不得复制再发布。

---

"""
C = D + r"\CHANGELOG.md"
t = io.open(C, encoding="utf-8", newline="").read()
j = t.index("---\n\n") + len("---\n\n")
io.open(C, "w", encoding="utf-8", newline="").write(t[:j] + section + t[j:])

print("成功 %d 处" % ok)
for f in fail:
    print("  未匹配：" + f)
