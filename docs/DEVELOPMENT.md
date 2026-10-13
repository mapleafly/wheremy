# Wheremy 开发手册（DEVELOPMENT）

**这份文档回答**：新机器上怎么把环境搭起来、日常敲哪几条命令、动代码时哪些"不"必须守住、要加一样东西时按什么清单走、出问题先查哪里。
**它不回答**：从零重建的先后顺序（`REBUILD.md` §3）、规则怎么算（`RULES.md`）、流程门禁（`PROCESS.md`）。

**基线**：commit `cf73f5b`（2026-10-06）。主源 57 个 Kotlin 文件 / 6881 行；测试 39 个结果类 / 481 例。所有 `文件:行` 以这个 commit 为准——**行号会漂、符号名不漂**，找不到行就按符号名搜。

**本文与 `REBUILD.md` 的分工**：`REBUILD.md` 是"假设 `app/src/` 全没了，按什么顺序做回来、每步怎么自证"的路线图；本文是"在这套代码上日常怎么干活"。凡是"为什么是这个顺序""当时踩过什么坑"，本文一律给指针，不复制过来——复制就会两份一起漂。

---

## 1. 这份文档管什么 / 不管什么

| | 内容 |
|---|---|
| **管** | 环境与工具链事实、日常命令、编码纪律（"不"清单）、依赖与构建配置的改动规矩、调试与真机取数的姿势、提交前自检、八类扩展点的完整改动清单、排障对照表 |
| **不管** | 从零重建的顺序与里程碑切分 → `REBUILD.md` §3；规则谓词/阈值/逐字文案 → `RULES.md`；屏幕结构与字段次序 → `UI.md`；视觉与组件规范 → `DESIGN_SYSTEM.md`；一次改动走哪条通道与门禁 → `PROCESS.md`；怎么测、怎么判绿、怎么验牙 → `TESTING.md`；要做什么、优先级 → `PRD.md`；为什么这么定 → `DECISIONS.md` |

三条使用约定：**① "必须""不许"都指得出出处**（源码给 `文件:行`，规则给某份文档的某一节），想推翻一个约束先读那处原文；**② 标记的含义**——`🔒` 不可变项（全表在 `REBUILD.md` §2）、`📐` 数字/阈值、`🗣` 逐字文案、`⚠️` 已知偏差；**③ 本文不含任何密钥、`local.properties` 内容、真机数据**（`REBUILD.md` §9）。

---

## 2. 环境准备与 5 分钟上手

### 2.1 先证明这九条（缺一条后面每一步都在猜）

| # | 事实 | 怎么自证 | 出处 |
|---|---|---|---|
| 1 | PATH 上的 `gradle` 是 **8.14.3**，由 version-fox 提供 | `gradle --version` | `REBUILD.md` §8 第 1 条；本机解析到 `C:\Users\lifxu\.version-fox\cache\gradle\current\bin\gradle.bat` |
| 2 | **仓库里没有 `gradlew` / `gradlew.bat`，也不要生成 wrapper** | `ls gradlew*` 应无输出 | `REBUILD.md` §8 第 1 条、§7 A5。**例外**：要加 CI 时官方写法要求提交 wrapper（`TECH_STACK.md` §8.2 更正三 / T10）——wrapper 只服务 CI，本机继续用 PATH 上的 gradle |
| 3 | `java` 是 **Temurin 21.0.8**，但项目按 **JVM 17** 编译 | `java -version`；`app/build.gradle.kts:24-27`（`compileOptions`）与 `:42`（`jvmTarget`） | `REBUILD.md` §8 第 1 条 |
| 4 | `ANDROID_HOME` 指向 SDK 目录 | `echo $ANDROID_HOME` | `REBUILD.md` §8 第 2 条 |
| 5 | `local.properties` 里是 SDK 路径，**已被 `.gitignore` 忽略** | `git check-ignore -v local.properties`（应打印命中 `.gitignore` 的那一行） | `.gitignore`；`REBUILD.md` §8 第 2 条 |
| 6 | `adb` **不在 PATH**，要用 SDK 目录内的绝对路径 | `ls /d/Lib/Android/Sdk/platform-tools/adb.exe` | `REBUILD.md` §8 第 2 条 |
| 7 | ⚠️ 本机 SDK 里**没有 `cmdline-tools`，因此没有 `sdkmanager`** | `ls /d/Lib/Android/Sdk`（只有 `platform-tools`、`platforms`、`build-tools`、`emulator` …） | 与 `REBUILD.md` §8 第 2 条把 `adb` 与 `sdkmanager` 并列的写法不符，见文末 |
| 8 | 平台是 **Windows 11 + Git Bash**，两个坑写在下面 | — | `REBUILD.md` §8 第 4 条 |
| 9 | **没有装 Android Studio**，构建全走命令行 Gradle | — | `REBUILD.md` §8 第 3 条 |

**Git Bash 的两个坑（都来自真机操作）**：

- **带设备路径的 adb 参数会被 Git Bash 改写**：`MSYS_NO_PATHCONV=1` 必须写在**同一条命令之前**（`REBUILD.md` §7 B8）。`adb pull` 的本地目标要用 Windows 形式路径。
- **`python -c` 传中文 argv 会乱码**：改用 UTF-8 的 `.py` 文件 + `PYTHONIOENCODING=utf-8`。另外 Git Bash 的 `/tmp` 就是 `C:\Users\lifxu\AppData\Local\Temp`（`REBUILD.md` §8 第 4 条）。

### 2.2 五步命令清单（克隆 → 构建 → 装真机 → 跑测试 → 判绿）

**所有 gradle 命令都必须以仓库根为 cwd**：脚本里跑 gradle 忘了给 `cwd` 会得到 `Directory '…' does not contain a Gradle build`、3–4 秒 exit 1、且不写任何 XML（`REBUILD.md` §7 A2）。

