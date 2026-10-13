# 重新归档后的记录更新：发布记录 / RELEASE §10.10 / 归档 README / 各处过期的测试数
import io
import sys

sys.stdout.reconfigure(errors="replace")
W = r"D:\src\dsh\wheremy"
D = W + r"\docs"
ok, fail = 0, []

NEW_SHA = "05005413a2ef85014724e6e9bb99a11f3e0f50aea603bdd0d8d80d27f3e3ddd5"
NEW_SIZE = "2,763,109"
OLD_SHA = "05d0ebd179b995570486c1d457f69cfbf8b0f21ff5b44ae2951bfdac1743703e"
OLD_SIZE = "2,746,725"


def sub(path, old, new, label, count=1):
    global ok
    t = io.open(path, encoding="utf-8", newline="").read()
    if old not in t:
        fail.append(label)
        return
    io.open(path, "w", encoding="utf-8", newline="").write(t.replace(old, new, count))
    ok += 1


# ---------- 1) 发布记录：产物三行 ----------
rel = D + r"\release-0.1.0-2026-10-13.md"
sub(
    rel,
    "| commit | 本记录所在提交 **`70345b9`**；**归档的那个包由 `75e7c54` 构建**（依据：APK 内 `META-INF/version-control-info.textproto`；两者在 `app/src/main` 上零差异） |",
    "| commit | 本记录所在提交见 git 历史；**归档的那个包由 `e81a5fb` 构建**（依据：APK 内 `META-INF/version-control-info.textproto`）。"
    "**2026-10-14 重新归档过一次**：`FR-SRC-08` 与 `FR-BAK-09` 落地后重构建，"
    "与真机验收那次（`b5d6d5b`）在 **`app/src/main` 上逐字节相同**（`git diff --stat b5d6d5b e81a5fb -- app/src/main` 为空），"
    "所以 §10.6–§10.8.1 的验收结论对这一个包同样成立；新包另做了装机的两处冒烟（多图界面 + 加密那行状态，都在） |",
    "记录 commit 行",
)
sub(
    rel,
    "| 发布物文件名 + SHA-256 | **`wheremy-0.1.0-release.apk`**，2,746,725 字节，`05d0ebd179b995570486c1d457f69cfbf8b0f21ff5b44ae2951bfdac1743703e` |",
    "| 发布物文件名 + SHA-256 | **`wheremy-0.1.0-release.apk`**，**"
    + NEW_SIZE
    + " 字节**，`"
    + NEW_SHA
    + "`（2026-10-14 重建版；旧版 "
    + OLD_SIZE
    + " 字节 / `"
    + OLD_SHA
    + "` 已被它取代） |",
    "记录 APK 行",
)

# ---------- 2) RELEASE §10.10 ----------
r = D + r"\RELEASE.md"
sub(
    r,
    "| release APK | `wheremy-0.1.0-release.apk`，**2,746,725 字节**，SHA-256 `05D0EBD179B995570486C1D457F69CFBF8B0F21FF5B44AE2951BFDAC1743703E` —— **这就是真机验收与 TalkBack 走查用的那个包**，由提交 `75e7c54` 构建，2026-10-13 从设备上取回 |",
    "| release APK | `wheremy-0.1.0-release.apk`，**"
    + NEW_SIZE
    + " 字节**，SHA-256 `"
    + NEW_SHA.upper()
    + "` —— 由提交 `e81a5fb` 构建（**2026-10-14 重建版**）。"
    "验收结论的迁移依据：`FR-SRC-08`/`FR-BAK-09` 落地后的这个包与验收那次（`b5d6d5b`）在 **`app/src/main` 上逐字节相同**"
    "（`git diff --stat b5d6d5b e81a5fb -- app/src/main` 为空），且新包已装机冒烟（多图界面、「自动导出会用这台手机上记住的口令加密」那行都在）。"
    "旧版 2,746,725 字节 / `05D0EBD1…` 是 2026-10-13 从设备取回的那个，已被取代 |",
    "RELEASE §10.10 APK 行",
)
sub(
    r,
    "### 10.10 当前发布物与归档 —— 2026-10-13",
    "### 10.10 当前发布物与归档 —— 2026-10-13（APK 于 2026-10-14 重建）",
    "RELEASE §10.10 标题",
)

