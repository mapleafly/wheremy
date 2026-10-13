# 归档与回滚：修 §10.1（发布物过期）、§10.7（提交号凭记忆写错）、§9 第 26/28/29 条
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\docs\RELEASE.md"
t = io.open(PATH, encoding="utf-8").read()
ok, fail = 0, []


def sub(old, new, label):
    global t, ok
    if old not in t:
        fail.append(label)
        return
    t = t.replace(old, new, 1)
    ok += 1


# ---- §10.1 发布物：三行按事实改，并补归档行与该注意的坑 ----
sub("| release APK | `app-release.apk`，**2,691,833 字节**，SHA-256 `B13EB88EDE264F77DC178F29382F7553A9A29DE06C0DCC3A2D1D49C712CE848B`（R8 + 资源收缩） |",
    "| release APK | **`wheremy-0.1.0-release.apk`**，**2,746,725 字节**，"
    "SHA-256 `05D0EBD179B995570486C1D457F69CFBF8B0F21FF5B44AE2951BFDAC1743703E`（R8 + 资源收缩）。"
    "**这就是真机验收与 TalkBack 走查用的那个包**——2026-10-13 从设备上取回（`adb pull $(pm path com.wheremy.app)` 去掉 `package:` 前缀），"
    "哈希与 §10.7 记的一致 |",
    "§10.1 release APK")
sub("| debug APK | `app-debug.apk`，17,380,427 字节，SHA-256 `4C1615E9C42C9B55ADEE04CCD8CA2315F9A38A798CAA87B410240F7707A293A0` |",
    "| debug APK | `app-debug.apk`，**17,623,205 字节**，"
    "SHA-256 `7FEE258D3A6A8AE0AB7F932806EFD7925CFFEC954696476A203D0FDB0D4637FE` |",
    "§10.1 debug APK")
sub("| 签名 | ⚠️ **调试签名**（⭐FR-RLS-01 未完成，§3.2）；能装能跑，不能作为正式交付 |",
    "| 签名 | ✅ **用户自己的 keystore**（⭐FR-RLS-01 已完成，2026-10-10；见 §3.2 与 `KEYSTORE.md`）。"
    "`apksigner verify --print-certs` 结果：DN `CN=Wheremy, OU=Personal, O=Wheremy, L=Beijing, ST=Beijing, C=CN`，"
    "证书 SHA-256 `8118b97d588e43c91a77540154fda366749e0111f1bafd2765ee7a47676b8369` |",
    "§10.1 签名")
sub("| R8 冒烟包 | `.r8smoke`",
    "| 归档 | **`artifacts/release/0.1.0/`**（`wheremy-0.1.0-release.apk` + `SHA256SUMS.txt` + `README.md`）；"
    "**不进 git**（2.6 MB 二进制没必要进仓库，哈希与证据在上表与 §10.7）。keystore **位置**见 §3.2（只记位置，绝不记口令） |\n"
    "| R8 冒烟包 | `.r8smoke`",
    "§10.1 归档行")

# 在 §10.1 表后补一段"归档这件事的坑"
sub("### 10.2 §9 清单逐条",
    "**归档这件事踩到的坑（写下来，别让下一个人再踩）**：\n\n"
    "1. **APK 不是字节可复现的**：同一份代码两次构建，字节数与哈希都可能不同（这次两次构建大小相同、哈希不同；"
    "150 个条目里 149 个逐字节一致，唯一不同的是 AGP 写的 `META-INF/version-control-info.textproto`——它记的是构建时的 git revision）。"
    "所以**「归档的包」必须就是「验收的包」**，不能验收完再重新构建一个来归档。\n"
    "2. **归档要在验收之后立刻做**：这次差点错过——验收包被后续的本地 CI 构建覆盖掉了，"
    "最后是**从设备上把装着的那个 APK 拉回来**才拿到（`adb shell pm path com.wheremy.app` → `adb pull`）。\n"
    "3. **提交号别凭记忆填**：APK 里那个 `version-control-info.textproto` 写着真实 revision，是最可靠的依据——"
    "这次就靠它发现 §10.7 原先记的提交号写错了（见该节）。\n\n"
    "### 10.2 §9 清单逐条",
    "§10.1 归档坑")

