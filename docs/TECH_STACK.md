# Wheremy 技术选型与依赖策略（TECH_STACK）

**这份文档回答**：技术栈**凭什么这么选**、哪些地方是官方/成熟方案、哪些地方是自己造的、自己造的凭什么留、要换的话按什么顺序换。
**它不回答**：代码现在长什么样（`ARCHITECTURE.md`）、某个决策的完整 ADR（`DECISIONS.md`）、怎么搭环境（`DEVELOPMENT.md`）。

**本位**：技术选型准则的权威出处。与 `DECISIONS.md` 的关系：本文给**准则与对照表**，`DECISIONS.md` 给**逐条决策记录**（每条自造/放弃的完整理由与复核条件）。

**最近一次准则变更**：2026-10-07 —— 用户拍板 **"优先官方推荐的最佳实践或成熟第三方的最佳实践，尽量不要自己造轮子"**（§1）。这一条改变了本项目的默认方向：**从"够用就自己写"改成"先找官方/成熟库，自造必须给证据"**。

---

## 1. 选型准则（五条）

| # | 准则 | 落到动作上是什么 |
|---|---|---|
| **P1** | **官方优先** | 能用 AndroidX / Jetpack / 平台 API 解决的，不自造。判断顺序：平台 API → AndroidX/Jetpack → Kotlin 官方库 → 社区成熟库 → 自造 |
| **P2** | **第三方优先于自造** | 官方没有对应物时，选**维护活跃、许可宽松、与官方栈兼容、被广泛使用**的库（本项目的 Coil / OkHttp / Robolectric / mockwebserver 都属于这一类） |
| **P3** | **自造只在三种情况下允许** | ① 没有合适的库可用；② 有库但代价明显不划算（包体、性能、依赖图、与既有栈冲突）；③ **需求的语义库给不了**——本项目特有的一条是"界面上每一句话都必须真的成立"（NFR-02），要求对文案与分支有逐字控制 |
| **P4** | **自造必须留证据** | 写清"找过谁、为什么不用、代价是什么"，落成 `DECISIONS.md` 的一条 ADR，并给出**复核触发条件**。只写"够用"不算证据 |
| **P5** | **迁移必须带触发条件** | 不许写"以后重构时统一换"（`PROCESS.md` §9.2 明确禁止）。队列在 §4，每条都有触发信号 |

**P3 的③要慎用**：它是一条真理由，也是最好用的借口。用它的地方必须在 ADR 里写清"库给不了的具体是什么条款，写一段代码做不到吗"。

---

## 2. 判定标记

| 标记 | 含义 |
|---|---|
| ✅ | **符合准则**：用的就是官方/成熟方案，保持 |
| 🟡 | **可迁移**：官方/成熟方案存在，现在自造/旧方案，已排进 §4 队列 |
| 🔒 | **保留自造**：有 ADR 说明理由，且给了复核触发条件；改它要写新 ADR |
| ⬜ | **待评估**：还没定，评估方法与触发条件写在 §4 |

---

## 3. 现状对照表

**这一节是"我们站在哪"的全景**。每行都能回答"这是官方方案吗？不是的话凭什么？"

### 3.1 已经符合准则的部分（✅ 保持）

| 领域 | 现在用的 | 它是什么 | 判定 |
|---|---|---|---|
| UI 框架 | Jetpack Compose + Material 3 | Android 官方 | ✅ |
| 应用架构 | UI 层（Compose + ViewModel + `StateFlow` 单向数据流）/ 可选 domain / data（仓库） | 官方《应用架构指南》的分层 | ✅ |
| 状态容器 | `ViewModel` + `StateFlow` + `update {}` | 官方推荐 | ✅ |
| 数据库 | Room（runtime + ktx + compiler via KSP） | 官方 ORM | ✅ |
| 键值偏好 | DataStore（Preferences） | 官方（SharedPreferences 的替代） | ✅ |
| 后台任务 | WorkManager（`PeriodicWorkRequest` + `KEEP`） | 官方（可延迟后台工作的唯一推荐方案） | ✅ |
| 相机 | CameraX（core / camera2 / lifecycle / view 四个 artifact） | 官方 | ✅ |
| 图片元数据 | `androidx.exifinterface` | 官方 | ✅ |
| 声明式 UI 图片加载 | Coil 3（`coil3`） | 第三方事实标准（Compose 生态主流） | ✅ |
| HTTP 客户端 | OkHttp 4.12（同步 `execute()` + `callTimeout`） | 第三方事实标准（Android 无官方 HTTP 客户端） | ✅ |
| 序列化 | kotlinx.serialization（`@Serializable` 实体直出 JSON） | Kotlin 官方 | ✅ |
| 权限与系统交互 | Activity Result API + 平台 `NotificationManager` / `WorkManager` | 平台/官方 | ✅ |
| 日期选择 | Material 3 `DatePickerDialog` | 官方组件 | ✅ |
| 生命周期与 Compose 集成 | `androidx.lifecycle`、`activity-compose` | 官方 | ✅ |
| 测试 | JUnit4 + Robolectric + `androidx.compose.ui.test`（`createComposeRule` / `createAndroidComposeRule`）+ `mockwebserver` | 社区标准 + 官方测试 API | ✅（见 §4 T8） |
| 构建 | Gradle + AGP + KSP + 版本目录 | 官方工具链 | ✅ |
| 协程 | kotlinx.coroutines（`Dispatchers.IO`/`Default`、`SupervisorJob`） | Kotlin 官方 | ✅ |

### 3.2 自造 / 旧方案的部分（🟡 可迁移、🔒 保留、⬜ 待评估）