# ---------- 3) §9 清单里过期的份数/例数 ----------
sub(r, "`app/build/test-results/testDebugUnitTest/*.xml` **42 份**", "`app/build/test-results/testDebugUnitTest/*.xml` **47 份**", "§9 第 9 条")
sub(
    r,
    "11. [ ] 自己数出 `tests=261 failures=0 errors=0`——**不认 exit code**（G1）。（数字随批次增长，当前 42 类 / 261 例，2026-10-10；",
    "11. [ ] 自己数出 `tests=300 failures=0 errors=0`——**不认 exit code**（G1）。（数字随批次增长，当前 47 类 / 300 例，2026-10-14；",
    "§9 第 11 条",
)
sub(r, "13. [ ] 覆盖对账反向：**42 个类 / 261 例**（G3；数字随批次增长）。", "13. [ ] 覆盖对账反向：**47 个类 / 300 例**（G3；数字随批次增长）。", "§9 第 13 条")

# ---------- 4) 其它"当前数字"型的地方 ----------
dev = D + r"\DEVELOPMENT.md"
sub(dev, "（当前 42 份）", "（当前 47 份）", "DEVELOPMENT 64", 3)
sub(D + r"\TESTING.md", "**当前 = 42 类 / 261 例**（2026-10-10）", "**当前 = 47 类 / 300 例**（2026-10-14）", "TESTING 197")
sub(
    D + r"\PROJECT.md",
    "| 测试 | **42 类 / 261 例全绿**（2026-10-12：本地 CI 四道门 + GitHub CI 真跑全绿）；判绿方式见 `PROCESS.md` §6 G1 |",
    "| 测试 | **47 类 / 300 例全绿**（2026-10-14：本地 CI 四道门 + GitHub CI 真跑全绿）；判绿方式见 `PROCESS.md` §6 G1 |",
    "PROJECT 209",
)
sub(
    D + r"\DELTA.md",
    "**P0 基线 4 类 / 6 例 → 现在 42 类 / 261 例**（2026-10-10）",
    "**P0 基线 4 类 / 6 例 → 现在 47 类 / 300 例**（2026-10-14）",
    "DELTA 187",
)

# ---------- 5) 归档 README（不进 git，但也要准） ----------
arc = W + r"\artifacts\release\0.1.0\README.md"
sub(arc, "| 文件 | `wheremy-0.1.0-release.apk`，**2,746,725 字节** |", "| 文件 | `wheremy-0.1.0-release.apk`，**" + NEW_SIZE + " 字节** |", "归档 README 文件行")
sub(arc, "| SHA-256 | `" + OLD_SHA + "`（同 `SHA256SUMS.txt`） |", "| SHA-256 | `" + NEW_SHA + "`（同 `SHA256SUMS.txt`） |", "归档 README 哈希行")
sub(arc, "| 构建提交 | `75e7c54`（依据：APK 内 `META-INF/version-control-info.textproto`） |", "| 构建提交 | `e81a5fb`（依据：APK 内 `META-INF/version-control-info.textproto`） |", "归档 README 提交行")
sub(arc, "| 归档时间 | 2026-10-13 |", "| 归档时间 | 2026-10-13（**2026-10-14 重建**：`FR-SRC-08`/`FR-BAK-09` 落地后重构建；与验收那次在 `app/src/main` 上逐字节相同） |", "归档 README 时间行")
sub(arc, "- 本地 CI 四道门 + 真 CI 全绿（43 类 / 268 例）", "- 本地 CI 四道门 + 真 CI 全绿（**47 类 / 300 例**）", "归档 README 测试数")
sub(
    arc,
    "- **迁移与反面演练四条全过**（2026-10-13：合并导入 / 覆盖导入 / schema 更高被拒 / 逐字节往返对账；§10.8）",
    "- **迁移与反面演练四条全过**（2026-10-13：合并导入 / 覆盖导入 / schema 更高被拒 / 逐字节往返对账；§10.8）\n"
    "- **加密包演练四条全过**（2026-10-14：加密导出→包里搜不到物品名 / 错口令「口令不对」/ 对口令导入成功 / 明文包仍不问口令；§10.8.1）",
    "归档 README 演练行",
)

print("成功 %d 处" % ok)
for f in fail:
    print("  未匹配：" + f)