```bash
# 1) 拿到仓库并 cd 到仓库根（之后每条命令都在这里跑；本机上现成的副本是 /d/src/qoder/wheremy）
cd <仓库根>

# 2) 构建 debug 包
gradle :app:assembleDebug
#    产物：app/build/outputs/apk/debug/app-debug.apk

# 3) 装到真机（先把 adb 的绝对路径存成变量；序列号见 REBUILD.md §8 第 5 条）
ADB=/d/Lib/Android/Sdk/platform-tools/adb.exe
$ADB devices                                            # 期望一行 "<serial>	device"
$ADB -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk

# 4) 跑全量单测（必须前台跑；后台 shell 没有 gradle，见 REBUILD.md §7 A1）
gradle :app:testDebugUnitTest

# 5) 判绿：不看 exit code，看「新鲜的 XML 份数（当前 47 份）+ 自己数出来的 tests/failures/errors」
find app/build/test-results/testDebugUnitTest -name '*.xml' -newermt '<本轮起跑时间>' | wc -l
grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
  app/build/test-results/testDebugUnitTest/*.xml \
  | awk -F'"' '{t+=$2; f+=$6; e+=$8} END {print "tests="t, "failures="f, "errors="e}'
```

| 步 | 判据 | 不符时先看 |
|---|---|---|
| 2 | `BUILD SUCCESSFUL` + APK 文件存在 | 依赖能不能下（官方源，§5.1） |
| 3 | `Success` | 设备没授权 / 被系统拦包（`REBUILD.md` §7 D4） |
| 4 | 命令真的跑完了（有输出、耗时是分钟级） | 后台跑了？`cwd` 给了吗（`REBUILD.md` §1.1、§7 A1/A2） |
| 5 | **本轮份数的新鲜 XML**（当前 47 份）且 `failures=0 errors=0` | 陈旧 XML 是「假绿」的头号来源（`REBUILD.md` §1.1） |

**判绿是"双证"**：`BUILD SUCCESSFUL` **且** 本轮 XML 是新的——完整判据与跑法见 `TESTING.md` §6 判绿双证。拿上一轮的绿 XML 当结论，按 `REBUILD.md` §1.1 算 **NO-VERDICT**，不算绿。

**上手第一天的推荐路线**（`README.md` §4 的路线②）：本文 §2–§5 → `ARCHITECTURE.md` §1/§11/§13 → `RULES.md` §1/§2 → `UI.md` §1 → 真正跑一次 `gradle :app:testDebugUnitTest` 并按上面判绿。**没亲手跑绿过一次之前，不要动源码**。

---

## 3. 代码地图

### 3.1 包 → 职责 → 关键类 → 权威章节

单 Gradle module（`app`），按包分层；**无 NavHost、无 Fragment、无多 Activity**（`ARCHITECTURE.md` §1）。依赖方向单向：`ui/` → `domain/` → `data/`，`di/` 横切；五个 ViewModel 由外壳持有活引用，界面状态全在 `ui/WheremyRoot.kt` 里管（`ARCHITECTURE.md` §11.1、§11.2）。

| 包 | 职责 | 关键类/文件 | 权威章节 |
|---|---|---|---|
| `com.wheremy.app`（根） | 进程入口与 Application | `MainActivity.kt`、`WheremyApp.kt` | `ARCHITECTURE.md` §3.3 |
| `di/` | 手写装配，无 Hilt | `AppContainer.kt`、`AppViewModel.kt`（`LocalAppContainer`、`appViewModel`） | §3.1、§3.2 |
| `data/db/` | 四张表、三个 DAO、库、派生列 | `Entities.kt`、`Daos.kt`、`WheremyDatabase.kt`、`SearchTextBuilder.kt` | §4.1–§4.4 |
| `data/repo/` | 物品/位置仓库、界面载体类型、照片回收 | `ItemRepository.kt`、`LocationRepository.kt`、`Models.kt`、`PhotoReclaim.kt` | §6.1–§6.3、§5.2 |
| `data/photos/` | 三档尺寸与 EXIF 烤平 | `PhotoStore.kt` | §5.1 |
| `data/vision/` | AI 接缝、请求、提示词、解析、留痕 | `PhotoAnalyzer.kt`、`ApiPhotoAnalyzer.kt`、`Prompt.kt`、`AnalysisParser.kt`、`AnalysisResult.kt`、`AnalysisTrace.kt`、`OfflinePhotoAnalyzer.kt` | §8.1–§8.5 |
| `data/backup/` | 备份格式、IO、导入导出 | `BackupFormat.kt`、`BackupIo.kt`、`BackupRepository.kt` | §10.1–§10.3 |
| `data/work/` | 到期提醒的 Worker 与排班 | `ReminderWorker.kt`、`ReminderScheduler.kt` | §9 |
| `data/settings/` | DataStore 读写（AI 三态、提前天数、派生列版本） | `SettingsRepository.kt` | §6.4 |
| `data/camera/` | 快门接缝（接口里无 CameraX 类型） | `CameraShutter.kt` | §11.3 |
| `domain/` | 8 个用例/纯逻辑，**全部无 Android 依赖** | `CaptureFlow`、`LocationTree`、`LocationTreePolicy`、`SearchRanker`、`SuggestLocationUseCase`、`ReminderEngine`、`AnalyzePhotoUseCase`、`CameraGate` | §7 |
| `util/` | 日历天换算的唯一一处 | `Dates.kt` | §1 |
| `ui/` | 外壳 + 5 屏 + VM + common + camera + theme | `WheremyRoot.kt`；`gallery/`、`tree/`、`detail/`、`review/`、`settings/`；`common/`（`UiFormat`、`ItemRow`、`LocationPicker`、`LocationFlatten`、`SnackbarMessage`）；`camera/`；`theme/Theme.kt` | §11.1–§11.5；信息架构见 `UI.md` §1 |

### 3.2 我要改 X → 先打开哪个文件

