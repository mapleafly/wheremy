# Wheremy（本工作区新实现）开发计划与总流程（PLAN）

**这份文档回答**：现在能不能开工、按什么顺序做、**先做 UI 还是先写代码**、每阶段的出口判据、怎么与参照实现对比、结果记在哪。
**它不回答**：产品要什么（`PRD.md`）、一次改动怎么走（`PROCESS.md`）、技术选型凭什么（`TECH_STACK.md`）、参照实现的细节（`ARCHITECTURE.md` / `RULES.md` / `UI.md`）。

**三个坐标必须先分清**：

| 名称 | 位置 | 性质 |
|---|---|---|
| **参照实现** | `D:\src\qoder\wheremy`（commit `cf73f5b`） | **只读**。它是需求与验收的对照物：功能、细节、界面都要和它比 |
| **本实现**（新） | 本工作区 `D:\src\dsh\wheremy` | 从零开发的产品；目标是在功能、细节、界面三个维度**不弱于且优于**参照实现 |
| **文档集** | 本工作区 `docs/` | 两者共用的规范来源；产品/规则/界面语义跨实现不变，**as-built 类文档（`ARCHITECTURE.md`/`RULES.md`/`UI.md`/`REBUILD.md`）描述的是参照实现** |

---

## 0. 一页结论（TL;DR）

| 问题 | 结论 |
|---|---|
| 能不能开工？ | **可以**。§1 的六项就绪判据全部通过（含今日实测的工具链与网络） |
| 先写代码还是先做 UI？ | **都不是**：先做**可运行的 UI 骨架 + 设计系统**（Phase 0），之后按**纵向切片**交替推进——每个切片一次穿过"数据契约 → VM → UI → 测试 → 真机"（§4 给理由与反例） |
| 技术起点用哪套？ | **现行代际**，不从参照实现那套旧组合起步（§3）：Gradle wrapper 9.8.1 / AGP 9.4.1 / Kotlin 2.4.20 / KSP 2.3.12 / Compose BOM 2026.09.00（M3 1.4.0）/ Nav3 1.2.0 / Hilt 2.60.1；Room 2.x/3.x 由 Phase 0 的验证门决定 |
| 怎么保证不比参照差？ | 四类对比 + 三个维度验收（§6）：功能矩阵、细节清单（含把 W1–W11 逐条修掉）、界面截图并排、工程质量（CI/静态检查/截图回归/无障碍） |
| 结果记在哪？ | 本文 §10 阶段记录表 + `DELTA.md`（差异台账，逐条 FR 对照）+ 仓库根 `CHANGELOG.md` + `docs/CHANGELOG.md`（文档集自身） |
| 绝不做什么？ | **绝不修改 `D:\src\qoder\wheremy` 下的任何东西**（含不在那里跑构建）——见 §7.1 |

---

## 1. 就绪判定：能不能进开发阶段

| # | 判据 | 证据 | 结论 |
|---|---|---|---|
| G1 | **需求明确** | `PRD.md` 87 条 FR（✅65 / 🟡7 / ⬜14 / ⛔1）+ §5 待做队列 21 条，每条带触发条件与代价 | 通过 |
| G2 | **规则可执行** | `RULES.md` 全文（谓词/阈值/状态机/逐字文案）+ §16 的十条可检验判据 + §15 已知偏差 W1–W11 逐条记录 | 通过 |
| G3 | **界面语义冻结** | `UI.md` 五屏 + 底栏四格 + 单 FAB + 返回键优先级 + 全部 🗣 逐字文案；`DESIGN_SYSTEM.md` 给了视觉自由区 | 通过 |
| G4 | **流程与门禁就绪** | `PROCESS.md` 八条变更通道 + 文档同步矩阵 + G0–G5 + 8 张模板 | 通过 |
| G5 | **技术基线可执行** | `TECH_STACK.md` §8/§8.9：官方口径 + 版本实测 + 引用来源；本机实测 JDK 21.0.8、SDK 平台 29/34/35/36、build-tools 36.1.0、adb 就位、六个依赖源全部 200 | 通过 |
| G6 | **设备与对照物可用** | 真机 `9aa4b43e` 在线；参照实现可构建（在它自己的目录里由用户/其流程构建，**我们不去构建**）；官方样板 `nowinandroid` / `compose-samples` / `nav3-recipes` 可访问 | 通过 |

**不阻塞开工、但必须在发布前解决的两件事**（沿用参照实现的判断）：⭐FR-DB-01（真实 Migration 取代破坏性迁移）、⭐FR-RLS-01（release 签名）。本实现从一开始就**不引入破坏性迁移**（新库新装，没有历史数据），所以第一条对我们而言是"从一开始就写对"，不是"以后补"。

**开工前唯一要单独验的一件事**：Phase 0 的**工具链 spike**（§3.3）。它不通就没有"现行代际"这条路，要走回退阶梯。

---

## 2. 目标与"更优"的定义（三个维度，都可验收）

泛泛说"更好"没有意义，所以先把"更优"写成可验收的条目：

### 2.1 功能维度