| # | 领域 | 现在的做法 | 官方/成熟方案 | 判定 | 理由与代价 |
|---|---|---|---|---|---|
| A | **依赖注入** | 手写 `di/AppContainer.kt`（普通 class 装单例 + `CompositionLocal` 下发 + `appViewModel(container)`） | **Hilt**（Android 官方推荐的 DI 方案） | 🟡 **可迁移** | 现状能跑（104 行），代价是：作用域、测试替身、生命周期都要自己维护，与官方示例/文档脱节。迁移成本中等（57 个文件里注入点集中在容器与 5 个 VM） |
| B | **导航** | 手写 `ui/WheremyRoot.kt`（枚举 tab + `rememberSaveable` + `return` 整屏接管 + 四层返回键 + 叠层取景屏） | **Navigation Compose**（`androidx.navigation:navigation-compose`，官方 Compose 导航方案） | 🟡 **可迁移** | 现状 403 行，承担了"整屏接管 + 叠层 + 返回键优先级 + 状态恢复"四件事；迁移收益是返回栈与恢复语义交给框架，代价是要把"取景屏必须叠层"（ADR-18）与"✕ 与返回键同一个门"（`UI.md` §2.3）在导航图里重新表达——**迁移前必须先写回归测试** |
| C | **界面文案资源** | 全部硬编码在 Kotlin（`res/values/strings.xml` 只有 `app_name`） | **`strings.xml` 资源**（官方 i18n 做法；有 lint 检查、可本地化、可在编译期发现缺失） | 🟡 **可迁移** | 现状的理由是"界面测试按文字断言"（ADR-16）。**这条理由只对"翻译"成立，对"资源化"不成立**：`strings.xml` 里的中文与现在逐字一样，运行时屏幕上的字不变，按文字找节点的断言照样绿。代价：约 200+ 条文案搬迁 + 测试里出现字符串常量的地方要改成 `context.getString`（Robolectric 下可用） |
| D | **搜索** | `searchText LIKE '%q%'` + 内存打分（`SearchRanker`），候选上限 300 | **Room 的 FTS4/FTS5**（官方支持 `@Fts4`）；中文场景可用 SQLite **`trigram` 分词器**（3.34+，支持子串与 CJK） | ⬜ **待评估** | 现状的否决理由（`DECISIONS.md` ADR-01）是"FTS5 默认 `unicode61` 把连续中文当一个 token"，这条**仍然成立但已不是唯一选项**（`trigram` 是后来被记进队列的新事实）。触发条件：命中量接近 300、或用户报"明明有却搜不到"（`PRD.md` ⬜FR-FND-07） |
| E | **位置树** | 全量读 `location_node` 后在内存建树（`LocationTree`），`seen` 断环 | 官方无对应物；SQL 侧可用递归 CTE | 🔒 **保留** | 位置几十到几百个，一次全量读比递归 CTE 更简单，且规避各版本 SQLite 对 recursive CTE 的支持差异（ADR-02）。有 ADR、有复核条件（节点数上千再评估） |
| F | **备份包格式** | 自造：`manifest.json` + `data.json`（四表行）+ `photos/**` 的 zip | 平台方案是 **Android Auto Backup**（`allowBackup=true`），与"无云、key 不出门"直接冲突；官方**没有**面向用户的"导出可移植 zip" API | 🔒 **保留** | ① 没有官方等价物（P3①）；② Auto Backup 会把数据交给系统云备份，与本项目 NFR-03 冲突（ADR-12/ADR-15）；③ 格式已冻结为不可变项（`REBUILD.md` §2 第 2 条）。代价：格式演进要自己写兼容与校验，见 `RELEASE.md` §5 |
| G | **AI 留痕** | 自造：`files/ai-trace/*.json`，滚动保留 10 条，单条 8000 字截断 | 没有官方/成熟库做"给用户看的提示词-回应原文滚动日志"；用 Room 存反而更重 | 🔒 **保留** | 需求是"人能一眼读原文"（`RULES.md` §13.3），文件比表更贴；且目录刻意在 `files/photos` 之外（防止随备份出门）。复核触发：留痕要检索/统计时改存表（`PRD.md` ⬜FR-TRC-04/05） |
| H | **照片管线** | 自造：`BitmapFactory` 降采样 + `ExifInterface` 烤方向 + `Bitmap.compress` 三档（1600/256/1024） | 官方无"三档 + 烤方向 + 引用计数回收"的成品；Coil 负责显示不负责落盘 | 🔒 **保留** | 需求是"落盘尺寸/质量可控 + 方向烤进像素 + 失败自清理"，成品库不覆盖（P3③）。复核触发：出现 OOM 或需要 HEIC/WebP 时重估（可考虑 `androidx.graphics`） |
| I | **字符串 → 搜索列** | 自造派生列 `searchText` + `FIELD_VERSION` 版本号补算 | Room 的 `@DatabaseView`/`@TypeConverter` 都不解决"历史行补算"；这是本项目的数据迁移问题 | 🔒 **保留**（但迁移机制要并入 ⭐FR-DB-01） | 派生列本身没问题，"版本号 + 启动补算"是自造；真实 Migration 落地后应改为在 Migration 里一次性补算（`RELEASE.md` §5） |
| J | **界面状态恢复** | 手写 `rememberSaveable` 九格语义（`UI.md` §2.1 的八格 + 设置页 `pendingMode` 一格）+ 手写返回键四层 | 官方 `SavedStateHandle`（配合 Navigation）；Compose 的 `rememberSaveable` 本身是官方 API | 🟡 **随 B 一起迁移** | 单独迁移没有收益（`rememberSaveable` 已是官方 API），真正该交给框架的是"导航栈 + 状态句柄"这一对。触发条件与 B 相同 |
| K | **错误文案映射** | 自造 `UiFormat.failureText`（11 类中文映射） | 无官方对应物；这是产品文案 | 🔒 **保留** | NFR-02 要求逐字可控（P3③），且 11 条文案都被测试逐字钉住 |
| L | **DI 的测试替身** | 手写 `FakeTrace` / `InlineExecutor` / 注入 `CameraSource` | 与 A 同源 | 🟡 **随 A 迁移** | Hilt 的 `@TestInstallIn` 能替掉手写替身的一部分；但 `FakeTrace` 这类"只回答一个方法"的替身仍然手写更省 |
| M | **静态检查与 CI** | **零静态检查**：仓库里没有任何 `lint`/`ktlint`/`spotless`/`detekt` 配置，也没有 `.editorconfig`；`.github/` 不存在（无 CI） | 官方：AGP 自带的 Android Lint（`lint` 任务已在构建里，只是没人跑）；社区：ktlint / Spotless / detekt 三选一 | 🟡 **可迁移（T9）** | 代价小、收益直接：格式与常见错误从"人眼"变成"命令"。首次全量扫描会报出一批既有问题，所以按"只卡新增"起步；CI 方案见 §8.3 第 12/13 条 |

### 3.3 明确不许自造的东西（反模式清单）

即使 P3 的三条都不满足，也不许自己写这些——它们都有成熟方案，自造只会重蹈覆辙：

| 不许自造 | 该用什么 |
|---|---|
| DI 容器 / 服务定位器（现状 `AppContainer` 是**历史遗留**，不是范本） | Hilt（官方） |
| 路由 / 返回栈管理 | Navigation Compose（官方） |
| HTTP 客户端、重试/退避框架 | OkHttp（已有）；要重试策略用 OkHttp 的 `Interceptor` 或成熟库 |
| JSON 解析器 | kotlinx.serialization（已有） |
| ORM / SQL 拼串 | Room（已有） |
| 图片加载与内存/磁盘缓存 | Coil（已有） |
| 图片压缩/裁剪/变形的整套工具 | Coil 的变换 API 或 `androidx.graphics` |
| 权限申请封装 | Activity Result API（已有） |
| 日期/时间算术 | `java.time`（已有）；跨时区换算集中在 `util/Dates.kt` 一处 |
| 日志框架 | 平台 `Log` + 现有留痕；不要引第二套日志门面 |
| 事件总线 / 全局广播 | 协程 `Flow` + 现有容器状态 |
| 自定义 View 做列表/滚动 | Compose 官方组件 |

