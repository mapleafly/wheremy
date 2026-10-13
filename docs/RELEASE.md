# Wheremy 发布文档（RELEASE）

**这份文档回答**：把一个包交到用户手上之前必须做完哪几件事、每件事留下什么证据、出错了怎么退回来。
**读者**：软件经理 / 发布负责人（`README.md` §1 那张表里"发布 / 运维"一行）。一人多角是常态，但**每一件签收都要落到名字**。
**代码基线**：commit `cf73f5b`（2026-10-06）。主源 57 个 Kotlin 文件 / 6881 行；测试 39 个结果类 / 481 例全绿。
**不回答**：日常怎么开发（→`DEVELOPMENT.md`）、测试怎么写（→`TESTING.md`）、一次改动走哪条通道（→`PROCESS.md`）、每条规则怎么算（→`RULES.md`）、从零重建的顺序（→`REBUILD.md`）。

---

## 1. 这份文档管什么 / 不管什么

| 本文管 | 展开在 | 本文不管 → 去 |
|---|---|---|
| 版本号怎么涨；`versionCode` / `DB_SCHEMA_VERSION` / 备份包 `schemaVersion` 的关系 | §2 | 环境怎么搭、编码纪律、扩展点 → `DEVELOPMENT.md` |
| 发布前必做（⭐）——为什么必须、怎么算做完、谁签收 | §3 | 测试分层、判绿双证、变异验牙、缺陷分级 → `TESTING.md` |
| 发布门禁 G0–G5 在发布场景下要交什么证据 | §4 | 一次改动走哪条通道、文档同步矩阵 → `PROCESS.md` §3 / §5 |
| schema 升级、备份包兼容、降级、导出-升级-导入 | §5 | 每条规则的值与逐字文案 → `RULES.md` |
| 怎么出包、怎么装上去、什么绝不许进包 | §6 | 里程碑、风险登记、迭代节奏 → `PROJECT.md` |
| 出事后哪些能退、哪些退不了、先做什么 | §7 | 从零重建的顺序与不可变项 → `REBUILD.md` |
| 发布记录写在哪、用户反馈怎么变成缺陷 | §8 | 单条缺陷怎么修、走哪条通道 → `PROCESS.md` §3.2 |
| 一份照着打勾就能发的清单 | §9 | 打了勾之后的排期与迭代节奏 → `PROJECT.md` |

**边界一句话**：本文只写"交付那一刻要发生什么"，不写"交付之前每一天怎么写代码"。

---

## 2. 发布物与版本策略

### 2.1 现在的事实（**本实现**，2026-10-09 采）

| 项 | 值 | 出处 |
|---|---|---|
| `applicationId` | `com.wheremy.app`；**debug 带 `.dsh` 后缀**（与参照实现同机并存，`PLAN.md` §6.2） | `app/build.gradle.kts` |
| `versionName` / `versionCode` | `"0.1.0"` / `1` | 同上 |
| `compileSdk` / `minSdk` / `targetSdk` | 37 / 26 / 36（compileSdk 37 是 Compose BOM 2026.09.00 的硬要求） | 同上 |
| 工具链 | **Gradle wrapper 9.8.1** + AGP 9.4.1（内建 Kotlin）+ Kotlin 2.4.20 + KSP 2.3.12 | `gradle/wrapper`、`gradle/libs.versions.toml` |
| release 变体 | `isMinifyEnabled = true` + `isShrinkResources = true`，keep 规则在 `app/proguard-rules.pro`；**签名暂用调试签名**（⭐FR-RLS-01 未完成，见 §3.2） | `app/build.gradle.kts` |
| R8 冒烟变体 | `.r8smoke`（规则同 release、包名多一个后缀）——**不能直装 release**：它的包名与参照实现那个应用同名 | 同上 |
| `allowBackup` | `"false"` 🔒（在 release APK 的清单里核过） | `AndroidManifest.xml` |
| 权限 | 自己声明：`POST_NOTIFICATIONS` + `CAMERA` + `INTERNET`（AI 请求，P2 落地时补，F36）；**没有任何存储权限**（选图走 SAF/Picker）。合并清单里另有 WorkManager 带进来的 `WAKE_LOCK` / `ACCESS_NETWORK_STATE` / `RECEIVE_BOOT_COMPLETED` / `FOREGROUND_SERVICE` | `aapt2 dump badging`（release APK） |
| DB schema 号 | `DB_SCHEMA_VERSION = 1`，`exportSchema = true`；**没有 `fallbackToDestructiveMigration`**（⭐FR-DB-01 已按 §3.1 满足） | `WheremyDatabase.kt` |
| 构建入口 | **仓库里有 wrapper**（`gradlew.bat` / `gradlew`）；命令行与 CI 都用它 | 仓库根 |
| CI | GitHub Actions：`spotlessCheck` → `testDebugUnitTest` → `verifyRoborazziDebug` → `assembleRelease`（⚠️ 尚未在真实 GitHub 上跑过） | `.github/workflows/ci.yml` |
| 分发方式 | 侧载 APK（**已用用户自己的 keystore 签名**，见 §3.2 与 `KEYSTORE.md`） | `PRD.md` §5 ⭐FR-RLS-01 |

**2026-10-10 更新：⭐FR-RLS-01 已由用户完成。** 历史：在那之前 release 变体确实没有签名配置（`gradle :app:signingReport` 对 release 打印 `Config: null`），当时能侧载出去的只有 debug 包；现在用户自己生成了 keystore 并放好 `keystore.properties`（仓库外），release 包已用它签名——`signingReport` 是 `Config: release`、`apksigner verify --print-certs` 核过签名者、真机装的是正式包。做法与三个备份见 `KEYSTORE.md`。

### 2.2 版本号规则

| 号 | 怎么涨 | 记在哪 |
|---|---|---|
| `versionName` | 语义化 `主.次.修`：现在 `0.1.0`；第一次带签名交付进次位，只有修缺陷才动修订位 | `app/build.gradle.kts` 的 `defaultConfig` |
| `versionCode` | **每次出包 +1，只增不减**；同一天出两个包也要 +1 | 同上；另在`docs/CHANGELOG.md` 记一行（§8.1） |
| `DB_SCHEMA_VERSION` | 只在 schema 变了时 +1，且**必须同时写 Migration**（§5.1） | `WheremyDatabase.kt:16` |

**记账位置只有一个**：`app/build.gradle.kts` 的 `defaultConfig`；`CHANGELOG.md` 是它的历史，不是第二份真值（`RULES.md` §16 第 8 条）。

**版本号本身不参与任何兼容判断**：`app/src/main` 里没有一处读 `versionCode`；`versionName` 只经 `BuildConfig.VERSION_NAME` 写进备份包的 `manifest.appVersion`（`AppContainer.kt:54`），导入侧从不读它（`BackupRepository.kt` 只比 `schemaVersion`）。能不能导入由 `DB_SCHEMA_VERSION` 说了算。

### 2.3 版本兼容矩阵

| 号 | 定义在哪 | 谁写 | 谁读 | 变了会怎样 |
|---|---|---|---|---|
| `versionCode` | `app/build.gradle.kts:17` | 手工 | Android 安装器；**应用代码不读** | 变小的包装不上去（§5.3） |
| `DB_SCHEMA_VERSION` | `WheremyDatabase.kt:16` | 手工 | Room 的 `version`（`:20`）；导出时原样写进包的 `schemaVersion`（`BackupRepository.kt:49`） | +1 而没有 Migration ⇒ 破坏性迁移**静默清空整机数据**（⭐FR-DB-01） |
| 备份包 `schemaVersion` | `BackupFormat.kt:16` 的字段 | 导出时自动，值 = 当时的 `DB_SCHEMA_VERSION` | 导入侧**唯一**的兼容闸门：大于 `DB_SCHEMA_VERSION` 就拒（`BackupRepository.kt:139-143`） | 只查上界、**不查下界**——旧包一律照收 |
| 备份包 `appVersion` | `BackupFormat.kt:19` | 导出时写 `BuildConfig.VERSION_NAME`（`AppContainer.kt:54`） | **没有任何代码读它** | 只用于人拿 zip 追问"这包是谁导的" |

一句话记住：**一个号管安装（`versionCode`），一个号管数据（`DB_SCHEMA_VERSION` = 包里的 `schemaVersion`）**；改后者是高风险动作。