| 我要改 | 先打开 | 为什么是它 |
|---|---|---|
| 一个表单字段 | `ui/review/ReviewScreen.kt` → `data/repo/Models.kt`（`ItemDraft`） | 字段次序 🔒（`UI.md` §6.5），载体类型的默认值只有 `id` 没有（`Models.kt:5-21`） |
| 一个筛选条件 | `data/repo/Models.kt`（`Filters`）→ `ItemRepository.matches`（`ItemRepository.kt:329`） | 谓词只有这一处（`RULES.md` §10.1） |
| 界面上一句文案 | 那句话所在的 `Text(...)`（先 grep 逐字串） | 文案硬编码在 Kotlin，测试按文字找节点（`REBUILD.md` §5） |
| AI 提示词 | `data/vision/Prompt.kt` | 发出的与留痕的必须是同一串字（`ApiPhotoAnalyzer.kt:35-37`） |
| 一条 AI 失败文案 | `ui/common/UiFormat.kt`（`failureText`） | 11 条都在这里，判定在 `classify`（`RULES.md` §13） |
| 一种 AI 失败原因 | `data/vision/PhotoAnalyzer.kt`（`FailureReason`）→ `ApiPhotoAnalyzer.classify` | 状态码→结局的唯一映射（`ApiPhotoAnalyzer.kt:82`） |
| 备份包格式 | `data/backup/BackupFormat.kt` → `BackupRepository.kt` | 高风险通道：旧包必须还能导入（`REBUILD.md` §2 第 2 条） |
| 搜索排序 | `domain/SearchRanker.kt` → `data/db/Daos.kt` | 打分在域内，候选与次序在 SQL（`RULES.md` §10.2） |
| 位置树规则 | `data/repo/LocationRepository.kt` → `domain/LocationTreePolicy.kt` | 门槛定义与执行分两处（`RULES.md` §8.2、`ARCHITECTURE.md` §7） |
| 提醒窗口 / 提前天数 | `domain/ReminderEngine.kt`、`ui/settings/SettingsViewModel.kt` | 窗口上界只有 `util/Dates.expireWindowEnd` 一处（`RULES.md` §14） |
| 通知点进去落到哪 | `ui/WheremyRoot.kt`（`:271-276` + `:346-350`）、`MainActivity.onNewIntent` | 两跳落地，缺一不可（`UI.md` §2.9） |
| 主题色 | `ui/theme/Theme.kt` | 四个色值常量 + 两张 scheme（light/dark）；`tertiary` 当前没有任何界面在用（`ARCHITECTURE.md` §11.5） |
| 加一条依赖 | `gradle/libs.versions.toml` → `app/build.gradle.kts` | 唯一版本源（§5.1） |
| 加一个后台任务 | `data/work/ReminderScheduler.kt` + `WheremyApp.onCreate` | 频道要早于第一次发送（§8 ⑧） |

---

## 4. 编码纪律（这个项目的"不"清单）

每一条都有理由和出处。**理由不是风格偏好，是这个产品的一部分**。

| # | 不做 | 为什么 | 出处 |
|---|---|---|---|
| 1 | **不引 Hilt / Dagger / Koin** | 装配是手写的 `di/AppContainer.kt`（一个普通 class 装全部单例），经 `CompositionLocal` 发给界面 | `ARCHITECTURE.md` §1；PRD ⛔D5；`UI.md` §1.2 |
| 2 | **不引 `material-icons-extended`** | 只用 `material-icons-core`；extended 包体大且没有按需裁剪 | `REBUILD.md` §2 第 10 条（`gradle/libs.versions.toml`：`compose-material-icons-core`） |
| 3 | **界面文案不抽进 `strings.xml`** | `res/` 只有 5 个文件；抽资源会同时需要 i18n 与双语测试，这是刻意的取舍（`strings.xml` 只有 `app_name`） | `REBUILD.md` §2 第 11 条；`ARCHITECTURE.md` §2 |
| 4 | **不用 NavHost / Fragment / 多 Activity** | 单 Activity + 一个 Compose 外壳，导航状态在 `WheremyRoot` 里管 | `ARCHITECTURE.md` §1、§11.2；`UI.md` §1.1 |
| 5 | **`domain/` 不许依赖 Android** | 8 个文件全无 Android 类型（唯一例外 `CameraGate` 只有枚举与纯函数），这是它们能在纯 JVM 上被测的前提 | `ARCHITECTURE.md` §1、§7 |
| 6 | **实体不上界面层** | 界面只认 `data/repo/Models.kt` 的载体类型（`ItemDraft` / `Filters` / `ItemSummary` / `PhotoDeletion`）；`data/db/Entities.kt` 的 `@Entity` 不出仓库层 | `ARCHITECTURE.md` §6.3 |
| 7 | **派生列 `search_text` 改动必须升 `FIELD_VERSION`** | 它是写入时算出来的派生值，加字段不会让已录物品自动带上；启动时靠版本号比对决定是否补算全表 | `RULES.md` §10.3（`SearchTextBuilder.kt:9`，现值 2） |
| 8 | **照片文件的删除只走 `deleteUnreferencedPhotoFiles`** | 只有一条回收规则：没有任何照片行指着它才删文件；两类照片同表靠 `ownerType` 分家，无条件删文件会带走别人正在用的图 | `RULES.md` §9.2；`ARCHITECTURE.md` §5.2 |
| 9 | **不擅自改跨层写操作的顺序** | 那些先后次序是承重的，逐条列在 `ARCHITECTURE.md` §13（15 条）：`newDraft` 同事务、`onDraftSaved` 先于分析、`swapPhoto` 新→改→删、`save()` 先 `cancel`、`delete` 先删行再删文件、`setPhoto` 先 require 再写文件再替换再清旧、`import` 读包→校验→事务→删多余→搬图、`ReminderWorker` 发出去才回写、`deliverPhoto` 先读再归零…… **改任何一条之前，先把 §13 那 15 条读一遍** | `ARCHITECTURE.md` §13 |
| 10 | **不改 schema 字段** | 验收期冻结：`DB_SCHEMA_VERSION` 恒为 1（`WheremyDatabase.kt:16`）+ `fallbackToDestructiveMigration(true)`（`:35`）= 改字段等于静默清空整机数据。解禁条件见 `RULES.md` §15 W2 / `PRD.md` ⭐FR-DB-01 | `REBUILD.md` §2 第 1 条 |
| 11 | **不动返回键优先级、不改 `deliverPhoto` 单一收口** | 系统返回与那颗 ✕ 必须同一个门；照片两条来源必须落到同一处 | `REBUILD.md` §2 第 7、8 条；`UI.md` §2.3、§2.4 |
| 12 | **不把 `rememberSaveable` 那几格改成普通 `remember`**（也不许把 `remember` 的改成 saveable） | 进程被杀后要回到原来那一屏；而 `liveReviewVm` / `liveTreeVm` / `gate` 三格**故意**不是 saveable | `REBUILD.md` §2 第 6 条；`UI.md` §2.1；`ARCHITECTURE.md` §11.2 |