| 目标 | 验收方式 |
|---|---|
| 参照实现的 65 条 ✅ 全部复现 | 逐条 FR 在 `DELTA.md` 打勾，每条至少一个具名测试或真机动作 |
| 参照实现的 7 条 🟡 **补齐为 ✅** | 逐条给出"补齐方式 + 测试"（FR-AI-11 / FR-LOC-05 / FR-FND-07 / FR-BAK-03 / FR-PRM-02 / FR-PRM-05 / FR-TRC-05） |
| 从 14 条 ⬜ 里**选一批做进首个版本**（建议见 §2.4） | 选中的条目在 `PRD.md` 状态改 🟡，在 `DELTA.md` 记为"参照 ⬜ / 本实现 ✅" |
| 1 条 ⛔ 的处理 | 默认保持否决（`DECISIONS.md`）；**要推翻必须写新 ADR 并经用户拍板** |

### 2.2 细节维度（这一维最容易被忽视，也最能体现"更优"）

| 目标 | 验收方式 |
|---|---|
| **把 W1–W11 逐条修掉**（参照实现"记录不改"的已知偏差） | 每条给一个"成立"的测试：W8 英文异常不再上屏、W9 别名换行、W10 两种空名反馈统一、W11 空图格有说法(W11 可以给"图丢了"的明确占位与文案) |
| 每句话都有可达分支 | 沿用 `RULES.md` §16.3：新增/修改文案必须有变异能红的测试 |
| 错误文案零英文 | 静态检查：界面上不存在直接贴 `Throwable.message` 的路径 |
| 边界更硬 | 至少覆盖：空库/空筛选/超长名字/无照片/文件丢失/超大照片/坏备份包/额度耗尽/断网 |

### 2.3 界面维度

| 目标 | 怎么做到 |
|---|---|
| **Material 3 Expressive**（不是普通 M3） | Compose BOM 2026.09.00 → `material3 1.4.0`；设计系统按 expressive 的组件与动效重写一套 token |
| **自适应布局**：手机竖屏 + 横屏 + 平板 | `material3-adaptive-navigation-suite 1.4.0`（`NavigationSuiteScaffold`）+ 窗口尺寸分级；**参照实现锁竖屏（⛔FR-PRM-04），我们从第一天就不锁** |
| **深浅色都当一等公民** | 两套 scheme 同时验；截图回归覆盖 light/dark |
| 动态取色的取舍 | 需要一条 ADR：照片墙区域**保持中性固定色**（参照 ⛔D4 的理由成立：不许干扰用户对物品颜色的判断），但**允许 chrome/accent 跟随动态色**——这是"更好看"与"不干扰判色"的折中 |
| **无障碍达标** | 每个可点元素有语义标签、触控目标 ≥48dp、TalkBack 走查、字缩放 200% 不破版 |
| **视觉回归** | Roborazzi 截图测试进 CI（参照实现没有截图测试） |

### 2.4 ⬜ 条目的纳入建议（待用户确认）

| 档 | 条目 | 理由 |
|---|---|---|
| **建议首版纳入** | FR-PRM-04 横屏/平板、FR-PRM-05 无障碍、FR-FND-07 搜索上限告知（便宜且不骗人）、FR-TRC-04 累计调用次数、FR-TRC-05 留痕耗时、FR-CFG-06 词表自定义 | 都是"小-中"代价、直接提升"细节与界面"评价，且不改变数据模型 |
| **建议第二批** | FR-FND-08 多值筛选、FR-FND-09 最近动过排序、FR-AI-09 AI 预填到期日、FR-SRC-09 无照片手填 | 中等代价，涉及规则与测试面扩展 |
| **暂不纳入**（沿用参照判断） | FR-SRC-08 多图、FR-OPR-06 批量操作、FR-BAK-08 定时导出、FR-EXP-09 其他提醒 | 大代价或没有需求来源；保持 ⬜ 并保留触发条件 |

---

## 3. 技术基线（本实现起点）

### 3.1 起点组合

| 层 | 选择 | 版本 | 说明 |
|---|---|---|---|
| 构建 | Gradle（**自带 wrapper**） | **9.8.1** | 与参照实现不同：我们从第一天就提交 wrapper——它是官方 CI 写法的前置（`TECH_STACK.md` §8.2 更正三） |
| 构建 | AGP | **9.4.1** | AGP 9 内置 Kotlin 插件、旧 DSL 已移除；兼容基线 Gradle ≥9.1 / JDK 17（本机 JDK 21 ✓） |
| 构建 | Kotlin / KSP | **2.4.20 / 2.3.12** | Compose 编译器插件用 `org.jetbrains.kotlin.plugin.compose`（版本随 Kotlin） |
| UI | Compose BOM | **2026.09.00** | → `material3 1.4.0`（M3 Expressive）、`ui/foundation/runtime 1.12.1`、`material3-adaptive-navigation-suite 1.4.0` |
| 导航 | `androidx.navigation3:navigation3-runtime` + `navigation3-ui` | **1.2.0** | 官方 2026-09-23 标注 stable；官方样板 `android/nav3-recipes` 在维护 |
| DI | Hilt | **2.60.1**（KSP） | 官方明确推荐；本项目命中官方判据（多屏 + ViewModel、使用 WorkManager） |
| 数据 | Room：`androidx.room3` **3.0.3** 或 `androidx.room` **2.8.5** | Phase 0 决定 | **验证门**：先各写一个最小 DAO 跑通编译+测试，再定；不靠猜 |
| 偏好 / 后台 / 相机 | DataStore / WorkManager / CameraX | 1.2.1 / 2.12.0 / 1.6.2 | 都是当前 stable |
| 图片 / 网络 / 序列化 | Coil 3.6.3 / OkHttp 5.5.0 + `mockwebserver3` / kotlinx.serialization 1.11.0 / coroutines 1.11.0 | — | OkHttp 5 的测试包名变了，import 要写对 |
| 质量 | Spotless 8.10.3 + ktlint 插件 14.2.0 + AGP Lint | — | **不引 detekt**（2.x 仍 alpha，官方样板也不用） |
| 测试 | JUnit 4.13.2 / Robolectric 4.17 / androidx.test:core 1.7.0 / coroutines-test 1.11.0 / Roborazzi 1.76.0 | — | JVM 为主；截图回归从 Phase 0 就接 |
| CI | GitHub Actions：`checkout@v7` + `setup-java@v6` + `gradle/actions/setup-gradle@v6` | — | 第一天就要绿：`spotlessCheck` + `testDebugUnitTest`（`.github/workflows/ci.yml`，**尚未在真 GitHub 上跑过**） |
| SDK | compileSdk **37**、targetSdk **36**、minSdk **26**、JVM target **17** | — | **实测修正**：Compose 1.12.1 的 AAR 元数据要求 compileSdk ≥ 37（详见 §3.3 实测结果 F1）；targetSdk 保持 36；`java.time` 在 26 上原生可用，**不要加 desugaring** |
| 应用 id | `com.wheremy.app` + debug 变体后缀 **`.dsh`** | — | 让"参照包"与"本实现包"能在同一台真机并存、同屏对比（§6.2） |