### 2.4 新版本必须能导入旧包（不可变项）

`REBUILD.md` §2 第 2 条把**备份包格式**列为硬性不可变项，理由只有一句：**旧包必须还能导入**。技术上靠三条：① `ignoreUnknownKeys = true`——`@Entity` 将来加字段，旧包少那些键照样读（`BackupFormat.kt:45-50`）；② `explicitNulls = false`——NULL 列在 json 里是**缺键**，读侧一律 `.get()`，不许 `row["col"]`（`REBUILD.md` §7 B6）；③ 导入闸门只拦"来自更新的版本"，不拦旧包（`BackupRepository.kt:139`）。

**这条不可变项没有自动化守门人**：`BackupRepositoryTest`（18 例）造的是同版本内的包。所以每次发布都要**用上一版导出的真包演一遍**——那就是 §3 第 7 条与 §5.4。

---

## 3. 发布前必做（⭐ 清单）

七条，每条三栏都要写出来：**为什么必须**（不说清就会被下一轮当可选项砍掉）、**怎么算做完**（可判定，不是感觉）、**谁签收**（一个角色名，不是"大家"）。

> **本实现的逐条状态（2026-10-09）**：3.1 ✅ 已满足（比参照实现更早还清）｜3.2 ⬜ **未完成**（需要用户自己的 keystore；用户已拍板"后续再做，先按调试签名"）｜3.3 ✅ 走 B 路线（打开 R8）｜3.4 ✅ 已核（`INTERNET` 已于 P2 补上，F36）｜3.5 ✅ 已核（含 APK 清单）｜3.6 ✅ 单测 17 例 + 真机一次"导出 → 删一件 → 覆盖导入 → 那一件回来"｜3.7 ⬜ 无上一版可演（首版），反面演练由测试覆盖。详细证据见 §10。

### 3.1 ⭐FR-DB-01：用真实 Room Migration 替换 `fallbackToDestructiveMigration(true)`

**为什么必须**：破坏性迁移会在版本对不上时**直接清空四张表**——用户看不到任何提示，照片文件还在，行没了。
**怎么算做完**：① `DB_SCHEMA_VERSION` 升到 2；② 写 `Migration(1, 2)`，把本次字段变化逐条翻成 SQL；③ `Room.databaseBuilder(...)` 上加 `.addMigrations(...)`；④ `app/schemas/…/2.json` 导出来与 `1.json` 逐列对照；⑤ 写"从 1 升到 2 后老数据还在"的用例；⑥ 变异验牙：把 Migration 里的一条 `ALTER TABLE` 删掉，那条用例必须红。
**本实现的情况**：**这一条已经提前还清**——`WheremyDatabase.build()` 里从来没有 `fallbackToDestructiveMigration`，所以版本一升、没交 Migration 就会**当场抛错**（大声失败，而不是静默清库）。`DELTA.md` 的 W2、`WheremyDatabase.kt` 的 KDoc 都记着这件事。①–⑥ 是"将来真升 schema 时"要走的流程。
**谁签收**：架构师（Migration 写对）+ 测试（升级用例与变异）+ 软件经理（验收）。

### 3.2 ⭐FR-RLS-01：release 签名

**为什么必须**：release 现在**用调试签名**（`signingConfig = signingConfigs.getByName("debug")`），只够做"R8 之后还跑得起来"的真机冒烟；侧载给别人用的正式包必须有自己的 keystore。
**怎么算做完**：① 生成 keystore（口令与别名**记在密码管理器里，不写进仓库、不写进任何文档**）；② keystore 放在**仓库之外**；③ 配 `signingConfigs` + `buildTypes.release.signingConfig`（把现在那行调试签名换掉）；④ `gradle :app:signingReport` 里 release 那段不再是 `Config: null`；⑤ `apksigner verify --print-certs` 核签名者；⑥ `git status` 干净，KE 仓库里没有 keystore。
**本实现的情况**：✅ **已完成（2026-10-10，由用户自己做）**——用户生成了 keystore（`D:\keys\wheremy-release.jks`，别名 `wheremy-release`）并在仓库外放好 `keystore.properties`；六条「怎么算做完」逐条核过：`signingReport` 是 `Config: release`（不再 null）、`apksigner verify --print-certs` 的 SHA-256 与 keytool 一致、有效期到 2054、`git status` 干净、仓库里没有 keystore 文件。**口令始终在用户手里**，本文与 `KEYSTORE.md` 只记位置不记口令。
📖 **操作手册在 `KEYSTORE.md`**：从「生成 → 三份备份 → 写 `keystore.properties`（仓库外）→ 接进构建 → 三条验证命令 → 装机两个坑 → 换密钥不可逆」一条条写清，并附本次在本机用一把**一次性**密钥跑通整条链的实测记录。构建侧已经就绪：`app/build.gradle.kts` 会按顺序找 `<仓库父目录>/keystore.properties` 与 `~/.wheremy/keystore.properties`，**找不到就退回调试签名并在出 release 包时喊一声**——所以你把文件放好之后，不需要改任何代码。
**谁签收**：软件经理（只有他知道 keystore 在哪）+ 架构师（配置）。

### 3.3 `isMinifyEnabled` 的处置

**为什么必须**：混淆开不开**必须在发布记录里写明理由**；否则下一个人会以为这是忘了配。
**怎么算做完**：二选一写进发布记录（A 保持 `false` 并写理由；B 打开混淆 + 完整 keep 规则 + 全量回归 + 真机冒烟）。
**本实现的选择**：**B（打开）**。理由与证据：
- 规则三条：kotlinx.serialization 的 `$$serializer`、Room 3 的实体、Hilt 的 Worker（`app/proguard-rules.pro`）；
- 全量回归 **27 类 / 175 例全绿**（R8 不影响 JVM 测试，但保证代码本身没依赖"名字还在"的怪写法）；
- **真机冒烟**（唯一能验反射的步骤）：装 `.r8smoke` 包 → `--es dev backupcheck` → 「导出：位置 3 · 物品 5 · 照片 5 · 44331 字节」+「合并导入：0/0/0（按主键跳过）」→ 无崩溃；
- 顺带收益：release APK **2.69 MB**（debug 17.4 MB），冷启动 **233 ms**（debug 926 ms）。
**谁签收**：软件经理。

### 3.4 权限复核

**为什么必须**：`PRD.md` FR-PRM-01 要求「权限清单最小」，多一条就推翻这条需求。
**怎么算做完**：① 数 `AndroidManifest.xml` 里的 `<uses-permission>`；② 装机后在系统「应用信息 → 权限」里对照；③ 核**合并后**的清单（`aapt2 dump badging <apk>`），把库带进来的也认一遍。
**本实现的情况**：✅ 自己声明 `POST_NOTIFICATIONS` + `CAMERA` + `INTERNET`，**无存储权限**；合并清单里另有 4 条来自 WorkManager 的传递依赖，已在 §2.1 记明。⚠️ `INTERNET` 是 P2 真机连模型时才补上的（F36）：少了它，IO 异常会被判成 `OFFLINE`，界面就说"网络不可用"——一句假话。
**谁签收**：测试（真机那一遍）+ 软件经理（清单那一遍）。

### 3.5 `allowBackup="false"` 复核

**为什么必须**：一动就把库和 key 交给系统云备份；而且设置页那句承重文案立刻变成假话（NFR-02）。
**怎么算做完**：① `AndroidManifest.xml` 仍是 `android:allowBackup="false"`；② 装机后把设置页那句读一遍；③ 记进发布记录。
**本实现的情况**：✅ 源码与 **release APK 的清单**都核过（`aapt2 dump xmltree … | findstr allowBackup` → `false`）。
**谁签收**：软件经理。

### 3.6 备份 / 导入回归（`RULES.md` §12 全部判据）

**为什么必须**：导出 zip 是**唯一**的迁移手段；这条链坏掉，用户数据就没有出口。
**怎么算做完**：按 §12 四小节各走一遍并留证据（导出口径 / 固定顺序 / 四拒一放行 / MERGE 与 OVERWRITE）。
**本实现的情况**：✅ 自动化 **17 例**（`BackupRepositoryTest`）+ 真机一次完整闭环：「导出到文件」→ 删掉一件 →「覆盖导入」→ **那一件回来了**（结果句 `覆盖导入完成 · 位置 3 个 · 物品 5 件 · 照片 5 张`；库拉下来点数 3/5/5/5）。
**谁签收**：测试（自动化那一半）+ 用户（真机那一半）。