写文案之前另有一组硬约束：**界面每一句话都必须真的成立**（NFR-02 的 10 条判据在 `RULES.md` §16），操作步骤是 `REBUILD.md` §5 的五步门。

---

## 5. 依赖与构建配置

### 5.1 `gradle/libs.versions.toml` 是唯一版本源

所有版本、库坐标、插件 alias 都只在这里出现；`app/build.gradle.kts` 只写 `libs.xxx`，不写字面版本。当前 `[plugins]` 5 条 alias 全在 `app/build.gradle.kts:1-7` 用上：`android-application` / `kotlin-android` / `kotlin-compose` / `kotlin-serialization` / `ksp`（`ARCHITECTURE.md` §2）。

**仓库与解析模式**（`settings.gradle.kts`）：`pluginManagement` 与 `dependencyResolutionManagement` **都只用官方源**（`google` / `mavenCentral` / `gradlePluginPortal`；2026-10-10 起阿里云镜像已从仓库配置里去掉，原因见 F46），并设了 `RepositoriesMode.PREFER_SETTINGS`——模块里自己写的仓库会被忽略，所以**不要**在 `app/build.gradle.kts` 里写 `repositories { }`。

### 5.2 加依赖之前必须回答的三个问题

1. **标准库或已有依赖做不了吗？** 这个项目的依赖集合刻意很窄（Room / Compose / WorkManager / DataStore / OkHttp / kotlinx-serialization / Coil / exifinterface / CameraX + 测试用 Robolectric、mockwebserver）。一个只需要几行代码的能力，加一个库就是把"以后升级它"变成长期负担。
2. **包体多少？** 已经有两条因此被否决的先例：`material-icons-extended`（`REBUILD.md` §2 第 10 条）、动态取色（`ARCHITECTURE.md` §11.5，否决项 D4）。判据是"用户装的东西少了没有"，不是"开发省了几行"。
3. **有没有更小的替代？** 同功能优先选小的那一个；选不到就先不加——`⬜ 待做` 比一个错误的依赖便宜。

定下来之后按 `PROCESS.md` §3.6 走：**一条依赖一次提交**，跑全量回归 + 真机冒烟。加测试专用依赖（`testImplementation`）同样是改动，一并声明在 `libs.versions.toml`，别在模块里写死坐标。

### 5.3 `app/build.gradle.kts` 里四处非默认设定必须留着

| 设定 | 行 | 为什么不许删 |
|---|---|---|
| `buildConfig = true` | `:37` | 备份包的 `manifest.json` 要写 App 版本（`BuildConfig.VERSION_NAME` → `AppContainer.kt:54`）；关掉它备份就写不出 `appVersion` |
| `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` | `:44` | 导出 schema JSON（仓库里有 `app/schemas/com.wheremy.app.data.db.WheremyDatabase/1.json`）——将来做真实 Migration 的基线，不导出就只剩猜 |
| `testOptions.unitTests { isIncludeAndroidResources = true; isReturnDefaultValues = true }` | `:28-33` | Robolectric 跑 Compose 界面测试的前提；删了它，Robolectric 那一层（27 类，含全部界面测试）就集体跑不起来 |
| `release { isMinifyEnabled = true }` + `isShrinkResources = true`，签名走用户 keystore | `:74-88` | **R8 在 P6 已打开**（不是「未混淆」了）；签名配置读仓库外的 `keystore.properties`，没有时回落到 debug 签名并打 ⚠️。⭐FR-RLS-01 已于 2026-10-10 由用户完成：`gradle :app:signingReport` 现在是 `Config: release`（`RELEASE.md` §3.2） |

另外两条容易碰坏的：`compileOptions` / `jvmTarget` 都是 **17**（`:24-27`、`:42`），别跟着本机的 JDK 21 改；CameraX 是**四个 artifact**（core / camera2 / lifecycle / view，`:61-65`），没有 `camera-compose` 也没有 `camera-video`（不录像），那句注释就是这个分工。

---

## 6. 常用命令与调试

### 6.1 构建与测试

```bash
gradle :app:assembleDebug                 # 出 debug 包
gradle :app:testDebugUnitTest             # 全量单测（前台跑）
gradle :app:testDebugUnitTest --rerun     # 强制重跑一遍，不看 up-to-date（跑回归必带）
```

按类、按方法只跑一条（调试时省时间，但**判绿只认全量**）：

```bash
gradle :app:testDebugUnitTest --tests "com.wheremy.app.domain.SearchRankerTest"
gradle :app:testDebugUnitTest --tests "com.wheremy.app.ui.review.ReviewScreenTest.editingAStoredItemThenLeavingDoesNotCallItADraft"
```
命名规则：类名 = 文件相对 `app/src/test/java/` 去掉 `.kt` 再把 `/` 换成 `.`。

**测试报告位置**：`app/build/test-results/testDebugUnitTest/`（每类一份 XML，期望 **39** 份）与 `app/build/reports/tests/testDebugUnitTest/index.html`。

### 6.2 装包、重启与"新代码到底跑了没有"

```bash
ADB=/d/Lib/Android/Sdk/platform-tools/adb.exe
$ADB -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
$ADB -s <serial> shell am force-stop com.wheremy.app
$ADB -s <serial> shell am start -n com.wheremy.app/.MainActivity
```

- **`install -r` 成功 ≠ 新代码在跑**：重装后前台可能还是旧进程，必须 `force-stop` + `am start`（`REBUILD.md` §7 C12）。
- **`install -r` 不清权限**：把"装完通知没了"归给重装包是错的；反过来 `pm clear` 会把 `POST_NOTIFICATIONS` 一起清掉，要验通知就补 `pm grant`（`REBUILD.md` §7 C12）。
- **`am force-stop` 会把已发出的通知一起收掉**：要验"点通知跳哪"，用 HOME + `am kill`，并先确认 `pidof` 为空（`REBUILD.md` §7 C13）。

### 6.3 logcat 看什么

**先说结论：应用自己不打印一行日志**——`app/src/` 下没有任何 `android.util.Log` 调用（全量 grep 无命中）。所以 logcat 的价值在**系统侧**，不在业务：