---

## 4. 迁移队列（带触发条件）

**排序原则**：先做"收益高、风险低、与官方栈同向"的；高风险迁移必须先补回归测试（`PROCESS.md` §3.5）。

| # | 迁移 | 收益 | 成本 / 风险 | 触发条件 | 前置 |
|---|---|---|---|---|---|
| **T1** | 文案资源化：Kotlin 硬编码 → `strings.xml`（官方 i18n） | 可本地化、lint 可查漏、与官方做法一致 | 中：约 200+ 条搬迁；测试里按文字断言的写法不变（运行时文字不变） | 出现"要给非中文用户"或"文案越来越大想统一管"时；也可作为一次性技术改进直接开工 | 先给 `UiFormat`/`UI.md` 的逐字表建一份清单（就是对账基线） |
| **T2** | 依赖注入：`AppContainer` → Hilt | 与官方文档/示例一致；作用域与测试替身有标准做法 | 中：注入点集中在容器与 5 个 VM；测试要改造 | 发生"要加第三个容器状态/新的可替换依赖"或需要多模块时 | 先冻结容器 API（`AppContainerTest` 的 5 例就是它的接口契约） |
| **T3** | 导航：`WheremyRoot` → **官方导航库**（见下方"选哪个"） | 返回栈与状态恢复交给框架；路由可测试；官方要求"UI navigation tests 作为 CI 回归" | **高**：返回键四层、整屏接管、取景屏叠层、恢复语义都要重新表达 | 出现"要加第六屏"或"需要深链接/可分享链接"时 | 必须先把 `WheremyRootTest`(20) 全绿当作基线，并补"返回键四层"的回归用例。**选哪个**：官方落地页（2026-10-07 实读）仍写"如果是纯 Compose 应用就用 Navigation Compose"；而 **Navigation 3 已于 2026-09-23 发布 1.2.0 并标注 stable**，官方示例 `nowinandroid` 已迁 Nav3。→ **届时直接选 Navigation 3**（概念不同：自持 back stack + `NavDisplay`），避免"先 Nav2 再 Nav3"的二次迁移；开工前按 §1 P4 写 ADR |
| **T4** | 搜索：`LIKE` → FTS + `trigram` | — | — | **默认不做**（2026-10-07 联网核实后**判定**，见 §8.2 更正一：`trigram` 对 <3 个 unicode 字符的查询"不匹配任何行"，而本项目大量搜两字中文词；Room 只有 `@Fts3/@Fts4` 注解；`trigram` 需 SQLite ≥ 3.34 而 minSdk 26 设备靠系统 SQLite 拿不到，必须自带 `androidx.sqlite:sqlite-bundled`） | 只有"查询普遍 ≥3 字 **且** 数据量大到 `LIKE` 明显变慢"时才重新评估；评估时先看 §8.6 的未验证项 |
| **T5** | 测试：补真机 instrumented 测试（`androidTest`） | 相机/通知/选图器这类"只能真机"的部分能回归 | 中：要设备或 CI 上的模拟器（而模拟器给不出四类行为，见 `PROJECT.md` R3） | 有稳定的真机/CI 设备时 | 先解决"CI 上判定哪台设备"的问题 |
| **T6** | 派生列补算：自造版本号 → Migration 内一次性补算 | 少一个启动期分支 | 小 | 与 ⭐FR-DB-01（真实 Migration）一起做 | 无 |
| **T7** | 备份格式：保持自造，但补一次"格式版本矩阵" | 降低"旧包导不进来"的风险 | 小：文档与演练 | 与 ⭐FR-RLS-01（签名包）一起做 | `RELEASE.md` §5 |
| **T8** | 位置树 / 留痕 / 照片管线 | — | — | **不迁**：🟡 见 §3.2 E/G/H 的复核触发条件 | 新事实出现（节点上千 / 要统计留痕 / 需要 HEIC）时重估 |
| **T9** | 补静态检查基线（Android Lint 已在 AGP 里，另加 ktlint + Spotless） | 现在是**零静态检查**（仓库里没有任何 lint/ktlint/spotless/detekt 配置，也没有 `.editorconfig`）：格式与常见错误只靠人眼 | 小：加一处 Gradle 插件 + 一条 CI/本地命令；风险是首次全量扫描会报出一堆既有问题，要先定"只卡新增" | 下一次要动 ≥10 个文件时；或出现第二次"格式/笔误类"返工 | 先跑一次只报告不失败的基线，把既有问题记成一张清单（`PROCESS.md` §9.2 的技术债口径）。**具体选型见 §8.5**：`com.diffplug.spotless` 8.10.3 + `org.jlleitschuh.gradle.ktlint` 14.2.0；**不引 detekt**（2.x 仍 alpha，官方示例也没用） |
| **T10** | 提交 Gradle Wrapper + 建 CI（跑现有 481 例） | 把 G0/G1 从"人工双证"升级成机器判绿；官方 CI 写法的**前置条件就是 wrapper** | 小：`gradle wrapper` 生成 3 个文件；CI 用 `actions/checkout@v7` + `actions/setup-java@v6` + `gradle/actions/setup-gradle@v6` | **加 CI 时必做**；注意这与现状"本机不生成 wrapper、用 PATH 上的 gradle"并存：wrapper 只服务 CI，不改本机用法（`REBUILD.md` §7 A5、§8 第 1 条要补这句话） | 先确认 CI 上 Robolectric 需要什么（官方无网络下载模型，但 Robolectric 会拉 android-all 包，需允许缓存） |
| **T11** | 截图/UI 回归：Roborazzi | 补"UI 导航测试作为 CI 回归"这条官方要求；与现有 Robolectric + `createComposeRule` 路线同源 | 小-中：会引入 golden 文件与审批流程 | 有 CI 之后 | 用 Roborazzi（官方示例用的就是它）；**不用 Paparazzi**（stable 仅 1.3.5、2.x 长期 alpha），也不用官方 Compose screenshot testing（仍 alpha，且独立插件方式已废弃） |
| **T12** | 一批次纯版本 bump（无 API 变更） | 止住"版本落后"：WorkManager / Coil / OkHttp / serialization / coroutines / Robolectric / Compose BOM / lifecycle / activity / exifinterface | 小：逐项见 §8.3；OkHttp 4→5 会改 MockWebServer 包名（`mockwebserver3`），单独一批做 | 想止血漂移时立即可做 | 全量回归 + G2 抽查；一次提交只升一组（`PROCESS.md` §3.6） |
| **T13** | **构建链大版本**：AGP 8.13.2 → 9.x、Gradle 8.14.3 → 9.x、Kotlin 2.2.21 → 2.4.x、KSP 换到新编号体系 | 长期跟进官方；AGP 9 把 Kotlin 插件内置，配置面变小 | **高**：三者**必须同批**（AGP 9 内置 Kotlin、旧 DSL/`applicationVariants` 已移除、opt-out 将在 AGP 10 删除）；任何一处不配套就编不过 | 其他项稳定、CI 就绪之后（T10 先做完），且**单独一批** | 先读 AGP 9 release notes；兼容基线是 Gradle ≥ 9.1 / JDK 17 / KGP ≥ 2.2.10；`RELEASE.md` §3 的发布清单要在升级后重跑 |
| **T14** | 性能发布配置：R8 full mode / Baseline Profiles / Macrobenchmark | 官方**强推荐**；现在 release 未混淆也无 profile | 中：混淆要 keep 规则 + 全量回归 + 真机冒烟（`RELEASE.md` §3 已写成"保持 or 打开 + 理由"） | 与 ⭐FR-RLS-01（签名包）同批做 | 先定"混淆 or 保持"并写理由，再谈 Baseline Profiles |
| **T15** | 依赖新鲜度自动化：Renovate **或** Dependabot | 止住"版本落后一年"；两者现在都原生支持 `gradle/libs.versions.toml` | 小：加一个配置文件 | T10（CI）就绪后 | **只上一个**（准则 P2 的反面：不引第二套同类工具） |

