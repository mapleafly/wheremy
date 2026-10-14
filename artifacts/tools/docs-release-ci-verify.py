# 把发版工作流的验证结果记进 KEYSTORE §13.2 + CHANGELOG
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


sub(
    D + r"\KEYSTORE.md",
    "**第一次跑会是红的**——那是在告诉你\"secret 还没配\"。这不是故障，是保险丝（见上）。",
    "**第一次跑会是红的**——那是在告诉你「secret 还没配」。这不是故障，是保险丝（见上）。\n"
    "\n"
    "**已经验过（2026-10-14，run `38493705822`）**：在**没配 secret** 的情况下手动派发一次，结果是\n"
    "第 5 步「检查密钥齐不齐」失败、**后面 6–11 步全部 skipped**（没有产出任何包），\n"
    "失败输出逐字为 `##[error]缺少 secret： KEYSTORE_BASE64 KEYSTORE_STORE_PASSWORD KEYSTORE_KEY_PASSWORD`\n"
    "＋「配法：docs/KEYSTORE.md §13」＋「本工作流故意失败：没有 keystore 时 Gradle 会退回调试签名，那种包不能对外发。」\n"
    "——即 YAML 有效、保险丝生效、假包产不出来。**配上 secret 之后请再派发一次**，那时应当一路绿到产物上传。",
    "KEYSTORE §13.2 验证记录",
)

section = """## 2026-10-14 · 0.1.0 签字（自用侧载）+ 把「用 CI 出签名包」做成可选的一条路

**用户 2026-10-14 的决定**：本次发布按**自用侧载**签（发布负责人 = 用户本人 / 2026-10-14），
「想给别人用」是**下一步**。发布记录那两格已按此填好。

**用户的问题「是否可以用 CI 生成（签名包）」**：能，已经做成一条**默认关着**的路
（`.github/workflows/release.yml`，只在手动 dispatch 或推 `v*` 标签时跑，不影响 `ci.yml` 的日常门禁）：

- 四个 secret（`KEYSTORE_BASE64` / `KEYSTORE_STORE_PASSWORD` / `KEYSTORE_KEY_PASSWORD` + 写死的别名）
  ——**缺任何一个就直接失败**。为什么这么硬：Gradle 找不到 `keystore.properties` 会**退回调试签名**
  （`app/build.gradle.kts` 的注释写着），那种"发布包"装到别人手机上**将来覆盖升级不了**（`KEYSTORE.md` §8）；
- 还原 keystore 到**仓库外**（与本地同一套查找路径 `../keystore.properties`）→ `assembleRelease` →
  **验签并断言证书指纹**（`8118b97d…` 写死；指纹是公开信息）→ 算 SHA-256 → 上传产物；
- 推标签时用 `gh release create` 发 Release（**私有仓库的 Release 只有协作者看得见**）。

**已经验过**（run `38493705822`，没配 secret 的情况下手动派发）：第 5 步保险丝失败、6–11 步全部 skipped、
没有产出任何包，错误逐字为「缺少 secret： KEYSTORE_BASE64 KEYSTORE_STORE_PASSWORD KEYSTORE_KEY_PASSWORD」
＋指向 `KEYSTORE.md` §13。即 YAML 有效、保险丝生效。**配上 secret 后请再派发一次**，那时应当一路绿。

**文档**：`KEYSTORE.md` §13（四个 secret 怎么配、Windows 转 base64、第一次跑红是设计、
以及"keystore 从此也进 GitHub secret"这条取舍）；`RELEASE.md` §9.1 对外分发（六项待办 +
渠道 A 仓库转公开 / B 私有手动给 / C 上架 的代价对比，**建议先走 B**）；`PLAN.md` F52
（文档里有设备序列号、本机路径等，若走渠道 A 要先扫一遍）。

---

"""
C = D + r"\CHANGELOG.md"
t = io.open(C, encoding="utf-8", newline="").read()
j = t.index("---\n\n") + len("---\n\n")
io.open(C, "w", encoding="utf-8", newline="").write(t[:j] + section + t[j:])

print("成功 %d 处" % ok)
for f in fail:
    print("  未匹配：" + f)