| 看什么 | 命令 | 能回答的问题 |
|---|---|---|
| 崩溃 | `$ADB logcat -d -b crash` | 是不是 `AndroidRuntime` 崩了（**`WheremyApp` 漏了 `android:name` 时 Worker 半夜 ClassCastException**，`ARCHITECTURE.md` §2） |
| 通知到底发没发 | `$ADB shell dumpsys notification \| grep -i wheremy` | 看 `NotificationRecord`；`NOTIFICATION_ID` 固定，重复 notify 是覆盖不是叠两条（`REBUILD.md` §7 C14） |
| 现在前台是谁 | `$ADB shell dumpsys window \| grep mCurrentFocus` | 点没反应之前先确认屏幕（`REBUILD.md` §7 C5、C10） |

业务事实**不要**去 logcat 找：提示词与模型原文在 **AI 留痕**（下一节），物品与照片在**库里**（§6.5）。

### 6.4 AI 留痕怎么看

- **位置**：`files/ai-trace`（应用数据目录下）；刻意**不在** `files/photos` 底下，所以不随备份出门（`RULES.md` §13.3、`ARCHITECTURE.md` §3.1）。
- **界面怎么看**：设置页的 debug 区显示条数——`SettingsScreen.kt:260-267` 🗣「AI 留痕：N 条（发出去的提示词与模型回应原文，只留最近 10 条）」。这一区由 `showDebugTools` 控制，**默认值取 `BuildConfig.DEBUG`**（`SettingsScreen.kt:69`），所以正式包里没有这个入口。
- **一次尝试一条**，重试就是两条；每条含 `model / status / outcome / prompt / response`，**没有 key、没有那张 base64 的图**（`AnalysisTrace.kt`、`ApiPhotoAnalyzer.record`）。上限是滚动保留最近 10 条（`ANALYSIS_TRACE_MAX = 10`）、单字段 8000 字后截断（`RULES.md` §14）。

### 6.5 真机取库：日常要用的三条

完整清单在 `REBUILD.md` §7 B 组（九条，每条都付过代价）。日常只用三条：

1. **db 与 `-wal` 两份都要取**。真数据可能还在未 checkpoint 的 `-wal` 里；只取主库会读到旧值（`ls -l databases/` 里 `-wal` 非 0 就说明主库是旧的）。
2. **一次取整目录，解到一个全新目录**：`exec-out run-as com.wheremy.app tar c -C databases .` → 解到新目录 → 连它、查完**立刻 close()** → 删目录。**"覆盖同名文件"这个动作本身就是 bug**（会读出"旧页 + 新帧"混出来的假世界）。
3. **取二进制用 `exec-out`，不要 `adb shell cat`**（会被做 CRLF 转换 → `malformed database schema`）；取回后先 `PRAGMA integrity_check` 再下任何结论。

两条附注：表名是 `item` / `item_photo` / `location_node` / `item_tag`（别猜）；`run-as` 只对 debuggable 的 debug 包有效。

---

## 7. 提交前自检清单（DoD 的开发者版本）

一次改动提交之前，逐条勾：

- [ ] **测试全绿，且是这一轮的绿**：本轮份数（当前 47 份）的 XML 新鲜 + `failures=0 errors=0`（§2.2），不是「昨天跑过」。
- [ ] **变异验牙做过了**：把改到的那一行故意改坏，期望的那条**恰好**变红；没有红就是假绿，补断言或删断言（`REBUILD.md` §1.3）。
- [ ] **涉及文案时连测试一起改了**：界面测试按逐字串找节点，改文案不改断言 = 留下一颗定时炸弹；走 `REBUILD.md` §5 的五步门。
- [ ] **涉及规则时先改 `RULES.md`**：谓词、阈值、状态机、逐字文案的权威在 `RULES.md`，文档先行再改代码（`PROCESS.md` §3.2）。
- [ ] **涉及 schema / 备份格式时按高风险通道走**：先读 `REBUILD.md` §2 的不可变项与 `RELEASE.md` §5，按 `PROCESS.md` §3.7 走。
- [ ] **没有把 key / 真机数据带进提交**：无 API key、无 `local.properties`、无厂商 base_url / model / key 字面值、无真机导出的 `data.json`、无截图与 dump（`REBUILD.md` §9）。
- [ ] **没有把 key 写进备份包、AI 留痕或日志**（`RULES.md` §12、§13.3）。
- [ ] **每个 🔒 规则都有守门测试**：对照 `RULES.md` §18 那张"规则 → 守门测试"表，改动落在哪一格就去看那一格。
- [ ] **提交信息说清这一批改了什么以及代价**：`feat:` / `docs:` 前缀 + 中文一句话，按仓库既有风格。
- [ ] **不可逆动作先问人**：`pm clear`、改机内数据、删用户可见的物品或照片——用户没点头就不写手机上的数据（`REBUILD.md` §9）。

流程侧还有**六道门 G0–G5**（G0 构建 → G1 单测双证 → G2 变异验牙 → G3 覆盖对账 → G4 文档同步 → G5 真机人工清单），每门的判据、证据形状与豁免条款见 `PROCESS.md` §6。

---

## 8. 扩展点：加东西的完整清单

每一条给三样：**要动的文件**、**必须动的测试**、**最容易漏的一步**。

### ① 加一个物品字段

| | |
|---|---|
| 文件 | `data/db/Entities.kt`（`ItemEntity` 加列）→ `data/repo/Models.kt`（`ItemDraft` 加字段，注意只有 `id` 没有默认值）→ `ItemRepository.kt` 的 `fieldsOf`（`:308-322`）与私有 `Fields`（`:344`）→ `SearchTextBuilder.build` 与 `FIELD_VERSION` → `ui/review/ReviewScreen.kt`（表单次序 🔒）→ `ui/detail/DetailScreen.kt` → `RULES.md` |
| 测试 | `ItemSearchDaoTest` / `ItemRepositoryTest`（读写与 `fieldsOf` 收口）、`SearchTextBuilderTest`（新字段参与拼列）、`ReviewScreenTest`（表单次序与逐字）、`BackupRepositoryTest`（进出包） |
| 容易漏 | ① `blankItem`（`ItemRepository.kt:295-301`）要把新字段一起清空，否则新建的物品带着上一件的值；② **`FIELD_VERSION` 要加一**，否则老物品的 `search_text` 永远不带这个字段（`RULES.md` §10.3）；③ `ItemEntity` 加列 = 改 schema，验收期**冻结**（`REBUILD.md` §2 第 1 条）——先确认解禁条件 |