**队列里每一条都要能被"拿出来单独做"**：一条件一次迭代、一次提交（`PROCESS.md` §4.1），不许"顺手一起换"。
**顺序建议（2026-10-07 联网核实后）**：T10（wrapper+CI）→ T9（ktlint+Spotless）→ T12（纯版本 bump）→ T15（依赖更新自动化）→ T11（Roborazzi）→ T13（AGP9/Gradle9/Kotlin2.4，单独一批）→ T2（Hilt）→ T3（Nav3）→ T14（R8/Profiles）；T1（文案资源化）与 T5–T8 按各自触发条件插空；**T4 默认不做**。

---

## 5. 引入一个新依赖的评审流程

与 `PROCESS.md` §3.6 一致，这里给**判据**（照着答，答不满不许引）：

1. **官方有没有？** 有 → 用官方的，不再往下问。
2. **现有依赖能不能做？** 能 → 不加（这是"不造轮子"的反面：也不许随便加轮子）。
3. **库本身的五项体检**：维护活跃度（最近一年有发布/有回应）、许可（与项目兼容）、包体（方法数/体积）、
   与现有栈的冲突（Kotlin/Compose/Room/KSP 版本是否打架，是否需要 `@OptIn`）、**是否可以只取一个 artifact 而不是一整包**。
4. **是否引入第二套同类技术**？例如再加一个 HTTP 客户端、再加一个图片库、再加一个 DI 框架——
   一律不准（要么替换旧的，要么不加）。
5. **写一条 ADR**（`templates/adr.md`），写清上面四问的答案；版本只改 `gradle/libs.versions.toml`。

**已有的两条纪律仍然成立**（`REBUILD.md` §2）：不引 `material-icons-extended`（一条图标引一整包）、
不为了让界面好看而引 UI 组件库（M3 官方组件已经够用，`UI.md` §12 允许换组件但要走 UI 文档）。

---

## 6. 与既有决策的冲突处理

本次准则变更**不自动推翻**任何既有 ADR：它改变的是**默认方向**，以及**自造必须给证据、必须给复核条件**这两条要求。
需要重新审视的条目（都在 `DECISIONS.md` 里有对应条目，迁移动作在本文 §4）：

| 既有决策 | 在新准则下的状态 | 动作 |
|---|---|---|
| ADR-05 不用 Hilt（手写装配） | **方向冲突** | 已由 **`ADR-25` 替代**（`DECISIONS.md` §4.25，状态 `被 ADR-25 替代`）；迁移动作是 T2，**执行前现状仍然有效**（as-built 以 `ARCHITECTURE.md` §3.1 为准） |
| ADR-16 文案硬编码 Kotlin | **理由部分不成立**（原理由只对"翻译"成立，对"资源化"不成立） | 已由 **`ADR-25` 替代**；迁移动作是 T1，被取代前不改代码 |
| ADR-01 不做 FTS5 | **理由仍成立但选项变多**（`trigram`） | 保留决策，新增"复核触发条件：命中量接近 300 或搜不到"→ 指向 T4 |
| ADR-02 不用递归 SQL 建树 | 不冲突（官方无对应物） | 保留，补一条复核触发（节点数上千） |
| ADR-07 不用 Espresso/androidTest | **部分冲突**（instrumented 是官方路径之一） | 保留现状（Robolectric 也是官方支持的 Compose 测试路径），补 T5 作为"有稳定设备时"的增补项 |
| ADR-12 / ADR-15 备份不带 key、包格式自造 | 不冲突（无官方等价物） | 保留 |
| ADR-17 整屏接管 / ADR-18 取景屏叠层 | **会与 Navigation 迁移相撞** | 迁移前先把这两条写成回归测试（`UI.md` §1.1、§2.3）；是 T3 的前置 |

**流程上的要求**：任何一条"被取代"的标记都要写清**取代者的编号与状态**（`提议 / 已接受 / 已执行`），
避免出现"以为换了、其实没换"的中间态。

---

## 7. 变更记录

| 日期 | 变更 | 来源 |
|---|---|---|
| 2026-10-07 | 建立本文档：选型准则 P1–P5、现状对照表（✅/🟡/🔒/⬜）、反模式清单、迁移队列 T1–T9、依赖评审流程 | 用户拍板"优先官方/成熟最佳实践，尽量不自己造轮子" |
| 2026-10-07 | 补 §8："最佳组合"的**联网核实结果**（四档证据强度 + 版本对照 + 三条判定更正 + 引用来源） | 用户要求"多从网络/GitHub 等技术网站搜集最佳组合"；本会话的既有工具被环境挡住，改用命令行 `curl` 直连各仓库元数据完成核实 |
| 2026-10-08 | 补 §8.9：**第二轮复核**（用户开通搜索后）。`web_search` 间歇可用、`web_fetch` 仍被挡，主力仍是 `curl`；确认既有结论、修正 Gradle/Kotlin 小版本，**新增发现 Room 3.0.3、Compose BOM→material3 1.4.0、官方 Nav3 样板仓库、trigram-CJK 的独立证据**；并实测本机环境就绪度 | 用户："搜索现在可以执行了，正好用这个过程验证文档中的最佳实践" |

