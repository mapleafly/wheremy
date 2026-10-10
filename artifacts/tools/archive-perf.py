# 工程侧收尾：冷启动基线更新 + Baseline Profile 评估 + targetSdk 37 评估 + 修 §10.5 过期结论
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
DOCS = r"D:\src\dsh\wheremy\docs"
RELEASE = DOCS + r"\RELEASE.md"
CHANGELOG = DOCS + r"\CHANGELOG.md"
BUILD = r"D:\src\dsh\wheremy\app\build.gradle.kts"

# —— 1) RELEASE §10.4 实测表 ——
r = io.open(RELEASE, encoding="utf-8").read()
old_table = """| 指标 | 本实现（R8 release） | 本实现（debug） | 参照实现（记录值） |
|---|---|---|---|
| 冷启动（`am start -W` TotalTime，4 次中位数） | **233 ms** | 926 ms | 980 ms |
| APK 体积 | **2.69 MB** | 17.4 MB | 18.96 MB |"""
new_table = """| 指标 | 本实现（R8 release） | 本实现（debug） | 参照实现（记录值） |
|---|---|---|---|
| 冷启动（`am start -W` TotalTime，**5 次中位数**） | **218 ms**（215–231） | 951 ms（939–983） | 980 ms |
| APK 体积 | **2.62 MB** | 16.49 MB | 18.96 MB |

> 复测方法：`pwsh -File artifacts/tools/measure-startup.ps1 -Runs 5`（每次先 `am force-stop` 保证是冷启动，
> 报全部值 + 中位数，谁都能复跑出同一量级）。**2026-10-10 复测**：与本会话早期记录的 233 ms / 2.69 MB 相比，
> 在加完批量操作、组合筛选、自动导出、无照片手填等一批功能之后**没有回退**（反而略快）。"""
if old_table in r:
    r = r.replace(old_table, new_table, 1)
    print("§10.4 表已更新")
else:
    print("!! §10.4 表没匹配")

# —— 2) §10.4 之后插入两节评估（放在 §10.5 之前）——
marker = "### 10.5 结论"
addition = """### 10.5 工程侧两项评估（2026-10-10）

**① Baseline Profile：现在不加。**

官方入口：[Baseline Profiles overview](https://developer.android.com/topic/performance/baselineprofiles/overview)。
它的收益是让热点代码路径预编译、省掉首次 JIT；代价是**要新加一个 `:baselineprofile` 模块 + macrobenchmark 用例 +
在设备上生成并随版本维护 profile**。

判据是量出来的数字：release 冷启动**中位数 218 ms**，且这个包只有 2.62 MB、单用户本地应用。
在这个量级上加一套模块与生成流程，收益（几十毫秒）与维护成本不成比例。

**触发条件**（满足任一条再回来做）：① release 冷启动回退到 **400 ms** 以上；
② 冷启动时出现肉眼可感的卡顿（列表首帧、缩略图加载）；③ 包体或启动路径显著变重（例如引入大库、首屏要读全库）。

**② `targetSdk`：保持 36，等设备到 37 再评估。**

- 现状：`compileSdk = 37`（Compose 1.12.1 的 AAR 元数据要求，见 §3.3 F1）、`targetSdk = 36`、`minSdk = 26`；
- **设备是 Android 16 / API 36**（`ro.build.version.sdk = 36`）——targetSdk 37 的行为在**唯一那台真机上根本跑不到**，
  现在跳上去等于"改了一堆测不了的行为"；
- Android 17（API 37）已知的一条主要变更是**大屏上应用的方向/可调整大小限制被忽略**
  （[Behavior changes: Apps targeting Android 17](https://developer.android.com/about/versions/17/behavior-changes-17)、
  [Prepare your app for the resizability and orientation changes in Android 17](https://developer.android.com/blog/posts/prepare-your-app-for-the-resizability-and-orientation-changes-in-android-17)）。
  我们**不锁方向**：清单里没有 `screenOrientation` / `resizeableActivity` / `configChanges` 覆盖，
  横屏与矮窗口早就按自适应做过（F21 那条），所以这条变更对我们**没有可预见的破坏**；
- 另两项常见要求本来就不适用：**16 KB 页大小**（本应用**没有原生库**，`jniLibs` 为空）、
  `allowBackup` 已是 `false`（NFR-03）。

**等设备升到 API 37 时，按这四条复查**：① 大屏/折叠屏上的方向与尺寸行为；
② edge-to-edge 与窗口 insets（自适应已经处理过一轮，但要再看一遍）；
③ 通知与 WorkManager 的配额/权限有没有新要求（到期提醒、自动导出两条链路）；
④ 升级 `targetSdk` 后把 `RELEASE.md` §3 的七项 ⭐ 与 §9 清单重跑一遍。

### 10.6 结论"""
if marker in r:
    r = r.replace(marker, addition, 1)
    print("§10.5/§10.6 已插入两项评估")
