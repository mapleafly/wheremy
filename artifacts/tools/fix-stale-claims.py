# 把剩余「当前状态」过期说法对齐事实（历史记录不动；F4 只加一条「已被 F46 撤回」的注）。
import io
import sys

sys.stdout.reconfigure(errors="replace")
D = r"D:\src\dsh\wheremy\docs"
ok = 0
fail = []


def sub(path, old, new, label):
    global ok
    t = io.open(path, encoding="utf-8").read()
    if old not in t:
        fail.append(label)
        return
    io.open(path, "w", encoding="utf-8", newline="").write(t.replace(old, new, 1))
    ok += 1


# 1) ARCHITECTURE：release 块
sub(D + r"\ARCHITECTURE.md",
    "- `release { isMinifyEnabled = false }` 且**无 signingConfig**：见 `PRD.md` ⭐FR-RLS-01。",
    "- `release { isMinifyEnabled = true }` + `isShrinkResources = true`（keep 规则在 `proguard-rules.pro`）、"
    "签名走用户自己的 keystore（仓库外 `keystore.properties`，没有则回落到 debug 签名并打 ⚠️）："
    "**R8 在 P6 已打开**，⭐FR-RLS-01 于 2026-10-10 由用户完成（`RELEASE.md` §3.2、`KEYSTORE.md`）。",
    "ARCHITECTURE release 块")

# 2) ARCHITECTURE：settings.gradle.kts 仓库
sub(D + r"\ARCHITECTURE.md",
    "`settings.gradle.kts`（21 行）：仓库走阿里云镜像 + `RepositoriesMode.PREFER_SETTINGS`。",
    "`settings.gradle.kts`：仓库**只用官方源**（google / mavenCentral / gradlePluginPortal）+ "
    "`RepositoriesMode.PREFER_SETTINGS`。⚠️ 2026-10-10 更正：阿里云镜像**不再写进仓库配置**——"
    "它在 GitHub runner 上返回 502，把 CI 的插件解析搞红了（F46）；本机加速交给本机的 init 脚本。",
    "ARCHITECTURE settings.gradle")

# 3) DEVELOPMENT：build.gradle 表格那一行
sub(D + r"\DEVELOPMENT.md",
    "| `release { isMinifyEnabled = false }` 且**无 signingConfig** | `:22` | 见 `PRD.md` ⭐FR-RLS-01。"
    "**注意后果**：release 变体的签名配置是 `null`（用 `gradle :app:signingReport` 可自证），"
    "所以现在**打不出能侧载的 release 包**——开发与验收期只用 debug 包；签名是发布前的必做项（`RELEASE.md` §3） |",
    "| `release { isMinifyEnabled = true }` + `isShrinkResources = true`，签名走用户 keystore | `:74-88` | "
    "**R8 在 P6 已打开**（不是「未混淆」了）；签名配置读仓库外的 `keystore.properties`，没有时回落到 debug 签名并打 ⚠️。"
    "⭐FR-RLS-01 已于 2026-10-10 由用户完成：`gradle :app:signingReport` 现在是 `Config: release`（`RELEASE.md` §3.2） |",
    "DEVELOPMENT release 行")

# 4) DEVELOPMENT：仓库与解析模式
sub(D + r"\DEVELOPMENT.md",
    "**仓库与解析模式**（`settings.gradle.kts:1-19`）：`pluginManagement` 与 `dependencyResolutionManagement` 都走阿里云镜像（`gradle-plugin` / `google` / `public`），并设了 `RepositoriesMode.PREFER_SETTINGS`",
    "**仓库与解析模式**（`settings.gradle.kts`）：`pluginManagement` 与 `dependencyResolutionManagement` **都只用官方源**"
    "（`google` / `mavenCentral` / `gradlePluginPortal`；2026-10-10 起阿里云镜像已从仓库配置里去掉，原因见 F46），"
    "并设了 `RepositoriesMode.PREFER_SETTINGS`",
    "DEVELOPMENT 仓库与解析模式")

# 5) DEVELOPMENT：第 2 步那处括号
sub(D + r"\DEVELOPMENT.md",
    "| 2 | `BUILD SUCCESSFUL` + APK 文件存在 | 依赖能不能下（阿里云镜像，§5.1） |",
    "| 2 | `BUILD SUCCESSFUL` + APK 文件存在 | 依赖能不能下（官方源，§5.1） |",
    "DEVELOPMENT 第2步")

# 6) TECH_STACK：性能行
sub(D + r"\TECH_STACK.md",
    "| 性能 | R8 full mode 强推荐；Baseline/Startup Profiles 强推荐；Macrobenchmark | **没做**：release 现在 `isMinifyEnabled = false` 且无签名（⭐FR-RLS-01）；混淆决策在 `RELEASE.md` §3 已写成\"保持 or 打开 + 理由\"，Baseline Profiles 尚无条目 → 记为 T14 |",
    "| 性能 | R8 full mode 强推荐；Baseline/Startup Profiles 强推荐；Macrobenchmark | **R8 已做**（`isMinifyEnabled = true` + 资源收缩，P6 打开）；"
    "**Baseline Profiles 评估后决定现在不做**：R8 冷启动 218 ms / 2.62 MB，触发条件（≥400 ms / 可感卡顿 / 启动路径显著变重）见 `RELEASE.md` §10.5。T14 至此收口 |",
    "TECH_STACK 性能行")