### 3.2 仓库布局（本工作区）

```
D:\src\dsh\wheremy\
├── app/                          # 单 module，按包分层（同参照的分层规则）
├── docs/                         # 文档集（本文件所在）
│   ├── PLAN.md                   # ← 本文：开发计划与总流程
│   ├── DELTA.md                  # 差异台账：本实现 vs 参照实现（逐条 FR）
│   └── ...（其余 16 份正文 + templates/ + superpowers/）
├── gradle/                       # wrapper + libs.versions.toml
├── .github/workflows/ci.yml      # G0/G1/G2 的门禁
├── artifacts/                    # 截图/导出/dump 等产物（gitignore，不提交隐私）
├── settings.gradle.kts / gradlew / gradlew.bat / .gitignore
└── CHANGELOG.md                  # 软件版本记录（与 docs/CHANGELOG.md 分工见 RELEASE.md §8）
```

### 3.3 Phase 0 的工具链验证门（spike，半天）

按顺序验，每一步都留证据（命令 + 输出）：

1. `git init` + `.gitignore` + `.editorconfig`；
2. 空 Compose 工程 → `./gradlew :app:assembleDebug`；
3. 第一条 Robolectric 测试（`BuildSmokeTest`）→ `./gradlew :app:testDebugUnitTest`；
4. ktlint + Spotless `check`（只报告不自动格式化）；
5. Hilt 装配（一个 `@HiltViewModel`）+ KSP 通过；
6. Nav3 一个往返（两个目的地 + 返回栈）；
7. Room：**room3 3.0.3 与 room 2.8.5 各跑一个最小 DAO**，谁顺用谁（同一份用例，可比）；
8. Roborazzi 出一张 golden；
9. CI 跑绿（先只跑 `test`）；
10. 装到真机 `9aa4b43e` 看一眼空壳不崩。

**回退阶梯（每一档都要在 Phase 0 内出结论并写回本文）**：
① `AGP 9.4.1 + Kotlin 2.4.20 + KSP 2.3.12` → ② 只退 Kotlin（2.3.x / AGP 9.3.x） → ③ 退到参照组合（AGP 8.13.2 / Gradle 8.14.3 / Kotlin 2.2.21，已证实可用）。
**判定原则**：宁可退一档也不要在一个周末里跟工具链打架；退档必须写 ADR（`DECISIONS.md`），写清"为什么退、什么时候再进"。

### 3.4 实测结果（2026-10-08，第 ① 档全部通过）

**结论：现行代际组合可用，不需要回退。** 10 步验证的实况：

| # | 步骤 | 结果 | 证据 |
|---|---|---|---|
| 1 | `git init` + `.gitignore` + `.editorconfig` | ✅ | 首个提交 `4628a9b` |
| 2 | `./gradlew :app:assembleDebug` | ✅ | `BUILD SUCCESSFUL`；APK 12.9 MB |
| 3 | 第一条 JVM 测试 | ✅ | `BuildSmokeTest` |
| 4 | Spotless（ktlint）`check` | ✅ | `:app:spotlessCheck` 通过 |
| 5 | Hilt 装配 + KSP | ✅ | `@HiltAndroidApp` + `@HiltViewModel` + `@Provides`；`hiltJavaCompileDebug` 执行 |
| 6 | Nav3 往返（两个目的地 + 返回栈） | ✅ | `NavDisplay` + `entryProvider`；返回键走 `onBack` |
| 7 | **Room 3 验证门** | ✅ **选 Room 3** | `androidx.room3:room3-runtime 3.0.3` + `room3-compiler`(KSP)：内存库读写通过；schema 已导出 `app/schemas/…/1.json` |
| 8 | Roborazzi golden | ✅ | `design-gallery-light.png` / `design-gallery-dark.png`（411×891，实图已人工看过） |
| 9 | CI | ⏳ 文件已写，**未在真 GitHub 上执行** | `.github/workflows/ci.yml` |
| 10 | 真机安装 + 冒烟（`9aa4b43e`） | ✅ **通过**（2026-10-08 晚补测） | 安装 `Success`；冷启动 `TotalTime 569 ms`（竖屏）/ `619 ms`（横屏）；`topResumedActivity` = 本应用；`pidof` 有进程；`logcat -b crash` 为空；点击进画廊、**返回键正确回到上一屏且进程不退**（Nav3 `onBack` 生效）；竖屏/横屏各留截图 |