---

## 8. "最佳组合"：联网核实结果（2026-10-07）

### 8.0 怎么核的、可信度到哪一档

| 项 | 说明 |
|---|---|
| 既有工具为何没用上 | `web_search` 缺 `DEEPSEEK_API_KEY`；`web_fetch` 对 `developer.android.com` / `sqlite.org` / `raw.githubusercontent.com` / `cdn.jsdelivr.net` / `api.github.com` 一律返回 `URL hostname resolves to a non-public IP address`；只有 `github.com` 能返回 200，但内容被站点框架占满并截断 |
| 实际用的办法 | 命令行 `curl` 直连**包仓库元数据**与 GitHub；官方文档站的 OAuth 重定向死循环绕不过去时，读同一套内容的镜像 `developer.android.google.cn?hl=en`（**文中链接一律给规范地址**） |
| 版本号怎么来的 | Google Maven（`https://dl.google.com/dl/android/maven2/<group>/group-index.xml`）、Maven Central（`https://repo1.maven.org/maven2/<group>/<artifact>/maven-metadata.xml`）、Gradle Plugin Portal、GitHub Releases/API。**所有版本号都是 2026-10-07 实测值**；本机另做了一轮**独立二次复核**，结论一致的有：Room `2.8.5`（最新 stable）、OkHttp `5.5.0`、Coil `3.6.3`、Hilt `2.60.1`、ktlint Gradle 插件 `14.2.0`、Spotless `8.10.3`、WorkManager `2.12.0`，以及官方示例 `nowinandroid` 的版本目录原文（`androidxNavigation 2.8.5` **与** `androidxNavigation3 1.0.0` 并存 = Nav3/Nav2 互操作、`hilt 2.59`、`kotlin 2.3.0`、`ksp 2.3.4`、`ktlint 1.4.0`、`spotless 8.3.0`、`roborazzi 1.56.0`，**没有 detekt**——复核命令见 §8.8） |
| 仍旧没核到权威来源的 | 见 §8.6，一律保留"未验证"，**不写成结论** |

### 8.1 结论（四档证据强度）

| 结论 | 强度 | 依据 |
|---|---|---|
| **保持**：Compose + M3、Room、DataStore、WorkManager、CameraX、`androidx.exifinterface`、kotlinx.serialization、OkHttp、Coil 3、Robolectric + Compose UI test、`java.time`（minSdk 26 原生，**不要加 desugaring**）、`BitmapFactory` + `inSampleSize` 压缩管线、手写 `AppContainer` | **高（联网核实）** | 官方架构/DI/测试/性能推荐页；官方 `load-bitmap` 页仍教 `BitmapFactory.Options` + 采样；官方明说"简单应用可用手写 DI"；本机复核了 Room/OkHttp/Coil 的最新版本 |
| **保持 + 版本落后要补**：WorkManager 2.10.1→2.12.0、Coil 3.3.0→3.6.3、OkHttp 4.12.0→5.5.0、serialization 1.10.0→1.11.0、coroutines 1.10.2→1.11.0、Robolectric 4.16.1→4.17、Compose BOM 2026.06.01→2026.09.00、lifecycle 2.10.0→2.11.0、activity-compose 1.11.0→1.13.0、exifinterface 1.4.1→1.4.2 | **高（实测）** | §8.3 的版本对照表；一次纯 bump 不改 API |
| **该补、且现在就该补**：① 提交 Gradle Wrapper + 建 CI（T10）② ktlint + Spotless（T9）③ Roborazzi 截图回归（T11） | **高（官方口径 + 本地缺口）** | 官方 `gradle/actions` README 原文："**The recommended way to execute any Gradle build is with the help of the Gradle Wrapper**"；官方测试金字塔要求"UI navigation tests that are useful as regression tests in CI"；本仓库零静态检查、零 CI |
| **应当迁移，但时机由触发条件定**：DI → Hilt（T2）、导航 → Navigation 3（T3） | **高（联网核实）** | 官方原文："**Hilt is the officially recommended library for dependency injection in Android**"；官方给出的"该上 Hilt"的判据是"多屏 + ViewModel / 用 WorkManager / VM 作用域跟着返回栈"——**Wheremy 命中前两条**。但官方同时也明确允许手写 DI，所以这是"该做"而不是"必须马上" |
| **明确不做**：FTS/`trigram`（T4，判定见 §8.2 更正一）；detekt（2.x 仍 alpha，官方示例也不用）；Paparazzi（stable 停在 1.3.5，2.x 长期 alpha）；Room KMP / kotlinx-datetime / Ktor（单平台项目无收益） | **高（联网核实）** | §8.2、§8.5 |
| **没有官方对应物、保留自造**：备份 zip 格式、照片三档管线、AI 留痕文件、位置树内存建树、失败文案映射 | **高（本地实证）** | §3.2 E/F/G/H/I/K；Auto Backup 与本项目"无云、key 不出门"冲突（ADR-12/ADR-15） |

### 8.2 三条判定更正（本轮把先前的判断翻了或改了）

**更正一：搜索不上 FTS，`LIKE` 保持不动（T4 从"可迁移"改为"默认不做"）**

- SQLite 官方原文：trigram "extends FTS5 to support substring matching in general"；但 "**Substrings consisting of fewer than 3 unicode characters do not match any rows**"（full-text query）。中文里 1–2 字词极常见（"书""外套"），**这类查询一条都匹配不到**；中文+CJK 场景第三方扩展的动机说明也印证："**in CJK a single Unicode character can be a whole word**"。
- `trigram` 是 SQLite **3.34.0（2020-12-01）** 才有的；Android 的系统 SQLite 版本随系统走，minSdk 26 真机拿不到 → 要用就得自带 SQLite（`androidx.sqlite:sqlite-bundled`，stable 2.7.1）。
- Room 侧只有 `@Fts3`/`@Fts4`（release notes 原文只提 FTS3/FTS4）；`room-runtime-android-2.8.5` 的 classes.jar 里只有 `FtsTableInfo`，**没有 `Fts5` 注解**。
- **所以**：本项目现在的 `LIKE '%q%'` + 内存排序，对中文反而更稳（`ADR-01` 的结论得到更强证据，不是"够用"，是"更合适"）。重新评估的前提是两个条件同时成立：查询串普遍 ≥3 字 **且** `LIKE` 在全量数据上明显变慢。

**更正二：导航迁移的目标从 "Navigation Compose" 改为 "Navigation 3"**

