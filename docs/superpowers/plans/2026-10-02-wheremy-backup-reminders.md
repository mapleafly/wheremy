# 备份与到期提醒（非 AI 部分）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 spec 里剩下的两块非 AI 功能做完——导出/导入备份（第 6.6 节、M6）与到期提醒（第 6.5 节、M5），让用户不用 API key 也能安全地攒真实数据。

**Architecture:** 备份走 `BackupRepository`：zip 里放 `manifest.json`（元信息）+ `data.json`（四张表的行）+ `photos/**`（原图与缩略图），导入先整体校验再在单个 Room 事务里落库，照片文件在事务提交之后才写盘，所以任何中途失败都不会留下半个库。提醒走 `ReminderEngine`（纯查询 + 聚合文案，JVM 可测）+ `ReminderWorker`（WorkManager 每日一次）+ 一个通知渠道，Android 13+ 运行时申请 `POST_NOTIFICATIONS`，被拒时降级为只在应用内标记。

**Tech Stack:** 与计划 1 完全一致：Kotlin 2.2.21 / AGP 8.13.2 / Gradle 8.14.3（PATH 上的，不生成 wrapper）/ Compose BOM 2026.06.01 / Room 2.8.5 / kotlinx-serialization-json 1.10.0（`kotlin-serialization` 插件已在 `app/build.gradle.kts` 里 applied）/ Robolectric 4.16.1。新增依赖只有 `androidx.work:work-runtime-ktx`（Task 5 才需要）。

**Spec:** `docs/superpowers/specs/2026-10-02-wheremy-design.md` 第 6.5、6.6 节；里程碑 M5/M6 在第 10 节。计划 1 的产物与约定见 `docs/superpowers/plans/2026-10-02-wheremy-core-loop.md`。

## Global Constraints

沿用计划 1 的全部约束，其中与本计划直接相关的：

- API key 只存 DataStore；**绝不写进备份文件，绝不提交进 git**。导出路径压根不读 `SettingsRepository`。
- `allowBackup="false"` 保持不动：系统级备份不该碰应用数据，导出/导入是唯一的用户可见通道。
- 图标只用 `material-icons-core` 内置项，不引入 `material-icons-extended`。
- minSdk 26、targetSdk 36、compileSdk 36，包名 `com.wheremy.app`。
- 每个任务结束前 `gradle :app:testDebugUnitTest` 必须全绿才允许提交；不生成 gradle wrapper；`adb devices` 列为空时不要用模拟器凑数。
- ViewModel 层 JVM 测试用 `Room.inMemoryDatabaseBuilder(...).setQueryExecutor(InlineExecutor).setTransactionExecutor(InlineExecutor)`（计划 1 偏差记录第 9 条）。
- **验收期间冻结 Room schema**：`@Entity` 字段不加不改，因为 Plan 1 仍开着 `fallbackToDestructiveMigration`，改了会清空测试数据。本计划确实不需要动 schema（`expireNotifiedAt` 已存在）。

## 与 spec 的有意偏离（需要你确认）

1. **备份内容用四张表的行（JSON），不是 spec 第 6.6 节写的 `wheremy.db` 文件拷贝。** 三个理由：合并模式要按 id 逐行比对，裸 db 文件做不到；`fallbackToDestructiveMigration` 还开着，旧 db 文件的 schema 版本和新代码未必对得上；行级 JSON 顺带解决了"导出必须排除设置"这条——不读 DataStore 就物理上不可能带出 key。代价是导入时要自己校验引用完整性（Task 2 里做）。
2. **提醒的通知权限在打开数据页时申请一次，而不是首次导出后。** spec 第 6.5 节只说"被拒时不崩溃、降级为应用内标记"，没定时机。放在数据页是因为那里有解释文案，用户知道自己在同意什么。

实现层还有四处偏离（照片落临时目录而不是攒内存、zip 条目名白名单、位置条目先父后子、`BackupSummary.photos` 的计数口径），都记在各自任务的"执行情况"里，不影响 spec 的任何承诺。

## 文件结构

| 文件 | 责任 | 任务 |
|---|---|---|
| `app/src/main/java/com/wheremy/app/data/db/Entities.kt` | 四个 entity 加 `@Serializable`；`ItemStatus` 同样 | Task 1 |
| `app/src/main/java/com/wheremy/app/data/db/Daos.kt` | 补 `all()`/`deleteAll()`/`upsertAll()`/`allTagRows()` | Task 1 |
| `app/src/main/java/com/wheremy/app/data/backup/BackupFormat.kt` | `BackupManifest`、`BackupData`、`ImportMode`、zip 条目名常量 | Task 1 |
| `app/src/main/java/com/wheremy/app/data/backup/BackupRepository.kt` | `export(OutputStream)` / `import(InputStream, ImportMode)` | Task 1、2 |
| `app/src/test/java/com/wheremy/app/data/backup/BackupRepositoryTest.kt` | 上述全部行为 | Task 1、2 |
| `app/src/main/java/com/wheremy/app/data/backup/BackupIo.kt` | `Uri` → 流的接缝接口 + `ContentResolver` 生产实现 | Task 3 |
| `app/src/main/java/com/wheremy/app/ui/settings/SettingsViewModel.kt` | SAF 之外的状态机：忙、成功文案、失败文案；Task 7 起多带 `leadDays` | Task 3、7（交付时叫 `ui/data/DataViewModel.kt`） |
| `app/src/main/java/com/wheremy/app/ui/settings/SettingsScreen.kt` | 导出/导入两个按钮 + 合并/覆盖选择 + key 不进备份的说明；Task 7 起上半段是到期提醒 | Task 3、7 |
| `app/src/main/java/com/wheremy/app/ui/WheremyRoot.kt` | 第 4 个 Tab 与全屏路由（Task 3 叫「数据」，Task 7 改「设置」） | Task 3、7 |
| `app/src/test/java/com/wheremy/app/ui/settings/SettingsViewModelTest.kt` | 状态机五条分支 + 覆盖导入还原 + 提前天数读写与夹取 | Task 3、7 |
| `app/src/main/java/com/wheremy/app/domain/ReminderEngine.kt` | 到期窗口查询 + 聚合通知文案（纯逻辑） | Task 4 |
| `app/src/main/java/com/wheremy/app/util/Dates.kt` | 日历天换算从 `UiFormat` 挪出来，界面与提醒共用一套 | Task 4 |
| `app/src/test/java/com/wheremy/app/domain/ReminderEngineTest.kt` | 窗口边界与文案 | Task 4 |
| `app/src/main/java/com/wheremy/app/data/work/ReminderWorker.kt` | `PeriodicWorkRequest` 每日一次，写回 `expireNotifiedAt` | Task 5 |