**实测发现（全部已解决或已入档）**：

| # | 发现 | 处理 |
|---|---|---|
| F1 | **Compose 1.12.1（BOM 2026.09.00）要求 `compileSdk ≥ 37`**，而参照实现的 36 不够 | `compileSdk = 37`（AGP 自动补装平台，SDK license 已接受）；targetSdk 仍 36。**这是"本实现比参照新一档"的直接证据** |
| F2 | **Robolectric 4.17 在 JDK 17+ 需要模块开放**，否则所有 Robolectric 测试报 `Failed to interact with raw FileDescriptor internals` | `testOptions.unitTests.all { jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED", …/java.util, …/java.io, …/jdk.internal.access) }` |
| F3 | Compose 的 `@Composable` 函数用 PascalCase，**ktlint 的 `function-naming` 会误报**；且根 `.editorconfig` 传不进 Spotless 的临时目录 | Spotless 里用 `editorConfigOverride(mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable"))` |
| F4 | 本机有组织级 Gradle init 脚本（注入阿里云镜像），官方推荐的 `FAIL_ON_PROJECT_REPOS` 会**直接构建失败** | 改 `PREFER_SETTINGS`；同时把阿里云镜像**显式写进 `settings.gradle.kts`**（仓库自包含，换机/CI 自动回落官方源） |
| F5 | AGP 9 需要 Gradle ≥ 9.1，而本机 PATH 上是 8.14.3 → `NoClassDefFoundError: ProjectTypeBinding` | 用临时下载的 Gradle 9.8.1 生成 wrapper，此后一切走 `./gradlew`（这正是"提交 wrapper"的价值） |
| F6 | `androidx.room3` **没有 `room3-ktx`**（ktx 已并入 runtime）；catalog 别名不能含 `class`（Gradle 保留字） | 依赖表已按实测调整 |
| F7 | Compose 测试的 `createComposeRule` 已被 `androidx.compose.ui.test.junit4.v2.createComposeRule` 取代（构建给出弃用警告） | 已切到 v2（`StandardTestDispatcher` 语义），测试仍全绿 |
| F8 | Nav3 的 `entry` 是 `EntryProviderScope` 的成员，**不需要 import** | 已修；`NavKey`/`entryProvider`/`NavDisplay` 三个 import 足够 |
| F10 | **Room 3 把类型转换注解改名了**：`androidx.room3` 里**根本没有** `@TypeConverter` / `@TypeConverters`，3.x 叫 **`@ColumnTypeConverter` / `@ColumnTypeConverters`**（另有 `ProvidedColumnTypeConverter`、`DaoReturnTypeConverter` 等新东西）；另外 3.x **没有 `room3-ktx`**（ktx 已并入 runtime）。错用旧名的表现是 KSP 报一句极其笼统的 `[MissingType]: Element 'X' references a type that is not present` | 已修（`Entities.kt` / `WheremyDatabase.kt` 注释里写明差别）。**教训**：选 3.x 前先把注解名逐个核实，别假设与 2.x 同名 |
| F11 | **我们的 schema 与参照逐字段一致**（这是备份双向兼容的硬前提） | 用两边导出的 `1.json` 逐字段比对：4 张表、20/7/2/7 个字段的**列名/类型/非空/主键/外键/索引全部相同** ✅。以后任何 schema 改动都要重跑这次比对 |
| F12 | **Room 3 的事务 API 也改名了**：2.x 的 `db.withTransaction { }` 在 3.x 是 **`db.withWriteTransaction { }`**（另有 `withReadTransaction`/`useWriterConnection`/`useReaderConnection`）；写错时编译报 `Unresolved reference 'withTransaction'`。另外在 `try/catch` 里用它要显式写 `withWriteTransaction<Unit>`，否则报 `Return type mismatch: expected 'Nothing', actual 'Unit'`（catch 分支是 `throw`） | 已修；连同 F10 一起写进 `TECH_STACK.md` §8.10 |
| F13 | **KDoc 里写 `/**` 会让整个文件变成未闭合注释**（Kotlin 的块注释可以嵌套）：`目标不存在/**不能移到…**` 这串字符里 `/**` 开了一个嵌套注释，外层的 `*/` 只关掉了内层 → 报 `Syntax error: Unclosed comment`，且报错行号在文件末尾之外 | 已修（把那处改成顿号分隔）。**规矩**：中文里紧跟斜杠的 `**加粗**` 要留空格，别让 `/` 和 `*` 挨上 |
| F14 | `spotlessApply` 与编译**不能放在同一次 Gradle 调用里**（`org.gradle.parallel=true` 时并行执行，格式化正在改源文件，编译读到半截文件 → 报出根本不存在的位置错误，如 `LocationRepository.kt:211` 而文件只有 210 行） | 本地规矩：先 `spotlessApply`，再单独跑测试；CI 里是 `spotlessCheck`（只读）所以不受影响 |
| F15 | **位置树的语义细节必须按参照对齐**：`selectablePaths()` 返回**路径字符串**且**根不列**（根是"家/工作间"那层，不是容器）；`descendantIds()` **含自己**（`subtreeItemCount` 直接拿它求和） | 我第一版按"后代不含自己"写，3 条测试红；读参照 `LocationTree.kt` 后逐条对齐 |
| F16 | **"确认"不等于"已归位"**：待归类的谓词是 `locationId IS NULL OR status = 'DRAFT'`，所以**确认了但没选位置**的物品**仍然**在待归类里——把它搬出去的动作是"选位置/搬到"，不是"保存" | 真机自检第一版把这条判反了（断言"确认后不在待归类"），自检自己抓出来的；已改成两条断言：没选位置→仍在；选了位置→离开 |
| F18 | **"数据没落盘"的虚惊**：真机上把库拉下来看到 `item` 0 行而界面里明明有 5 件，一度怀疑事务没提交 | 查清了：**事务是对的**（JVM 的 `DurabilityTest`「关掉再打开还在不在」+ 真机 `pm clear` 后的干净实验都过；重启后 5 件仍在、WAL 436KB 已提交）。真相是我的**开发期 seed 工具不幂等**——第二遍跑时 `clearSeeds()` 先把种子删了，再因"同级重名"抛错，留下 0 件。已修成 `ensureLocation`（有就复用）。**教训两条**：① 开发期工具也必须幂等；② 持久化要用"磁盘库 + 关掉重开"测，内存库测不出来 |
| F19 | **待归类页只改了标题、没开收件箱谓词**：`inboxMode` 当时只影响标题与芯片，`filters.onlyInbox` 没跟着开，于是"待归类"页把全库列了出来 | 真机截图对比时发现；加 `setInboxMode(on)`（标题 + 谓词一起切）并由测试钉住。**核对方法**：同一屏在两种模式下必须给出不同的行集 || F20 | **刷新竞态**：VM 构造时那次刷新与用户刚点的筛选同时在飞，先发的后落地 → 屏幕上是没筛过的结果而芯片显示"已选" | 就是 `DELTA.md` 里 W6 那条"参照实现没有 seq 守卫"；`GalleryViewModel.refresh()` 加了 `refreshSeq`，过期结果直接丢 |
| F21 | 横屏下照片墙只露图片、文字行被挤出可视区，FAB 压在图上 | 记入 **P6 自适应**清单：横屏/平板要换布局（多列 + 侧栏/双栏），FAB 与网格的避让要在 landscape 下单独算 |
| F23 | **DataStore 在 Windows/Robolectric 上写不进去**：报 "Unable to rename …tmp to …" / "multiple instances of DataStore" | 那是**测试环境**的坑：DataStore 落盘是"写临时文件再原子改名"，而 Windows 上改名覆盖已存在的文件会失败（真机是 Linux，没这个问题）。测试改用内存 `DataStore`（只替掉"怎么存"），**文件形态的持久化由真机兜**：把提前天数改成 30 → 杀进程 → 再进来仍是「提前 30 天提醒」 |
| F24 | **两个时钟**：窗口按传入的 `now` 算，回写 `expireNotifiedAt` 却用 `System.currentTimeMillis()` | 平时两者相等看不出来，但它让"最多两次"这条规则**没法确定性验证**（测试注入的假时间与回写时间差 7 个月，第二分支直接判否）。已统一成一个 `now` |
| F25 | 应用**已在运行时**点到期通知，会落在用户原来那一屏 | `CLEAR_TOP` 会复用栈顶实例，只在 `onCreate` 读 `intent` 拿不到新 extra → 补 `onNewIntent`（把落点放进 `mutableStateOf`）+ 外壳里 `LaunchedEffect(entry)` 先回照片墙。⚠️ adb `am start` 的复现路径与真实通知点击不完全一致：真机验证到的是"冷启动带 `entry` → 照片墙自动筛「快到期/已过期」"这一条链 |
| F26 | **底栏页签把照片墙也清掉了**：`switchTo` 是 `clear()+add()`，于是"在设置页按返回"**直接退出应用**（而不是回照片墙） | 真机点返回时发现。加 `switchTab`：把当前页签换掉、**照片墙永远留在栈底**；返回键于是自然回到照片墙。`switchTo` 只留给"重置到根"（确认页办完、开发期种数据） |
| F27 | **照片墙回到前台不刷新**：覆盖导入之后回到墙上还是导入前那几件（库已经变了，视图没跟上） | 真机上"删一件 → 覆盖导入 → 回墙"时发现；与 `UI.md` §4.2 位置页那条"每次进入本页都 `refresh()`"对齐，`GalleryScreen` 加 `LaunchedEffect(Unit) { vm.refresh() }` |
| F28 | 导出把数据**吞了**：计数流只加字节数、没把字节转发给调用方的流 → 导出的 zip 是空的 | 备份的往返测试当场抓住（6 条红）。计数流必须转发；关流只 `flush`，谁打开的谁关 |
| F17 | 确认页的协程测试**不能用虚拟时间的 `runTest`**：VM 跑在 `viewModelScope`（Main），Room 的挂起查询在它自己的线程池上完成，`runTest` 会在结果回来之前结束 | 测试改成 `Dispatchers.setMain(Dispatchers.Unconfined)` + 轮询等待（`ReviewViewModelTest` 的 `awaitState`）；另有一条"用名字等待会匹配到旧槽位"的竞态要避免（改等 `saved == false`） |
| F22 | 开发期脚手架要能**从命令行直接拉起**，别塞进产品界面 | `MainActivity` 读 `intent.getStringExtra("dev")`（`selfcheck` / `design` / `seed`）；产品界面里只在「设置」占位页留入口 |