### 3.7 迁移演练：旧包 → 导入 → 对账

**为什么必须**：只有真拿**上一发布版导出的包**灌进**本次要发的包**，才知道 §2.4 那条不可变项还成立。
**本实现的情况**：**首版没有"上一发布版"可演**（跨版本那一半仍等 0.1.0 发出去、0.2.0 要发时补）；但**同版本往返 + 旧包合并**已在 2026-10-13 真机演过一遍（回执 3 条、逐字节对账，见 §10.8）。反面演练（把包里 `schemaVersion` 改成比当前大）已经有了自动化版本：`BackupRepositoryTest.schema 更高直接拒` ✓。
**谁签收**：发布负责人（①–⑤）+ 用户（⑥ 回"看到了什么"）。

---

## 4. 发布门禁 G0–G5（与 `PROCESS.md` §6 同一套）

门禁定义在 `PROCESS.md` §6，发布这件事本身走的是 `PROCESS.md` §3.8 发布通道；本节只写**发布场景下的证据要求**：日常迭代里这六门随批次走，发布时必须**一次性全齐**，证据要能贴进发布记录（§8.2）。六门是**与关系**。

| 门 | 发布场景要交的证据 | 不满足就不许发布，因为 |
|---|---|---|
| **G0 构建** | `gradle :app:assembleDebug` 前台跑完、`BUILD SUCCESSFUL`；`app/build/outputs/apk/debug/app-debug.apk` 的 mtime 是本轮的 | 包可能是上一轮的——`install -r` 成功 ≠ 新代码生效（`REBUILD.md` §6） |
| **G1 单测全绿双证** | ① 命令输出 `BUILD SUCCESSFUL`；② `app/build/test-results/testDebugUnitTest/*.xml` 里 **39 份**的 mtime 都晚于本轮起跑时刻；③ 自己数出 `tests=481 failures=0 errors=0` | 只有 exit code 是 0、或 XML 是上一轮的，只能判 **NO-VERDICT**，不是绿（`REBUILD.md` §1.1、§7 A1/A2） |
| **G2 变异验牙** | 本批改动**每一处**的验牙记录：改坏哪一行、期望哪条红、实际红了哪条 | 全绿只证明"测试跑过了"，不证明"测试有牙"（`REBUILD.md` §1.3） |
| **G3 覆盖对账** | 正向：`PRD.md` §7 表里每一行 → 测试类真的存在、`@Test` 数与表一致；反向：`grep -rl "@Test" app/src/test \| wc -l` = 39、`grep -c` 求和 = 481 | **类名存在不等于有人守**；`ItemRow` 这种"没有自己的类"的行要落到别家的例数里（`REBUILD.md` §1.2） |
| **G4 文档同步** | `PROCESS.md` §5 同步矩阵要求的每一份都改到位；`CHANGELOG.md` 有本次发布那一行；所有 `⬜` 都带触发条件与代价 | 改代码不改文档 = 没做完（`README.md` §6） |
| **G5 真机人工清单签收** | `REBUILD.md` §6 那七条**由人在真机上**逐条回"看到了什么"，失败的回原话；交给用户的那份走 `templates/device-acceptance.md` 与 §6 末三条硬规矩 | 代理无法自证这七条；相机 HAL、选图器、通知、图标渲染只有真机能答（`UI.md` §15） |

**与 §3 的分工**：§3 是"要做的动作"，G0–G5 是"这些动作必须留下什么证据"。两处都不许用"应该没问题"代替证据。

---

## 5. 迁移与兼容

### 5.1 schema 升级的标准流程（六步，顺序不许换）

1. **先确认这次真的非改不可**：验收期字段冻结（`REBUILD.md` §2 第 1 条），先想清楚能不能靠不动 schema 解决。
2. **写 `Migration(old, new)`**，把每一条字段变化翻成 SQL。
3. **升 `DB_SCHEMA_VERSION`**（`WheremyDatabase.kt:16`）。这一处同时定了 Room 的 `version`（`:20`）与导出时写进包的 `schemaVersion`（`BackupRepository.kt:49`）——**只有一个号要改**，别去手改包里的号。
4. **让 schema JSON 导出来再对账**：跑一次会触发 KSP 的构建，`app/schemas/com.wheremy.app.data.db.WheremyDatabase/<新号>.json` 会出来；与上一份**逐列 diff**，多出来的列、改掉的非空约束、索引，每条都要能在第 2 步的 SQL 里找到对应。
5. **写"升上来"的测试**：`androidx.room:room-testing` 已在 `app/build.gradle.kts:74`；从旧版本建库 → 升 → 断言老数据还在、新列是默认值。
6. **删掉破坏性迁移**：`.addMigrations(...)` 与 `.fallbackToDestructiveMigration(true)` 不能并存（`WheremyDatabase.kt:33-36`）。**留着它，第 2 步写漏一条也不会红，只会静默清空。**

备份包 `schemaVersion` 的处置：**不需要手改**，它跟着 `DB_SCHEMA_VERSION` 走。真正要保证的只有一件事——新版本仍能导入 `schemaVersion = 1` 的旧包（§2.4）。**派生列也要一起想**：`search_text` 带 `SearchTextBuilder.FIELD_VERSION`（现在 2），加字段后是启动时补算一遍；能在这条 Migration 里一次算完就别留给启动（`TECH_STACK.md` 的自造清单 I 行与 T7 记着这件事）。

### 5.2 备份包向前兼容规则

- **闸门只有一条**：包里的 `schemaVersion > DB_SCHEMA_VERSION` 就拒，文案是 `BackupRepository.kt:140-142` 那句（以「这个备份来自更新的版本」开头、以「先升级应用再导入」结尾，括号里插 `schema X > N`）。**语义要读准**：这是"你机上的应用比包旧"，解法是**升应用**；把包里的号改小只会让后面的字段解析悄悄出错。
- **旧包一律照收**：没有下界检查。所以"新版本能导入旧包"必须自己演一遍（§3 第 7 条）。
- `explicitNulls = false` ⇒ NULL 是**缺键**：读侧一律 `.get()`，不许 `row["col"]`（`BackupFormat.kt:46-50`、`REBUILD.md` §7 B6）。
- `ignoreUnknownKeys = true` ⇒ **加字段安全；删字段或改字段语义不安全**——旧包里的键会被静默丢掉，界面当时不会说话。

### 5.3 降级不可行

1. **装回旧版本解决不了 schema**：只要有任何一版升过 schema，库里就是新的，而旧版本里没有那条 Migration——破坏性迁移还在（`WheremyDatabase.kt:35`），结果轻则报错、重则清空。（今天两边都还是 `DB_SCHEMA_VERSION = 1`，所以这一条眼下还只是预案；一旦 ⭐FR-DB-01 落地，它立刻变成现成的坑。）
2. **降级安装本身就被挡**：本机 `adb --help` 写着 `-d: allow version code downgrade (debuggable packages only)`——这条捷径**只对 debug 包成立**。正式包要装回旧版只能先卸载，而卸载会把机内数据一起删掉。
3. **已经覆盖导入的数据回不来**：`RULES.md` §12.4——覆盖导入先 `deleteAll` 四张表再按包重建，并按包内清单删掉本机多余的照片（`removeLocalPhotosNotIn`），那一步不在事务里。

**结论：能退的只有 APK，退不了的是数据。** 所以动手之前先导出（§7.3）。

### 5.4 导出 → 升级 → 导入（推荐路径）

```
旧机（旧版本）             设置 → 备份 →「导出到文件」→ 记下回执那三个数
      ↓ 同一个 zip
新机 / 新版本（侧载 APK）  设置 → 备份 →「合并导入」→ 对账三个数
      ↓ 只在"就是要用包里的内容覆盖本机"时才走
                          设置 → 备份 →「覆盖导入」（先清空 + 二次确认）
```