---

## Task 1: 备份导出

**Files:**
- Modify: `app/src/main/java/com/wheremy/app/data/db/Entities.kt`
- Modify: `app/src/main/java/com/wheremy/app/data/db/Daos.kt`
- Create: `app/src/main/java/com/wheremy/app/data/backup/BackupFormat.kt`
- Create: `app/src/main/java/com/wheremy/app/data/backup/BackupRepository.kt`
- Test: `app/src/test/java/com/wheremy/app/data/backup/BackupRepositoryTest.kt`

**Interfaces:**
- Consumes: `WheremyDatabase.locationDao()/itemDao()/photoDao()`、`PhotoStore.rootDir`、`ItemRepository`。
- Produces: `BackupRepository(db, photoStore, appVersion).export(into: OutputStream): BackupSummary`；`BackupManifest(schemaVersion: Int, exportedAt: Long, itemCount: Int, appVersion: String)`；zip 条目名 `manifest.json` / `data.json` / `photos/<相对路径>`；`SCHEMA_VERSION` 常量 = Room 的 `version`。

- [x] **Step 1: 给 entity 加 `@Serializable`**

`Entities.kt` 顶部 `import kotlinx.serialization.Serializable` 与 `kotlinx.serialization.SerialName`，四个 entity 与 `ItemStatus` 各加 `@Serializable`。Room 不看这些注解，KSP 也不会因此改变生成结果。`ItemStatus` 序列化成名字字符串（`DRAFT`/`CONFIRMED`），与库里 `TypeConverter` 存的值一致。

- [x] **Step 2: 补 DAO 的批量读写**

```kotlin
// LocationDao
@Upsert suspend fun upsertAll(nodes: List<LocationNodeEntity>)
@Query("DELETE FROM location_node") suspend fun deleteAll()

// ItemDao
@Upsert suspend fun upsertAll(items: List<ItemEntity>)
@Query("DELETE FROM item") suspend fun deleteAll()
@Query("SELECT itemId, tag FROM item_tag") suspend fun allTagRows(): List<ItemTagEntity>
@Query("DELETE FROM item_tag") suspend fun deleteAllTags()

// PhotoDao
@Query("SELECT * FROM item_photo") suspend fun all(): List<ItemPhotoEntity>
@Upsert suspend fun upsertAll(photos: List<ItemPhotoEntity>)
@Query("DELETE FROM item_photo") suspend fun deleteAll()
```

`allTagRows` 而不是 `allTags`：`ItemDao` 里已经有一个 `allTags(): List<String>` 给标签筛选条用，同名不同返回类型会在 JVM 上撞签名。

- [x] **Step 3: 写失败测试——导出的 zip 里有元信息、行和照片，且没有 key**

见 `BackupRepositoryTest.exportWritesManifestRowsAndPhotosWithoutTheKey`：先 `locations.add(null, "家")` + `items.newDraft(jpeg())` + `items.confirm(...)`，把 `SettingsRepository` 里的 key 设成一个可搜索的哨兵串，导出到 `ByteArrayOutputStream`，再用 `ZipInputStream` 读回：`manifest.json` 的 `itemCount` 等于物品数；`data.json` 反序列化回来的四张表行数与库里一致；`photos/originals/<uuid>.jpg` 的字节与原文件逐字节相等；整包里任何一条文本都不含那个哨兵串。

- [x] **Step 4: 跑测试确认失败**

Run: `gradle :app:testDebugUnitTest --tests "com.wheremy.app.data.backup.BackupRepositoryTest" --console=plain`
Expected: 编译失败，`Unresolved reference: BackupRepository`。

- [x] **Step 5: 实现 `BackupFormat.kt`**

```kotlin
package com.wheremy.app.data.backup

import kotlinx.serialization.Serializable

const val ENTRY_MANIFEST = "manifest.json"
const val ENTRY_DATA = "data.json"
const val PHOTOS_PREFIX = "photos/"

@Serializable
data class BackupManifest(
    val schemaVersion: Int,
    val exportedAt: Long,
    val itemCount: Int,
    val appVersion: String,
)

@Serializable
data class BackupSummary(
    val locations: Int,
    val items: Int,
    val photos: Int,
    val bytes: Long,
)
```

- [x] **Step 6: 实现 `BackupRepository.export`**

要点：`data.json` 装 `BackupData(locations, items, photos, tags)`；照片按 `ItemPhotoEntity.filePath`/`thumbPath` 从 `photoStore.rootDir` 读出来，读不到就跳过并在 `BackupSummary` 里如实少算，**不要**因为一张丢了的图整个导出失败；`ZipOutputStream` 用 `DEFLATED`，写完 `finish()` 但不要把调用方的流关掉（SAF 的 `OutputStream` 由调用方 `use`）。

- [x] **Step 7: 跑测试确认通过**

同上命令，Expected: PASS。

- [x] **Step 8: 提交**

```bash
git add app/src/main/java/com/wheremy/app/data app/src/test/java/com/wheremy/app/data/backup
git commit -m "feat: 备份导出（manifest + 行数据 + 照片，不含 key）"
```

### Task 1 执行情况（2026-10-02）

- 落地名字与计划略有出入：常量叫 `DB_SCHEMA_VERSION`，放在 `data/db/WheremyDatabase.kt`（`@Database(version = DB_SCHEMA_VERSION)` 直接引用它，避免包里的号和真实 schema 两处各写一遍）；`BackupSummary` 不带 `@Serializable`，它只给 UI 看不进包；两个测试叫 `exportContainsManifestDataAndPhotoFiles` 与 `exportNeverCarriesTheApiKey`。
- `ZipOutputStream` 包在一个不关调用方流的 `CountingOutputStream` 里，顺带把 `bytes`（zip 真实体积）统计出来；`close()` 只 flush。
- Task 1 与 Task 2 合在同一个提交里：两者改的是同一个 `BackupRepository.kt`，拆开提交会先留下一个只有导出版本的文件。

---

## Task 2: 备份导入（合并 / 覆盖 / 回滚）