**规模（此刻）**：主源 8 个 Kotlin 文件 / 测试 4 个类 / 6 例；wrapper + CI + Spotless + 截图基线已就位。

---

## 4. 先写代码，还是先做 UI？——结论与理由

### 4.1 结论

**先做"可运行的 UI 骨架 + 设计系统"（Phase 0），然后按纵向切片交替推进。**
不是"先做完全部界面"，也不是"先做完全部数据层"。

### 4.2 为什么不是"先做纯 UI（静态稿 + 假数据）"

1. 本项目的界面**由规则驱动**：确认页 13 个字段的次序、退出框两支的逐字文案、照片墙的四种筛选叠加，全部依赖真实状态（`RULES.md` §5/§6/§10）。假数据做出的界面在接真状态机时必然返工。
2. `RULES.md` §16.3 要求"每档说法都要有能到达它的数据状态"——**没有真实状态就验不了这句话**，而这正是本项目最高纪律（NFR-02）。
3. 界面测试按**逐字文案找节点**：UI 一旦与真实分支脱节，测试就是假绿。

### 4.3 为什么不是"先做纯数据层"

1. 用户明确要求"界面效果更优"，而界面是本项目的主要验收面（照片墙/确认页/位置页）；UI 介入越晚，返工越贵。
2. 设计系统（颜色/字号/间距/组件/状态样式/无障碍）是**一整套 token 与组件**，越早立住，后面每加一屏都是套模板；最后统一美化等于重写。
3. 截图回归基线要在**界面还少的时候**建立，否则每加一屏都要回头补 golden。