- **合并导入是默认选择**：已有的按主键原样保留，只补没有的（`RULES.md` §12.4）。
- **覆盖导入是破坏性的**：那句标题「覆盖导入会清空本机数据」不是修辞。
- **AI 配置不跟着走**：包内不含任何设置（`PRD.md` FR-BAK-04、⛔D12）。新机上要重填 base_url / model / key，按「保存 AI 配置」之后才生效。旧机上那句实话要照做：「key 只存在这台手机的本地设置里：不进备份包，也不跟着任何云端同步走。换手机请先在旧机上把它清空，再重装。」（`SettingsScreen.kt:256-258` 🗣）。

---

## 6. 构建与分发

### 6.1 命令行构建 fact（**本实现**）

| 事实 | 值 | 出处 |
|---|---|---|
| wrapper | **仓库里有**（`gradlew.bat` / `gradlew`，Gradle 9.8.1）；本地与 CI 都用它，`./gradlew` 而不是 PATH 上的 gradle | `gradle/wrapper` |
| 工具链 | AGP 9.4.1（内建 Kotlin）+ Kotlin 2.4.20 + KSP 2.3.12；项目按 JVM 17 编译 | `gradle/libs.versions.toml`、`app/build.gradle.kts` |
| SDK | `ANDROID_HOME = D:\Lib\Android\Sdk`；`local.properties` 被 gitignore：**不提交，内容也不写进任何文档** | 本机 |
| adb | **不在 PATH**，用 SDK 目录内的绝对路径（`…\platform-tools\adb.exe`） | 本机 |
| 平台 | Windows 11 + PowerShell；带设备路径的 adb 参数注意引号 | 本机 |
| 真机 | Xiaomi 13 / HyperOS，序列号 `9aa4b43e`；`adb devices` 为空时**不许用模拟器凑数** | `REBUILD.md` §8 第 5 条 |
| 依赖源 | `settings.gradle.kts` **只用官方源**（google / mavenCentral / gradlePluginPortal）；开了 `RepositoriesMode.PREFER_SETTINGS` 是因为机器上那份 `init.d/init.gradle` 会注入镜像。⚠️ 2026-10-10 更正：镜像**不再写进仓库配置**（runner 上 502 → CI 红，F46） | `settings.gradle.kts`（F4/F46） |

```bash
./gradlew.bat :app:assembleDebug              # 出 debug 包（17.4 MB）
./gradlew.bat :app:testDebugUnitTest --rerun  # 全量单测（判绿要 --rerun 才保证 XML 是本轮的）
./gradlew.bat :app:spotlessCheck              # 静态检查
./gradlew.bat :app:verifyRoborazziDebug       # 截图回归（基线在 app/src/test/screenshots/）
./gradlew.bat :app:assembleRelease            # release（R8 + 资源收缩，2.69 MB）
./gradlew.bat :app:assembleR8smoke            # R8 真机冒烟变体（包名多 .r8smoke，可直装）
./gradlew.bat :app:recordRoborazziDebug --rerun-tasks   # 更新截图基线（不加 --rerun-tasks 会静默不录，F34）
```

**gradle 只许前台跑**（`REBUILD.md` §7 A1）；脚本调 gradle 必须显式给项目根当 `cwd`（A2）。

### 6.2 debug 与 release 的差别

| | debug | release（现在） |
|---|---|---|
| 包名 | `com.wheremy.app.dsh`（与参照实现并存） | `com.wheremy.app` |
| 混淆 | 无 | **R8 + 资源收缩**（keep 规则见 §3.3） |
| 签名 | 自动 debug 签名 | 有 `keystore.properties`（仓库外）就用你自己的 keystore，没有就暂时用 debug 签名（⭐FR-RLS-01，§3.2；做法见 `KEYSTORE.md`） |
| 设置页 debug 区 | 有 | **没有**（`BuildConfig.DEBUG`） |
| 体积 / 冷启动 | 17.4 MB / 926 ms（中位数） | 2.69 MB / **233 ms**（中位数，`.r8smoke` 同规则） |
| 可降级安装 | 可以 | 不可以（§5.3） |

**由此得出一条发布期的实话**：`SettingsScreen.kt:172-174` 的「调试：立刻检查一次到期提醒」与 `:260-267` 的「AI 留痕：N 条…」**只在 debug 包上出现**（`UI.md` §7.1 第 5 项、§7.2 第 6 项）。所以正式包上**不能**按一下立刻验提醒链路（只能等那一天自己到），也**看不到** AI 留痕那一行（要看只能连 adb 取 `files/ai-trace`，`AppContainer.kt:72`）。这句要写进发布记录，免得下一个人以为正式包上有这两样。

### 6.3 侧载步骤

⚠️ **先读 `KEYSTORE.md` §7 的两个坑**：release 的包名 `com.wheremy.app` 与参照实现那个应用**同名**（覆盖安装会失败，强装要先卸载并丢数据），以及 `versionCode` 只能涨不能降。

```bash
ADB=/d/Lib/Android/Sdk/platform-tools/adb.exe        # 不在 PATH，用绝对路径
$ADB -s 9aa4b43e install -r app/build/outputs/apk/debug/app-debug.apk
$ADB -s 9aa4b43e shell am force-stop com.wheremy.app
$ADB -s 9aa4b43e shell am start -n com.wheremy.app/.MainActivity
```

- **三件必须连着做**：`install -r` 之后要 `am force-stop`（否则前台还是旧进程）+ `am start`（`REBUILD.md` §7 C12）。
- **`install -r` 不清权限**：`POST_NOTIFICATIONS` 不会因为重装而变（C12）。别把"装完通知没了"归给重装包；真正会清掉它的是 `pm clear`。
- **`am force-stop` 会把已发出的通知一起收掉**：要验"点通知跳转"得用 HOME + `am kill`（C13）。
- 想在真机上直接落到"快到期 / 已过期"那张照片墙（通知那一跳的落点）：`$ADB shell am start -n com.wheremy.app/.MainActivity --es com.wheremy.app.entry expiring`（键与值来自 `MainActivity.kt:38,41`）。
- 装完先点开一次不崩，再去跑人工清单。

### 6.4 环境快照要写哪两行

交付给用户的验收单（`templates/device-acceptance.md`）与发布记录都必须**写在最前面**（`REBUILD.md` §6 末）：① **装的是哪个包、装于何时**——`install -r` 成功 ≠ 新代码生效；② **本机数据是否为空、那条权限是否还没授过**——不清空的数据会让"第一次"的行为根本测不出来。

### 6.5 绝不许把 key 打包或注入

- 开发期的 key 走 `local.properties`（已 gitignore），**构建时不注入**（`PRD.md` FR-CFG-03）——它只是"记在本地别丢"，不是构建输入。
- key 只存 DataStore（`wheremy_settings`）：**不进备份包、不进 AI 留痕、不进 git**（`RULES.md` §12.1、§13.3；`PRD.md` ⛔D12）。
- 导出通道**物理上**碰不到 key：`BackupRepository` 的构造参数里刻意没有 `SettingsRepository`（`BackupRepository.kt:28`）。
- 发布前用一句话自查：**这个包里、这次提交里、这条日志里，有没有任何一串能当凭据用的字符串？** 有就停手（`README.md` §6 第 3 条、`REBUILD.md` §9）。

---

## 7. 回滚与事故处置

### 7.1 哪些能退、哪些退不了

| 对象 | 能退吗 | 怎么退 | 前提 |
|---|---|---|---|
| APK | ✅ 能 | 装回旧版本（注意 §5.3 的降级限制） | 旧包还在手里 |
| 已经升过 schema 的库 | ❌ 不能 | 装回旧版本救不了它，只会更糟；只能**前滚**（出一版带 Migration 的包） | 有包可前滚 |
| 被覆盖导入覆盖掉的数据 | ❌ 不能 | 只能拿那个 zip 重新导入 | **动手前导出过** |
| API key | 不适用（它是配置，不是数据） | 换机前在旧机上清空再重装（`SettingsScreen.kt:257-258` 🗣） | 得先知道旧机上填的是什么 |

### 7.2 事故分级与处置动作