- 官方 Navigation 落地页（2026-10-07 实读）仍写："If your app is built entirely with Jetpack Compose, use Navigation Compose."
- 但 **Navigation 3 已于 2026-09-23 发布 1.2.0，发布说明原文 "1.2.0 is now stable!"**；官方示例 `nowinandroid` 的版本目录已同时带上 `Navigation3 1.0.0` 与 Navigation 2.8.5（互操作）。
- **所以**：等触发条件到了再上，且**直接选 Navigation 3**（自持 back stack + `NavDisplay`），避免"先 Nav2 再 Nav3"的二次迁移。开工前按 §1 P4 写 ADR——它要撞 `ADR-17`/`ADR-18` 两条整屏接管/叠层决策。

**更正三：CI 的前置条件是 Gradle Wrapper，而现状是"没有 wrapper"——两者并存，不是矛盾**

- 官方 `gradle/actions` README 原文："The recommended way to execute any Gradle build is with the help of the **Gradle Wrapper**"。
- 现状（`REBUILD.md` §7 A5、§8 第 1 条）是"仓库里没有 `gradlew`、本机用 PATH 上的 gradle 8.14.3"——**本机开发用法不改**；wrapper 只为 CI 而提交（T10）。**这条要一起改文档**：`REBUILD.md` 的两个地方现在都写着"别提 wrapper"，需补一句"加 CI 时另说"。

### 8.3 版本对照（本仓库 vs 上游最新 stable，2026-10-07 实测）

| 组件 | 本仓库 | 上游最新 stable | 动作 |
|---|---|---|---|
| Gradle / AGP | 8.14.3 / 8.13.2 | **9.8.1 / 9.4.1**（本机复核：Gradle current = 9.8.1；AGP stable 最新 = 9.4.1，其后是 9.5.0-alpha08） | **高风险，单独一批**（见 T13） |
| Kotlin / KSP | 2.2.21 / 2.2.21-2.0.5 | **2.4.20 / 2.3.12**（本机复核：Kotlin stable 最新 2.4.20，其后是 2.5.0-Beta1） | 与 AGP 9 同批；**KSP 编号已与 Kotlin 解耦**（旧格式 `<kotlin>-<ksp>` 是上一代） |
| Compose BOM | 2026.06.01 | **2026.09.00**（本机解析 BOM 的 POM 得到映射：`material3 = 1.4.0`、`ui/foundation/runtime = 1.12.1`、`material3-adaptive-navigation-suite = 1.4.0`） | T12；**新实现直接用 2026.09.00**（M3 1.4.0 就是 Material 3 Expressive 那一代） |
| activity-compose / lifecycle | 1.11.0 / 2.10.0 | **1.13.0 / 2.11.0** | T12 |
| Room | 2.8.5 | **2.8.5**（2.x 线已最新，本机复核）；**另有一条 3.x 线：`androidx.room3:room3-runtime` 3.0.3 已是 stable** | 参照实现不动；**新实现用哪条线在 Phase 0 定**（见 `PLAN.md` §3 与 §8.9） |
| DataStore | 1.2.1 | **1.2.1**（已最新） | 不动 |
| WorkManager | 2.10.1 | **2.12.0** | T12 |
| CameraX | 1.6.2 | **1.6.2**（已最新） | 不动 |
| Coil | 3.3.0 | **3.6.3**（本机复核） | T12 |
| OkHttp（+ mockwebserver） | 4.12.0 | **5.5.0**（本机复核） | 单独一批：**MockWebServer 拆到 `mockwebserver3` 坐标** |
| kotlinx.serialization / coroutines | 1.10.0 / 1.10.2 | **1.11.0 / 1.11.0** | T12 |
| exifinterface | 1.4.1 | **1.4.2** | T12 |
| Robolectric / JUnit / androidx.test:core | 4.16.1 / 4.13.2 / 1.7.0 | **4.17 / 4.13.2 / 1.7.0** | T12 |

**2026 年官方侧的三条变化**（会影响 upgrade 计划）：① **AGP 9.0 内置 Kotlin 插件并默认开启**——不再 need `org.jetbrains.kotlin.android`，旧 DSL/`applicationVariants` 已移除、opt-out 将在 AGP 10 删除；AGP 9 的兼容基线是 **Gradle ≥ 9.1.0 / JDK 17 / KGP ≥ 2.2.10**。② **KSP 版本号与 Kotlin 解耦**（`2.3.x` 系列）。③ **Compose Preview Screenshot Testing 仍是 alpha 且独立插件方式已废弃**（改用 AGP test suites）——所以截图回归选 **Roborazzi**（T11）。

### 8.4 官方推荐基线的要点（与我们的差距）

| 主题 | 官方口径（要点） | 我们的状态 |
|---|---|---|
| 分层与 UDF | UI / Domain（可选）/ Data；`uiState` 用 `stateIn` + `WhileSubscribed(5s)`；不推荐 `AndroidViewModel`；**"Do not send events from the ViewModel to the UI"**（事件在 VM 内处理成状态） | 分层与单向数据流已符合；`stateIn` 那条**没核实**（`GalleryViewModel` 用自建 `loadSeq` 护栏 + `_state.update`），列为 §8.6 #5 |
| DI | "Use dependency injection. **Strongly recommended**"；"**Use Hilt. Recommended.**"；判据：多屏 + ViewModel / 用 WorkManager / VM 作用域跟返回栈 | **命中前两条** → T2（Hilt 2.60.1，走 KSP）；手写 DI 也被官方允许，所以是"该做" |
| 导航 | 纯 Compose → Navigation Compose；Nav3 已 stable | T3（目标改 Nav3） |
| 测试 | 本地测试在开发机/服务器跑；测试金字塔含"能在 CI 当回归的 UI 导航测试"；"**Prefer fakes to mocks**" | 已经是 JVM + Robolectric（本地测试路径）；**缺 CI 与截图/导航回归**（T10/T11）。"fakes over mocks" 与本项目的手写替身（`FakeTrace` 等）方向一致 |
| 性能 | R8 full mode 强推荐；Baseline/Startup Profiles 强推荐；Macrobenchmark | **没做**：release 现在 `isMinifyEnabled = false` 且无签名（⭐FR-RLS-01）；混淆决策在 `RELEASE.md` §3 已写成"保持 or 打开 + 理由"，Baseline Profiles 尚无条目 → 记为 T14 |
| 依赖新鲜度 | —（非官方项） | Renovate 与 Dependabot **现在都原生支持** `gradle/libs.versions.toml`（后者官方文档原文列出该文件）→ 二选一即可，见 T15 |

### 8.5 静态检查 / CI / 截图测试的具体选型（T9–T11 的坐标）