### ② 加一个筛选条件

| | |
|---|---|
| 文件 | `data/repo/Models.kt`（`Filters` 加属性，`:23-33`）→ `ItemRepository.matches`（加入 AND 组合，`:329-335`）→ `ui/gallery/GalleryViewModel.kt`（setter 与"点同一个 = 取消"的语义）→ `ui/gallery/GalleryScreen.kt` 的 `FilterBar`（芯片）→ `RULES.md` §10.1 |
| 测试 | `GalleryViewModelTest`（切换与取消）、`GalleryScreenTest`（芯片逐字与收起态）、`ItemRepositoryTest`（谓词边界） |
| 容易漏 | ① **收起态的点名**：`FilterBar` 收起时只把 `expireUntil / season / tag` 三样写进「更多筛选 · …」（`GalleryScreen.kt:200-204`），新条件不进去就会出现"列表少了一半却看不出哪个条件开着"；② `isNarrowing`（`:190-193`）决定空态说哪句话，新条件要一起判；③ `GalleryViewModel.load()` 会把 effective filters **写回** state（`RULES.md` §15 W5），`onlyInbox` 因此每次读都被钉成 true |

### ③ 加一个 AI 字段

| | |
|---|---|
| 文件 | `data/vision/AnalysisResult.kt`（加字段，同时看 `isEmpty` 的判据）→ `data/vision/Prompt.kt`（字段行与软上限）→ `data/vision/AnalysisParser.kt` 的 `KNOWN_KEYS`（`:18-21`）→ 合并两级：`ItemRepository.applyAnalysis`（`:109-130`）与 `ReviewViewModel.merge` 的 `touched` 清单（`:229-249`）→ `RULES.md` §5.1/§5.2/§5.6 |
| 测试 | `AnalysisParserTest`（解析与去重）、`ApiPhotoAnalyzerTest`（请求体里的提示词）、`ItemRepositoryTest`（`applyAnalysis` 不覆盖 CONFIRMED）、`ReviewViewModelTest`（用户改过的字段赢） |
| 容易漏 | ① **`KNOWN_KEYS` 不加，这个字段永远解析不出来**——提示词写得再准也没用（回退路径靠"含已知键的对象"认领）；② 两级合并不是一套规则：**落库级**是 `AI ?: 库里现有`，**表单级**是 `touched` 里有的字段用户赢，混了就会出现"改了名字被 AI 覆盖"或"保存之后又被迟到结果改写"；③ `tags` 是整组替换（空数组保留库里那组），别照字段的写法套 |

### ④ 加一类 AI 失败原因

| | |
|---|---|
| 文件 | `data/vision/PhotoAnalyzer.kt`（`FailureReason` 加枚举值）→ `ApiPhotoAnalyzer.kt` 的 `classify`（`:82-95`，状态码→结局的唯一映射）与 `RETRYABLE`（`:176`）→ `ui/common/UiFormat.kt` 的 `failureText`（`:35-56`，11 条变 12 条）→ `RULES.md` §13.1/§13.2 |
| 测试 | `UiFormatTest`（逐字）、`ApiPhotoAnalyzerTest`（分类与重试次数）、`ReviewScreenTest`（失败话术上屏） |
| 容易漏 | ① 忘了决定它**可不可重试**（不在 `RETRYABLE` 里就一次都不重试）；② 文案里抄了第二份真值——比如写死秒数（`TIMEOUT` 故意不写秒数就从这条来的，`RULES.md` §13.2）；③ 每条文案都要带**下一步**，没有一条只说"失败了"；④ 枚举**改名**会让 `files/ai-trace` 里的旧标签对不上（`outcome` 存的是 `FailureReason.name`） |

### ⑤ 加一屏

| | |
|---|---|
| 文件 | `ui/WheremyRoot.kt`（`RootTab` 或一个新的 `return` 分支）→ 新屏的 `ui/<name>/XxxScreen.kt` + `XxxViewModel.kt`（走 `appViewModel(container) { … }`）→ `UI.md`（信息架构与逐字） |
| 测试 | `WheremyRootTest`（路由、返回键、状态恢复）、新屏自己的 Compose 测试 + VM 测试 |
| 容易漏 | ① **五屏 + 底栏四格 🔒**：往底栏加第五格会改掉信息架构（`REBUILD.md` §2 第 5 条、`UI.md` §12）——"加一屏"在这套架构里通常指**整屏接管**（像详情页/录入页那样 `return`），不是加 tab；② 新分支要**在返回键优先级表里排个位置**（`UI.md` §2.3，后注册的优先），否则返回键会跳过它；③ 新屏的路由状态必须进 `rememberSaveable` 那套恢复语义，而 **VM 的活引用绝不能存**（照 `liveReviewVm` / `liveTreeVm` 的先例）；④ 整屏接管时外壳的 `SnackbarHost` 不在组合树里，`say()` 会永远挂起——所以 `say` 在录入页开着时干脆不说（`WheremyRoot.kt:112-113`）；⑤ 同类 VM 共存要靠 `key`（`"gallery"` vs `"inbox"`、`"review-$reviewSeq"`） |

### ⑥ 加一条通知

| | |
|---|---|
| 文件 | `data/work/ReminderWorker.kt`（新频道：`CHANNEL_ID` / `CHANNEL_NAME` / `ensureChannel`；`NOTIFICATION_ID`；`post` 里的 `PendingIntent`）→ `WheremyApp.onCreate`（频道必须早于第一次发送）→ 消费侧 `MainActivity` + `ui/WheremyRoot.kt` |
| 测试 | `ReminderWorkerTest`（12 例：发得出去/发不出去/回写时机） |
| 容易漏 | ① **回写只能排在真的发出去之后**：`markExpireNotified` 早一行，就是在用户没收到的情况下把账记成"已提醒"（`RULES.md` §11.4、`ARCHITECTURE.md` §13 第 11 条）；② `NOTIFICATION_ID` 固定 → 重复 notify 是覆盖不是叠两条，判"发没发"要看 `dumpsys notification`；③ `PendingIntent` 用同一个 id 当 requestCode（`ReminderWorker.kt:109`），换了 id 会让点击跳转行为变；④ 出口**只能跳系统设置页**，不许放"申请权限"按钮（HyperOS 静默驳回，`REBUILD.md` §7 D1）；⑤ `canNotify` 只看 `areNotificationsEnabled()`——**频道静音但总开关开着仍算"已提醒"**（`RULES.md` §15 W3），新通知要自己决定认不认这一条 |

