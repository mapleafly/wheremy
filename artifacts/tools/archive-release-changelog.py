# CHANGELOG：修提交号 + 记归档与回滚预案
import io
import sys

sys.stdout.reconfigure(errors="replace")
CH = r"D:\src\dsh\wheremy\docs\CHANGELOG.md"
t = io.open(CH, encoding="utf-8").read()
ok, fail = 0, []


def sub(old, new, label):
    global t, ok
    if old not in t:
        fail.append(label)
        return
    t = t.replace(old, new, 1)
    ok += 1


sub("**同版本往返 + 旧包合并**；跨版本那一半仍留给 0.2.0。设备 = 小米 13（HyperOS 3.0.309.0），提交 `3ae596a`。",
    "**同版本往返 + 旧包合并**；跨版本那一半仍留给 0.2.0。设备 = 小米 13（HyperOS 3.0.309.0），"
    "机上那个包由提交 `75e7c54` 构建（依据见 §10.7）。",
    "演练那节的提交号")
sub("**签收记录**：`RELEASE.md` §10.7（含环境快照：2.62 MB、SHA-256 `05D0EBD1…`、用户 keystore、提交 `3ae596a`）；",
    "**签收记录**：`RELEASE.md` §10.7（含环境快照：2.62 MB、SHA-256 `05D0EBD1…`、用户 keystore、"
    "构建提交 `75e7c54`——原先写 `3ae596a` 是凭记忆填错了，后从 APK 内的 "
    "`META-INF/version-control-info.textproto` 核实并改正）；",
    "TalKBack 那节的提交号")

section = """## 2026-10-13 · 归档发布物 + 回滚预案（§9 第 26/28 条）+ 修 §10.7 的提交号

把发布这件事里还剩的**文件级**动作做掉：

- **归档**（第 28 条）：`artifacts/release/0.1.0/` = `wheremy-0.1.0-release.apk`（**2,746,725 字节**，
  SHA-256 `05D0EBD1…`）+ `SHA256SUMS.txt` + `README.md`。**这个包就是真机验收与 TalkBack 走查用的那一个**——
  它是 2026-10-13 用 `adb shell pm path com.wheremy.app` → `adb pull` **从设备上取回来**的（验收包文件被后续本地 CI 构建覆盖了）。
  `apksigner verify` 通过；证书 DN `CN=Wheremy…`，证书 SHA-256 `8118b97d…`。归档**不进 git**（2.6 MB 二进制没必要），哈希与证据写进 `RELEASE.md`。
- **回滚预案**（第 26 条）：新增 `RELEASE.md` **§7.1.1**——装回哪个包（就是上面这个）、要不要先导出（要，且别改名别移动那个 zip）、
  谁知道 keystore 在哪（位置记在 §3.2，口令只在用户密码管理器里）、首版特殊之处（没有更旧的正式版可退，
  所以"回滚"= 重装同一个包 + 用最新导出覆盖导入）。
- **24 小时观察项**（第 29 条）：条目本就在 `templates/release-checklist.md` §6——原先 §9 第 29 条把它指向 §8.4 是**写错了**（§8.4 讲留痕与 debug 区），已改。

**顺带修掉三处文档问题**（都是这一轮核对出来的）：

1. **§10.7 的提交号写错了**：原先写走查用 `3ae596a`，而 APK 内的 `version-control-info.textproto` 写着
   **`75e7c54`**（验收包是本地 CI 那次构建出来的）。已按证据改正，并注明两者在 `app/src/main` 上零差异。
2. **我自己一度改错了 §10.1**：§10 开头写明"这一节是 2026-10-09 的签收快照，**内容按当时的事实保留、不回改**"，
   我却把表里的 APK 哈希与签名行直接改成了当前值。发现后**已还原**，改为按该节自己的惯例追加一条带日期的更新说明，
   并把"当前发布物"另立 **§10.10**（含签名指纹与下面那三个坑）。
3. **归档这件事的三个坑**（写进 §10.10，别再踩）：
   ① **APK 不是字节可复现的**——同代码两次构建哈希不同（这次两次**大小相同**，150 个条目里 **149 个逐字节一致**，
   差的那个是 AGP 写的 `META-INF/version-control-info.textproto`），所以"归档的包"必须就是"验收的包"；
   ② **归档要在验收后立刻做**，否则像这次一样只能从设备里往回拉；
   ③ **提交号别凭记忆填**，APK 里那个文件就是依据。

---

"""
i = t.index("---\n\n") + len("---\n\n")
t = t[:i] + section + t[i:]
io.open(CH, "w", encoding="utf-8", newline="").write(t)
print("CHANGELOG 已插入；sub 成功 %d 处" % ok)
for f in fail:
    print("  未匹配：" + f)