| 级 | 什么算（判据以 `PROCESS.md` §7.2 为准） | 第一步 | 第二步 |
|---|---|---|---|
| **P0** 数据损失 / 不可逆 | 用户数据被删、被覆盖、备份不可恢复、静默清库 | **停手**：不再执行任何会写库或删文件的动作 | 让用户把**最近一次导出的包**原样留住——别改名、别移动、别解压 |
| **P1** 主线不可用 / 假话 | 录入或查找走不通；**界面上有一句话不成立**（NFR-02 违例——文案不成立按 P1，不是 P3） | 让用户回一句**屏幕上的原话**，并把当前那一屏截下来 | 判是包的问题还是数据的问题：用空数据重装一次看能不能复现 |
| **P2** 局部错误 | 某个分支不对、文案不精确、排序 / 统计偏差 | 记成缺陷（`templates/defect-report.md`） | 下一个空闲批次处理，别为它临时出包 |
| **P3** 体验与打磨 | 排版、措辞可改、无障碍缺口 | 记档 | 与相关 FR 的 `⬜` 一起排 |

三条纪律，每条都是真代价换来的：① **对用户数据有影响的动作先停手**——`pm clear`、覆盖导入、删除物品 / 位置（`REBUILD.md` §9）；② **先取证再动手**——报"丢了什么"之前先把"现在还剩什么"读一遍（`RULES.md` §16 第 1 条、`REBUILD.md` §7 C18）；③ **任何会动机上数据的脚本，动作必须在 `if __name__ == "__main__"` 守卫之下**（`REBUILD.md` §7 C16——这条是当场删掉用户一件物品换来的）。

### 7.3 硬规矩：用户数据先导出再动手

在**任何**会写库或删文件的操作之前，先让用户在应用内走一次设置页 → 备份 →「导出到文件」，并确认他说看见了那句回执。

适用场景（不限于）：覆盖导入、`pm clear`、卸载重装、换机、装一个带 schema 升级的新包、任何"试试看"的实验。理由很直接：**导出是唯一的迁移手段**（`SettingsScreen.kt:307-308` 🗣），没有第二个出口。

---

## 8. 发布后跟踪

### 8.1 `docs/CHANGELOG.md`（工作区唯一一份）

⚠️ 2026-10-10 更正：本节此前写「先分清两份同名文件：仓库根那份记软件版本、`docs/` 那份记文档集改动」——**工作区里只有 `docs/CHANGELOG.md` 一份**，根目录那份从来不存在。现在这一份同时承担两件事：**软件版本记录**（每一版给用户的变化、已知问题，发布负责人写）与**文档集改动**（谁在哪一轮改了哪份文档），按日期倒序、每批一节；发布那一节另起标题（见 §8.2）。

⚠️ 2026-10-10 更正：本节此前写「仓库根现在还没有这份文件，发布的第一次就要建」——**不要建第二份**。发布说明直接写进 `docs/CHANGELOG.md` 的新一节（标题用 `## <版本号> — <日期>（versionCode N）`），与其他节按日期倒序排在一起。格式照仓库既有的提交风格——中文一句话说清"改了什么以及代价"（`REBUILD.md` §9）：

```markdown
# 版本记录

## <版本号> — <日期>（versionCode N）
### 新增
- 拍照录入与 AI 预填；位置树；搜索与筛选；到期提醒；手动导出 / 导入。
### 已知问题
- 只写**这一版真实存在的**已知问题；已经解决的就不要再列（例如 release 签名已于 2026-10-10 由用户完成）。
```

**写入时机**：**出包之前**写，不是发出去之后补——`versionCode` 的记账位置就在那里（§2.2），发布记录（§8.2）要引用它。**只写用户看得懂的话**：不写 `文件:行`、不写内部批次号、不写任何 key（`REBUILD.md` §9）。

### 8.2 发布记录

模板 `templates/release-checklist.md`：复制一份填，建议文件名 `release-<versionName>-<日期>.md`；**别把填好的记录放回 `templates/`**，那是空白表单。发布记录至少要留下：环境快照两行（§6.4）、⭐ 七项的签收人、G0–G5 的证据、人工清单七条的回话、发布物哈希、24 小时观察项、回滚决定与签收人。

**发布物哈希**：出包后对 APK 算一次 SHA-256 记下来（Windows 上 `certutil -hashfile <apk> SHA256`，或任何手边的哈希工具），连文件名一起记；配套的签名摘要用 `apksigner verify --print-certs` 看（§3.2）。下次要判"机上装的是不是这一版"，这两样是唯一的凭据。

### 8.3 用户反馈 → 缺陷 → 修复

```
用户回一句原话
  → templates/defect-report.md（标题 / 环境 / 最小复现 / 证据 / 影响面 / 分级）
  → PROCESS.md §3.2（改一条已有行为）或 §3.1（补一条 FR）
  → 先写一条会红的测试，再改代码
  → 修完重新走 G0–G5，进下一版 CHANGELOG.md
```

- 用户念的那句**原话**是最有价值的证据：界面测试按文字找节点（`REBUILD.md` §2 第 9 条），他念的那句话常常就是要改的断言。
- 用户报"某个说法不成立"要当**缺陷**收，不当文案建议收——那是 NFR-02（`RULES.md` §16）。
- 缺陷单里**不许贴隐私**：不贴真机导出的 `data.json`、不贴含隐私的截图与 dump（`README.md` §6 第 3 条）。

### 8.4 留痕与设置页 debug 区的使用

- **只在 debug 包上**（§6.2）：设置页「AI 分析照片」那节底下有「AI 留痕：N 条（发出去的提示词与模型回应原文，只留最近 10 条）」（`SettingsScreen.kt:264-265`）。按过一次录入页的「重新分析」再回来看这一行，**条数在涨就说明留痕真落了盘**——不连 adb 也能确认 AI 那一跑发生了什么（`UI.md` §7.2 第 6 项）。
- 留痕落在 `files/ai-trace`（`AppContainer.kt:72`），**不在 `files/photos` 底下**，所以不随备份出门（`PRD.md` FR-TRC-03）；里面是用户的**位置清单**，属隐私，不许贴进缺陷单。
- 同一块 debug 区还有「调试：立刻检查一次到期提醒」（`SettingsScreen.kt:173`）：它**无视"今天已经说过"**再跑一次，验收通知链路不用等到第二天（`ReminderWorker.kt:44-51`）。

---

## 9. 发布检查清单（可直接执行版）

顺序就是操作顺序，每条都能判"做了没做"。

**准备**

1. [ ] 确认这次发的是什么：改了哪些 FR / CR，逐条写进`docs/CHANGELOG.md`（§8.1）。
2. [ ] `versionCode` +1、`versionName` 按语义化定好（`app/build.gradle.kts` 的 `defaultConfig`）（§2.2）。
3. [ ] 逐条过 ⭐ 七项（§3）并写下签收人；没做的写清为什么这次不做、什么时候做。
4. [ ] 本次动了 schema 的话：走完 §5.1 六步，`app/schemas/…/<新号>.json` 已进 git，`fallbackToDestructiveMigration(true)` **已删除**。
5. [ ] 权限复核：`AndroidManifest.xml` 只有 `INTERNET` / `POST_NOTIFICATIONS` / `CAMERA`，无存储权限（§3.4）。
6. [ ] `allowBackup="false"` 复核，设置页那句"不做云备份"逐字仍成立（§3.5）。
7. [ ] 文档同步：`PROCESS.md` §5 矩阵要动的每一份都动了；`⬜` 项都带触发条件与代价（G4）。

**构建与判绿**