else:
    print("!! 没找到 §10.5 结论")

# —— 3) 修 §10.5（现 §10.6）里过期的"keystore 没做" ——
old_line = "**不可以当作\"给别人用的正式包\"**：⭐FR-RLS-01（自己的 keystore）没做，release 现在是调试签名——这一步需要用户的 keystore 与口令保管决定，代理由此停手并记录在 §3.2。"
new_line = ("**可以当作\"给别人用的正式包\"**：⭐FR-RLS-01 已由用户完成——用户自己生成了 keystore"
            "（`D:\\keys\\wheremy-release.jks`，别名 `wheremy-release`）并放好 `keystore.properties`，"
            "release 包已用它签名、`apksigner` 校验通过（`Config: release`）、真机安装为正式包。"
            "口令始终在用户手里，文档只记位置不记口令（`KEYSTORE.md` §12）。")
if old_line in r:
    r = r.replace(old_line, new_line, 1)
    print("§10.6 的过期结论已修")
else:
    print("!! §10.6 过期结论没匹配（可能措辞不同，待手工看）")

io.open(RELEASE, "w", encoding="utf-8", newline="").write(r)

# —— 4) build.gradle.kts 的注释指向评估结果 ——
b = io.open(BUILD, encoding="utf-8").read()
old_c = "// targetSdk 仍保持 36（运行时行为不跟着跳，等 P6 统一评估）。"
new_c = "// targetSdk 保持 36：设备是 API 36，跳上去等于改一堆测不了的行为；评估见 RELEASE.md §10.5。"
if old_c in b:
    b = b.replace(old_c, new_c, 1)
    io.open(BUILD, "w", encoding="utf-8", newline="").write(b)
    print("build.gradle.kts 注释已指向评估结果")
else:
    print("!! build.gradle.kts 注释没匹配")

# —— 5) CHANGELOG ——
c = io.open(CHANGELOG, encoding="utf-8").read()
section = """## 2026-10-10 · 工程侧收尾：冷启动基线复测 + Baseline Profile / targetSdk 37 两项评估

**冷启动复测**（`am start -W` TotalTime，5 次中位数，同一台机器）：

| 包 | 中位数 | 范围 | 体积 |
|---|---|---|---|
| release（R8） | **218 ms** | 215–231 | **2.62 MB** |
| debug | 951 ms | 939–983 | 16.49 MB |
| 参照实现（记录值） | 980 ms | — | 18.96 MB |

加完批量操作、组合筛选、自动导出、无照片手填这一批功能之后**没有回退**（早期记录 233 ms / 2.69 MB）。
新增 `artifacts/tools/measure-startup.ps1`：每次先 `am force-stop` 保证冷启动、连续 N 次、报全部值与中位数
——把量法固定下来，避免"挑一次好看的"。

**两项评估都给了"做/不做"与触发条件**（`RELEASE.md` §10.5）：

- **Baseline Profile：现在不加**。release 冷启动 218 ms、包体 2.62 MB，为几十毫秒引入
  `:baselineprofile` 模块 + macrobenchmark + 每次发版维护 profile，成本不划算。
  触发条件：冷启动回退到 **400 ms 以上**、或出现肉眼可感的首帧卡顿、或启动路径显著变重；
- **`targetSdk` 保持 36**。设备是 **Android 16 / API 36**，targetSdk 37 的行为在唯一那台真机上**跑不到**。
  Android 17 已知的主要变更是**大屏上方向/可调整大小限制被忽略**——我们**不锁方向**（清单里没有
  `screenOrientation`/`resizeableActivity`/`configChanges` 覆盖，横屏与矮窗口早按自适应做过），
  16 KB 页大小也不适用（**没有原生库**）。等设备 OTA 到 37 再按四条清单复查（§10.5 里列了）。

**顺手修掉一句过期结论**：§10.5 原文写着「⭐FR-RLS-01 没做、release 现在是调试签名」——
用户已经生成 keystore 并签名、真机装的是正式包，这句已按事实改掉。

---

"""
anchor = c.index("## 2026-10-10 · CI 收口")
c = c[:anchor] + section + c[anchor:]
io.open(CHANGELOG, "w", encoding="utf-8", newline="").write(c)
print("CHANGELOG 已插入")