### 4.4 纵向切片的标准动作（每个切片一次做完这六步）

```
① 数据契约（Models.kt 里的载体类型 + 仓库方法签名）
② VM 状态机（状态 + 意图 + 边界；先想清"哪几档说法"）
③ UI（套设计系统；逐字文案照 UI.md/RULES.md）
④ 测试（JVM 纯逻辑 + Robolectric 数据 + Compose 界面 + 文案变异）
⑤ 真机自验（`9aa4b43e`；结论写回文档）
⑥ 文档同步（`DELTA.md` 打勾 + 相关 as-built 增补 + 门户 CHANGELOG）
```

### 4.5 明确不许的三件事（反例）

- 先写一半页面再接数据（会造成两套状态）；
- 先做全套 mock UI 再整体替换（等于 UI 写两遍）；
- 把设计系统留到最后"统一美化"（会破坏已建立的测试基线）。

---

## 5. 阶段计划 P0–P6

每阶段的**出口判据**都走 `PROCESS.md` §6 的 G0–G5，外加本文的阶段记录表与 `DELTA.md`。

| 阶段 | 目标 | 主要产出 | 出口判据（摘要） |
|---|---|---|---|
| **P0 工程与设计系统** | 能装、能跑测试、CI 绿；设计系统立住 | 仓库骨架 + wrapper + CI + ktlint/Spotless + 工具链 spike 结论（含 Room 2/3 决定）+ 主题与 token + 组件库骨架 + 导航外壳（空屏可切）+ `DesignGallery` 预览屏 + 截图基线 | 10 步 spike 全过；`./gradlew test` 与 CI 绿；空壳 APK 在真机上不崩；Roborazzi 出 golden |
| **P1 数据与照片层** | 四表 + 仓库 + 照片三档 + 派生搜索列 | Room schema + DAO + 仓库（含事务与顺序不变量）+ `PhotoStore`（EXIF 烤平/失败自清理）+ 引用计数回收 + 搜索与排序 | 数据层测试全绿；中文 `LIKE` 命中；删除门槛与引用计数有测试；EXIF 方向在真机正确 |
| **P2 录入闭环** | 第一条端到端价值路径 | 选图 → 立即落库 → 确认页（13 字段 + 多件芯片）→ 保存/稍后处理/不要这件 → 待归类 + 退出三笔账 | `RULES.md` §3/§5/§6 全部判据；"保存比 AI 快"可达；退出两支逐字对得上 |
| **P3 查找闭环** | 照片墙 + 搜索 + 筛选 + 待归类 + 详情/编辑/删除/搬移 | 外壳完整（底栏四格 + 单 FAB + 返回键 + 状态恢复）+ 网格 + 五种筛选 + 三句空态 + 详情与编辑 | `UI.md` §2–§5 逐条；删除话术四档；无障碍一次走查 |
| **P4 位置树** | 建/改/移/排序/删除门槛 + 位置照片 | 树页 + 面包屑 + 节点菜单 + 选择器 + 防环防重名 | `RULES.md` §8 全部；`FR-LOC-05` 的"搬走物品"入口（参照未做） |
| **P5 提醒与备份** | 到期提醒 + 导入导出 + 设置页 | WorkManager 聚合通知 + 提前天数 + 点击落点 + zip 导出/合并/覆盖导入 + 四态设置页 | `RULES.md` §11/§12 全部；导入拒绝四类 + 放行一类；覆盖导入二次确认 |
| **P6 相机与打磨** | 取景 + 无障碍 + 自适应 + 性能 + 发布就绪 | CameraX 取景屏 + 三态门禁；横屏/平板自适应；浅深色；字缩放；R8/Baseline Profile；真实 Migration 策略；签名与发布清单 | `REBUILD.md` §6 人工清单签收；`RELEASE.md` §3 勾完；截图回归覆盖两套主题与三种窗口宽度 |

**规模预期（对齐参照实现，便于比较）**：参照 57 个主源文件 / 6881 行 / 39 个测试类 / 481 例。本实现的目标是**测试数不少于它**（新纳入的 ⬜ 与修掉的 W 条目都会长测试），代码量允许更多（设计系统与自适应布局要额外代码）。