- **格式化与 lint**：`com.diffplug.spotless` **8.10.3** + `org.jlleitschuh.gradle.ktlint` 插件 **14.2.0**（注意：**Gradle 插件版本号 ≠ ktlint 工具版本号**），加 AGP 自带的 Android Lint。
  **不引 detekt**：旧 id `io.gitlab.arturbosch.detekt` 停在 1.23.8，新 id `dev.detekt` 只有 2.0.0-alpha.6（2.x 未 stable），且**官方示例 `nowinandroid` 没用 detekt、只用 ktlint + Spotless + Lint**。
- **CI**：`actions/checkout@v7` + `actions/setup-java@v6` + `gradle/actions/setup-gradle@v6`，跑 `./gradlew test`；**前置是提交 wrapper**（8.2 更正三）。
- **截图回归**：**Roborazzi**（官方示例用的就是它，纯 JVM/Robolectric 友好，与现有 `createComposeRule` 路线同源）；**不用 Paparazzi**（stable 仅 1.3.5、2.x 长期 alpha），也不用官方 Compose screenshot testing（仍 alpha）。
- **依赖更新**：Renovate 或 Dependabot 均可（都支持 version catalog）；
- **崩溃上报**：纯本地项目**建议不上报**；若要，走"本地落盘 + 用户手动导出"的路子（与本项目"数据不出门"一致），**不引 Firebase Crashlytics**（依赖 Google Play 服务与网络）。

### 8.6 仍未验证 / 需要现场确认

| # | 未验证的事 | 为什么要紧 | 怎么补 |
|---|---|---|---|
| 1 | KSP `2.3.12` 与 Kotlin `2.2.21` 的兼容矩阵（官方没给对照表） | 升 KSP 编号体系时可能卡住 | 升 Kotlin 时按官方 KSP 快速开始页给的配对试；失败就回退 |
| 2 | `androidx.room.Fts5` 注解是否存在（API 索引里有词条、参考页 404）；**新增线索：Room 3（`androidx.room3`）3.0.3 已是 stable，FTS 注解在新包里叫什么没核实** | 若存在，FTS 的可行性评估要重做 | 看 Room 3 的 release notes / API 参考；**在此之前按"没有"处理** |
| 3 | CI 上 Robolectric 拉 `android-all` 包的缓存策略 | 会影响 CI 时长与稳定性 | 先跑一次看日志，再决定是否加缓存 |
| 4 | `androidx.room:room-gradle-plugin` 在本项目（KSP 路线）是否必要 | 可能重复 KSP 配置 | 升 Room 时试一次 |
| 5 | 官方要求的 `stateIn(WhileSubscribed(5_000))` 与现有 `loadSeq` 护栏的关系 | 影响后续 ViewModel 改造 | 读 `GalleryViewModel`/`TreeViewModel` 现状后决定，别为改而改 |

### 8.7 引用来源（本轮核实用到的）

- https://developer.android.com/topic/architecture/recommendations （分层、DI"强推荐"、Hilt、测试金字塔、R8/Baseline Profiles）
- https://developer.android.com/training/dependency-injection/hilt-android （"Hilt is the officially recommended library for DI in Android"）
- https://developer.android.com/jetpack/androidx/releases/navigation3 （"1.2.0 is now stable!"，2026-09-23）
- https://developer.android.com/guide/navigation （纯 Compose → Navigation Compose）
- https://developer.android.com/build/releases/agp-9-0-0-release-notes （AGP 9 内置 Kotlin、Gradle 9.1 / JDK 17 基线、旧 DSL 移除）
- https://developer.android.com/studio/preview/compose-screenshot-testing （截图测试仍 alpha、独立插件方式废弃）
- https://kotlinlang.org/docs/ksp-quickstart.html （KSP 版本号与 Kotlin 解耦）
- https://sqlite.org/fts5.html （trigram 的"<3 个 unicode 字符不匹配任何行"与 LIKE/GLOB 索引行为）
- https://sqlite.org/releaselog/3_34_0.html （trigram 自 3.34.0 起）
- https://github.com/streetwriters/sqlite-better-trigram （第三方视角：内置 trigram 把 CJK 当普通字符、单字成词）
- https://developer.android.com/topic/performance/graphics/load-bitmap （`BitmapFactory` + 采样仍是官方指导）
- https://developer.android.com/studio/write/java8-support （desugaring 是给低版本补齐 `java.time` 用的）
- https://github.com/gradle/actions （官方 CI 写法；wrapper 是推荐前提）
- https://github.com/android/nowinandroid （官方示例的真实选型：Nav3 + Hilt + ktlint/Spotless + Roborazzi，无 detekt）
- https://github.com/android/nowinandroid/blob/main/gradle/libs.versions.toml （版本目录原始出处）
- https://github.com/google/dagger/releases （Hilt/Dagger 版本）
- https://github.com/robolectric/robolectric ・ https://github.com/takahirom/roborazzi ・ https://github.com/cashapp/paparazzi
- https://github.com/diffplug/spotless ・ https://github.com/pinterest/ktlint ・ https://plugins.gradle.org/m2/org/jlleitschuh/gradle/ktlint-gradle/maven-metadata.xml
- https://docs.github.com/en/code-security/reference/supply-chain-security/supported-ecosystems-and-repositories （Dependabot 支持 `gradle/libs.versions.toml`）
- https://github.com/ACRA/acra ・ https://docs.sentry.io/platforms/android/ （本地式崩溃上报的可选方案）
- 版本实测入口：`https://dl.google.com/dl/android/maven2/<group>/group-index.xml`、`https://repo1.maven.org/maven2/<group>/<artifact>/maven-metadata.xml`

### 8.8 复核节奏

- **每季度一次**，或每次要动 T9–T15 里任何一项之前：按 §8.3 的两个元数据入口重跑一遍版本实测（一条 `curl` 一行），把结果写回本节表格并更新 §7 变更记录。可直接照抄的三条：

  ```bash
  # ① Google Maven（androidx 系）：取最后一个版本号
  curl -s https://dl.google.com/dl/android/maven2/androidx/work/group-index.xml | grep -o 'work-runtime versions="[^"]*"' | tr ',' '\n' | tail -3
  # ② Maven Central（square/coil 等）
  curl -s https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/maven-metadata.xml | grep -E '<latest>|<release>'
  # ③ 官方示例的真实选型（每次升级前先看它，再看官方文档）
  curl -s https://raw.githubusercontent.com/android/nowinandroid/main/gradle/libs.versions.toml | grep -E '^(agp|kotlin|ksp|hilt|androidxNavigation3|room|coil|ktlint|spotless|roborazzi)'
  ```
  （Git Bash 或 PowerShell 里都能跑；本机复核就是这么做的。）
- **结论变了就写 ADR**：例如"导航目标从 Nav2 改 Nav3"这种，如果将来又变，必须留下 ADR 而不是改表格了事。
- **没核完的不算结论**：§8.6 里还挂着"未验证"的事项，**不许在别处被当作已定的结论引用**。