**Files:**
- Modify: `app/src/main/java/com/wheremy/app/data/backup/BackupRepository.kt`
- Test: `app/src/test/java/com/wheremy/app/data/backup/BackupRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 1 的全部产物、`db.withTransaction { }`（`androidx.room:room-ktx` 已在依赖里）。
- Produces: `BackupRepository.import(from: InputStream, mode: ImportMode): BackupSummary`；`enum class ImportMode { MERGE, OVERWRITE }`；`class BackupFormatException(message: String) : Exception(message)`。

- [x] **Step 1: 写失败测试——合并模式按 id 跳过已存在**

本机先造 A、B 两件物品并导出；然后只删掉 A、新增 C，再 `import(bytes, MERGE)`：断言 B 没被复制成两份（`itemDao.all().size` 仍然是 2 + 1 件新的），A 回来了，C 还在，且备份里 B 的 `updatedAt` 没覆盖本机被改过的值。

- [x] **Step 2: 写失败测试——覆盖模式清空一切且删掉旧照片文件**

本机造一件备份里没有的物品 X（带照片），`import(bytes, OVERWRITE)` 后 `items.get(x)` 为 null，且 X 的照片文件在磁盘上消失，而备份里的物品与照片都在。

- [x] **Step 3: 写失败测试——坏包整库不动**

三种坏法各一条：`data.json` 不是合法 JSON、`manifest.schemaVersion` 比当前大、物品的 `locationId` 指向备份里不存在的位置。三条都要 `BackupFormatException` 且断言抛出前后 `itemDao.all()` 完全相同（这是 spec 第 6.6 节"不留半个库"的可执行定义）。

- [x] **Step 4: 跑测试确认失败** → `import` 尚未实现。

- [x] **Step 5: 实现 import**

顺序固定，每一步都可能失败但只有第 3 步之后才碰磁盘：

1. `ZipInputStream` 读全三样东西进内存（照片先攒在 `Map<String, ByteArray>`），任何解析问题立刻 `throw BackupFormatException(可读中文)`。
2. 校验：`schemaVersion <= SCHEMA_VERSION`；`items.locationId` 必须为 null 或在 `locations` 里；`photos.ownerId` 必须指向存在的 item。
3. `db.withTransaction { if (OVERWRITE) { 四张表 deleteAll() } ; 过滤已存在 id（MERGE）; upsertAll(...) }`。
4. 事务成功后才落照片文件：`OVERWRITE` 先把 `photoStore.rootDir` 下不在导入清单里的文件删掉，再把攒的字节写出。
5. 照片写失败只在 `BackupSummary` 里体现条数，不回滚库——spec 第 8 节已经定了"DB 是唯一真相源"，丢文件是缩略图空白而不是丢物品。

- [x] **Step 6: 跑测试确认通过**

- [x] **Step 7: 提交** `feat: 备份导入，合并/覆盖两种模式与失败回滚`

### Task 2 执行情况（2026-10-02）

四处按计划实现会出问题，都改了并在测试里钉住：

1. **照片不攒内存。** Step 1 原文是"照片先攒在 `Map<String, ByteArray>`"，实际改成边读边解到 `rootDir/.incoming/`，事务成功后再 `renameTo` 搬进正式目录（搬不动退化成复制），`finally` 删掉临时目录。几百张照片按原方案就是几十 MB 堆内存，中途 OOM 会让导入直接崩在半路。
2. **条目名白名单。** `import` 解的是来路不明的 zip，`photos/evil.sh`、`photos/../evil.sh`、`photos/originals/sub/x.jpg` 都会写盘。现在只接受 `originals/<名>` 或 `thumbs/<名>` 且恰好一个 `/`、不含 `..`。`PhotoStore` 因此把两个目录名开放成 `PhotoStore.DIR_ORIGINALS` / `DIR_THUMBS`（覆盖模式清理孤儿文件也要用）。测试：`rejectsPhotoEntriesOutsideTheTwoPhotoDirs`。
3. **位置条目必须先父后子。** 导出用的 `all()` 没有 `ORDER BY`，顺序不可控，而 SQLite 的外键是逐条语句立刻检查的——孩子先写就 `SQLiteConstraintException`。`importToleratesLocationsListedChildFirst` 用 `locations.reversed()` 复现了这条红的，实现里加 `parentsFirst()` 做拓扑排序（成环就原样交给外键拒），并把落库阶段的 `SQLiteConstraintException` 统一包成可读的 `BackupFormatException`。
4. **`BackupSummary.photos` 在导入侧是落库的照片行数，不是文件数。** 单个文件搬不进去只是缩略图空白，不改计数也不回滚（spec 第 8 节：DB 是唯一真相源）。Step 5 第 5 点原文写的是"在 `BackupSummary` 里体现条数"，那句只在导出侧成立（导出时行数就是文件是否存在的判断）。

顺带一条测试基建的坑：`exportNeverCarriesTheApiKey` 会往 DataStore 写哨兵 key，而 Robolectric 的 `filesDir` 是跨测试类共用的，跑全量时把 `AppContainerTest` 里两条"没配 key"的断言带红了。现在那个测试在 `finally` 里把 key 清掉。

---

## Task 3: 数据页与 SAF 接线

**Files:**
- Create: `app/src/main/java/com/wheremy/app/ui/data/DataViewModel.kt`
- Create: `app/src/main/java/com/wheremy/app/ui/data/DataScreen.kt`
- Modify: `app/src/main/java/com/wheremy/app/ui/WheremyRoot.kt`
- Modify: `app/src/main/java/com/wheremy/app/di/AppContainer.kt`
- Test: `app/src/test/java/com/wheremy/app/ui/data/DataViewModelTest.kt`

**Interfaces:**
- Consumes: `BackupRepository`、两个 SAF contract（`ActivityResultContracts.CreateDocument("application/zip")`、`ActivityResultContracts.OpenDocument()`）。
- Produces: `DataUiState(busy: Boolean, message: String?, error: String?, pendingImport: ImportMode?)`，`DataViewModel.exportTo(uri)` / `pickImportFile()` / `importFrom(uri, mode)`。

- [x] **Step 1: 写失败测试**：`exportTo` 前后 `busy` 复位、成功文案含物品数；`importFrom` 抛 `BackupFormatException` 时 `error` 是那句中文而 `busy` 仍然复位（这是 Task 2 里 `guard` 模式的复用）。

- [x] **Step 2: ViewModel**，SAF 的 `Uri` → `contentResolver.openOutputStream/openInputStream` 一律 `withContext(Dispatchers.IO)`，与 `CaptureFlow.io` 同一个理由。

- [x] **Step 3: UI**：底栏第 4 项 `Icons.Filled.Settings`，label「数据」。页面上四行字必须原样出现：
    - 「导出是一个 zip 包：位置树、物品、照片。存到你自己选的位置。」
    - 「**备份文件里不含 API key，也不含任何设置。**」
    - 「导入：合并=已有的条目原样保留，只补没有的；覆盖=先清空再导入。」
    - 「应用已被系统设置为不做云备份（`allowBackup=false`），导出是你唯一的迁移手段。」

- [x] **Step 4: 全量单测 + assembleDebug 全绿** → 提交 `feat: 数据页，SAF 导出与导入入口`

### Task 3 执行情况（2026-10-02）

与计划不同处，都是实现时发现的接线问题：

- **`BackupIo` 接口是计划外的一层。** Step 2 原话是让 ViewModel 直接调 `contentResolver`，那样 ViewModel 就得拿到 `Context`，本项目的 ViewModel 一律不持 `Context`（`WheremyRoot` 用 `appViewModel` 造它们）。改成把"打开流"抽成 `data/backup/BackupIo.kt` 的接口，生产实现 `ContentResolverBackupIo` 只是胶水，单测用内存流假实现，状态机的五条分支全在 JVM 里跑完。`ContentResolverBackupIo` 本身没单测覆盖——它只有两行，且要真 `ContentResolver` 才有意义，留给人工验收。
- **`openOutputStream` 直接用框架返回的 `FileOutputStream`。** 中途试过 `ParcelFileDescriptor` + `FileOutputStream(descriptor.fileDescriptor)`，那是把简单事绕复杂了，`use { }` 关流本身就够。
- **`DataUiState` 没有 `pendingImport`，ViewModel 没有 `pickImportFile()`。** 选文件是 UI 行为：`OpenDocument` 的回调在 composable 里，模式用 `rememberSaveable` 存在屏幕上，重建能续上。把模式塞进 ViewModel 只是让状态多绕一趟。
- **`OpenDocument` 的类型过滤器是 `arrayOf("*/*")`。** 备份包是 zip，但 SAF 的 MIME 由提供方决定，用户在文件管理器里选到的常是 `application/octet-stream`；过滤收紧只会让人选不到自己的包，而内容对不对由 Task 2 的 zip 校验兜底——选错了会得到可读中文而不是静默。
- **覆盖导入有独立的确认对话框。** 计划只说"合并/覆盖选择"，但覆盖会先清空本机数据，属破坏性动作，所以按下去先问一次再开选择器。
- 单测新增 `DataViewModelTest` 5 例（导出计数、打不开文件、覆盖导入还原 id、坏包给可读错误、消息一次性消费）；`Room` 在这里额外开了 `allowMainThreadQueries()`：`InlineExecutor` 让 `withTransaction` 就地跑，而 Robolectric 下测试线程即主线程，Room 的检查会把"导入用了事务"变成假异常。

---

## Task 4: 到期窗口查询与聚合文案

**Files:**
- Create: `app/src/main/java/com/wheremy/app/domain/ReminderEngine.kt`
- Modify: `app/src/main/java/com/wheremy/app/data/db/Daos.kt`
- Test: `app/src/test/java/com/wheremy/app/domain/ReminderEngineTest.kt`

**Interfaces:**
- Consumes: `ItemDao.expiringBetween(from, to, leadDays)`、`SettingsRepository.current().reminderLeadDays`。
- Produces: `data class ReminderPlan(count: Int, nearestDays: Int?, body: String)`、`ReminderEngine(items, leadDays).plan(now: LocalDate): ReminderPlan?`（没有到期项返回 null）。

- [x] **Step 1: 写失败测试**：lead=3 时，4 天后到期的不算、3 天后与 1 天后到期的算、已过期 2 天的也要算（spec 第 6.5 节的窗口是 `now .. now + lead`，但"已经过期却从没提醒过"的物品继续沉默是错的，这条记一笔偏离）；文案形如「3 件物品临近到期，最近的还有 1 天」。

- [x] **Step 2: DAO**：
```kotlin
@Query("SELECT * FROM item WHERE expiresAt IS NOT NULL AND status = 'CONFIRMED' " +
    "AND (expireNotifiedAt IS NULL OR expireNotifiedAt < :windowStart) " +
    "AND expiresAt <= :windowEnd ORDER BY expiresAt LIMIT 50")