---

## 6. 与参照实现的对比方法

### 6.1 四类对比对象

| 类型 | 比什么 | 产出 |
|---|---|---|
| **功能矩阵** | 87 条 FR 逐条：参照状态 / 本实现状态 / 是否更优 | `DELTA.md` §1（自动从 `PRD.md` 生成骨架，人工填"本实现"列） |
| **细节清单** | W1–W11 是否修掉；错误文案；边界行为；空态与失败态 | `DELTA.md` §2 |
| **界面** | 同屏截图并排（浅/深 × 竖/横 × 数据态） | `DELTA.md` §3 引用 `artifacts/screenshots/**` |
| **工程** | 目录结构、测试分层与数量、CI、静态检查、截图回归、无障碍 | `DELTA.md` §4 |

### 6.2 双包并存（不覆盖、不污染用户数据）

- 参照包：`com.wheremy.app`（**设备上那份属于用户，我们只读不动**）。
- 本实现：debug 变体加 `applicationIdSuffix ".dsh"` → `com.wheremy.app.dsh`，release 再定正式 id。
- 两者的数据库/照片目录互不可见（不同包名），**可以在同一台机器上并存**，便于同屏切换对比。
- 我们自己的包用**seed 演示数据**（造一份备份包导入），**不去复制参照包的数据**。

### 6.3 截图对照流程

1. 统一条件：同一台真机、同一屏幕尺寸/字体缩放/深浅色；每次采集前记录环境快照两行（包名 + 时间）；
2. 采集：`adb exec-out screencap -p > artifacts/screenshots/<phase>/<screen>-<theme>-<width>.png`；
3. **隐私**：参照包里的数据是用户的——采集参照截图**必须先经用户同意**，且产物不进 git、不贴进文档正文（`DELTA.md` 只写文件路径）；
4. 对比：并排 + 逐项打勾（信息层次/间距/可读性/状态完整度/无障碍）；
5. 结论写回 `DELTA.md` §3：谁更好、差在哪、要不要改。

### 6.4 `PRD.md` 状态列的归属（避免两套状态打架）

- `PRD.md` 的状态标记仍然指**参照实现**（保持可比性）；
- **本实现的落地状态只记在 `DELTA.md`**；
- 全部完成（或用户要求）时，做一次显式迁移：把 `PRD.md` 的状态列切成"本实现现状"，并在 `CHANGELOG.md` 记一行——**这是一次动作，不是每天同步**。

---

## 7. 工作纪律与边界

### 7.1 对参照实现的硬边界

- **绝不修改 `D:\src\qoder\wheremy` 下的任何文件**；也**不在那个目录里执行任何会写盘的命令**（构建、`gradle`、生成文件、`git` 写操作）。
  需要参照实现的产物时：由用户在其目录里产出，或只读地读取已有产物。读代码、读文档、`git log/status` 这类只读操作允许。
- 我们的构建、测试、截图、导出全部落在本工作区（`build/`、`artifacts/`，均已 gitignore）。

### 7.2 文档先行

- 规则/文案/门禁三类改动：**先改文档 → 再改测试（红） → 再改代码（绿）**（`PROCESS.md` §0.1）；
- 每个切片结束必须更新：`DELTA.md`（差异）+ 阶段记录表 + `CHANGELOG.md`；被改动到的 as-built 文档同步更新。

### 7.3 设备与隐私

- 只有一台真机 → 真机验证**串行**；`adb` 用绝对路径；
- 系统弹窗（账号/登录/授权）**只按返回键取消，绝不输入任何账号密码**；
- 取库读数：db + `-wal` 两份、整目录取到新目录、`exec-out` 原始字节、先 `PRAGMA integrity_check`（`REBUILD.md` §7 B 组）；
- 不提交：API key、`local.properties`、任何截图与 dump、真机导出的 `data.json`。

### 7.4 每次会话的收尾动作

1. `./gradlew test` 全绿且是**这一轮**的绿（判绿双证，`TESTING.md` §6）；
2. 变异验牙一次（改了哪条规则就变哪条）；
3. 更新 `DELTA.md` / 阶段记录表 / `CHANGELOG.md`；
4. 提交信息写清"这一批改了什么 + 代价"。

---

## 8. 立刻要做的三步

| # | 动作 | 预估 | 前提 |
|---|---|---|---|
| 1 | **Phase 0 spike**：仓库骨架 + 10 步验证 + CI 绿 + Room 2/3 决定 | 半天 | 无 |
| 2 | **设计系统落地**：把 `DESIGN_SYSTEM.md` 的 token 变成 Kotlin 主题 + 组件库 + `DesignGallery` 预览屏（真机上看效果） | 1 天 | 第 1 步 |
| 3 | **对比基线**：功能矩阵骨架（`DELTA.md` 已可生成）+ 参照实现截图采集（**需用户同意**） | 半天 | 用户确认 |

---

## 9. 风险与对策（本实现特有）

