# 还原 §10.1（它是 10-09 的历史快照，不许回改），把当前发布物挪到新的 §10.10
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


# ---- 1) 把 §10.1 表里的三行还原成 10-09 的原文 ----
sub("| release APK | **`wheremy-0.1.0-release.apk`**，**2,746,725 字节**，"
    "SHA-256 `05D0EBD179B995570486C1D457F69CFBF8B0F21FF5B44AE2951BFDAC1743703E`（R8 + 资源收缩）。"
    "**这就是真机验收与 TalkBack 走查用的那个包**——2026-10-13 从设备上取回（`adb pull $(pm path com.wheremy.app)` 去掉 `package:` 前缀），"
    "哈希与 §10.7 记的一致 |",
    "| release APK | `app-release.apk`，**2,691,833 字节**，"
    "SHA-256 `B13EB88EDE264F77DC178F29382F7553A9A29DE06C0DCC3A2D1D49C712CE848B`（R8 + 资源收缩） |",
    "还原 release APK 行")
sub("| debug APK | `app-debug.apk`，**17,623,205 字节**，"
    "SHA-256 `7FEE258D3A6A8AE0AB7F932806EFD7925CFFEC954696476A203D0FDB0D4637FE` |",
    "| debug APK | `app-debug.apk`，17,380,427 字节，"
    "SHA-256 `4C1615E9C42C9B55ADEE04CCD8CA2315F9A38A798CAA87B410240F7707A293A0` |",
    "还原 debug APK 行")
sub("| 签名 | ✅ **用户自己的 keystore**（⭐FR-RLS-01 已完成，2026-10-10；见 §3.2 与 `KEYSTORE.md`）。"
    "`apksigner verify --print-certs` 结果：DN `CN=Wheremy, OU=Personal, O=Wheremy, L=Beijing, ST=Beijing, C=CN`，"
    "证书 SHA-256 `8118b97d588e43c91a77540154fda366749e0111f1bafd2765ee7a47676b8369` |",
    "| 签名 | ⚠️ **调试签名**（⭐FR-RLS-01 未完成，§3.2）；能装能跑，不能作为正式交付 |",
    "还原签名行")
sub("| 归档 | **`artifacts/release/0.1.0/`**（`wheremy-0.1.0-release.apk` + `SHA256SUMS.txt` + `README.md`）；"
    "**不进 git**（2.6 MB 二进制没必要进仓库，哈希与证据在上表与 §10.7）。keystore **位置**见 §3.2（只记位置，绝不记口令） |\n",
    "",
    "移除 §10.1 里的归档行")

# ---- 2) 把"归档坑"那段从 §10.1 挪走 ----
sub("**归档这件事踩到的坑（写下来，别让下一个人再踩）**：\n\n"
    "1. **APK 不是字节可复现的**：同一份代码两次构建，字节数与哈希都可能不同（这次两次构建大小相同、哈希不同；"
    "150 个条目里 149 个逐字节一致，唯一不同的是 AGP 写的 `META-INF/version-control-info.textproto`——它记的是构建时的 git revision）。"
    "所以**「归档的包」必须就是「验收的包」**，不能验收完再重新构建一个来归档。\n"
    "2. **归档要在验收之后立刻做**：这次差点错过——验收包被后续的本地 CI 构建覆盖掉了，"
    "最后是**从设备上把装着的那个 APK 拉回来**才拿到（`adb shell pm path com.wheremy.app` → `adb pull`）。\n"
    "3. **提交号别凭记忆填**：APK 里那个 `version-control-info.textproto` 写着真实 revision，是最可靠的依据——"
    "这次就靠它发现 §10.7 原先记的提交号写错了（见该节）。\n\n"
    "### 10.2 §9 清单逐条",
    "### 10.2 §9 清单逐条",
    "挪走归档坑段落")

# ---- 3) 在 §10 开头的历史说明块里追加一条带日期的更新（沿用该块自己的写法） ----
sub("所以本节表格里凡写「调试签名」的地方都只代表 10-09 那天；**看当前状态请以 §3.2 与 `docs/CHANGELOG.md` 为准**。",
    "所以本节表格里凡写「调试签名」的地方都只代表 10-09 那天；**看当前状态请以 §3.2 与 `docs/CHANGELOG.md` 为准**。\n"
    ">\n"
    "> ⚠️ **后续更新（2026-10-13）**：本节表格里那两个 APK 哈希同样是 **10-09 那天**的构建。"
    "**当前发布物、归档位置、签名证书指纹与「归档这件事的三个坑」见 §10.10**。",
    "历史说明块追加更新")

# ---- 4) 新增 §10.10（放在 §10.9 结论之后） ----
section = """### 10.10 当前发布物与归档 —— 2026-10-13

> §10.1 是 10-09 的签收快照（按当时的哈希保留），这一节才是**当前**该看的那一份。

| 项 | 值 |
|---|---|
| 归档位置 | **`artifacts/release/0.1.0/`**：`wheremy-0.1.0-release.apk` + `SHA256SUMS.txt` + `README.md`。**不进 git**（2.6 MB 二进制没必要进仓库）；哈希与证据在本表与 §10.7 |
| release APK | `wheremy-0.1.0-release.apk`，**2,746,725 字节**，SHA-256 `05D0EBD179B995570486C1D457F69CFBF8B0F21FF5B44AE2951BFDAC1743703E` —— **这就是真机验收与 TalkBack 走查用的那个包**，由提交 `75e7c54` 构建，2026-10-13 从设备上取回 |
| debug APK | `app-debug.apk`，17,623,205 字节，SHA-256 `7FEE258D3A6A8AE0AB7F932806EFD7925CFFEC954696476A203D0FDB0D4637FE`（仅本机构建产物，不归档） |
| 签名 | ✅ **用户自己的 keystore**（⭐FR-RLS-01 已完成，2026-10-10；见 §3.2 与 `KEYSTORE.md`）。`apksigner verify --print-certs`：DN `CN=Wheremy, OU=Personal, O=Wheremy, L=Beijing, ST=Beijing, C=CN`，证书 SHA-256 `8118b97d588e43c91a77540154fda366749e0111f1bafd2765ee7a47676b8369`；`apksigner verify` 无输出 = 通过 |
| keystore **位置** | §3.2（`D:\\keys\\wheremy-release.jks`，别名 `wheremy-release`）——**只记位置，口令只在用户自己的密码管理器里** |

**归档这件事踩到的坑（写下来，别让下一个人再踩）**：

1. **APK 不是字节可复现的**：同一份代码两次构建，哈希不同（这次两次构建**大小相同、哈希不同**；
   150 个条目里 **149 个逐字节一致**，唯一不同的是 AGP 写的 `META-INF/version-control-info.textproto`——
   它记的是构建时的 git revision）。所以**「归档的包」必须就是「验收的包」**，不能验收完再重新构建一个来归档。
2. **归档要在验收之后立刻做**：这次差点错过——验收包被后续的本地 CI 构建覆盖了，
   最后是**从设备上把装着的那个 APK 拉回来**才拿到（`adb shell pm path com.wheremy.app` → 去掉 `package:` 前缀 → `adb pull`）。
3. **提交号别凭记忆填**：APK 里那个 `version-control-info.textproto` 写着真实 revision，是最可靠的依据——
   这次就靠它发现 §10.7 原先记的提交号写错了（已改）。

"""
sub("## 附：写作时发现的差异", section + "## 附：写作时发现的差异", "新增 §10.10")

io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("成功 %d 处" % ok)
for f in fail:
    print("  未匹配：" + f)