8. [ ] 前台跑 `gradle :app:testDebugUnitTest`，拿到 `BUILD SUCCESSFUL`（§6.1）。
9. [ ] 数 XML：`app/build/test-results/testDebugUnitTest/*.xml` **42 份**，mtime 全部晚于本轮起跑时刻（G1）。（也可直接 `pwsh -File artifacts/tools/ci-local.ps1 -Cold`：它会替你把四道门跑完并报测试合计。）
10. [x] **CI 首次真跑** ✅ **2026-10-10 已完成**：私人仓库 `mapleafly/wheremy`，run [`38056478443`](https://github.com/mapleafly/wheremy/actions/runs/38056478443)，8m32s，四道门全 success。（首跑抓出三处问题，见 `PLAN.md` §3.3 第 9 步与 F46/F47。）以下为当时的操作步骤，换远端时重做——
    ```
    # 1) 在 GitHub 上建一个空仓库（不要勾 README/.gitignore）
    git remote add origin git@github.com:<你的账号>/<仓库名>.git
    git push -u origin main
    # 2) 去 Actions 页看这次 run：四道门（spotlessCheck → testDebugUnitTest →
    #    verifyRoborazziDebug → assembleRelease）应当全绿；红了先看「打印环境」那一步
    ```
    跑通之后把结论记进 `PLAN.md` §3.3 的 CI 行（把「仍未跑过」划掉、写上日期与 run 链接）。
    本地等价验证见 `PLAN.md` §3.3 的说明与 `artifacts/tools/ci-local.ps1`。
11. [ ] 自己数出 `tests=261 failures=0 errors=0`——**不认 exit code**（G1）。（数字随批次增长，当前 42 类 / 261 例，2026-10-10；`pwsh -File artifacts/tools/ci-local.ps1 -Cold` 会替你跑完四道门并报合计。）
12. [ ] 覆盖对账正向：`PRD.md` §7 每行都能落到真实存在的测试类，`@Test` 数与表一致（G3）。
13. [ ] 覆盖对账反向：**42 个类 / 261 例**（G3；数字随批次增长）。
14. [ ] 变异验牙：本批每一处改动都做过"改坏它 → 期望那条红"；无牙的补或删（G2）。
15. [ ] `gradle :app:assembleDebug`，确认 `app/build/outputs/apk/debug/app-debug.apk` 的 mtime 是本轮的（G0）。
16. [ ] 记下 APK 的 SHA-256（§8.2）。

**装机与人工**

17. [ ] 环境快照第 1 行：装的哪个包、装于何时（§6.4）。
18. [ ] 环境快照第 2 行：本机数据是否为空、`POST_NOTIFICATIONS` 是否还没授过（§6.4）。
19. [ ] **先导出**：设置页 → 备份 →「导出到文件」，记下回执那三个数（§7.3）。
20. [ ] `install -r` → `am force-stop` → `am start`，点开不崩（§6.3）。
21. [ ] 真机人工清单七条逐条签收（`REBUILD.md` §6）；交给用户的那份用 `templates/device-acceptance.md`
22. [x] **无障碍专项：TalkBack 焦点次序走查** ✅ **2026-10-13 用户本人走查通过**（两轮：第一轮抓出 4 处问题、第二轮复验通过；记录见 §10.7）——（`FR-PRM-05` 里唯一必须人工的一条）——交给用户的那份用 `docs/templates/talkback-walkthrough.md`；ATF 自动化只覆盖「有没有名字/触摸目标够不够大」，**次序只能靠人听**（§10.5），**不许出现内部编号**。
23. [x] 迁移演练：旧版本的包 →「合并导入」对账 →「覆盖导入」对账（§3.7）。✅ **2026-10-13 真机做过**（首版没有「上一发布版」，做的是**同版本往返 + 旧包合并**；回执与逐字节对账见 §10.8；跨版本那一半仍按 §3.7 留给 0.2.0）。
24. [x] 反面演练：把包的 `schemaVersion` 改成 `2`，导入被拒且本机数据一条没动（§5.2）。✅ **2026-10-13 真机做过**：拒绝文案与 §5.2 写的一字不差，拒绝后 `data.json` 哈希与导入前相同（§10.8）。
25. [x] 备份包自查：包里没有 key、没有任何设置项（只看条目名，**别把包内容贴进任何文档**）。✅ **2026-10-13 做过**：包内**只有 4 个条目**（`manifest.json`、`data.json`、`photos/originals/<照片>.jpg`、`photos/thumbs/<照片>.jpg`），没有任何设置文件、没有 key 文件。

**发布与跟踪**

26. [ ] 回滚预案写下来：装回哪个包、要不要先导出、谁知道 keystore 在哪（§7.1）。
27. [ ] 签收：§3 七项 + G0–G5 六门，每格有名字（§4）。
28. [ ] 归档：APK + 哈希 + keystore **位置**（只记位置，绝不记口令）。
29. [ ] 发布后 24 小时观察项定下来：能不能打开、录一条、搜一次、导出一次、到期提醒那天有没有响（§8.4）。
30. [ ] 24 小时内用户回的原话里出现异常 → 立刻开 `templates/defect-report.md`，按 §7.2 定级。

---

## 10. 本实现的签收记录 —— 0.1.0（2026-10-09，P6 收口）

> 这一节是**本实现**（不是参照实现）的发布记录。逐条对 §9 的 28 项；没做的那几项写清为什么、什么时候做。
> 判据一律是**机器产生的证据**，不是"跑过了"。
>
> ⚠️ **后续更新（2026-10-10）**：这一节是 **2026-10-09 当时**的签收快照，**内容按当时的事实保留、不回改**。
> 其中「⭐FR-RLS-01（release 签名）未完成」这一条**后来已由用户完成**（见 §3.2 与 `KEYSTORE.md`），
> 所以本节表格里凡写「调试签名」的地方都只代表 10-09 那天；**看当前状态请以 §3.2 与 `docs/CHANGELOG.md` 为准**。

### 10.1 发布物

| 项 | 值 |
|---|---|
| release APK | `app-release.apk`，**2,691,833 字节**，SHA-256 `B13EB88EDE264F77DC178F29382F7553A9A29DE06C0DCC3A2D1D49C712CE848B`（R8 + 资源收缩） |
| debug APK | `app-debug.apk`，17,380,427 字节，SHA-256 `4C1615E9C42C9B55ADEE04CCD8CA2315F9A38A798CAA87B410240F7707A293A0` |
| 版本 | `versionCode 1` / `versionName 0.1.0`；`minSdk 26` / `targetSdk 36` / `compileSdk 37` |
| 签名 | ⚠️ **调试签名**（⭐FR-RLS-01 未完成，§3.2）；能装能跑，不能作为正式交付 |
| R8 冒烟包 | `.r8smoke`（规则同 release，包名 `com.wheremy.app.r8smoke`）——因为 release 的包名与参照实现那个应用同名，**直装会顶掉用户机上那个应用** |

### 10.2 §9 清单逐条

| # | 项 | 结果 | 证据 / 说明 |
|---|---|---|---|
| 1 | 改动逐条进 `CHANGELOG.md` | ✅ | `docs/CHANGELOG.md` 六批（P0…P6b）各一节 |
| 2 | 版本号 | ✅ | `1` / `0.1.0`（首个交付版，未发布过，不需要 +1） |
| 3 | ⭐ 七项签收 | 🟡 | §3 逐条写了状态：3.1 ✅、3.2 ⬜（要用户的 keystore）、3.3–3.6 ✅、3.7 ⬜（首版无旧包） |
| 4 | schema 变更流程 | ✅ 不适用 | 本次没动 schema；`DB_SCHEMA_VERSION = 1` 且**没有** `fallbackToDestructiveMigration` |
| 5 | 权限复核 | ✅ | 自己声明 `POST_NOTIFICATIONS` + `CAMERA` + `INTERNET`，无存储权限；合并清单另有 WorkManager 四条（§2.1 已记） |
| 6 | `allowBackup="false"` | ✅ | 源码 + **release APK 清单**两处核过 |
| 7 | 文档同步 | ✅ | `PLAN.md` §10 阶段表、`DELTA.md` §3、`CHANGELOG.md`、本轮 `RELEASE.md` 全部更新 |
| 8 | 单测 `BUILD SUCCESSFUL` | ✅ | `./gradlew.bat :app:spotlessCheck :app:testDebugUnitTest :app:assembleDebug --rerun-tasks` → `BUILD SUCCESSFUL` |
| 9 | XML 全fresh | ✅ | **27 份** XML 的 mtime 全部晚于本轮起跑时刻（`freshXml=27/27`） |
| 10 | 自己数出用例数 | ✅ | **tests=175 failures=0 errors=0**（不认 exit code） |
| 11–12 | 覆盖对账正/反向 | ✅ | 27 个测试类 / 175 例（正向：`PRD.md` §7 每行都有类守，见 `DELTA.md` §2 的矩阵） |
| 13 | 变异验牙 | 🟡 | 本批做过 4 次有效验牙（期间真红过：备份往返 6 条、CameraGate 2 条、Gallery 3 条、确认页 4 条）；**没有**对每一处改动都做，如实记 |
| 14 | debug APK 是本轮的 | ✅ | `app-debug.apk` mtime 2026-10-09 12:22 |
| 15 | APK SHA-256 | ✅ | 见 §10.1 |
| 16–17 | 环境快照 | ✅ | 装的是 `com.wheremy.app.dsh`（debug）与 `com.wheremy.app.r8smoke`；库里是我们自己的演示数据（5 件合成照片物品） |
| 18 | **先导出** | ✅ | 「导出到文件」→ `wheremy-backup-2026-10-09.zip`，回执 `导出完成 · 位置 3 个 · 物品 5 件 · 照片 5 张 · 44 kB` |
| 19 | `install -r` → 启动不崩 | ✅ | 每次安装后 `am start` + `logcat -b crash` 为空 |
| 20 | 真机人工清单 | 🟡 | P0–P6 每阶段的真机项都逐条走了（截图在 `artifacts/screenshots/`）；`REBUILD.md` §6 那七条**尚未一次性签收**，留给 P6 最后一公里 |
| 21–22 | 迁移 / 反面演练 | 🟡 | 无旧包可演（首版）；反面演练（schema 更高被拒）有自动化用例 ✓ |
| 23 | 备份包自查 | ✅ | 包内条目只有 `manifest.json` / `data.json` / `photos/**`；构造参数里没有 `SettingsRepository`，key 物理上进不去 |
| 24 | 回滚预案 | ✅ | 见 §7.1：APK 可装回旧版；**已经升级过 schema 的库退不回来**（本版没有 schema 变更，风险为零）；keystore 位置待定（§3.2） |
| 25 | 签收 | 🟡 | §3 七项 + G0–G5 六门逐格有名字（见 10.3）；⭐3.2 与 G5 的人工清单待办 |
| 26 | 归档 | 🟡 | 哈希已记（§10.1）；**keystore 位置待用户的决定** |
| 27–28 | 发布后跟踪 | ⬜ | 本条要等真的装上用户日常使用才有意义 |

### 10.3 门禁 G0–G5

| 门 | 结果 | 证据 |
|---|---|---|
| **G0 构建** | ✅ | `assembleDebug` / `assembleRelease` / `assembleR8smoke` 均 `BUILD SUCCESSFUL`，三个 APK 的 mtime 都是本轮的 |
| **G1 单测全绿双证** | ✅ | `BUILD SUCCESSFUL` + **27/27** XML fresh + 自数 `tests=175 failures=0 errors=0` |
| **G2 变异验牙** | 🟡 | 见 §10.2 第 13 项：本批 4 次有效验牙，未覆盖每一处改动 |
| **G3 覆盖对账** | ✅ | 27 类 / 175 例；`DELTA.md` §2 的 87 行矩阵把每条 FR 落到具体测试类 |
| **G4 文档同步** | ✅ | 见 §10.2 第 7 项 |
| **G5 真机人工清单** | 🟡 | 分阶段真机验证都做了（含相机 HAL、选图器、通知、备份闭环、横屏、R8 冒烟）；`REBUILD.md` §6 七条的一次性签收待补 |

### 10.4 性能与体积（本轮实测，同一台机器）

| 指标 | 本实现（R8 release） | 本实现（debug） | 参照实现（记录值） |
|---|---|---|---|
| 冷启动（`am start -W` TotalTime，**5 次中位数**） | **218 ms**（215–231） | 951 ms（939–983） | 980 ms |
| APK 体积 | **2.62 MB** | 16.49 MB | 18.96 MB |

> 复测方法：`pwsh -File artifacts/tools/measure-startup.ps1 -Runs 5`（每次先 `am force-stop` 保证是冷启动，
> 报全部值 + 中位数，谁都能复跑出同一量级）。**2026-10-10 复测**：与本会话早期记录的 233 ms / 2.69 MB 相比，
> 在加完批量操作、组合筛选、自动导出、无照片手填等一批功能之后**没有回退**（反而略快）。

⚠️ 比口径要诚实：参照那个 980 ms / 18.96 MB 是**同一个手机上的侧载包**，与我们的 release 包可比；与我们的 debug 包比则不公平（debug 不混淆、带着一堆调试设施）。

### 10.5 工程侧两项评估（2026-10-10）

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

### 10.6 真机人工清单复走 —— 2026-10-12（用户本人，**7/7 通过**）

> 这一节是**这一轮**的签收，判据同样是机器/用户产生的证据。交付给用户的那份是
> `templates/device-acceptance.md`（七条，写在最前面、不出现内部编号）。

**环境快照**（模板要求的两行）：

1. 手机上装的是 **Wheremy release 包**：`com.wheremy.app`、versionName `0.1.0` / versionCode `1`、
   用**用户自己的 keystore** 签名（`apksigner` 的 SHA-256 `B070DD26…`）、**2.62 MB**；
   装的时间 **2026-10-12 15:38**（重装，故走查开始时是全新空白）；
2. 数据状态：走查开始前 **全新空白**（用户此前把两个包都删了）；通知权限：走查前 **未授过**（第 7 步里授的）。

**逐条结果**：

| # | 步骤 | 结果 | 用户回的原话（逐字） |
|---|---|---|---|
| 1 | 打开看一眼 | ✅ 通过 | —— |
| 2 | 拍一张或选一张 | ✅ 通过 | —— |
| 3 | 存进去一件 | ✅ **通过**（本轮修掉 F48 之后） | 首走时卡住：「焦点进入「名称」输入框后，不能调出键盘。填写不进去任何文字，所以也不能保存（因为名称为空）。」 |
| 4 | 再找回来 | ✅ 通过 | —— |
| 5 | 建一个位置 | ✅ 通过 | —— |
| 6 | 导出 | ✅ 通过 | 🗣「导出完成 · 位置1个 · 物品1件 · 照片1张 · 273KB」 |
| 7 | 通知这条路 | ✅ 通过 | —— |
| 8 | 回我三件事 | ✅ 已回 | 🗣「每一步都一样完成，导出回执：导出完成 · 位置1个 · 物品1件 · 照片1张 · 273KB，没有任何其他弹窗」 |

**第 6 步回执自洽性核对**：位置 **1** 个（第 5 步建的）、物品 **1** 件（第 3 步存的）、照片 **1** 张
（第 2 步拍的）——三个数字与走查动作一一对应，**自洽**。

**第 8 步的关键答复**：「**没有任何其他弹窗**」。模板把"有没有出现账号/登录/密码/验证类弹窗"
列为必答项，因为那意味着应用要了不该要的东西；答"没有"= 这条通过。

**⚠️ 本轮**没有**覆盖到的**：**AI 预填**。重装后 API key 丢失（设置页显示「还缺 API key」），
所以第 2 步走的是「没配 key」的分支：照片能拍进来、字段留空手填。AI 预填的真机验证在更早轮次做过
（`PLAN.md` §10 的 P2 行：AI 已接、真机连模型验证通过）。要本轮也覆盖，需在设置页重填 key 后再走一次第 2 步。

**判据对应**：`RELEASE.md` §9 第 21 条（真机人工清单七条逐条签收）→ ✅；第 22 条（TalkBack 焦点次序走查）
**无障碍专项已于 2026-10-13 单独走查通过**（那是与这七条并列的另一件事，见 §10.7）。

### 10.7 TalkBack 焦点次序走查 —— 2026-10-13（用户本人，**通过**）

> 这是 §9 第 22 条，也是 `FR-PRM-05` 里**唯一必须人工**的一条：
> ATF 自动化只能查「有没有名字、触摸目标够不够大」，**焦点次序与措辞只能靠人听**（§10.5）。
> 交付给用户的那份是 `docs/templates/talkback-walkthrough.md`（7 屏，不出现内部编号）。

**环境快照**：`com.wheremy.app` release 包，versionName `0.1.0`，**2.62 MB**，
SHA-256 `05D0EBD179B995570486C1D457F69CFBF8B0F21FF5B44AE2951BFDAC1743703E`，
用**用户自己的 keystore** 签名（`apksigner` 证书摘要 `8118b97d…`），装于 **2026-10-13 11:06**；
走查所用提交 = `3ae596a`。

**两轮过程**（第一轮抓出的问题都已修并复验）：

| 轮次 | 用户报的原话 | 判定 | 处置 |
|---|---|---|---|
| 第一轮（10-12） | 「talkback 的设置我完全找不到」 | **我们的文档写错了路径**：HyperOS 3 上不存在「设置 → 更多设置 → 无障碍」 | 在真机上逐屏翻出真实路径（**辅助功能 → 视觉 → 屏幕阅读器 → TalkBack**；开关叫「使用"TalkBack"」，旁边还有「"TalkBack"快捷方式」），重写走查单那一节（`PLAN.md` F49） |
| 第一轮 | 「焦点还在「位置」上，没有自动…首先读标题」 | **清单预期写错了**：焦点停在底栏标签是 Android 对底部导航的标准行为，不是缺陷；且位置页标题是「全部位置」而不是「位置」 | 各屏标题加 `heading()`、屏根加 `paneTitle`（让读屏知道「在哪一屏」）；清单写明「切屏焦点在底栏是标准行为」（F50 ③） |
| 第一轮 | 「名称（应念出必填）这一条没有念出必填」 | **真问题**：视觉是「名称 *」，星号读屏读不出来 | 加 `semantics { contentDescription = "名称，必填" }`（视觉不变，且仍是可输入框）（F50 ②） |
| 第一轮 | 「开关和标签是分离的…不知道这是什么开关」 | **真问题** | 按官方模式把整行做成一个可切换项（`toggleable(role = Role.Switch)` + `Switch(onCheckedChange = null)`），并统一成「标签在左、开关在右」（F50 ①） |
| 第二轮（10-13） | 🗣「**走查通过**」 | ✅ 通过 | —— |

**顺带做的全应用审计**（用户追问「其他页面也检查」）：全应用可切换控件只有 **2 处 `Switch`**（都在设置页，已修）；
其余筛选控件本来就是 `FilterChip`（自带 `selected` 语义）；唯一靠改文案表达状态的「只看待归类」
已按用户批准换成 `FilterChip`。审计写成了断言（读屏那棵树），见 §10.3。

**判据对应**：§9 第 22 条 → ✅。

### 10.8 迁移与反面演练 —— 2026-10-13（真机，**四条全过**）

> 对应 §9 第 23/24/25 条。**先说清这一条演的是什么**：§3.7 写明「首版没有『上一发布版』可演」，
> 所以这里演的是**同版本往返**（同一版导出的包灌回同一版）**＋旧包合并**（拿 10-12 那个更早的包走合并），
> 真正的跨版本迁移仍留给 0.2.0。设备 = 小米 13（HyperOS 3.0.309.0），提交 `3ae596a`。

**动作与回执**（每一步都是界面上真做的，回执逐字抄下来）：

| # | 动作 | 回执（🗣 原文） | 判定 |
|---|---|---|---|
| ① | **基线导出**（拿当前数据当基准） | 「导出完成 · 位置 1 个 · 物品 3 件 · 照片 2 张 · 468 kB」 | 基准 = 1/3/2 |
| ② | **合并导入**（10-12 那个更早的包） | 「合并导入完成 · 位置 0 个 · 物品 0 件 · 照片 0 张」 | ✅ 与文档写的合并语义一致（**按主键跳过本机已经有的**）——那个包里的 1 位置/1 物品主键都还在，所以全部跳过，**一件没多也一件没少** |
| ③ | **覆盖导入**（用①的基线包） | 「覆盖导入完成 · 位置 1 个 · 物品 3 件 · 照片 2 张」 | ✅ 清空重建后与基准**数字相同** |
| ④ | 覆盖导入后**再导出** | 「导出完成 · 位置 1 个 · 物品 3 件 · 照片 2 张 · 468 kB」 | ✅ 与①**一字不差** |
| ⑤ | **逐字节对账**（①的包 vs ④的包） | `data.json` 与 4 张照片（原图+缩略图）**哈希全同**；`manifest.json` 只差 `exportedAt`（导出时间戳，设计如此） | ✅ **内容完全往返** |
| ⑥ | **反面演练**：把包的 `schemaVersion` 改成 `2` 再导入 | 「合并导入失败：这个备份来自更新的版本（schema 2 &gt; 1），先升级应用再导入」 | ✅ 与 §5.2 写的文案一致，**被拒** |
| ⑦ | 被拒之后**再导出**对账 | 「导出完成 · 位置 1 个 · 物品 3 件 · 照片 2 张 · 468 kB」；`data.json` 哈希与①**相同** | ✅ **本机数据一条没动** |
| ⑧ | **备份包自查**（第 25 条） | 包内 4 个条目：`manifest.json`、`data.json`、`photos/originals/<照片>.jpg`、`photos/thumbs/<照片>.jpg` | ✅ 没有 key、没有任何设置项 |

**给下次做这件事的人（这次踩到的三个点）**：

1. **「覆盖导入」是两步**：先弹确认框（「覆盖导入会清空本机数据」→「确定覆盖导入」），**确认之后才开选择器**。
   按「点了按钮就去找文件」的直觉做会扑空。
2. **小米的文件选择器是多选式的**：点文件名只是**选中**（打勾），还要再点右下角「**确定**」才会把 URI 交给应用。
3. **用 `adb push` 放进去的文件才出现在选择器的「今天」里**；用 `adb shell cp` 复制的副本**不出现**
   （大概率没进媒体索引）。要造测试包就用 `push`。

**没做的**：跨版本迁移（首版无旧版可演，§3.7）、以及真机上的「降级安装」（§5.3 已判不可行，不做）。

### 10.9 结论

**可以发布给用户自己用（侧载）**：功能闭环、备份出口、到期提醒、相机取证都真机走过一遍，测试与截图回归都有基线。
**可以当作"给别人用的正式包"**：⭐FR-RLS-01 已由用户完成——用户自己生成了 keystore（`D:\keys\wheremy-release.jks`，别名 `wheremy-release`）并放好 `keystore.properties`，release 包已用它签名、`apksigner` 校验通过（`Config: release`）、真机安装为正式包。口令始终在用户手里，文档只记位置不记口令（`KEYSTORE.md` §12）。

---

## 附：写作时发现的差异（2026-10-07 已全部处理）

写本文时按基线 `cf73f5b` 核对了源码与现有文档。下面这些**不是本文的立场**，是核对时撞出来的差异；按 `README.md` §3.2 的裁法（先判定、再改错的那一边）**五处都已改掉**，留表作裁决留痕：

| 文档 A 位置 | 文档 B 位置 | 冲突点 | 处理 |
|---|---|---|---|
| `PRD.md` §3.8 FR-BAK-06「四条拒，一条放行」 | `REBUILD.md` §3 M5「三条拒一条放行」 | 源码 `BackupRepository.kt:149-168` 的 `validate()` 实际抛 **4** 种拒 | **判"四条"对** → `REBUILD.md` §3 M5 已改；`RULES.md` §12.3 标题已改；另在 `RULES.md` §12.3 补了一句：读包阶段还有**第五道**拒（`manifest.schemaVersion > DB_SCHEMA_VERSION`，`BackupRepository.kt:139-141`），它不在 `validate()` 里 |
| `DEVELOPMENT.md` §5.3 表最后一行："release 包只是'能装'" | 本文 §2.1 / §6.2；`PRD.md` ⭐FR-RLS-01 | "能装"不成立：`gradle :app:signingReport` 对 release 变体打印 `Config: null`（本机实跑） | **判本文对** → `DEVELOPMENT.md` §5.3 已改成"打不出能侧载的 release 包，验收期只用 debug 包" |
| `PROCESS.md` §5 同步矩阵表（第 244 行） | 同表其余各行 | 两行被并成一行（缺换行），渲染时表体错行 | **已修**：`PROCESS.md` §5 拆回两行 |
| `PRD.md` §5 ⭐FR-DB-01 那一行把注释引作「原话」 | 源码 `WheremyDatabase.kt:32` | 引文略去"破坏性迁移（尚无真实数据）"半句 | **判源码对** → `PRD.md` 已补全注释原文 |
| `README.md` §5 表格最后一行「走 `RELEASE.md` §4 清单」 | 本文 §4 / §9 | 指路偏了：§4 是门禁 G0–G5，可执行清单在 §9 | **判本文对** → `README.md` §5 已改成"逐条签收 `RELEASE.md` §9 的可执行清单（门禁与证据要求见 §4）" |