| # | 风险 | 对策 |
|---|---|---|
| 1 | **工具链过新**（AGP9/Kotlin2.4/KSP 新编号） | Phase 0 spike + 三级回退阶梯；退档写 ADR |
| 2 | **Room 2/3 选错** | 同一份最小 DAO 两边都跑，按"编译/测试/文档可用性"三项打分再定；不通过就回 2.8.5 |
| 3 | **新配色/动效让"判色"失真**（参照 ⛔D4 的教训） | 照片墙区域固定中性色；动态色只用于 chrome；写 ADR |
| 4 | **范围膨胀**（想把 14 条 ⬜ 全做） | 按 §2.4 分档，用户确认后才进阶段计划 |
| 5 | **真机单点** | 串行验证；关键结论写进文档；模拟器只用于"能不能装/能不能跑"，不用于结论 |
| 6 | **与参照实现"同源"导致照抄** | 文档是共同基线，**代码独立编写**；`DELTA.md` 必须能说清每一处差异 |
| 7 | 用户数据被误碰 | 双包并存 + 演示数据 + §7.3 的取数纪律 |

---

## 10. 阶段记录表（随开发滚动更新）

| 阶段 | 状态 | 开始 | 结束 | 主要产出 | 出口判据（G0–G5 + 阶段判据） | 备注 |
|---|---|---|---|---|---|---|
| P0 工程与设计系统 | ✅ 完成 | 2026-10-08 | 2026-10-08 | 仓库骨架 + wrapper + CI 文件 + Spotless + 设计系统 token + DesignGallery + 截图基线（4 类 / 6 例）+ 真机冒烟 | 10 步全过（第 10 步晚补）；唯一未做的是 **CI 上真 GitHub 跑一次**（首次推送时验） | §3.4 有完整实测与 F1–F9 |
| P1 数据与照片层 | ✅ 完成 | 2026-10-08 | 2026-10-09 | 四表 + 三 DAO + 派生搜索列 + 照片三档 + EXIF 烤方向 + 引用计数回收 + **仓库层与领域层**（`ItemRepository`/`LocationRepository`/`LocationTree`/`SearchRanker`） | **12 类 / 73 例全绿**（`freshXml=12/12`）；真机自检 7 项全绿；schema 与参照逐字段一致（F11） | 位置树语义按参照逐条对齐（F15）；`applyAnalysis`（AI 字段合并）留到 P3 与 AI 层一起做 |
| P2 录入闭环 | 🟡 进行中（单件闭环已通） | 2026-10-09 | | 选图→落库→确认页（13 字段 + 退出框两支 + 空壳回收）→ 待归类列表 | **14 类 / 85 例全绿**；**真机自检 10 项全绿**（选图即落库、确认页状态机、确认≠已归位、稍后处理、空壳回收、P1 四项）；选图器拉起/返回无崩溃 | **多件录入**（芯片排切换与「不要这件」完整路径）与详情页/删除/搬移留给 P2b/P3；AI 分析（`applyAnalysis` + 位置建议）在 P3 接 |
| P3 查找闭环 | ✅ 完成 | 2026-10-09 | 2026-10-09 | 照片墙（顶栏/搜索/三层筛选/三句空态/网格/⋮ 菜单/搬到/删除四档）+ **详情页**（三态互斥、正文五块、字段卡十行、搬到/删除）+ **点格子进详情** + 待归类同屏两模式 | **19 类 / 113 例全绿**；真机：上墙 5 件、筛选生效、待归类谓词正确、详情页名称/位置卡/字段卡/更新于 正确、搬到后位置卡即时刷新、删除框逐字正确、重启数据仍在 | 两处**故意留到后面**：照片来源弹窗（要等 P6 的相机）、排序说明（`PRD.md` ⬜ FR-FND-09） |
| P4 位置树 | ✅ 完成 | 2026-10-09 | 2026-10-09 | 顶栏（面包屑即标题 + 上一层 + 新建）、正文五块（位置照片区 → 空态两句 → 子位置行 → 这里的物品 → 80dp 空白）、节点行（缩略图只在真记过照片时画 / 计数括号只在子树≠直属时加 / ▸ / ⋮「位置操作」）、节点菜单（**边界不给方向按钮**）、移动到（rootLabel「移到根层级」）、删除框（固定那句）、新建/编辑框（类型是自由标签；**空名字按钮禁用**） | **20 类 / 123 例全绿**；真机：根层 `家 · 房间 · 0 件（含子位置 3 件）`、下钻子行计数正确、无图时只给选图、边界只有「下移」、菜单次序正确 | 位置照片的**来源弹窗**与 P6 相机一起做；`FR-LOC-05`（位置里直接录物品）留作 ⬜ |
| P5 提醒与备份 | ✅ 完成 | 2026-10-09 | 2026-10-09 | 设置页三节（到期提醒 / AI 分析照片 / 备份）+ `SettingsRepository` + `ReminderEngine`/`Runner`/`Worker` + `BackupRepository`（导出事务读、五条拒、路径白名单、MERGE/OVERWRITE、`operate` 五种收场） | **24 类 / 161 例全绿**；真机：调试按钮两条路径、通知落系统、设置持久化、`entry=expiring` 落点、**导出 → 删一件 → 覆盖导入 → 那一件回来了**（含结果句「覆盖导入完成 · 位置 3 个 · 物品 5 件 · 照片 5 张」与设备上的 zip） | AI 留痕调试区等在 P2 收尾（要 key）；备份不做加密/定时（`PRD.md` ⬜ FR-BAK-08/09） |
| P6 相机与打磨 | ⬜ 未开始 | | | | 人工清单签收 + `RELEASE.md` §3 勾完 | |

**状态只有四种**：`⬜ 未开始` / `🟡 进行中` / `✅ 已完成` / `⛔ 已放弃（写原因）`。
每格填完都要能在 `DELTA.md` 或 `CHANGELOG.md` 找到对应记录——**表格不许出现"填了但没证据"的状态**。