### ⑦ 改备份格式

| | |
|---|---|
| 文件 | `data/backup/BackupFormat.kt`（`ENTRY_MANIFEST` / `ENTRY_DATA` / `PHOTOS_PREFIX`、`BackupManifest`、`BackupData`、`Json` 配置）、`data/backup/BackupRepository.kt`（导出、`validate`、`writeRows`、`isPhotoPath` 白名单）、`data/backup/BackupIo.kt` |
| 测试 | `BackupRepositoryTest`（18 例：导出/导入/四条拒一条放行/MERGE 与 OVERWRITE）、`DatabaseSmokeTest` |
| 容易漏 | ① **旧包必须还能导入**（`REBUILD.md` §2 第 2 条）——`ignoreUnknownKeys = true` 让旧包仍可读，但反过来"新包在老版本上"要靠 `manifest.schemaVersion > DB_SCHEMA_VERSION` 那句拒绝（`BackupRepository.kt:139-141`）拦下；② `explicitNulls = false` 让 NULL 列在 json 里是**缺键**，读侧一律 `.get()`，别用 `row["col"]`；③ 解包路径白名单是**安全边界**（只认 `PhotoStore` 两个目录、只有一层分隔、字符集受限、不许含 `..`），不许放宽；④ 导入顺序 🔒：读包 → 整体校验 → 一个事务落库 → 事务成功后才动磁盘上的照片；⑤ 界面上那句承重文案要跟着改（"清空之后就只能靠这个包了"），**不许把它改成"只替换数据"** |

### ⑧ 加一个后台任务

| | |
|---|---|
| 文件 | `data/work/` 下新增 Worker 与 Scheduler；`WheremyApp.onCreate` 里 `ensureChannel`；`MainActivity.onCreate` 里 `schedule()`（每次 create 都调） |
| 测试 | 照 `ReminderWorkerTest` 的写法（`androidx.work.testing` 已在 `testImplementation` 里） |
| 容易漏 | ① **排班理由要写进代码注释**：本项目用 `PeriodicWorkRequestBuilder(1 天)` + `enqueueUniquePeriodicWork(KEEP)`，`KEEP` 而不是 `UPDATE` 的理由是"参数每次现读设置，重排只会白白丢掉已排好的班次"（`ReminderScheduler.kt:18`）——新任务不许默认抄 `UPDATE`；② Worker 拿依赖只能走 `(applicationContext as WheremyApp).container`，漏了 Manifest 里的 `android:name` 会在后台崩而界面一切正常（`ARCHITECTURE.md` §2）；③ 新任务要自己决定权限与"发不出去"的说法（照 `notifyProblem` 的两句话），并**不许**在界面上放申请权限的按钮；④ WorkManager 自己会在重启后继续跑，**不要**加 BootReceiver |

---

## 9. 排障：症状 → 先查什么

| 症状 | 先查什么 | 出处 |
|---|---|---|
| 测试"绿了"但怀疑根本没跑 | 陈旧 XML：判绿要自己数本轮 XML 的 mtime；后台 shell 没有 gradle（exit 0 + 空输出） | `REBUILD.md` §1.1、§7 A1 |
| 脚本跑 gradle 三秒 exit 1，报 `does not contain a Gradle build` | 没给项目根当 `cwd`；这种跑法不写 XML，而上一轮的绿 XML 会让你误判 | `REBUILD.md` §7 A2 |
| `Unable to delete directory … binary/output.bin` | 是文件锁不是测试红：`GradleWorkerMain` 还攥着，`gradle --stop` 后再跑 | `REBUILD.md` §7 A3 |
| 真机上改了代码没生效 | `install -r` 成功 ≠ 新代码在跑：`am force-stop` + `am start` | `REBUILD.md` §7 C12 |
| 看不到刚录进去的物品 | 空查询没有 `ORDER BY`（`Daos.kt:31`），新行在**最后**——两列网格下第 5、6 格在屏幕外 | `RULES.md` §10.2、`REBUILD.md` §7 C6 |
| 点通知没跳对地方 | 三种：entry 没被消费（要在 GALLERY 分支里清）、`force-stop` 把通知收掉了、应用活着时走的是 `onNewIntent` | `UI.md` §2.9、`REBUILD.md` §7 C13 |
| 取景屏黑屏 | 三种因：没绑上 / 没画出来 / 拍到的就是黑图——把快门那张 JPEG 从 `files/photos/originals` 取回亲眼看，再看 AI 留痕里模型怎么说 | `REBUILD.md` §7 D6 |
| 导入报"引用对不上" | 四条拒：物品的 `locationId` 不在包里 / 照片 `ownerId` 找不到条目 / 照片 `filePath` 或 `thumbPath` 为空白 / 标签挂在不在包里的物品上；另外成环或孤儿位置会**过校验、死在事务里**，报的是同一句笼统的引用错（"备份里有对不上的引用"） | `RULES.md` §12.3、§12.4 |
| 搜索搜不到某件东西 | 候选上限 `LIMIT 300`（按 `updatedAt` 截断在打分之前）；类别/季节是**精确相等**，词表外的值永远筛不到（但 `search_text` 带了它，输关键词仍搜得到）；标签筛选查的是 `item_tag` 行不是文本字面 | `RULES.md` §10.1、§5.7、§14 |
| 列表明显被筛过，却看不出哪个条件开着 | `FilterBar` 收起态只点名 `expireUntil / season / tag`；`category` 与搜索词不在里面 | `GalleryScreen.kt:200-204`、`:190-193` |
| 图片路径有值但那一格是空的 | 六处 `AsyncImage` 都没有 `placeholder`/`error`：文件丢了或解不开时那一格**一句话都不说** | `RULES.md` §15 W11 |
| 长文案把按钮挤成竖排 | 文字与按钮同处一个 `Row` 时必须给可变长度的 `Text` `weight(1f)` | `REBUILD.md` §5 第 4 条、`RULES.md` §16 |
| 通知"该弹没弹" | 频道静音但系统总开关开着时仍算"已提醒"（批次 V 拍板保持现状）；判发没发用 `dumpsys notification` | `RULES.md` §15 W3、`REBUILD.md` §7 C14 |

