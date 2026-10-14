# 对外分发这条路：KEYSTORE §13（CI 签名）+ RELEASE §9（分发清单）+ 发布记录签字
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


# ---------- 1) 发布记录：签字 ----------
sub(
    D + r"\release-0.1.0-2026-10-13.md",
    "| 发布负责人 / 日期 | **待用户签**：软件经理＝用户本人（只有他持 keystore）；日期留空待填 |",
    "| 发布负责人 / 日期 | **用户本人 / 2026-10-14**（软件经理＝用户本人，只有他持 keystore；本次发布按此签字生效） |",
    "发布负责人",
)
sub(
    D + r"\release-0.1.0-2026-10-13.md",
    "| 给谁、怎么给（侧载渠道） | **待定**：目前只有用户自己一台机器在用；没有对外分发渠道，也没有上架计划 |",
    "| 给谁、怎么给（侧载渠道） | **自用侧载**（2026-10-14 用户定）：只有用户自己一台机器在用，包在 `artifacts/release/0.1.0/`，手机上装的就是它。"
    "**对外分发是下一步，不是这一次**：用户 2026-10-14 表示想给别人用，路线与待办见 `RELEASE.md` §9.1（用 CI 出签名包 + 选分发渠道） |",
    "渠道行",
)

# ---------- 2) KEYSTORE §13 ----------
keystore_section = """## 13. 用 CI 出签名包（`FR-RLS-02` 的一半：对外分发用）

**能不能用 CI 生成签名包**：能。`.github/workflows/release.yml` 已经写好，**但默认关着**——
它只在「手动 dispatch」或「推 `v*` 标签」时跑，日常门禁还是 `ci.yml`，互不干扰。

**为什么要四个 secret 才能跑**：Gradle 找不到 `keystore.properties` 时会**退回调试签名**
（`app/build.gradle.kts` 的注释写着），那样产出的"发布包"是**假货**——装到别人手机上，
将来用真 keystore 的升级包**覆盖不了**（`KEYSTORE.md` §8：换密钥＝换应用）。
所以那个工作流**缺 secret 就直接红**，宁可不出包，也不出假包。

### 13.1 要配的四个 secret（仓库 → Settings → Secrets and variables → Actions）

| Secret 名 | 值从哪来 |
|---|---|
| `KEYSTORE_BASE64` | 把 `D:\\keys\\wheremy-release.jks` 转成一行 base64（命令见下） |
| `KEYSTORE_STORE_PASSWORD` | `keystore.properties` 里的 `storePassword` |
| `KEYSTORE_KEY_PASSWORD` | 同上的 `keyPassword`（PKCS12 里与 store 口令相同） |
| （别名不设 secret） | 工作流里写死 `wheremy-release`，与 `KEYSTORE.md` §4 一致 |

Windows 上转 base64（**输出是一行**，别带换行）：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('D:\\keys\\wheremy-release.jks')) | Set-Clipboard
# 然后粘进 GitHub 的 secret 输入框
```

### 13.2 跑一次

```
Actions → Release APK → Run workflow（tag 留空 = 只出包不发 Release）
# 或者：git tag v0.1.0 && git push origin v0.1.0   ← 顺手发一个 Release
```

工作流会做五件事：还原 keystore（写在**仓库外**，与本地同一套查找路径）→ `assembleRelease` →
**验签并断言证书指纹**（对不上就红：`8118b97d…` 写在 `EXPECTED_CERT` 里，指纹是公开信息）→
算 SHA-256 写 `SHA256SUMS.txt` → 上传产物（打标签时再 `gh release create`）。

**第一次跑会是红的**——那是在告诉你"secret 还没配"。这不是故障，是保险丝（见上）。

### 13.3 安全上的取舍（看明白再决定）

- 配了 secret，**keystore 就不再只在你机器上**：它以加密形式存在 GitHub 的 secret 里，
  仓库管理员可见、workflow 里能取到。仓库现在是**私有**的，风险可控，但这条要说清。
- 另一条路是**CI 只出未签名包、你本地签名**：更安全，但每次发版都要你在场（不符合"自动推进"）。
- 两条路都**必须用同一张 keystore**：换密钥＝换应用（§8）。
- 我（助手）**永远不会**读、写、打印口令与 keystore 内容；上面那条 base64 命令由你自己跑。

"""
sub(D + r"\KEYSTORE.md", "## 12. 我要从你这拿到的三样（口令**不要**发我）", keystore_section + "## 12. 我要从你这拿到的三样（口令**不要**发我）", "KEYSTORE §13")

# ---------- 3) RELEASE §9.1 对外分发清单 ----------
dist = """### 9.1 对外分发（"想给别人用"，2026-10-14 用户提出）

**现状**：0.1.0 按**自用侧载**签收（发布记录）。想给别人用是**下一步**，要补的不是一行命令，是这几件：

| # | 要做的 | 谁做 | 说明 |
|---|---|---|---|
| 1 | 给 CI 配四个 secret（`KEYSTORE_BASE64` 等） | **用户** | 一次性；配法 `KEYSTORE.md` §13。配完 CI 就能出签名包 |
| 2 | 选**分发渠道** | **用户决定** | 三条路见下表；私有仓库的 Release 只有协作者看得见 |
| 3 | 仓库敏感信息清理 | 助手 | 若选"仓库转公开"：文档里有设备序列号、keystore 路径等，要先扫一遍（`PLAN.md` F52） |
| 4 | 给别人用的**使用说明** | 助手 | 一页纸：怎么装、照片与数据只在本机、AI 要自己填 key、怎么导出备份 |
| 5 | 隐私政策 / 权限说明 | 助手起草，用户过目 | 走应用商店才强制；直接发 APK 也建议有 |
| 6 | 上架材料（图标、截图、软著、开发者账号） | 用户决定要不要 | 只有选"上架"才需要 |

**分发渠道三选一**：

| 路 | 怎么做 | 代价 |
|---|---|---|
| **A. 仓库转公开 + GitHub Releases** | CI 出包后自动发 Release，谁都能下 | 源码与文档全部公开；要先做第 3 项清理 |
| **B. 保持私有 + 手动给** | CI 出 artifact，你下载后自己发给朋友 | 每次发版要你手动转一次；`Release` 页面对外不可见 |
| **C. 上架应用商店** | 开发者账号 + 隐私政策 + 软著 + 审核 | 周期最长（周级），且有审核驳回风险 |

**建议**：先走 **B**（今天就能用，零风险）→ 想省事再考虑 **A**。

"""
sub(D + r"\RELEASE.md", "## 10. 本实现的签收记录 —— 0.1.0（2026-10-09，P6 收口）", dist + "## 10. 本实现的签收记录 —— 0.1.0（2026-10-09，P6 收口）", "RELEASE §9.1")

# ---------- 4) PLAN F52 ----------
sub(
    D + r"\PLAN.md",
    "| F51 |",
    "| F52 | **文档里有几处「不该公开」的信息**（设备序列号 `9aa4b43e`、keystore 路径 `D:\\keys\\…`、本机路径 `D:\\src\\dsh\\wheremy`）✗ "
    "现在仓库是私有的，没影响；**一旦走「仓库转公开」这条路（`RELEASE.md` §9.1 渠道 A）就得先扫一遍** | 2026-10-14 记下，等用户选渠道时处理 |\n"
    "| F51 |",
    "PLAN F52",
)

print("成功 %d 处" % ok)
for f in fail:
    print("  未匹配：" + f)