suspend fun expiringBy(windowStart: Long, windowEnd: Long): List<ItemEntity>
```

- [x] **Step 3: 纯函数实现 + 测试转绿**，时间全部走 `LocalDate`，与 `UiFormat.daysUntil` 同一套换算，避免"今天到期"在两个模块里差一天。

- [x] **Step 4: 提交** `feat: 到期窗口查询与聚合文案`

### Task 4 执行情况（2026-10-02）

- **DAO 名字是 `expiringBy(windowStart, windowEnd)`**，与 Interfaces 段写的 `expiringBetween(from, to, leadDays)` 不同：提前天数是文案层面的事，SQL 只需要两个毫秒边界，把 `leadDays` 塞进 DAO 反而让查询知道得太多。Step 2 的 SQL 原样落地。
- **额外加了 `ItemDao.markExpireNotified(ids, notifiedAt)`**：Task 5 要回写 `expireNotifiedAt`，而本任务才是动 `Daos.kt` 的那个，留在 Task 5 里改同一个文件会多一次无谓提交。
- **`ReminderPlan` 多带一个 `ids`，`nearestDays` 改成非空 `Int`。** `plan()` 无结果时返回的是 `null`，所以"有结果却没有 nearestDays"这个状态本来就不存在，`Int?` 只会让 Task 5 多写一次判空；`ids` 是 Worker 回写 `expireNotifiedAt` 的范围——数了哪 20 件就只该标记那 20 件。
- **`localStartOfDay`、`daysUntil` 等日期换算从 `ui/common/UiFormat.kt` 移到了新的 `util/Dates.kt`。** Step 3 要求提醒与界面用同一套换算，而 `domain` 反过来 import `ui.common` 是层级倒置；移过去顺带把 `UiFormatTest` 里的日期用例拆成 `DatesTest`，`UiFormat.kt` 只留文案（`expireHint` / `formatBytes` / `failureText`）。受影响的界面只是改 import：`ItemRow`、`DetailScreen`、`GalleryScreen`、`ReviewScreen`。
- **窗口上界取"第 N 天"的最后一毫秒**，`expiresAt <= localStartOfDay(now+lead+1) - 1`。直接 `<= localStartOfDay(now+lead)` 会把当天到期但存了带时刻的值漏掉（备份包导入的行未必落在本地零点）。
- **`LIMIT 50` 让 `count` 是上限内的条数**，超过 50 件时通知会写"50 件"。个人物品一次积压到 50 件本身就该先清库，不为这个把整张表读进内存；`countStopsAtTheQueryCap` 把这条钉住。
- 偏离 spec 第 6.5 节的一处已按计划记录：窗口不设下界，所以"早就过期、从没提醒过"的物品会进来并被说成"到期或已过期"。**这条的附带后果已在评审修正里改掉**（见文末「评审修正」第 3 条）：当时留的判断题——过期又没人处理的东西每天被重新提醒一次是否太吵——我按"太吵，而且会把 LIMIT 50 占满"改了，现在过期物品只说一次。
- 新增单测：`ReminderEngineTest` 7 例（窗口边界、DRAFT 与空日期排除、过期未提醒、今天已提醒不重复、昨天提醒今天再来、lead=0、LIMIT 上限），`DatesTest` 3 例。全量 172 例全绿。

---

## Task 5: ReminderWorker 与通知

**Files:**
- Create: `app/src/main/java/com/wheremy/app/data/work/ReminderWorker.kt`
- Create: `app/src/main/java/com/wheremy/app/data/work/ReminderScheduler.kt`
- Modify: `app/build.gradle.kts`、`gradle/libs.versions.toml`、`app/src/main/AndroidManifest.xml`、`app/src/main/java/com/wheremy/app/WheremyApp.kt`、`app/src/main/java/com/wheremy/app/di/AppContainer.kt`
- Test: Robolectric 下直接调 `ReminderWorker.doWork()`，断言通知文案与 `expireNotifiedAt` 回写

新依赖：`androidx.work:work-runtime-ktx`（取 Aliyun 镜像上 compileSdk 36 能收的最新版；本机 Gradle 走 `D:\Lib\gradle-repository`，装不上就停在这一步说明需要人工换源）。`RECEIVE_BOOT_COMPLETED` 由 WorkManager 自己声明，不手写 `BootReceiver`。

- [x] 渠道 `wheremy_expire`（`IMPORTANCE_DEFAULT`，名字「到期提醒」），只在 `WheremyApp.onCreate` 里创建一次。
- [x] `POST_NOTIFICATIONS` 加进 manifest；`DataScreen` 里点一次申请，被拒时页面显示"通知未开启，到期只在本页标记"，`doWork()` 检查权限后跳过 `notify` 但仍回写 `expireNotifiedAt`（否则每天重复劳动）。
- [x] 唯一通知 id 固定 `1001`，个人物品提醒没必要每条一个。
- [x] 点击 `setContentIntent` 打开照片墙的待归类之外再加一条筛选——本计划先落到默认 Tab，筛选参数留给计划 3（记一笔偏离）。
- [x] 全量单测全绿 → 提交。

### Task 5 执行情况（2026-10-02）

依赖这一关没触发：`androidx.work:work-runtime-ktx:2.10.1` 与 `androidx.work:work-testing:2.10.1` 都能从 Aliyun 的 google 镜像取到，写进 `gradle/libs.versions.toml`（`work = "2.10.1"`）。

- **测试用 `TestListenableWorkerBuilder.from(app, ReminderWorker::class.java).build()`，不是 `new ReminderWorker(app, WorkerParams(...))`。** 2.10.1 的 `WorkerParameters` 只有一个 12 参公开构造器（要 `TaskExecutor`、`ProgressUpdater` 这些实现类型），计划里设想的 `WorkerParams.Builder` 在这个版本根本不存在——`javap` 过 `work-runtime-2.10.1.aar` 才确认。`work-testing` 就是官方给的那条路，加进 testImplementation 比手搓参数便宜也不会踩到内部 API。
- **排班调用的位置从 `WheremyApp.onCreate` 挪到 `MainActivity.onCreate`。** WorkManager 的自动初始化走 `androidx.startup` 的 `InitializationProvider`，它和 `Application.onCreate()` 谁先谁后不值得赌；`enqueueUniquePeriodicWork(..., KEEP, ...)` 每次启动调一次的代价可以忽略。通知渠道仍然只在 `WheremyApp.onCreate` 建一次（它不依赖 WorkManager）。
- **渠道常量、`ensureChannel`、`canNotify`、发通知的实现都放在 `ReminderWorker.Companion`**，没按计划之外的第三个文件走：这个类只有 Worker 和 DataScreen 两个调用方，多一个文件只是多一处要跳着看的地方。
- **小图标用框架的 `android.R.drawable.ic_popup_reminder`。** 这个仓库目前没有任何 `drawable/` 或 `mipmap/` 资源（manifest 里也没写 `android:icon`），不为一颗通知图标开一套资源目录。
- **`canNotify` 先判 `SDK_INT < 33` 再查权限**，而不是直接 `checkSelfPermission`：13 以下 `POST_NOTIFICATIONS` 不是平台权限，查询结果取决于 ROM，判 SDK 更可靠。`uses-permission` 没加 `maxSdkVersion`。
- **DataScreen 的文案改成「通知未开启，到期只在应用内标记」**，比计划里的"只在本页标记"更贴近实际降级路径（照片墙与详情页本来就在显示到期倒计时）。另加一行「到期提醒每天检查一次，几件物品也只发一条聚合通知。」，验收时好对照。
- 新增单测 `ReminderWorkerTest` 3 例（@Config sdk=34，直接调 `doWork()`）：发一条聚合通知并回写 `expireNotifiedAt`、被拒权限时不发通知但仍然回写、没到期的东西什么都不做。全量 175 例全绿，`assembleDebug` 通过，合并后的 manifest 里 `allowBackup="false"` 保持不动。

---

## Task 6: 非 AI 验收清单增补

- [x] 计划 1 的 A 段之外补 5 条：导出→卸载重装→导入能全回来；导入到一半断（改坏 zip）不丢原数据；覆盖模式把旧物品清干净；到期 3 天内的物品能收到一条聚合通知；关掉通知权限不崩溃。
- [x] `docs: 备份与提醒手工验收` 提交。

### Task 6 执行情况（2026-10-02）

落在计划 1 的 Step 3 里新增 **C 段（6 条）**，紧接 B 段之后：拆成 6 条而不是 5 条，是因为"导出的包里没有 key"这件事值得单独解开 zip 看一眼（第 1 条的后半段），而"卸载重装能全回来"必须走合并导入那条路。每条都写了失败时先看哪个模块，以及**没有 adb 时哪两条测不了要如实标注**——第 5、6 条要等 `ReminderWorker` 真跑一次，本机现在 `adb devices` 是空的。

## Task 7: 设置页（提醒提前天数 + 通知引导）

**Files:**
- Rename: `app/src/main/java/com/wheremy/app/ui/data/DataViewModel.kt` → `ui/settings/SettingsViewModel.kt`
- Rename: `app/src/main/java/com/wheremy/app/ui/data/DataScreen.kt` → `ui/settings/SettingsScreen.kt`
- Rename: `app/src/test/java/com/wheremy/app/ui/data/DataViewModelTest.kt` → `ui/settings/SettingsViewModelTest.kt`
- Modify: `app/src/main/java/com/wheremy/app/ui/WheremyRoot.kt`

**Interfaces:**
- Consumes: `SettingsRepository.current()/setReminderLeadDays(Int)`、已有的 `BackupRepository`/`BackupIo`、`ReminderWorker.canNotify(Context)`。
- Produces: `SettingsUiState(leadDays: Int, busy: Boolean, message: String?, error: String?)`、`SettingsViewModel.setLeadDays(Int)`、`SettingsScreen(vm)`。

- [x] **Step 1: 写失败测试**：初值来自存储而不是写死的 3；`setLeadDays(30)` 之后存储与界面都显示 30；`setLeadDays(500)` 与 `setLeadDays(-5)` 显示的是**仓库夹取之后**的 90 与 0。
- [x] **Step 2: ViewModel**：`init` 读一次存储，写入走 `settings.setReminderLeadDays` 之后再读回来更新状态。
- [x] **Step 3: UI**：`ui/data/` 两个文件 `git mv` 到 `ui/settings/`，上半段新增「到期提醒」（± 按钮 + 五个预设 + 一行说明 + 从备份段搬上来的通知引导），底栏第 4 个 Tab label 改「设置」。
- [x] **Step 4: 全量单测 + assembleDebug 全绿** → 提交 `feat: 设置页，到期提醒提前天数与通知引导`

### Task 7 执行情况（2026-10-02）

这个任务不在计划 2 的六条里，是计划 1 的 C 段自己把它逼出来的：那条清单写着"调一调提前天数再看窗口"（spec 第 9 节 M5 的验收步骤也是这一句），而这个仓库当时**没有任何界面能改 `reminderLeadDays`**——测不了就等于没实现。所以做的范围按那句话倒推，不多不少。

- **「数据」页并成「设置」页，没有新开第 5 个底栏 Tab。** spec 第 6.5 节的原话是"设置页显示引导"，第 10 节 M5 列的也是设置页；而 `material-icons-core` 里能勉强表达"备份"的图标只有 `Icons.Filled.Settings` 一颗（Task 3 就是用它当「数据」页图标的）。再加一个 Tab 只会让底栏出现两颗齿轮含义不清，所以提醒段与备份段合成一页，类名与包名跟着改。测试代码是 `git mv` 挪的，一行没重写。
- **夹取上限是 0..90，spec 第 9 节写的验收步骤是"调到 365"。** 这条我没改：`SettingsRepository.setReminderLeadDays` 早就定了 `coerceIn(0, 90)`（计划 1 里定的，理由是每日聚合通知提前一年开说没意义）。要么把上限放开、要么把那句验收改掉，是产品判断，等你回来定；现在界面在 90 处点「＋」不会再涨，行为与提示是一致的，不算隐性失败。
- **状态显示的是存储里真正生效的数，不是 VM 自己算的。** `setLeadDays` 走完 `settings.setReminderLeadDays(days)` 之后再 `settings.current()` 读回来更新状态。夹取规则只存在 `SettingsRepository` 一处，VM 里再写一遍 `coerceIn` 就是两个真相。**这条已被第三轮评审改掉**：读回在连点时会把新值踩成旧值（`expected:<8> but was:<7>`），现在 VM 用 `SettingsRepository.LEAD_DAYS_MIN/MAX` 这两个共享常量先夹好显示、不再读回，见文末「第三轮评审修正」第 5 条。
- **改完天数不需要重新排班。** `ReminderWorker.doWork()` 每次运行都现读 `settings.current().reminderLeadDays`（`ReminderWorker.kt:29`），排班只是"每天一次"这个频率。验收时别误以为要重启应用才生效。
- **`Dispatchers.setMain(UnconfinedTestDispatcher())` 治不了 DataStore。** 第一次跑这两条测试红的：`vm.setLeadDays(30)` 之后立刻 `assertEquals(30, ...)` 读到的是 7，`setLeadDays(500)` 读到 3。原因不在 `Main` 调度器——`viewModelScope` 里的协程确实就地起跑了，但它挂起在 DataStore 自己的 IO 线程上，`runTest` 的虚拟时间看不见那条线程，也就"快进"不了。原本想靠"先断言 `settings.current()` 让它挂起一次"糊过去，那次挂起并不等 VM，所以只是把竞态藏深了一层。现在改成 `awaitLeadDays()`：在真实时钟上 `withTimeout(2_000) { vm.state.first { it.leadDays == days } }`，等的是**可观测结果**而不是时间——写成了立刻返回，写不成 2 秒超时。之后那句 `assertEquals(30, settings.current())` 才是在断言持久化。
- **DataStore 的文件在 Robolectric 的临时目录里跨测试类存活**，`tearDown` 必须把 `reminderLeadDays` 还原成默认 3，否则这里写进去的 90 会让 `ReminderWorkerTest` 那条"按提前 3 天算窗口"的用例变红。这个坑计划 2 Task 2 的记里已经踩过一次（key 哨兵），这次是第二个字段。
- **单测 11 例**（备份 9 例搬过来 + 新增提前天数 2 例），全量 **25 个类 / 188 例全绿**，`assembleDebug` 通过，`app-debug.apk` 14.4 MB。Room schema 仍然一个字没动。
- **刻意没做**：API key / baseUrl / model 三个输入框。你说过"先不做 AI 分支"，而设置页是它们唯一的自然落点——现在这页先只装不依赖 key 的两件事，B 段照旧阻塞（计划 1 的 B 段说明已同步改成这个说法）。

---

## 自查（写完之后）

- spec 覆盖：第 6.5 节 → Task 4/5/7；第 6.6 节 → Task 1/2/3；第 8 节"DB 唯一真相源" → Task 2 Step 5 第 4 点；第 10 节 M5/M6 中非 AI 的两项 → Task 4/5 与 Task 1–3；第 9 节"调提前天数"那步 → Task 7。
- 未覆盖且刻意不做：设置页里的 API key / baseUrl / model 三个输入（AI 分支，你按下了暂停，Task 7 记了理由）、CameraX 取景、位置排序与位置拍照、真实 Migration、EXIF。

## 评审修正（2026-10-02，Task 1–6 四个提交之后）

对计划 2 的五个代码提交（`84997a2` 起到 `5bb9147`）做了一轮独立评审，回来九条。逐条对着代码复核之后改了八条、驳回一条。
**全量 186 例全绿**（比 Task 5 结束时多 11 例），`assembleDebug` 通过。Room schema 一个字没动，验收期间的冻结仍然有效。
C 段手工清单也跟着长了一条（"过期只说一次"必须被看得见），现在 7 条，另外把第 6 条补成"在系统设置里开关通知后回到数据页，提示要跟着变"。（紧接着的 Task 7 把这页并成「设置」，那句里的"数据页"现在叫设置页；C 段又加了一条提前天数，现在是 8 条。）

- **导出的"包大小"读早了。** `sink.written` 原来在 `ZipOutputStream.use{}` 里取，而每个条目的 data descriptor 和结尾的中央目录都是 `close()` 才写的，所以数据页上多半显示"0 字节"。改成 `use{}` 返回之后再 `summary.copy(bytes = sink.written)`。`exportCountsTheWholePackage` 把它钉成"等于底层流实际收到的字节数"，不再是一个大概。
- **导入那个字节数是假的，现在不报了。** 从 `InputStream` 上只能数到解压引擎恰好拉过的块，比包本身小一截，显示出来会让人怀疑备份缺内容。`CountingInputStream` 整个删掉，`BackupSummary.bytes` 在导入路径上是 0，`DataViewModel.describe` 见 0 就只报条数。
- **过期物品从"每天重复推送"改成"只说一次"。** 原谓词 `expireNotifiedAt < 今天零点` 让一件没人处理的过期货每天都被重新提醒一次，而且它们 `ORDER BY expiresAt` 排在最前，久了会把 `LIMIT 50` 全占满、让新到期的物品一条也挤不进来——这比吵更糟。现在是 `(expireNotifiedAt IS NULL) OR (expireNotifiedAt < windowStart AND expiresAt >= windowStart)`：过期的只说一次，还在窗口里的（含到期当天）保持"本次窗口内没说过就说"。新增 `overdueIsAnnouncedOnceThenGoesQuiet` 与 `silentOverdueItemsDoNotCrowdOutFreshOnes`。这就是 Task 4 留给你的那道判断题，我按"太吵 + 会挤占"定了；要改回每天一条只需把 `AND expiresAt >= :windowStart` 去掉。
- **"N 件物品今天到期"在只有一件是今天的时候是假话。** 文案改成数得出今天那几件：全今天仍是「2 件物品今天到期」，混着别的则说「3 件物品临近到期，其中 1 件今天到期」。两条新单测。
- **第三方打包工具写的目录条目不再被当攻击拒掉。** `photos/originals/` 这种以 `/` 结尾的条目既不是文件也不是路径穿越，原来会撞上文件名白名单、整次导入失败。现在先跳过再判。
- **非 `BackupFormatException` 的异常不再把英文句子贴给用户。** `IOException("disk full")` 之前会原样出现在 snackbar 里。`operate()` 现在每种操作各带一句中文下一步（导出：磁盘满/没权限、本机数据没受影响；导入：读到一半就断了、已取消、本机数据没动），只有 `BackupFormatException` 的消息（本来就是给人写的中文）才透传。
- **导出的四张表现在在同一个事务里读。** `ItemRepository.newDraft` 是"先写物品、再写照片"两条语句，中间读就会导出一张所属物品不在包里的照片行——那种包导入时被 `validate` 判成损坏，等于用户手里的备份是坏的。改成 `db.withTransaction { BackupData(...) }` 拿快照，zip 写在事务之外。
- **照片搬运失败仍然吞，但理由写进注释了。** 评审说 `moveStagedPhotos` 里 `runCatching` 会静默丢照片；确认是有意为之，不改：spec 第 8 节定了 DB 是唯一真相源，而那些字节仍完好地躺在用户选的 zip 里，重新导入一次就会再搬一遍。
- **通知权限不再被 `rememberSaveable` 冻住。** 在系统设置里手动开关通知不会走应用内的授权回调，原来页面会一直显示过期的"通知未开启"。现在用 `LifecycleEventObserver` 在 `ON_RESUME` 重新查一次。
- **驳回一条**：评审说数据页的 snackbar 是"Indefinite"。复核 `WheremyRoot.kt` 那段注释，讲的是 `showSnackbar` 挂起期间宿主由哪个界面持有，与 duration 无关；`LaunchedEffect` 里消费完就 `consumeMessage()`，不构成缺陷。（**第三轮评审把这句改判了**：真正的问题不在 duration，在 `message` 与 `error` 共用一个 `LaunchedEffect` 的 key，见文末第 7 条。）

## 第三轮评审修正（2026-10-02，提醒语义与界面状态）

两份独立只读报告，逐条对着代码复核，确认属实的都改了，提交 `1a023c6`。测试 191 → **199**；随后录入页「换一张」那一簇（计划 1 的 `5b30ee1`）到 **207 例 / 25 个类，0 失败 0 跳过**，`assembleDebug` 通过，`app-debug.apk` 约 14.4 MB。Room schema 仍然冻结，一个字没动。

这一节里有三条**推翻上面「评审修正」小节的旧结论**，我在旧条文上标了"已被第三轮改掉"，没有把历史抹掉——那是当时为什么那么做的理由，下次有人想改回去要看得到。

1. **DRAFT 物品不再被排除在提醒之外。** 上一版 `expiringBy` 带着 `status = 'CONFIRMED'`，于是"稍后处理"和"照片选了还没确认"的东西永远安静——而收件箱里那些恰恰是最容易放坏的。条件删掉，`ReminderEngineTest.draftWithADeadlineIsRemindedToo` 与 `ReminderWorkerTest.draftItemIsRemindedToo` 钉住。spec 第 6.5 节没写过"只提醒已确认的"，那是我自己加的过滤器。
2. **通知权限被拒时不再回写 `expireNotifiedAt`。** 上一版是"不发通知但照样标记"，理由写的"否则每天重复劳动"——而每天重复劳动在第 3 条之后本来就不会发生了。真正的后果更糟：用户一次没注意点了拒绝，那件物品的提醒就被永久吃掉，而且界面上毫无线索。现在 `doWork()` 只在 `canNotify` 为真且通知确实发出之后回写；`deniedPermissionPostsNothingAndLeavesTheItemAnnouncable` 先拒（断言既没通知也没标记）再授（同一条物品冒出通知）。
3. **每个截止日期最多说两次，不多不少。** 谓词重写成 `expireNotifiedAt IS NULL`（进窗口那一次）`OR (expireNotifiedAt < 今天零点 AND expiresAt >= 今天零点 AND expiresAt < 明天零点)`（到期当天那一次，且只在到期当天）。`ReminderEngine.plan()` 因此多算一个 `todayEnd = localStartOfDay(now + 1)`，`expiringBy` 变成三参。三条边界各一条单测，其中"昨天提醒过、明天到期必须保持安静"是对上一版语义的直接反证。
4. **改到期日会重新排队。** 新增 `ItemRepository.reArm(from, to)`：`expiresAt` 变了就把 `expireNotifiedAt` 清空，`confirm` 与 `keepAsDraft` 两条写路径都过它。之所以不动表结构：验收期间 schema 冻结，加一列"上次提醒针对哪个日期"是更干净的建模，但那要迁移；清空标记在语义上等价（新日期从头排队），代价是"改日期后当天可能重复说一句"这种边角情况。`editingTheDeadlineReArmsTheReminder` 同时钉住"只改名不改日期不该再说一遍"。
5. **设置页不再读回 DataStore 来更新状态。** 见 Task 7 那一条的标注。附带把 `SettingsRepository` 的 `LEAD_DAYS_MIN/MAX/DEFAULT_LEAD_DAYS` 提成公开常量，夹取规则仍然只有一份真相——VM 用的是同一组常量，不是自己抄的数。`init` 的那次异步读加 `leadDaysTouched` 门，否则启动时读得慢一点就会把用户已经按下去的值踩回默认。
6. **`PhotoStore.write` 自己清理半个文件。** 磁盘满、写到一半被中断时，那两个路径还没有任何库行认得，仓库层的清理只有拿到 `StoredPhoto` 才跑得动——所以谁写出来谁扫干净。这与计划 1 里 `newDraft` 的事务清理是两层，不重复。
7. **snackbar 的 `message` 与 `error` 拆成两个 `LaunchedEffect`（设置页、位置树），照片墙与详情页改成"先消费再显示"。** 共用一个 key 时，消费其中一个会改变 key、重启整个 effect，于是另一个分支永远被跳过；而 `showSnackbar` 挂起到你把界面划走为止，先显示后消费意味着离开页面几分钟后消息自己冒出来。**（后半句作废：第四轮 `8c62ab0` 发现"先消费再显示"里，`consume()` 改的正是这个 effect 自己的 key，正在挂起的 `showSnackbar` 被当场取消，条幅根本没出现。现在四个页面共用 `ui/common/SnackbarMessage.kt`，把文案抄进本地状态与挂起显示分成两个 effect，重放与自我取消一起解决。）**
8. **标签筛选大小写不敏感、chip 列表按 lowercase 去重。** `LOWER(tag) = LOWER(:tag)`；`allTags()` 走 `distinctBy { it.lowercase() }`，否则 `MacBook` 与 `macbook` 在筛选条上是两颗几乎一样的 chip。
9. **设置页读不到已存值时按默认值显示并说一句。** `runCatching { settings.current() }`，失败提示「读不到已存的设置，先按默认值显示」——首帧前的文件损坏或磁盘满不该让"打开设置页"变成崩溃。

改的过程中留下一个手误值得记：把提醒 SQL 从两参改三参时，`Edit` 的锚点选长了，连带删掉 `Daos.kt` 里没人碰的三个方法（`allTags`/`itemIdsWithTag`/`countAt`）。编译当场报错、当场补回，但它说明**改 `Daos.kt` 这种"一个文件塞了所有查询"的地方要用最短的锚点**——这个文件后面还会因为 C 段那些验收项被反复动。

## 第四轮自审修正（2026-10-02，通知到底发不发得出）

计划 1 那一轮的主题是"界面上的每句话、每颗按钮，代码做得到吗"，落到本计划的提醒链路上是这三处。测试 207 → **214 / 25 个类，0 失败 0 跳过**，提交 `8c62ab0`；schema 仍然冻结。

1. **`canNotify` 补上系统级的通知开关。** 原来只看运行时授权，于是"权限给着、但用户在 设置 → 应用 → Wheremy → 通知 里把通知整体关掉"被判成能发：`notify()` 静默失败，`doWork()` 却照样回写 `expireNotifiedAt`——与第 2 条被拒权限是同一个毛病，欠了账还说说过。现在 `canNotify = permissionGranted(ctx) && NotificationManagerCompat.from(ctx).areNotificationsEnabled()`，`systemLevelSwitchOffPostsNothingAndKeepsTheItemAnnouncable` 钉住"关掉时既没通知也没标记，打开之后补得回来"。
2. **设置页那句提示分两种，死按钮撤掉。** 新增 `ReminderWorker.notifyProblem(ctx)`：没给权限 → 「通知未开启，到期只在应用内标记」+「开启通知」按钮；权限给了、系统里关着 → 「通知在系统里被关掉了：到 设置 → 应用 → Wheremy → 通知 里打开…」，**并且不给按钮**——`RequestPermission()` 对已经授权的权限不会有任何效果，按下去什么都不发生。原来两种情况合并成同一句话配同一颗按钮，第二种下那句"到期只在应用内标记"仍然是对的，但那颗按钮是假的。
3. **「提前 0 天提醒」不说了。** 预设按钮里那一档明明写着「当天」，正文却写"提前 0 天"；现在 `LEAD_DAYS_MIN` 那一档显示「当天到期提醒」，两个方向键在 0/90 边界上禁用（与照片墙数量「－」的处置一致）。`notifyProblemPointsAtTheRightFix` 钉住两句话各归各。

Task 5 的 `canNotify` 代码清单、Task 7 的「通知未开启」那一条与 `LEAD_PRESETS` 附近的正文，都以源码为准。这一轮的 Compose 层改动（提示行、按钮禁用、`SnackbarMessage`）没有 UI 测试覆盖，只能靠计划 1 C 段那两条新增的验收项在真机上看。