### 8.9 第二轮复核（2026-10-08，用户开通搜索后）

**触发**：用户设置好 `DEEPSEEK_API_KEY`，要求"正好用这个过程验证文档里的最佳实践"。

| 工具 | 本轮实况 |
|---|---|
| `web_search` | **间歇可用**：前 3 次调用成功（返回来源列表），之后连续报 `DeepSeek search request failed: TypeError: fetch failed`（端点是 `https://api.deepseek.com/anthropic/v1/messages`）。它不是权威取数手段，**只用来发现线索**；要拿结论仍以 `curl` 直连为准 |
| `web_fetch` | 仍被环境挡住（多个域名被判 `non-public IP`） |
| `curl`（本轮主力） | **全部 200**：`api.github.com`、`raw.githubusercontent.com`、`dl.google.com/dl/android/maven2/*`、`repo1.maven.org`、`services.gradle.org`、`plugins.gradle.org` |

**确认（✅ 与 §8 原结论一致）**：Compose BOM `2026.09.00`；`navigation3-runtime` `1.2.0`（其后 `1.3.0-alpha02`）；activity-compose 稳定 `1.13.0`、lifecycle `2.11.0`；KSP `2.3.12`；Hilt `2.60.1`；ktlint Gradle 插件 `14.2.0`、Spotless `8.10.3`；Coil `3.6.3`；OkHttp `5.5.0`；WorkManager `2.12.0`；官方示例 `nowinandroid` 仍在活跃维护（21,894 star，2026-10-07 有推送）。

**修正（~ 小改）**：Gradle current 是 **9.8.1**（此前写 9.8.0）；AGP 稳定线确认为 **9.4.1**（其后是 9.5.0-alpha08）；Kotlin 稳定线确认为 **2.4.20**（最新发布是 2.5.0-Beta1）。

**新增发现（＋ 会影响新实现的技术基线，明细见 `PLAN.md` §3）**：

| # | 发现 | 证据（本机 curl 实测） | 影响 |
|---|---|---|---|
| 1 | **Room 有 3.x 线且已 stable**：`androidx.room3:room3-runtime` `3.0.0 → 3.0.3`（`3.1.0-alpha01` 在飞）；Room 2.x 线最新仍是 `2.8.5` | `dl.google.com/.../androidx/room3/group-index.xml`；`room3-runtime-android` 的 POM 返回 200、根 `room3-runtime` POM 是 KMP 转发壳 | **新实现要用哪条线是个决策**（Phase 0 验证）；参照实现的 2.8.5 选择不变 |
| 2 | **Compose BOM 2026.09.00 的实际映射**：`material3 = 1.4.0`、`material3-adaptive-navigation-suite = 1.4.0`、`ui/foundation/runtime = 1.12.1`、`material-icons-core = 1.7.8` | 解析 BOM 的 POM | **M3 1.4.0 就是 Material 3 Expressive 那一代**；自适应导航组件（`NavigationSuiteScaffold`）也在稳定版里 → 直接支撑"界面更优"这一目标 |
| 3 | 官方为 Nav3 单独开了样板仓库：`android/nav3-recipes`（1,391 star，2026-10-06 仍在推） | `api.github.com/search/repositories?q=org:android+compose` | Nav3 作为迁移目标再添一条官方侧证据 |
| 4 | 官方 UI/工程样板可直接当对照源：`android/compose-samples`（23,498 star）、`android/androidify`（1,984 star）、`android/codelab-android-compose`（1,727 star），均在本月有提交 | 同上 | `PLAN.md` §6 的"对比方法"多了四类外部参照 |
| 5 | FTS `trigram` 的 CJK 退化被**第三方独立复现**：OpenClaw 的 issue 记录"CJK 文本用 trigram 分词器搜索返回 textScore=0"，其修复提交是"在 trigram 模式下整块输出 CJK" | 搜索结果里的 GitHub issue/commit 链接 | 印证 §8.2 更正一（**中文不上 trigram**），不是单一来源的判断 |

**对"在本工作区另起一个新实现"的直接结论**（完整方案见 `PLAN.md`）：
① 起点就用**现行代际**（AGP 9.4.1 / Gradle 9.8.1 wrapper / Kotlin 2.4.20 / KSP 2.3.12 / Compose BOM 2026.09.00 / Nav3 1.2.0 / Hilt 2.60.1），不再从参照实现的旧组合起步；
② UI 侧直接吃到 M3 1.4.0 + adaptive navigation suite，并把**截图回归（Roborazzi）与无障碍**从第一天纳入；
③ Room 2.x/3.x 二选一做成 Phase 0 的**验证门**（先 spike，再定），不靠猜。

**环境就绪度实测（本工作区，同日）**：JDK Temurin `21.0.8`；PATH 上 gradle `8.14.3`（version-fox，**新实现将自带 wrapper**）；Android SDK 平台 `android-29/34/35/36`、build-tools 到 `36.1.0`、`platform-tools/adb.exe` 在位；真机 `9aa4b43e` 在线；上面列的各依赖源全部可达。

### 8.10 Room 3 落地实测（2026-10-08，Phase 0 验证门的结论）

**结论：选 Room 3（`androidx.room3:room3-runtime` 3.0.3）**，四张表、三个 DAO、schema 导出、外键约束全部在真机与 Robolectric 上跑通。**但 3.x 不是 2.x 的改名版**，实测撞到两处差异：

| 差异 | 2.x | 3.x（实测） |
|---|---|---|
| 类型转换注解 | `@TypeConverter` / `@TypeConverters` | **`@ColumnTypeConverter` / `@ColumnTypeConverters`**（`androidx.room3` 里**没有** `TypeConverter`）；另有新的 `ProvidedColumnTypeConverter`、`DaoReturnTypeConverter(s)` |
| ktx 构件 | `androidx.room:room-ktx` | **没有 `room3-ktx`**（能力已并入 `room3-runtime`） |
| 报错体验 | — | 写错注解名的报错极其笼统：`e: [ksp] [MissingType]: Element 'X' references a type that is not present` —— **看到这句先怀疑注解名/依赖名，不要去查 schema** |

**另外两条实测事实**：

1. **`compileSdk` 必须 ≥ 37**：Compose 1.12.1（BOM 2026.09.00）的 AAR 元数据硬要求，参照实现的 36 不够（`PLAN.md` §3.4 的 F1）。
2. **备份兼容性可以机器校验**：两边各自导出的 `app/schemas/…/1.json` 可以逐字段比对（表、列名、类型、非空、主键、外键、索引）。本次比对结果**完全一致**，这是"旧备份包能导进新实现"的硬前提（`PLAN.md` §3.4 的 F11）。**以后每次改 schema 都要重跑这次比对。**