# ---- §10.7 提交号修正 ----
sub("走查所用提交 = `3ae596a`。",
    "走查所用包由提交 **`75e7c54`** 构建（**依据**：APK 内 `META-INF/version-control-info.textproto` 写着 "
    "`revision: \"75e7c541e36f168d200079794f9d834eee6464bc\"`——不是凭记忆填的；原先本行写 `3ae596a` 是错的，已改）。"
    "`75e7c54` 与随后的 `3ae596a`/HEAD 在 `app/src/main` 上**零差异**（后面几个提交只有文档与测试），"
    "所以走查看到的界面与当前 HEAD 一致。",
    "§10.7 提交号")

# ---- §7.1.1 回滚预案（§9 第 26 条） ----
sub("### 7.2 事故分级与处置动作",
    "### 7.1.1 本次（0.1.0）的具体回滚预案\n\n"
    "> §9 第 26 条要的就是这一小节：**装回哪个包、要不要先导出、谁知道 keystore 在哪**。\n\n"
    "| 问题 | 答案 |\n|---|---|\n"
    "| 装回哪个包 | **`artifacts/release/0.1.0/wheremy-0.1.0-release.apk`**（2,746,725 字节 / "
    "`05d0ebd1…`，`SHA256SUMS.txt` 里也有）——**它就是验收通过的那一版** |\n"
    "| 要不要先导出 | **要**。任何写库或换包动作之前先在设置页导出一份，并**别改名、别移动**那个 zip"
    "（§7.2 的 P0 第一步就是这个）；导出回执里的三个数与「照片墙上看到的」对不上时先停下 |\n"
    "| 谁知道 keystore 在哪 | 位置记在 **§3.2**（`D:\\keys\\wheremy-release.jks`，别名 `wheremy-release`）；"
    "**口令只在用户自己的密码管理器里**，仓库与文档里一个字都没有（§3.2 的硬规矩） |\n"
    "| 首版特殊在哪 | 0.1.0 是**第一版**，没有「更旧的正式版」可退。所以这里的「回滚」= **重装同一个包** + 用最新导出的 zip「覆盖导入」"
    "把库重建回导出那一刻 |\n"
    "| 什么退不回来 | 已经升过 schema 的库（首版还没升过，但规则先立着）：装回旧版救不了，只能**前滚**（§5.3） |\n\n"
    "### 7.2 事故分级与处置动作",
    "§7.1.1 回滚预案")

# ---- §9 第 26/28/29 条 ----
sub("26. [ ] 回滚预案写下来：装回哪个包、要不要先导出、谁知道 keystore 在哪（§7.1）。",
    "26. [x] 回滚预案写下来：装回哪个包、要不要先导出、谁知道 keystore 在哪（§7.1）。"
    "✅ **2026-10-13 已写成 §7.1.1**（表格式，四问四答 + 首版特殊之处）。",
    "第26条")
sub("28. [ ] 归档：APK + 哈希 + keystore **位置**（只记位置，绝不记口令）。",
    "28. [x] 归档：APK + 哈希 + keystore **位置**（只记位置，绝不记口令）。"
    "✅ **2026-10-13 已归档到 `artifacts/release/0.1.0/`**（验收包 + `SHA256SUMS.txt` + README；不进 git），"
    "哈希与签名证据在 §10.1，keystore 位置在 §3.2。",
    "第28条")
sub("29. [ ] 发布后 24 小时观察项定下来：能不能打开、录一条、搜一次、导出一次、到期提醒那天有没有响（§8.4）。",
    "29. [x] 发布后 24 小时观察项定下来：能不能打开、录一条、搜一次、导出一次、到期提醒那天有没有响。"
    "✅ **2026-10-13 定下**：条目就在 `templates/release-checklist.md` **§6**"
    "（原先这里指向 §8.4 是**写错了**，§8.4 讲的是留痕与 debug 区，与观察项无关，已改）。",
    "第29条")

io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("成功 %d 处" % ok)
for f in fail:
    print("  未匹配：" + f)