---

## 10. 安全与隐私纪律（不可放松）

**API key 只有一条活路：本机 DataStore。**

| 绝不许 | 为什么 |
|---|---|
| 进备份包 | 导出通道的构造参数里**没有** `SettingsRepository`，物理上碰不到 key（`AppContainer.kt:54`）——别为了"顺便带上设置"把它接进去 |
| 进 AI 留痕 | 留痕只写提示词与模型回应原文，没有 key、没有那张 base64 的图（`RULES.md` §13.3） |
| 进 git | 永不提交：任何厂商的 key / base_url / model 字面值、`local.properties`、真机导出的 `data.json`、截图与 dump（`REBUILD.md` §9） |
| 写进文档 | 文档里只许写"字段名"和"填法"，不许写值（`README.md` §6 第 3 条） |
| 出现在界面文案里 | 连后几位都不要（`RULES.md` §16.7） |

另外两条不动的：**`allowBackup="false"` 保持**（`AndroidManifest.xml:13`；一动就把库和 key 交给系统云备份，界面上那句"应用已被系统设置为不做云备份"也就成了假话），**AI 留痕目录刻意不在 `files/photos` 底下**（它带着用户的完整位置清单，不随备份出门）。

**要碰 AI 配置 / 备份 / 真机数据时的三条硬规矩**：

1. **用户没点头，就不往手机上写数据**（含 `pm clear`、改库、删用户可见的物品与照片）。这是 `REBUILD.md` §7 C16/C17 两条事故的直接产物。
2. **任何会动机上数据的脚本，动作必须放在 `if __name__ == "__main__"` 守卫之下**；想复用函数就抽到独立的无副作用模块——`python -c "from 脚本 import 函数"` 会把那个脚本的整段动作在用户机器上再跑一遍。
3. **真机取数与清理全程走副本**：一次取整目录到全新目录、查完立刻 `close()`、删目录；清理清单**从 `ls` 生成，不从记忆生成**，含隐私的截图/dump 删完当场再 `ls` 一遍确认（`REBUILD.md` §7 B2、E3）。

系统弹窗（账号、登录、授权）出现时：**只按返回键取消，绝不输入任何账号密码**，并把"出现过"回报给用户（`REBUILD.md` §6）。

---

## 11. 去哪里问 / 去哪里记

| 我卡在哪 | 去哪 |
|---|---|
| 这次改动该走哪条通道、要同步哪几份文档、过哪几道门 | `PROCESS.md`（八条通道 + §5 同步矩阵 + §6 G0–G5） |
| 规则到底怎么算、这句话该怎么说 | `RULES.md`（谓词、阈值、状态机、逐字文案、§15 已知偏差、§16 判据） |
| 代码为什么长这样、哪一层该放什么、调用顺序 | `ARCHITECTURE.md`（as-built，§13 是改动前必读） |
| 屏幕上该有什么、次序、怎么点 | `UI.md`（信息架构 🔒）与 `DESIGN_SYSTEM.md`（视觉，可演进） |
| 这个技术选型为什么这么定、能不能推翻 | `DECISIONS.md`（ADR + 推翻条件）；否决项索引在 `PRD.md` §6 |
| 这个词在代码里叫什么、在哪张表哪一列 | `GLOSSARY.md` |
| 要做什么、优先级、验收口径 | `PRD.md`（目标态 + 状态标记） |
| 从零重建的顺序、不可变项、已踩过的坑 | `REBUILD.md`（§2 不可变项、§3 里程碑、§7 坑） |
| 怎么测、怎么判绿、怎么验牙、缺陷分级 | `TESTING.md` |
| 发布、签名、迁移、回滚 | `RELEASE.md` |

**记在哪**：任何一次"文档与源码不一致"的判决都要留痕——判定哪一边错了、改了什么，写进 `CHANGELOG.md` 一行（`README.md` §3.2）。**不允许**用一句"以源码为准"收场。

---

## 写作时发现的差异（2026-10-07 已全部处理）

复核基线 `cf73f5b` 时发现下面几处。按 `README.md` §3.2 的裁法（先判定、再改错的那一边）**五处都已改掉**，留表作裁决留痕：

| 文档 A 位置 | 文档 B 位置 | 冲突点 | 处理 |
|---|---|---|---|
| `REBUILD.md` §7 B8 / §8 第 2 条（把 `adb` 与 `sdkmanager` 并列说"用 SDK 目录内绝对路径调用"） | 本机实测 `D:\Lib\Android\Sdk` | `adb` 成立（`platform-tools\adb.exe` 存在）；但 SDK 里**没有 `cmdline-tools`**，全盘搜不到 `sdkmanager` | **判实测对** → `REBUILD.md` 两处已改成"`adb` 用绝对路径；本机没有 `sdkmanager`，别写进脚本" |
| `REBUILD.md` §2 第 6 条（`WheremyRoot.kt:76-93`，括注"全 `rememberSaveable`"） | `UI.md` §2.1；`ARCHITECTURE.md` §11.2 | 源码 `:83`/`:85`/`:93` 是 `remember`；该区间实为 8 处 saveable，"九格"也对不上 | **判 `UI.md` 对** → `REBUILD.md` 已改 |
| `REBUILD.md` §2 第 7 条（`deliverPhoto`，`:115-145`） | `UI.md` §2.4 与 `ARCHITECTURE.md` §11.2（均 `:123-145`） | `val deliverPhoto` 在 `:123`，`:116-122` 是 KDoc | **判 B 对** → `REBUILD.md` 已改 |
| `RULES.md` §12.3 标题（"三条拒 + 一条放行"）；`REBUILD.md` §3 M5 同写"三条拒" | 同节表体（#1–#4）+ `PRD.md` FR-BAK-06 | 源码 `validate()`（`:149-168`）抛 **4** 种拒 | **判"四条"对** → 两处标题已改 |
| `RULES.md` §12.3 校验表 | `BackupRepository.kt:139-141` | 少一条：读包阶段 `manifest.schemaVersion > DB_SCHEMA_VERSION` 即拒（不在 `validate()` 内） | **已补** → `RULES.md` §12.3 加了一段"另有第五道拒，不在 `validate()` 里" |