# 7) TECH_STACK：T14 队列行
sub(D + r"\TECH_STACK.md",
    "| **T14** | 性能发布配置：R8 full mode / Baseline Profiles / Macrobenchmark | 官方**强推荐**；现在 release 未混淆也无 profile | 中：混淆要 keep 规则 + 全量回归 + 真机冒烟（`RELEASE.md` §3 已写成\"保持 or 打开 + 理由\"） | 与 ⭐FR-RLS-01（签名包）同批做 | 先定\"混淆 or 保持\"并写理由，再谈 Baseline Profiles |",
    "| ~~**T14**~~ ✅ **已收口（2026-10-10）** | 性能发布配置：R8 full mode / Baseline Profiles / Macrobenchmark | 官方**强推荐** | —— | —— | R8 + 资源收缩已开（P6）；Baseline Profiles **评估后决定现在不做**，触发条件写在 `RELEASE.md` §10.5 |",
    "TECH_STACK T14")

# 8) PRD：技术基线那行
sub(D + r"\PRD.md",
    "- 版本目录 `gradle/libs.versions.toml`；仓库走阿里云镜像 + `RepositoriesMode.PREFER_SETTINGS`。",
    "- 版本目录 `gradle/libs.versions.toml`；仓库**只用官方源** + `RepositoriesMode.PREFER_SETTINGS`"
    "（2026-10-10 更正：阿里云镜像已从仓库配置移除，见 F46；本机加速走本机 init 脚本）。",
    "PRD 技术基线")

# 9) RELEASE：§3 依赖源那一行
sub(D + r"\RELEASE.md",
    "| 依赖源 | `settings.gradle.kts` 里显式写了阿里云镜像 + 官方源兜底；机器上那份 `init.d/init.gradle` 会注入镜像，所以开了 `RepositoriesMode.PREFER_SETTINGS` | `settings.gradle.kts`（F4） |",
    "| 依赖源 | `settings.gradle.kts` **只用官方源**（google / mavenCentral / gradlePluginPortal）；"
    "开了 `RepositoriesMode.PREFER_SETTINGS` 是因为机器上那份 `init.d/init.gradle` 会注入镜像。"
    "⚠️ 2026-10-10 更正：镜像**不再写进仓库配置**（runner 上 502 → CI 红，F46） | `settings.gradle.kts`（F4/F46） |",
    "RELEASE 依赖源")

# 10) PROJECT：M7 那一行的状态
sub(D + r"\PROJECT.md",
    "| **M7 发布就绪**（新增，见下） | ⭐真实 Migration、⭐签名包、备份兼容矩阵、发布演练 | 新增 migration 测试 | `RELEASE.md` §3 全部勾掉 + G0–G5 | ⬜ **未开始** |",
    "| **M7 发布就绪**（新增，见下） | ⭐真实 Migration、⭐签名包、备份兼容矩阵、发布演练 | 新增 migration 测试 | `RELEASE.md` §3 全部勾掉 + G0–G5 | 🟡 **大部分已完成（2026-10-12）**：⭐签名包 ✅（2026-10-10 用户完成）；发布演练 = 真机人工清单七条 **7/7 通过**（`RELEASE.md` §10.7）；⭐真实 Migration 对本实现**不构成债**（从第一天就没引入破坏性迁移，`WheremyDatabase.kt:15`），要升 schema 时按 `REBUILD.md` §2 走；备份兼容矩阵见 `RELEASE.md` §12 |",
    "PROJECT M7 行")

# 11) PROJECT：M7 的理由段（把「未混淆」这类过期说法收一下）
sub(D + r"\PROJECT.md",
    "它的存在理由：M0–M6 都用\"验收期可接受的临时手段\"收尾（破坏性迁移、无签名、未混淆），",
    "它的存在理由：M0–M6 都用\"验收期可接受的临时手段\"收尾（当时是无签名、未混淆；破坏性迁移本实现一直没引入），",
    "PROJECT M7 理由段")

# 12) PLAN：F4 加「已被 F46 撤回」的注（不改写历史结论，只标注后续）
sub(D + r"\PLAN.md",
    "| F4 | 本机有组织级 Gradle init 脚本（注入阿里云镜像），官方推荐的 `FAIL_ON_PROJECT_REPOS` 会**直接构建失败** | 改 `PREFER_SETTINGS`；同时把阿里云镜像**显式写进 `settings.gradle.kts`**（仓库自包含，换机/CI 自动回落官方源） |",
    "| F4 | 本机有组织级 Gradle init 脚本（注入阿里云镜像），官方推荐的 `FAIL_ON_PROJECT_REPOS` 会**直接构建失败** | 改 `PREFER_SETTINGS`；同时把阿里云镜像**显式写进 `settings.gradle.kts`**（仓库自包含，换机/CI 自动回落官方源）。⚠️ **2026-10-10 按 F46 撤回后半句**：镜像不该写进仓库配置（runner 上 502 → CI 红），已改回只用官方源 |",
    "PLAN F4 注")

print("成功替换 %d 处" % ok)
if fail:
    print("未匹配：")
    for f in fail:
        print("  - " + f)
