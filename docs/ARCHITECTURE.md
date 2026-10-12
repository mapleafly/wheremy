# Wheremy 技术架构（as-built）

**这份文档回答**：代码实际长什么样、每个职责在哪个类、谁调用谁、哪些顺序是不能动的。
**它不回答**："要做什么功能"（`PRD.md`）、"规则到底怎么算"（`RULES.md`）、"界面上有什么"（`UI.md`）。

**基线**：commit `cf73f5b`（2026-10-06）。主源 57 个 Kotlin 文件 / 6881 行；测试 39 个结果类 / 481 例。
**行号会漂**：每处都同时给出符号名，找不到行就按符号名搜。

---

## 1. 全局形态

- **单 Gradle module**（`app`），按包分层；**无 NavHost、无 Fragment、无多 Activity**。
- **单 Activity + 一个 Compose 外壳**：`MainActivity` 只负责取容器、排提醒班表、接通知 intent，
  所有导航状态在 `ui/WheremyRoot.kt`（403 行）里用 `rememberSaveable` 管。
- **手写依赖注入**：一个普通 class `di/AppContainer.kt` 装全部单例，通过 `CompositionLocal` 发给界面。
  不引 Hilt（否决项 D5）。
- **分层规则**（上层可依赖下层，反向不许）：

```
ui/  ──┐
       ├─→ domain/（纯 Kotlin，无 Android 类型）─┐
       │                                        ├─→ data/（repo, db, photos, vision, backup, work, settings, camera）
       └─→ di/AppContainer ─────────────────────┘
```

  `domain/` 里唯一的 Android 痕迹是 `CameraGate`（只有枚举与纯函数，无 import）。
  `util/Dates.kt` 也不属于上面任何一格：它只有 `java.time.*` 的 import，被 `domain/` 与 `ui/`
  **两层**同向调用（`data/` 层零引用：全项目只有 `domain/ReminderEngine.kt:4-6` 与
  `GalleryViewModel.kt:14`、`GalleryScreen.kt:60`、`DetailScreen.kt:45-46`、`ItemRow.kt:23`、`ReviewScreen.kt:63-66` 五个界面文件 import 它）——
  `formatDate` / `localDateOf` / `localStartOfDay` / `daysUntil` / `expireWindowEnd` /
  `utcPickerToLocalMillis`（系统日期选择器给的是 UTC 零点，这一句把它挪到本地零点）/
  `localMillisToUtcPicker`（反向：把本地零点毫秒喂回选择器，`ReviewScreen.kt:65` 在用）。
  提醒窗口、照片墙那颗芯片、详情页"还剩几天"三处共用它，
  **所以"差一天"这类 bug 只可能在这一个文件里长出来，也只可能在这一个文件里修**。

- **包 ↔ 职责**：

| 包 | 职责 | 文件数 |
|---|---|---|
| `com.wheremy.app`（根） | `MainActivity`、`WheremyApp`（Application） | 2 |
| `di/` | `AppContainer`、`AppViewModel` | 2 |
| `data/db/` | Entity、Dao、Database、SearchTextBuilder | 4 |
| `data/repo/` | ItemRepository、LocationRepository、Settings 之外的模型与照片回收 | 4 |
| `data/photos/` | PhotoStore | 1 |
| `data/vision/` | 分析器、提示词、解析、留痕 | 7 |
| `data/backup/` | 备份格式、IO、仓库 | 3 |
| `data/work/` | 提醒 Worker 与排班 | 2 |
| `data/settings/` | DataStore 读写 | 1 |
| `data/camera/` | `CameraShutter` 接口（无 CameraX 类型） | 1 |
| `domain/` | 8 个用例/纯逻辑 | 8 |
| `util/` | `Dates.kt`：日历天换算 | 1 |
| `ui/` | 外壳 + 5 屏 + VM + common + camera + theme | 21 |

  合计 **57 个 `.kt`**（§16 的清单逐个数过）。

---

## 2. 构建与依赖

**版本**（`gradle/libs.versions.toml` 61 行，全部集中在这里；`[plugins]` 5 条 alias 全在 `app/build.gradle.kts:1-7` 用上：
`android-application` / `kotlin-android` / `kotlin-compose` / `kotlin-serialization` / `ksp`）：


| 项 | 值 |
|---|---|
| Kotlin / KSP | 2.2.21 / 2.2.21-2.0.5 |
| AGP / Gradle | 8.13.2 / 8.14.3（**没有 wrapper**，本机 version-fox 提供 gradle；加 CI 时要提交 wrapper，见 `TECH_STACK.md` §8.2 更正三） |
| Compose BOM | 2026.06.01 |
| Room | 2.8.5（runtime + ktx + compiler via KSP） |
| Coil | 3.3.0（`coil3`） |
| WorkManager | 2.10.1 |
| DataStore | 1.2.1 |
| OkHttp | 4.12.0（+ 同版本 mockwebserver 测试） |
| kotlinx.serialization / coroutines | 1.10.0 / 1.10.2 |
| CameraX | 1.6.2，**四个 artifact**：`camera-core`（接口）/ `camera-camera2`（运行时实现）/ `camera-lifecycle`（随页面开关）/ `camera-view`（预览面 `PreviewView`）。**没有** `camera-compose`，也没有 `camera-video`（不录像）——绑定写在 `app/build.gradle.kts:61-65`，那句注释就是这个分工 |
| exifinterface / lifecycle / activity-compose / core-ktx | 1.4.1 / 2.10.0 / 1.11.0 / 1.18.0 |
| Robolectric / junit / androidx-test-core | 4.16.1 / 4.13.2 / 1.7.0 |
| compileSdk / targetSdk / minSdk / JVM | 36 / 36 / 26 / 17 |
| versionName / versionCode | 0.1.0 / 1 |

**`app/build.gradle.kts`（82 行）里四处非默认设定，都要留着**：
- `buildConfig = true`：备份 `manifest.json` 要写 App 版本（`BuildConfig.VERSION_NAME` → `AppContainer.kt:54`）。
- `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`：导出 schema JSON（`app/schemas/…/1.json` 在仓库里）。
- `testOptions.unitTests { isIncludeAndroidResources = true; isReturnDefaultValues = true }`：
  Robolectric 跑 Compose 界面测试的前提。
- `release { isMinifyEnabled = true }` + `isShrinkResources = true`（keep 规则在 `proguard-rules.pro`）、签名走用户自己的 keystore（仓库外 `keystore.properties`，没有则回落到 debug 签名并打 ⚠️）：**R8 在 P6 已打开**，⭐FR-RLS-01 于 2026-10-10 由用户完成（`RELEASE.md` §3.2、`KEYSTORE.md`）。

`settings.gradle.kts`：仓库**只用官方源**（google / mavenCentral / gradlePluginPortal）+ `RepositoriesMode.PREFER_SETTINGS`。⚠️ 2026-10-10 更正：阿里云镜像**不再写进仓库配置**——它在 GitHub runner 上返回 502，把 CI 的插件解析搞红了（F46）；本机加速交给本机的 init 脚本。

**`AndroidManifest.xml`（27 行）**：权限只有 `INTERNET` / `POST_NOTIFICATIONS` / `CAMERA`；
`uses-feature camera.any required=false`；`allowBackup="false"`；
Activity `screenOrientation="portrait"`、`windowSoftInputMode="adjustResize"`、**默认 launchMode**
（通知靠 `CLEAR_TOP|SINGLE_TOP` 走 `onNewIntent`）。

装配上有三件容易漏：

- `<application android:name=".WheremyApp">` —— 容器挂在 Application 上，`ReminderWorker` 和
  `MainActivity` 都是从 `(applicationContext as WheremyApp).container` 拿依赖的（`ReminderWorker.kt:53`）。
  **漏了这一行，Worker 会在半夜崩**（ClassCastException），而界面上一切正常。
- `<activity android:exported="true">` 是**必须**的：它是唯一的 LAUNCHER 入口（有 intent-filter，
  targetSdk 31+ 强制要求写明）。别因为"没有别的组件要调它"而删掉。
- **只有 `android:icon="@mipmap/ic_launcher"`，没有 `roundIcon`**：自适应图标（`mipmap-anydpi-v26`）
  自己填圆形槽位，加 `roundIcon` 反而要在各密度目录再塞一套位图。

**资源只有 5 个文件**（其余全在代码里）：
`drawable/ic_launcher_foreground.xml`、`mipmap-anydpi-v26/ic_launcher.xml`、
`values/ic_launcher_background.xml`、`values/strings.xml`（只有 `app_name`）、`values/themes.xml`。
**界面文案全部硬编码在 Kotlin**，不走资源文件——改文案就是改代码，测试断言逐字对齐。

---

## 3. 装配层

### 3.1 `di/AppContainer.kt`（104 行）

`class AppContainer(appContext: Context, cameraSource: CameraSource? = null)` — `:30`

| 字段 | 类型 | 构造 | 行 | 为什么这样 |
|---|---|---|---|---|
| `appContext` | `Context` | `applicationContext`（**非 lazy**） | :32 | 构造时就甩掉 Activity 引用 |
| `startupScope` | `CoroutineScope` | `SupervisorJob() + Dispatchers.Default` | :35 | 补算 `search_text` 跟页面无关，不该跟着界面被取消 |
| `settings` | `SettingsRepository` | lazy | :37 | |
| `db` | `WheremyDatabase` | lazy `WheremyDatabase.create` | :39 | |
| `photoStore` | `PhotoStore` | lazy `File(filesDir, "photos")` | :41 | 目录名 `photos` 是备份认路径的根 |
| `camera` | `CameraSource` | lazy `cameraSource ?: CameraXSource(appContext)` | :47 | JVM 里 `ProcessCameraProvider` 永远等不到回调，测试必须注入假的 |
| `items` | `ItemRepository` | lazy `(db, photoStore)` | :49 | |
| `locations` | `LocationRepository` | lazy `(db, photoStore)` | :51 | |
| `backup` | `BackupRepository` | lazy `(db, photoStore, BuildConfig.VERSION_NAME)` | :54 | **参数里没有 SettingsRepository**——导出通道物理上碰不到 key |
| `backupIo` | `BackupIo` | lazy `ContentResolverBackupIo` | :56 | |
| `reminders` | `ReminderScheduler` | lazy | :58 | |
| `httpClient` | `OkHttpClient` | lazy，`callTimeout(15s)` | :60 | 常量 `CALL_TIMEOUT_SECONDS = 15L` :102 |
| `analysisTrace` | `AnalysisTrace` | lazy `FileAnalysisTrace(File(filesDir, "ai-trace"))` | :71 | **刻意不在 `photos` 下**：备份只搬数据库行 + 那个目录，留痕（含用户位置清单）不该跟着出门 |
| `photoAnalyzer` | `ApiPhotoAnalyzer` | lazy `(httpClient, { settings.currentConfig() }, analysisTrace)` | :80 | config 为 null 时它自己给 `NO_CONFIG`，所以运行时不需要在两个实现之间切换 |
| `analyzePhoto` | `AnalyzePhotoUseCase` | lazy | :84 | |
| `suggestLocation` | `SuggestLocationUseCase` | lazy | :86 | |
| `pendingCapture` | `MutableStateFlow<ByteArray?>` | 普通 val | :89 | 选图与确认页在两个 composable 之间换手；MB 级字节，消费即清 |
| `entry` | `MutableStateFlow<String?>` | 普通 val | :95 | 通知入口意图；放容器而不是 Activity，因为消费它的是 Compose 外壳 |
| `captureFlow` | `CaptureFlow` | lazy `(items, locations, analyzePhoto, suggestLocation)` | :97 | |

**生命周期事实（重要）**：`AppContainer` **没有任何 close/清理**——不取消 `startupScope`、不关 db、
自己不清 `pendingCapture`/`entry`（由消费者清）。它在 `WheremyApp.onCreate` 造一次，活到进程结束。

### 3.2 `di/AppViewModel.kt`（30 行）

```kotlin
val LocalAppContainer = staticCompositionLocalOf { error("AppContainer 尚未提供") }   // :11

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    container: AppContainer, key: String? = null, noinline make: (AppContainer) -> VM,
): VM                                                                                    // :16
```

契约：每个屏写 `appViewModel(container) { XxxViewModel(it) }`。**`key` 是同类 VM 共存时唯一的区分手段**
（`"gallery"` vs `"inbox"`；`"review-$reviewSeq"`）。

### 3.3 启动序列

`WheremyApp.kt`（37 行）：
1. `container = AppContainer(this)` — `:16`
2. `ReminderWorker.ensureChannel(this)` — 通知频道必须在任何发送前存在
3. `rebuildSearchTextIfFieldsChanged()` — `:26-36`：在 `startupScope` 里比对 DataStore 的
   `searchTextVersion` 与 `SearchTextBuilder.FIELD_VERSION`，不等才 `items.rebuildSearchText()`；
   外层 `runCatching` 吞异常（补算失败不该让 App 起不来）。

`MainActivity.kt`（43 行）`onCreate` — `:13-29` 顺序固定：
1. `super`
2. `(application as WheremyApp).container`（容器不在这里造）
3. `container.reminders.schedule()`（每次 create 都调，`KEEP` 策略下无害）
4. **savedState 闸门** `:19-21`：`if (savedInstanceState == null) intent?.getStringExtra(EXTRA_ENTRY)?.let { entry.value = it }`
   —— 转屏重建时 intent 还是那条通知，无条件重放会把用户已经清掉的筛选又加上。
   注意这里用 `?.let`：extra 缺失就**不动** entry。
5. `setContent { WheremyTheme { CompositionLocalProvider(LocalAppContainer provides container) { WheremyRoot() } } }`

`onNewIntent` `:32-35`：**无条件**赋值 `intent.getStringExtra(EXTRA_ENTRY)`，缺就清成 `null`。
常量：`EXTRA_ENTRY = "com.wheremy.app.entry"` `:38`、`ENTRY_EXPIRING = "expiring"` `:41`。

---

## 4. 数据层

### 4.1 Room 数据库 `data/db/WheremyDatabase.kt`（38 行）

- `@Database(entities = [LocationNodeEntity, ItemEntity, ItemPhotoEntity, ItemTagEntity], version = DB_SCHEMA_VERSION, exportSchema = true)`
- `const val DB_SCHEMA_VERSION = 1`（**备份 manifest 也写这个号**，导入时据此判断包是否来自更新版本）
- `NAME = "wheremy.db"`
- `@TypeConverters(ItemStatusConverter::class)`：`ItemStatus` ↔ `String`（`valueOf` / `.name`）
- ⚠️ `fallbackToDestructiveMigration(true)` — 注释原话「Plan 1 期间允许破坏性迁移（尚无真实数据），发布前换成真实 Migration」。**这是 ⭐FR-DB-01，发布前必做。**

### 4.2 四张表（`data/db/Entities.kt` 81 行）

**`location_node`** `:25`：`id: String PK`、`parentId: String?`（FK→self，`onDelete = SET_NULL`，`Index("parentId")`）、
`name: String`、`kind: String?`、`sortOrder: Int`、`createdAt`、`updatedAt`。

**`item`** `:44`：`id PK`、`name`、`category?`、`seasons?`（逗号分隔 CSV）、`color?`、`brand?`、`material?`、
`aliases?`（CSV）、`description?`、`notes?`、`quantity: Int`、`locationId?`（FK→`location_node`，`SET_NULL`，
`Index("locationId")`）、`status: ItemStatus`（`Index("status")`）、`expiresAt: Long?`（`Index("expiresAt")`）、
`expireKind?`、`expireNotifiedAt?`、`searchText: String`（派生列）、`createdAt`、`updatedAt`、`lastMovedAt: Long?`。

**`item_photo`** `:69`：`id PK`、`ownerType: String`（`PhotoOwner.ITEM = "item"` / `LOCATION = "location"`，`:11`）、
`ownerId: String`、`filePath`、`thumbPath`、`sortOrder`、`createdAt`；`Index("ownerType", "ownerId")`。
**多态外键**：`ownerId` 不建 FK——物品和位置共用一张照片表，代价是"照片找不到主人"要靠导入校验拦（`RULES.md` §12.3）。

**`item_tag`** `:81`：`primaryKeys = ["itemId", "tag"]`、`Index("tag")`。标签独立成表是为了点选筛选。

四个 `@Entity` 全部 `@Serializable`——**备份包直接序列化 entity 列表**，不额外定义 DTO。

**数组字段的编码位置只有一处**：`seasons`/`aliases` 存 CSV 字符串、`tags` 存 `item_tag` 行，
转换在 `ItemRepository` 的 `splitList()` / `csv()` 私有扩展里（`ItemRepository.kt:337-340`）。

### 4.3 DAO（`data/db/Daos.kt` 126 行）

**`LocationDao`** `:11`：`upsert`、`all()`、`byId(id)`、`upsertAll`、`deleteAll`、`delete(node)`。
**全量查询是设计选择**：树在内存里建（否决项 D2）。

**`ItemDao`** `:21` 关键方法：

| 方法 | SQL 要点 | 行 |
|---|---|---|
| `upsert` / `upsertAll` / `deleteAll` / `delete` | | :22, :28, :29, :87 |
| `insertTags` / `clearTags` / `tagsFor` / `allTagRows` / `deleteAllTags` / `allTags` | 标签整批替换（先 clear 再 insert） | :23-27, :42 |
| `searchLike(pattern)` | `searchText LIKE :pattern ORDER BY updatedAt DESC **LIMIT 300**`（列名是 `searchText`，见 schema `1.json`） | :32 |
| `inbox()` | `locationId IS NULL OR status = 'DRAFT'` | :34 |
| `atLocation(id)` / `byStatus(s)` / `countAt(id)` | | :36-39, :49 |
| `moveTo(itemId, locationId, movedAt)` | 同一时间戳写 `lastMovedAt` 与 `updatedAt` | :40 |
| `itemIdsWithTag(tag)` | `LOWER(tag) = LOWER(:tag)`：**大小写不敏感**（AI 给 "MacBook"、手打 "macbook"） | :46 |
| `expiringBy(windowStart, todayEnd, windowEnd)` | 到期取数窗口，**下界不设**、`ORDER BY expiresAt LIMIT 50`；三段 OR 的语义写在它自己的 KDoc 里 | :68 |
| `expiringIgnoringNotified(windowStart, windowEnd)` | 只给设置页调试按钮；放宽的是"今天说过几次"，不是把沉默旧账翻回来 | :79 |
| `markExpireNotified(ids, notifiedAt)` | | :84 |

**`PhotoDao`** `:91`：`upsert`、`forOwner(ownerType, ownerId)`（按 `sortOrder`）、`deleteForOwner`、`all()`、`upsertAll`、`deleteAll`，
以及两条**引用计数查询**：
- `refsTo(path)` `:106` — 还有多少行指着这个路径。注释钉住：调用方必须**先删掉自己的行再问**，否则永远数到自己；
  原图与缩略图各一列但一处数就行（两个目录文件名互不重合）。
- `otherOwnersOnPath(ownerType, ownerId, path)` `:114` — 除自己之外**按 ownerType 分开数**（`COUNT(DISTINCT ownerId)`），
  返回 `PhotoOwnerCount(ownerType, owners)` `:126`。删除确认框那句"照片会不会跟着没"就靠它。

### 4.4 派生列 `data/db/SearchTextBuilder.kt`（38 行）

```kotlin
const val FIELD_VERSION = 2                                            // :9
fun build(name, brand, color, material, aliases, description, notes, tags): String  // :16
  = listOfNotNull(...).joinToString(" ").lowercase().replace(Regex("\\s+"), " ").trim()
```
- 参数顺序就是上面这个，但**拼接顺序不一样**：`listOfNotNull` 里是
  `name, brand, color, material, aliases, tags.joinToString(" "), description, notes`
  （`SearchTextBuilder.kt:26-33`）——标签插在中间，别按参数表顺序拼。
- 空值处理：`String?` 为 null 就不进；`tags` 是先 `joinToString(" ")` 再 `ifBlank { null }`，
  所以空列表等于没这一段，不会留下多余空格（末尾的 `replace(Regex("\\s+"), " ")` 也会收拾）。
- **写入侧唯一的 `lowercase()` 发生在这里**（写进 `searchText` 的那一份），所以搜索侧比较的是已小写化的串；中文不受影响，
  但英文品牌名大小写混着录也一样能搜到。**读侧另有各自的小写化**，别以为全库只有一处：
  `SearchRanker.kt:7,13,19,21`、`SuggestLocationUseCase.kt:11,38,45,46`、`ItemRepository.kt:211,222`、
  `ApiPhotoAnalyzer.kt:121` 都在自己那一层做（全量 12 处 / 6 个文件）。
- 参与字段：**不含 category/seasons**（那两个走独立列筛选，不走全文）。
- 颜色与材质是后加的（`FIELD_VERSION` 因此从 1 → 2）；派生列不会自动补，
  靠 `ItemRepository.rebuildSearchText()` 在升级后第一次启动补算一遍。

---

## 5. 照片层

### 5.1 `data/photos/PhotoStore.kt`（136 行）

`class PhotoStore(val rootDir: File)`，两个子目录 `originals/`、`thumbs/`（常量 `DIR_ORIGINALS`/`DIR_THUMBS` 是 **public**，
**备份要靠这两个名字认出"哪些文件才是照片"**）。

| 档位 | 长边 | 质量 | 落盘 | 用途 |
|---|---|---|---|---|
| original | 1600 | q85 | `originals/<uuid>.jpg` | 详情页大图、冷启动后重新分析 |
| thumb | 256 | q80 | `thumbs/<uuid>.jpg` | 网格/列表缩略图 |
| analysis | 1024 | q80 | **不落盘**（`StoredPhoto.analysisJpeg`） | 喂模型，省 token |

`write(jpeg): StoredPhoto` 的三条硬要求：
1. **`decode` 先读尺寸定 `inSampleSize`**（`inJustDecodeBounds`），避免原图整张进内存 OOM；
   读不出尺寸或解码失败 → `error("照片解码失败")`。
2. **EXIF 方向在这一步烤进像素**（`orientationOf` + `applyOrientation`，7 种方向全表）。
   理由写在注释里：`Bitmap.compress` 重编码时不再带方向标签，不烤进去存出来就是躺倒的——
   照片墙、详情页、喂给模型的那张一起躺倒。
3. **失败自清理**：`catch (t: Throwable)` 里删掉刚写的两个文件再抛。
   注释原话「谁写出来谁扫干净」——此刻库里还没有任何一行认得这两个路径，
   `ItemRepository` 的清理只有拿到 `StoredPhoto` 才跑得动。

对外还有 `file(relPath)` 与 `delete(relPath)`（相对 `rootDir`）。

### 5.2 `data/repo/PhotoReclaim.kt`（21 行）

```kotlin
internal suspend fun deleteUnreferencedPhotoFiles(photoDao, photoStore, paths) =
    paths.filter { it.isNotBlank() }.distinct().forEach {
        if (photoDao.refsTo(it) == 0) photoStore.delete(it)
    }
```
**这是全项目照片文件回收的唯一一条规则**，物品侧和位置侧都必须走它。
调用契约同 5.1：先删/换自己的行，再进来。

---

## 6. 仓库层

### 6.1 `data/repo/ItemRepository.kt`（365 行）

`class ItemRepository(private val db: WheremyDatabase, val photoStore: PhotoStore)` `:20`

| 方法 | 行 | 契约（跨层不变量写在这里） |
|---|---|---|
| `takePendingAnalysis()` | :28 | 本次 `newDraft`/`swapPhoto` 产出的 1024 分析图，**取一次即清**；`CaptureFlow` 用它省掉二次编码 |
| `newDraft(jpeg)` | :31 | 先 `photoStore.write` → `db.withTransaction { 物品行 + 照片行 }` → 失败删两个文件并清 pending。**两条同生同死**：物品行成了、照片行没成 = 一件永远没缩略图的物品 + 两个没人来删的孤儿文件 |
| `newDraftSharingPhoto(sourceItemId)` | :65 | 批次 AJ：同一张照第二件起，**一个新文件都不写**，只多两行；源物品没有照片行时必须拒（否则造出指向空路径的哑物品） |
| `swapPhoto(itemId, jpeg)` | :83 | **先写新文件 → 再改行 → 最后删旧文件**。顺序反了中途磁盘满就留下一件没照片的物品。失败删新对、旧图留着继续用 |
| `applyAnalysis(itemId, a)` | :109 | 只覆盖模型**明确给出**的字段（`a.x ?: cur.x`）；**非 DRAFT 直接 return**（已确认的物品不再被迟到的 AI 改写）；`tags` 空时保留现有标签 |
| `draftOf(itemId)` | :132 | entity → `ItemDraft`（CSV 拆回 list、补 tags）；不存在时 `error("物品不存在：$id")` |
| `confirm(draft)` | :152 | `require(name.trim().isNotEmpty()) { "名称不能为空" }`；算 `lastMovedAt`（三档：新建/位置变了/没变）；写 `searchText`；`writeTags` |
| `keepAsDraft(draft)` | :183 | `ReviewScreen.kt:98` 那颗「稍后处理」：写字段 + 翻 DRAFT，**位置不动**（谓词本来就是 OR） |
| `reArm(from, to)` | :149 | private，但语义是规则：`expiresAt` 变了就清 `expireNotifiedAt`（重新武装） |
| `get` / `tagsOf` / `thumbPathOf` / `photoBytesOf` | :196-:206 | `photoBytesOf` 读的是 **1600 原图**（冷启动后重跑分析唯一可用来源） |
| `rankedSearch(query, filters)` | :209 | 空 query 走 `all()`；否则 `searchLike("%q.lowercase()%")`；**标签筛走 `item_tag` 行而不是 `search_text` 字面**（描述里提过一次"冬装"不代表打了标签）；再 `SearchRanker.rank` |
| `inbox` / `atLocation` / `allTags` / `photosOf` / `summaries` | :218-:235 | `summaries` 是 **N+1 查询**（每件各查 tags 与 thumb），所以调用方要注意 seq 护栏（见 §11.1） |
| `move(itemId, locationId)` | :242 | `moveTo` 之后**有名字才顺手确认**：空名不转 CONFIRMED（否则会消失收件箱 + `search_text` 为空） |
| `rebuildSearchText()` | :254 | 全表补算，**只在内容真变了才写**（不为一次启动把整库 `updatedAt` 搅一遍） |
| `photoDeletionOf(itemId)` | :265 | 产出 `PhotoDeletion(hasPhoto, otherItems, otherOwners)`，给删除确认框分档用 |
| `delete(itemId)` | :282 | **先删行**（item → tags → photo 行）再 `deleteUnreferenced`；顺序不能反（spec §8） |

私有：`blankItem` `:295`（所有可空字段清空、`quantity = 1`、`status = DRAFT`、`searchText = ""`）、
`writeTags` `:303`、`fieldsOf` `:308`（`trim`、空串归一成 null、`quantity.coerceAtLeast(1)`）、
`searchTextOf` `:324`、`matches` `:329`（五个筛选项的 AND 组合）、`splitList`/`csv` `:337-340`、`Fields` `:344`。

### 6.2 `data/repo/LocationRepository.kt`（202 行）

`class LocationRepository(db, photoStore)`。

| 方法 | 行 | 契约 |
|---|---|---|
| `LOCATION_KINDS` | :17 | `房间/衣柜/抽屉/收纳盒/箱子/挂钩/货架/其他`；**输入框允许填别的词** |
| `tree()` | :32 | 全量 `all()` → `buildLocationTree(nodes)` |
| `add(parentId, name, kind)` | :38 | `requireName` + `requireNoSiblingNamed` 之后写；`sortOrder` 落到同级末尾 |
| `update(id, name, kind)` | :57 | **名字与类型一次读改写**（KDoc：分两笔会丢改名） |
| `changeParent(id, newParentId)` | :71 | 三道 require（不能移到它自己下面 / 目标不存在 / 不能移到它的子位置下面），挪过去落在该层末尾 |
| `itemCount(id)` / `photoOf(id)` | :89 / :92 | |
| `setPhoto(ownerId, jpeg)` | :102 | ①先 require 位置行在（`LocationRepository.kt:103` 「这个位置已经不在了，照片没处可挂」）②写文件 ③事务里 `deleteForOwner` **整类清掉再写一行**（一个位置只留一张）④失败删新文件 ⑤最后 `deleteUnreferencedPhotoFiles(旧)` |
| `clearPhoto(id)` | :131 | 无照片时是空操作 |
| `shiftWithinParent(id, up)` | :148 | **不是交换 sortOrder**，而是按显示次序给整条同级重排号；到边界返回 `false`。KDoc：重复值时交换等于没换 |
| `subtreeItemCount(id)` | :170 | 含子位置的物品总数 |
| `delete(id)` | :175 | 门槛只看**子位置数 + 物品数**；抛 `LocationNotEmptyException(childCount, itemCount)`；删完 `clearPhoto(id)` |
| `requireName` / `requireNoSiblingNamed` | | 「位置名称不能为空」/「同一层级下已有「$name」」 |

### 6.3 `data/repo/Models.kt`（61 行）

四个类型加三张词表，全部是 UI 与仓库之间唯一的载体；`data/db/Entities.kt` 的实体**不上到界面层**。

| 类型 | 行 | 字段 |
|---|---|---|
| `ItemDraft` | :5-21 | 15 个：`id, name, category?, seasons, color?, brand?, material?, aliases, description?, notes?, quantity, locationId?, expiresAt?, expireKind?, tags`。**只有 `id` 没有默认值**（`:6`），其余全有：`name = ""`、`quantity = 1`、三个 `List` 为 `emptyList()`、其它 `?` 为 null。界面上的输入框直接映射到这里，所以"空字符串"和"null"是两种不同的中间态，落到库里前由仓库收口（见下） |
| `Filters` | :23-33 | 5 个：`category?, season?, tag?, onlyInbox = false, expireUntil?`。`expireUntil` 的注释写明"非空 = 只看到期日不晚于这个毫秒"，也就是**临近到期与已过期一起**；无到期日的物品在非空时一律不算进来 |
| `ItemSummary` | :35-40 | 4 个：`item: ItemEntity` + `tags: List<String>` + `thumbPath?` + `locationPath?`（列表页一行要的全部） |
| `PhotoDeletion` | :49-53 | 3 个：`hasPhoto, otherItems, otherOwners`。注释解释了为什么要三个数：删除确认框对"照片会不会跟着没"要分三种说法，而 `otherOwners` 数的是**非物品**的主人（位置记录），正常操作造不出来但手改过的备份包能造出来 |

- 词表：`CATEGORIES`(12，`:55-57`) = 衣物 / 数码 / 电器 / 药品 / 食品 / 工具 / 文档 / 饰品 / 厨具 / 玩具 / 运动 / 其他；
  `SEASONS`(4，`:59`) = 春 / 夏 / 秋 / 冬；`EXPIRE_KINDS`(4，`:61`) = 保质期 / 保修 / 证件有效期 / 其他。
  **都是 `val listOf`，顺序就是界面上芯片的顺序**；位置类型不在这个文件里，是
  `LOCATION_KINDS`(8，`LocationRepository.kt:17`) = 房间 / 衣柜 / 抽屉 / 收纳盒 / 箱子 / 挂钩 / 货架 / 其他，见 §6.2。
- 列表 ↔ 列的转换在 `ItemRepository`，不在这里：`fieldsOf`（`ItemRepository.kt:308-322`）把 draft 压成
  `Fields`，`seasons` 与 `aliases` 走私有 `csv()`（`:340`，空列表 → **null** 而不是空串），
  `tags` 不进 `Fields`（它自己一张表），字符串字段一律 `trim()?.ifBlank { null }`，
  `quantity` 在这一层 `coerceAtLeast(1)`（`:318`）。读回来走 `splitList()`（`:337`，`split(",")` + trim + 去空）。
  **重做时这四条要一起照做**：否则"清掉了类别"会变成库里一个空串，`search_text` 与筛选都会跟着分叉。

### 6.4 `data/settings/SettingsRepository.kt`（89 行）

- DataStore 名 `wheremy_settings`（文件级扩展 `Context.settingsDataStore`）。
- `data class Settings(visionEnabled, baseUrl, model, apiKey, reminderLeadDays, searchTextVersion)`，
  `hasUsableKey = visionEnabled && apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()`。
- `changes: Flow<Settings>`（**生产代码里没有订阅者**，界面靠 `current()` 单次读；唯一一次订阅在测试 `SettingsViewModelTest.kt:166`）。
- `current()` / `currentConfig(): VisionConfig?`（不可用时 null）。
- setter：`setVisionEnabled` / `setBaseUrl` / `setModel` / `setApiKey`（三个都 `trim()`）/
  `setReminderLeadDays`（**写入前 `coerceIn(0, 365)`**）/ `setSearchTextVersion`。
- 默认值：`DEFAULT_BASE_URL = "https://api.deepseek.com"`、`DEFAULT_MODEL = "deepseek-flash"`、
  `DEFAULT_LEAD_DAYS = 3`、`LEAD_DAYS_MIN = 0`、`LEAD_DAYS_MAX = 365`。
  注释说明：默认值**只决定新装机第一次打开设置页框里预填什么**，存下来后请求读的是 DataStore。
- `searchTextVersion` 的默认值就是 `SearchTextBuilder.FIELD_VERSION`——新装机没什么可补算的。
- keys：`vision_enabled / base_url / model / api_key / reminder_lead_days / search_text_version`。

---

## 7. 领域层（8 个，全部无 Android 依赖）

| 文件 | 行 | 对外契约 |
|---|---|---|
| `domain/CaptureFlow.kt` | 126 | 录入编排。`capture(jpeg, onDraftSaved)` = `newDraft` → **先回调 `onDraftSaved(itemId)`** → `analyzeInto(itemId, takePendingAnalysis() ?: jpeg)`，全在 `withContext(io)`。`replacePhoto`、`reanalyze(itemId)`（读 1600 原图）同形。`analyzeInto` **只读一次位置树**——位置候选与本地匹配必须来自同一份快照，否则"模型看得见、本地认不出"；`suggest(...).takeIf { draft.locationId == null }`；`others = results.drop(1)`，每件**各算一次建议**、`id` 留空。产出 `CaptureOutcome(itemId, draft, containerGuess, suggestedLocationId, failure, others)` 与 `CapturedItem(...)` |
| `domain/LocationTree.kt` | 81 | `LocationNode(id, parentId, name, kind, sortOrder)`；`buildLocationTree(nodes)` 全量入内存；`roots()` 把 `parentId == null` **或父不存在**都当根；`ordering = compareBy({sortOrder}, {name})`；`childrenOf` / `pathOf`（用 `seen` 断环）/ `breadcrumb(id, sep = " / ")` / `selectablePaths()`（先序，**根不列**，无条数上限 → PRD FR-LOC-08）/ `descendantIds`（栈式） |
| `domain/LocationTreePolicy.kt` | 7 | `canDelete(children, itemCount) = children.isEmpty() && itemCount == 0`。**唯一的删除门槛定义**（实际执行在 `LocationRepository.delete`） |
| `domain/SearchRanker.kt` | 23 | 打分：`name == q`→100、前缀→80、包含→60、`aliases`→40、`searchText`→20、否则 0；`sortedByDescending` |
| `domain/SuggestLocationUseCase.kt` | 70 | 先 `pathMatch`（去掉所有空白后整条路径相等）；否则 `kind==guess \|\| name==guess`→3、kind 互含→2、name 互含→1；平分取 `depth` 更小；**`node.parentId == null` 跳过（永不建议根）**；对不上返回 null |
| `domain/ReminderEngine.kt` | 58 | `ReminderPlan(count, nearestDays, body, ids)`；`plan(now, force)`：`windowStart = localStartOfDay(now)`、`todayEnd = +1天`、`windowEnd = expireWindowEnd(leadDays, now)`，`force` 走 `expiringIgnoringNotified` 否则 `expiringBy`；`body(count, nearestDays, todayCount)` 四种措辞 |
| `domain/AnalyzePhotoUseCase.kt` | 24 | 唯一职责是把 analyzer 的 sealed 结果摊平成 `(List<AnalysisResult>, FailureReason?)`；**任何异常 → `emptyList to BAD_RESPONSE`**（不阻塞录入） |
| `domain/CameraGate.kt` | 13 | `enum Viewfinder { NeedsPermission, Ready, Unavailable }`；`of(granted, cameraFound)`：**先判 `!cameraFound → Unavailable`**（"没相机"比"没授权"先判，否则会把用户引去开一个开了也没用的权限） |

---

## 8. AI 层（`data/vision/`，7 个文件）

### 8.1 接缝

```kotlin
data class VisionConfig(baseUrl: String, model: String, apiKey: String)          // PhotoAnalyzer.kt
enum class FailureReason { NO_CONFIG, OFFLINE, TIMEOUT, UNAUTHORIZED, RATE_LIMITED,
                           SERVER, BAD_RESPONSE, BAD_REQUEST, INSUFFICIENT_BALANCE,
                           TRUNCATED, NOTHING_FOUND }                            // :12
sealed interface AnalysisOutcome {
    data class Success(val results: List<AnalysisResult>) : AnalysisOutcome
    data class Failed(val reason: FailureReason) : AnalysisOutcome
}
interface PhotoAnalyzer { suspend fun analyze(jpeg: ByteArray, locationPaths: List<String> = emptyList()): AnalysisOutcome }
```

`AnalysisResult`（10 字段，全可空/默认空）+ `isEmpty = name==null && category==null && description==null && tags.isEmpty()`
—— 全空的项在解析阶段就被丢掉，不占槽位。

`OfflinePhotoAnalyzer`（6 行）：恒返 `Failed(NO_CONFIG)`。**运行时不参与切换**（`configProvider` 返回 null 时
`ApiPhotoAnalyzer` 自己给 `NO_CONFIG`，行为一致），留给单测与离线调试构建。

### 8.2 `ApiPhotoAnalyzer.kt`（183 行）流程

```
analyze(jpeg, locationPaths)
 ├─ cfg = configProvider() ?: return Failed(NO_CONFIG)          // :32
 ├─ url = baseUrl.trimEnd('/') + "/chat/completions"            // :33
 ├─ systemPrompt = Prompt.systemFor(locationPaths)              // :36  ← 只算一次：发出的与留痕的必须是同一串字
 ├─ body = buildBody(model, jpeg, systemPrompt)                  // :37
 └─ repeat(MAX_ATTEMPTS = 2) {                                   // :39
      last = attempt(...)                                        // 每次尝试都落一条留痕
      if (Success || !isRetryable(last)) return last
    }
```

- **请求体**（`buildBody` `:147`）：`temperature 0.1`、**`max_tokens 2000`**、`response_format {"type":"json_object"}`、
  messages = system(提示词) + user[文本「识别这张照片里出现的物品。」, `image_url` = `data:image/jpeg;base64,…`]。
  `max_tokens` 从 600 抬到 2000 的实测理由写在注释里（推理模型的 reasoning token 也算额度，600 会把 JSON 截在半句上）。
- **请求头**（`attempt` `:58-63`）：`Authorization: Bearer ${cfg.apiKey}`（`:60`）+ `Content-Type: application/json`（`:61`），
  再 `.post(body.toRequestBody(JSON_MEDIA))`。**key 只在写请求头这一步用到**：它不进请求体、不进 URL、
  不进留痕（见下面 `record`）。这三条各由一个测试钉着，重做时别把它当成"OkHttp 顺手就加了"。
- **`attempt` 整体在 `withContext(Dispatchers.IO)` 里，用的是同步 `client.newCall(request).execute()`**（`:65`）
  而不是 `enqueue`。超时是**注入进来的 `OkHttpClient` 上的 `callTimeout` 15 秒**
  （`AppContainer.kt:60-64` + `CALL_TIMEOUT_SECONDS = 15L` 在 `:102`）；到点抛出来的就是
  `InterruptedIOException`（`java.net.SocketTimeoutException` 是它的子类），于是落进 `TIMEOUT` 那一支。
- **取消不掐请求，只掐结果**：`ReviewViewModel` 里那四处 `analysis?.cancel()`（`:145`、`:330`、`:373`、`:445`）
  取消的是 `viewModelScope.launch` 的 `Job`，而阻塞的 `execute()` **不可被协程取消打断**，全文件也没有 `call.cancel()`。
  于是"换一张 / 退出确认页"之后：请求照样飞完（该花的 token 照花），只是回来到不了 `_state`。
  这是 as-built 的事实，不是一个待修的口子——**重做时若真去掐请求，`InterruptedIOException` 那一支的语义会变**，
  留痕里也会多出"我们自己掐的"那类记录，得先想清楚。
- **异常 → 原因**：`InterruptedIOException → TIMEOUT`、`IOException → OFFLINE`（`:69-73`）。
  **catch 顺序是承重的**：`InterruptedIOException` 是 `IOException` 的子类，写反了一律变成"没网"，
  用户会去查 Wi-Fi 而不是查超时。
- **`classify(code, text)` 是状态码 → 结局的唯一一处映射**（`:82`，注释：界面报的那句话和留痕里的标签都从它拿，不许有两份说法）：
  `401/403→UNAUTHORIZED`、`429→RATE_LIMITED`、`402→INSUFFICIENT_BALANCE`、
  `400→isBalanceComplaint(text) ? INSUFFICIENT_BALANCE : BAD_REQUEST`、非 2xx→`SERVER`、2xx→`parseCompletion`。
  400 要看 body 的原因：实测 DeepSeek 报 400 + 一句 balance，只认状态码会把用户派去核对模型名。
- **可重试集合**：`RETRYABLE = {SERVER, OFFLINE, TIMEOUT, RATE_LIMITED}`（`:176`）。鉴权与配置类错误重试无意义。
- **`parseCompletion`** `:123`：取 `choices[0]`；**先查 `finish_reason == "length"` → TRUNCATED**；
  再取 `message.content`；`AnalysisParser.parseList(content)` 非空 → Success；
  空但 `echoedItemsKey(content)` → `NOTHING_FOUND`（模型照做了"认不出回 items: []"，不能报"无法解析"）；否则 `BAD_RESPONSE`。
- **`record`** `:97`：整段 `runCatching{}.getOrDefault(Unit)` —— **留痕是旁路**，目录写不了也必须把分析结果还给用户。
  写进去的是 `TraceEntry(model, status, outcome, prompt, response)`，**没有 key、没有那张 base64 的图**。

### 8.3 `AnalysisParser.kt`（85 行）

- 三组正则：`FENCE`（Markdown 围栏）、`OUTER_BRACES`（最外层 `{…}`）、`INLINE_OBJECTS` / `ITEMS_KEY`。
- `KNOWN_KEYS` = `AnalysisResult` 的 10 个字段名。
- `candidates` 顺序：**围栏 → 最外层 → 整段 → 散落对象**，再 `.distinct()`。
  distinct 的理由写在注释里（实测 deepseek-flash 会把 `{"type":"json_object"}` 回显进 content）。
- `parseList`：优先 `items` 数组；没有就把带认识键的对象逐个收下；最后过滤 `isEmpty`。

### 8.4 `Prompt.kt`（53 行）

- 两版 `container_guess` 行：`GUESS_GENERIC`（通用容器词）与 `GUESS_FROM_LIST`（**从文末「可选位置」清单里原样抄一条完整路径**）。
  有候选时是**替换**而不是追加——注释记着 2026-10-04 马克杯实测：两句冲突时模型听了贴着字段的那句。
- `template()` 全文要点：最多 4 件、同一件不要重复列、**承托面不算一件**、只输出 `{"items":[…]}`、
  认不出填 `[]`、`name` 用口语叫法且必填、`brand` 仅当照片上文字清晰可读、`description` ≤60 字、
  `tags` 3–8 个、**所有不确定的字段一律填 null，不要编造**。
- `systemFor(paths)`：空列表整段不出现（挂着"可选位置："后面空白只会诱导模型自己编一个）。

### 8.5 `AnalysisTrace.kt`（86 行）

- `data class TraceEntry(model, status: Int?, outcome: String, prompt, response)`（`:14-20`）。注释定义了两个字段的含义：
  **`status == null` 表示压根没连上**（超时、断网），那时 `response` 是空的；`outcome` 是 `FailureReason` 的名字或
  `success`，**只为了让文件能被一眼扫出来，判断不靠它**——查为什么填错看的是 `prompt` 与 `response` 两段原文。
- `interface AnalysisTrace { record(entry); count() }`（`:22-27`）+ `class FileAnalysisTrace(dir: File, now: () -> Long = System::currentTimeMillis)`（`:36-39`）。
  **第二个参数是给测试的**：传一个假时钟才能断言"文件名 = 时间戳"和滚动删除的先后，真机上不传。
- 三个数：`ANALYSIS_TRACE_MAX = 10`（**顶层 public const** `:33`，设置页 debug 那句"最多留 N 条"念的就是它，
  注释明写"一个数不许有两个版本"）；`MAX_CHARS = 8_000`、`MAX_SEQ = 1_000`（`:81,84`，companion 里 **private**）。
- `record` 每条一个文件：`dir.mkdirs()` → 写 `"%013d-%03d.json"`（`format` `:69`，毫秒 + 同一毫秒内第几条，
  **所以文件名排序就是时间排序**）→ `roll()`。文件内容只有这六个键：`at / model / status / outcome / prompt / response`。
- `clip`（`AnalysisTrace.kt:58-60`）超长时截到 8000 字，末尾补上「（截断，原长 」加原字数、「 字）」——
  注释说得很直白：**截断必须在文件里说出来，否则读到的是"看着完整"的假原文**。
- `roll()`（`:72-75`）`sortedBy { it.name }` 后删掉超出 10 条的最旧部分；
  `count()`（`:77`）= `dir.listFiles()?.size ?: 0`，**不认扩展名**，目录里塞进别的文件也会被数成留痕。
- `nextName`（`:63-67`）在 `seq < MAX_SEQ` 内找空位；**到上限就不再查存在性**，直接复用最后一格（同毫秒撞满 1000 条不可能）。
- 落点：应用私有目录 `File(appContext.filesDir, "ai-trace")`（`AppContainer.kt:71-73`）。
  **刻意不在 `files/photos` 底下**——那里的注释写着"备份只搬数据库行和那个目录，留痕（以及它里面的用户位置清单）
  不该跟着备份包出门"（`:68-69`）。改名或挪进 `files/photos` 都会把留痕送进备份包，是一条实打实的泄漏路径。

---

## 9. 到期提醒链路（三个文件）

```
MainActivity.onCreate → ReminderScheduler.schedule()          [排班]
WorkManager → ReminderWorker.doWork() → runOnce(ctx, force=false)
   → ReminderEngine.plan(now, force) → ItemDao.expiringBy(...) → 通知 → markExpireNotified
设置页调试按钮 → SettingsViewModel.reminderCheckNow → ReminderWorker.runOnce(ctx, force=true)
```

- **`data/work/ReminderScheduler.kt`**（27 行）：`PeriodicWorkRequestBuilder<ReminderWorker>(1, DAYS)` +
  `ExistingPeriodicWorkPolicy.KEEP`，`UNIQUE_NAME = "wheremy_expire_daily"`。
  KEEP 的理由：提前几天是 Worker 每次现读设置，重排只会白白丢掉已排好的班次。
- **`data/work/ReminderWorker.kt`**（123 行）：全部是 `companion object` 里的静态方法，`doWork`（`:31-35`）
  只调 `runOnce(applicationContext, force = false)` 并**丢掉返回的那句话**（`:32` 注释：排班要的是"发出去了就回写"，
  不是"给用户解释"），然后恒返 `Result.success()`——**不会因为通知发不出去就重试**。
  - `CHANNEL_ID = "wheremy_expire"`、`CHANNEL_NAME = "到期提醒"`、`NOTIFICATION_ID = 1001`（`:38-42`）。
    `ensureChannel`（`:66-70`）用 `IMPORTANCE_DEFAULT` 并设
    `channel.description = "物品临近保质期或已经过期时的聚合提醒"`（`:68`）——**这行别省**：系统"应用通知设置"页里
    频道名下面那一行念的就是它，没有它用户只看到一个词，不知道这类通知是干什么的。
  - `runOnce(ctx, force): String` 的顺序（`:52-64`）：`leadDays` 现读设置（`:56`）→ `plan` → `notifyProblem` →
    `post` → `markExpireNotified` → 回话。**发不出去就不回写 `expireNotifiedAt`**（`:60` 提前 return，`:62` 在它之后），
    KDoc 记着理由：先拒权限几天后才开的用户需要那几件还被说出来，代价只是每天白算一次窗口。
  - ⚠️ **`markExpireNotified`（`:62`）不在 `force` 判定之内**：按调试按钮（`force = true`）也会照样回写，
    于是吃掉"提前知道"那一次（`Daos.kt:69-70` 的第一支从此不成立）。到期当天那次仍会来（第二支），
    但**已经过期且从没说过的物品，按一次之后就再也不会被提醒**。这是 as-built，见 `RULES.md` §11.4。
  - 标题 `ReminderWorker.kt:115` 「Wheremy 到期提醒」、`smallIcon = android.R.drawable.ic_popup_reminder`、`setAutoCancel(true)`（`:113-119`）。
  - `notifyProblem` 两句话（`:86-90`，逐字见 `RULES.md` §11.4）；`canNotify`（`:78-79`）
    = `permissionGranted` **且** `NotificationManagerCompat.areNotificationsEnabled()`，而 `permissionGranted`（`:73-76`）
    在 Android 13 以下**恒为 true**。
  - `wallIntent`（`:100-104`）带 `EXTRA_ENTRY = ENTRY_EXPIRING` + `FLAG_ACTIVITY_CLEAR_TOP or SINGLE_TOP`
    （注释：应用还活着时复用顶上那个实例，新开实例会从空白状态起步，筛选反而看不见）。
    `post` 里 PendingIntent 的 **requestCode 传的也是 `NOTIFICATION_ID`（`:109`）**，配
    `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`（`:111`）——这两件合起来才让"一天只有一格、内容被覆盖"成立。
    真正 `notify()` 用的是 `getSystemService(NotificationManager::class.java)`（`:120`），
    而检查可用性用的是 `NotificationManagerCompat`（`:79`）；**这一处不统一是现状**，别顺手"改成都用 Compat"。
  - KDoc 记 2026-10-03 真机结论：HyperOS 在总开关关着时**静默驳回**就地申请，所以只跳系统页（`notificationSettingsIntent` `:92-94`）。
- **`domain/ReminderEngine.kt`**（58 行）：`plan(now, force)` 的三个边界全走 `util/Dates.kt`——
  `windowStart = localStartOfDay(now)`、`todayEnd = windowStart + 1 天`、`windowEnd = expireWindowEnd(leadDays, now)`；
  `force` 走 `expiringIgnoringNotified` 否则 `expiringBy`。**照片墙那颗「快到期/已过期」用的是同一个 `expireWindowEnd`**，
  否则点进通知看到的和通知里说的会差一天（`RULES.md` §11.5）。`body(count, nearestDays, todayCount)` 四种措辞见 §7。

---

## 10. 备份链路（三个文件）

### 10.1 `BackupFormat.kt`（63 行）

`ENTRY_MANIFEST = "manifest.json"`、`ENTRY_DATA = "data.json"`、`PHOTOS_PREFIX = "photos/"`；
`BackupManifest(schemaVersion, exportedAt, itemCount, appVersion)`；
`BackupData(locations, items, photos, tags)`（**直接是四表 entity 列表**）；
`enum ImportMode { MERGE, OVERWRITE }`；`BackupSummary(locations, items, photos, bytes)`；
`BackupFormatException(message)` —— 消息一律中文可显示；
`Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }`。

### 10.2 `BackupIo.kt`（29 行）

```kotlin
interface BackupIo {                       // :15
    suspend fun openOutput(target: Uri): OutputStream?   // :16
    suspend fun openInput(source: Uri): InputStream?     // :17
}
class ContentResolverBackupIo(ctx) : BackupIo            // :20，两个方法都是 withContext(Dispatchers.IO) 包一句 openOutputStream / openInputStream
```

- 存在的理由写在注释里：备份要落到 SAF 选出来的 `Uri` 上，而 `ContentResolver` 在单测里不好搭，
  所以只把"打开流"这一步抽出来（`:10-13`）。**ViewModel 只管状态机，不认识 ContentResolver。**
- 返回的流**由调用方负责关闭**——zip 尾记只在 close 时才写得完整（注释原话，`:13`）。
  实际关闭点是 `SettingsViewModel.kt:236` 与 `:244` 的 `use {}`。
- **两个方法都是 `suspend`**，接口上没有 `throws`，但 `openOutputStream` 可能抛（SecurityException 之类），
  所以调用处必须在 `runCatching` 里；**返回 null 只代表"连流都没打开"**，不是一种异常。
  这条区分是 `operate()` 的第三支（见下）唯一的来源。

### 10.3 `BackupRepository.kt`（285 行）

`class BackupRepository(db, photoStore, appVersion, io: CoroutineDispatcher = Dispatchers.IO)`（`:30-36`）
— **构造参数刻意没有 `SettingsRepository`**（NFR-03：API key 只活在 DataStore，导出通道物理上碰不到它，注释 `:28`）。
第 4 个参数是整包读写跑的调度器，注释说明单测传就地执行的调度器，免得断言抢在真实线程之前跑完（`:34`）。

- **`export(into: OutputStream): BackupSummary` `:37`**：`db.withTransaction` 里读四表（`:40-47`）→
  `ZipOutputStream(BufferedOutputStream(CountingOutputStream))` → 写 manifest/data →
  照片按路径 `putOnce` 去重（**两件物品共用一对文件时只写一份**；`:59-60` 注释：zip 里写第二个同名条目会直接抛
  `ZipException`，整个导出失败，而 `summary.photos` 因此是"写出的文件对数"）；
  `summary.bytes` 要在 `use {}` **之外**才读（关了流才有总数）。
  四表读必须在同一个事务里：`ItemRepository.newDraft` 是"先写物品、再写照片"两条语句，
  夹在中间读就会导出一个所属物品不在包里的照片行，那种包导入时被 `validate` 判成损坏（`:38-39` 注释）。
- **`import(from: InputStream, mode: ImportMode): BackupSummary` `:84`** 顺序固定：
  1. `readBundle` `:109` — 逐项跳过目录条目（`!name.endsWith("/")`，`:117` 注释：别的打包工具会给每个目录写一条
     `photos/originals/` 条目，它既不是文件也不是攻击）；认三个前缀（manifest / data / `photos/`）。
     照片路径必须过 `isPhotoPath()`：只认两个目录名、恰好一个 `/`、字符集"字母数字 - _ . /"、不含 `..`，
     不过就抛 `BackupRepository.kt:124` 「备份里有不合法的文件名」（后面接包里的原始条目名）。收完后四道拒收：
     缺 manifest 或 data → `BackupRepository.kt:133-135` 「这不是 Wheremy 的备份包」（后面接「：少了 」加文件名，**只报第一个缺的**）；
     manifest 解析不出来 → `BackupRepository.kt:138` 报「读不出来」并点名 `manifest.json`；
     `manifest.schemaVersion > DB_SCHEMA_VERSION` → `BackupRepository.kt:139-142` 「这个备份来自更新的版本」（后面带两个版本号）；
     data 解析不出来 → `BackupRepository.kt:145` 「读不出来，文件可能被截断了」。
  2. 照片先解到 `INCOMING_DIR = ".incoming"`（`:22`，**private**）。
     `:21` 的注释是这条的承重：**rootDir 下只有 `originals/` 与 `thumbs/` 会被当成照片**，
     所以暂存目录必须是第三个别的名字，否则暂存中的照片会被算成正式照片参与回收与导出。
  3. `validate` `:149` — **四条**完整性（顺序就是抛出顺序，一次只报一条）：
     物品的 `locationId` 不在包里（`:152`，报的是那件物品的名字，名字空则当 `BackupRepository.kt:153` 「未命名物品」）、
     照片的 `ownerId` 找不到主人（`:155-157`，按 `ownerType` 分别拿位置集/物品集比）、
     `filePath` 或 `thumbPath` 为空（`:161`）、标签挂在不在包里的物品上（`:165`）。
     **文件缺失放行、空路径拒**（批次 AM 钉住，`:158-160` 注释给了两条理由，判据见 `RULES.md` §12.3）。
     `validate` **不查位置的 `parentId`**——所以"父不在包里"的位置能过这一关、死在下面第 4 步的事务里，
     两侧各管一半见 `RULES.md` §12.4 与本文 `parentsFirst`（:197-216）。
  4. **一个事务**写四表（`writeRows`：MERGE 按主键逐表跳过已存在；`parentsFirst` 处理父序与环）；
     `SQLiteConstraintException` → 「备份里有对不上的引用，已取消导入，原来的数据没有动」。
  5. `removeLocalPhotosNotIn(keep)` `:95` — **仅 OVERWRITE**，且跑在事务提交之后、下一步之前。
  6. `moveStagedPhotos` `:98` — `renameTo` 失败退化成 copy。
  7. `finally` 删 `.incoming`（`:86`/`:103`；`removeLocalPhotosNotIn` 只走 `PHOTO_DIRS`（`:24`），
     所以它永远碰不到暂存目录）。
- `writeRows` `:171` 里另外几件：OVERWRITE 是**在同一个事务里先删四表**（顺序 item → 标签 → 照片 → 位置，`:174-177`，
  反着写会被外键即时检查拒掉）；MERGE 按各表自己的主键逐行比对，本机已有的原样保留（`:179-187`，
  标签的"主键"是 `itemId to tag` 这一对）；回来的 `BackupSummary.photos` 是 **`filePath` 去重后的对数**而不是行数
  （`:192-193`，注释：与导出口径一致，两件共用一张图时不该报二），且导入侧 `bytes` 恒为 `0L`
  ——所以设置页那句报数对导入没有大小，`describe()` 靠 `bytes > 0` 分两样显示（`SettingsViewModel.kt:278-281`）。
- `parentsFirst` `:197-216`：分批把"父已知或父不在本批里"的排前面；**一批都排不出来只可能是成环，
  剩下的原样交给外键去拒**（`:209-210`）。`:207` 的 `it.parentId !in ids` 同时把**位置孤儿**归进第一批，
  于是它在写表时当场被外键拒掉，报的是 `:93` 那句笼统的引用错——与读侧（`LocationTree.roots()` 把父不存在的当根）
  是两半，判据见 `RULES.md` §12.4。
- `moveStagedPhotos` `:226-233`：`renameTo` 在跨分区时**只是返回 false，不抛异常**（`:230` 注释），那时退化成
  `src.copyTo(target, overwrite = true)`，而**这一次复制包在 `runCatching` 里、失败不吭声**（`:231`）。
  后果是：行已经提交、文件没搬进去、导入照样报成功——那一件从此显示不出图。
  重做时若改成"复制失败就整包算失败"，得同时决定行要不要回滚，别只加一句 throw。
- `removeLocalPhotosNotIn(keep)` `:218-224`：判据是**"包里有没有这条字节"**（`keep` 在 `readBundle` `:125` 攒出来），
  而不是"新库有没有行引用它"；所以包里的照片只要字节在就保住，本机多余的孤儿也照样删——
  **孤儿文件不会被导入回收**，唯一的回收规则是 §5.2 的 `deleteUnreferencedPhotoFiles`。
- **上面这些异常一路抛给 `SettingsViewModel.operate(label, fallback, action)`（`SettingsViewModel.kt:253-275`）**，
  那里只有三种收场，**且只有 `BackupFormatException` 的 message 会上屏**（`:265`）：
  其它异常一律换成各自的 `fallback` 中文句——导出是「那个位置写不进去，可能是磁盘满了或没给权限，本机数据没有受影响」（`:233`），
  导入是「这个包读到一半就断了，已取消导入，本机数据没有动」（`:241`）；
  `action` 返回 null（连文件都没打开，`:235`/`:243`）走第三种「打不开那个文件，换个位置再试」（`:269`）。
  **重做时这三条分支的分工不许合并**：把 `err.message` 无条件贴出来就是把英文 `IOException` 贴给用户，
  而把 null 当异常会让"没权限"和"读断了"说同一句话。

---

## 11. 呈现层

### 11.1 五个 ViewModel 与它们的 state（全部 `StateFlow`，`_state.update {}` 改）

| VM | 构造参数 | state 类 | 关键公开方法（行） |
|---|---|---|---|
| `GalleryViewModel` `:37` | `items, locations, settings, val inboxMode: Boolean` | `GalleryUiState(query, filters, items, allTags, moveId, moveTargets, pendingDelete, loading, error)` `:21` | `refresh():53`、`setQuery:89`、`setCategory:94` / `toggleSeason:98` / `setTag:102`（**点同一个 = 取消**）、`setOnlyInbox:106`、`toggleExpiringSoon:111`、`showExpiringSoonOnly:126`（通知入口，**清掉 query 与三个筛选**）、`prepareMove:139`/`closeMove:149`/`move:153`、`askDelete:166`/`closeDeleteAsk:174`/`remove:178`、`consumeError:187` |
| `TreeViewModel` `:50` | `items, locations, io = Dispatchers.IO` | `TreeUiState(currentParentId, breadcrumb, children: List<TreeNodeUi>, currentItems, currentPhoto, loading, moveId, moveTargets, error, message)`；`TreeNodeUi(id, name, kind, directCount, subtreeCount, hasChildren, thumbPath)` `:20` | `refresh:63`、`open:67`、`up(): Boolean :73`（**返回是否消费了返回键**）、`create:118`、`updateNode:129`、`prepareMove:140`（**候选里过滤掉 descendantIds**，防环在源头）、`closeMove:155`、`shift:160`、`move:177`、`remove:194`、`setPhoto:210`（根层 `parentId ?: return`——根没有主人）、`clearPhoto:220`、`consumeError:230`/`consumeMessage:234` |
| `DetailViewModel` `:34` | `items, locations` | `DetailUiState(itemId, summary, photos, breadcrumb, moveTargets, moveOpen, loading, missing, pendingDelete, error, deleted)` `:18` | `load:43`（**同 id 只重置 error/missing，不同 id 整份 state 重建**）、`prepareMove:94`/`closeMove:104`/`moveTo:108`、`askDelete:122`/`closeDeleteAsk:131`/`remove:135`（成功置 `deleted = true`，**不 reload**）、`consumeError:145`/`consumeDeleted:149`。`missing` 与"读的时候抛了"**故意分开**（界面说的话不能一样） |
| `ReviewViewModel` `:96` | `capture: CaptureFlow, items, locations` | `ReviewUiState(slots, selected, analyzing, failure, locations, finished, confirmExit, error, notice)` `:49` + 六个派生计数（`notStored:70`、`leftBehind:77`、`alreadySaved:84`、`hasStoredItem:87`、`unsaved:90`、`canSave:93`）；`ReviewSlot(itemId, draft, containerGuess, suggestedLocationId, thumbPath, touched, saved)` `:28` + `isThrowawayShell:45` | `hasDraft:106`（外壳用它决定"换一张"还是"新建"）、`markTouched:113`、`start:115`、`replacePhoto:144`、`retryAnalysis:160`、`load:252`、`createLocation:269`、`select:285`、`update:289`（**不标记 touched**）、`setLocation:291`/`toggleSeason:296`/`toggleTag:303`/`setQuantity:310`/`setExpires:315`（这五个都标）、`save:326`、`keepForLater:370`、`discardCurrent:395`、`requestExit:415`/`exitWithoutSaving:424`/`exitKeepingEdits:427`/`cancelExit:432`、`consumeFinished:458`。私有：`bindPhoto:135`、`guard:176`、`apply:191`、`merge:229`、`refreshLocations:280`、`saveAnUnsavedSlot:346`、`finish:387`、`closeWithoutAsking:443` |
| `SettingsViewModel` `:56` | `backup, io, settings, trace, reminderCheckNow: suspend () -> String`（**无默认值**：漏接一处编译就不过） | `SettingsUiState(leadDays, visionEnabled, baseUrl, model, apiKey, busy, message, error, aiUsable, savedModel, traceCount)` `:25` | `setLeadDays:140`（乐观写 UI 再落盘）、`runReminderCheckNow:157`、`editBaseUrl:165`/`editModel:170`/`editApiKey:175`、`setVisionEnabled:181`（**同步更新 aiUsable**，落盘失败吞掉）、`saveAiConfig:191`、`exportTo:231`/`importFrom:239`（共用 `operate:253`）、`consumeMessage:283`/`consumeError:287` |

`SettingsUiState` 的两个易错规则：`aiUsable` 与 `savedModel` 说的是**库里存着的那份**，不是输入框草稿
（`SavedAi` 私有类 `:88` 存落盘真相；否则编辑到一半界面会宣称一个从没生效过的模型）。

**`GalleryViewModel` 的并发护栏**（`safeLoad():58` + `load(seq):65`）：`loadSeq` 自增，
每个挂起点都必须在 `_state.update` **之前**，因为 `update` 是 inline CAS 块而 `summaries` 是 N+1 查询；
`if (seq != loadSeq) return` `:78` 丢旧结果。`load()` 会把 `filters = effective` 写回 state，
所以 `inboxMode` 每次读都把 `onlyInbox` 钉成 true。

### 11.2 `ui/WheremyRoot.kt`（403 行）—— 唯一的导航层

**两个枚举**：`RootTab { GALLERY, TREE, INBOX, SETTINGS }` `:63`、`PhotoTarget { NONE, ITEM, LOCATION }` `:66`。
持久化的 tab 是 **String**，`currentTab` 解析不出来时回落 GALLERY `:106`。

**状态清单**（`rememberSaveable` 的都要在进程死亡后还能重建语义）：
`tab:76`、`detailId:77`、`reviewOpen:78`、`reviewItemId:80`（null=新增，非空=编辑）、`reviewSeq:81`（VM key + effect key）、
`liveReviewVm:83` 与 `liveTreeVm:85`（**`remember` 而非 saveable**：只活在组合里，用来把照片投给当前屏幕上那个 VM）、
`askingFor:87`、`photoTarget:89`（"有一张还在飞"）、`viewfinderOpen:91`、`gate:93`、
` DisposableEffect(lifecycleOwner){…}` `:96-102`（`ON_RESUME` 时重算 gate）、`askCamera:103`。

**屏幕选择 = 代码求值顺序 = 优先级**（自上往下覆盖）：

```
来源弹窗 :214      （在所有分支之前组合 → 永远浮在最上）
if (reviewOpen) 分支起点 :224 → return :264   { ReviewScreen :256 ; viewfinder() :262 }   ← 取景屏是叠层
detailId?.let 分支起点 :278 → return :293     { DetailScreen :281 }                        ← 这一支没有取景屏叠层
Box { Scaffold(底栏 :301 / FAB :328 / when(currentTab) :339) ; viewfinder() :394 }
```

- 取景屏**叠层而不是替换**：`ReviewScreen` 一被 dispose，`liveReviewVm` 就清空，
  "换一张照片"会变成新建一件物品（KDoc `:187-190`）。
- `say` `:112` 在 `reviewOpen` 时**不发 snackbar**：录入页整屏替换，外壳的 `SnackbarHost` 不在组合里，
  `showSnackbar` 会永远挂在那里（KDoc `:108-111`）。
- BackHandler 注册次序（后注册的优先）：`:193` 关取景屏 → `:252` `vm::requestExit`（系统返回和那颗 ✕ 走同一个门）
  → `:280` 退详情页 → `:366` `TreeBackHandler`（`:400-403` 单独抽出，避免树刷新重算外壳；根层 disabled 让系统退出 App）。
- **照片路由只有 `deliverPhoto(bytes)` 一处** `:123-145`：读 `photoTarget` → 立刻归零 →
  空字节说「读不到这张照片，换一张试试」→ 位置走 `liveTreeVm.setPhoto`（**不经 AI**）→
  录入页开着且 `live.hasDraft` 就 `replacePhoto`，否则 `pendingCapture = bytes; reviewItemId = null; reviewSeq += 1; reviewOpen = true`。
- **进录入页的分派** `LaunchedEffect(reviewSeq)` `:236-246`：`pending → vm.start / editId → vm.load / else → reviewOpen = false`
  （最后一支是进程死亡：字节只在内存里，没字节可恢复，就关页而不是开个空白表单）。
- 通知入口：`:271-276` 只把 tab 切到 GALLERY、`detailId = null`，**不消费 entry**；真正消费在 GALLERY 分支 `:346-351`
  （`vm.showExpiringSoonOnly(); container.entry.value = null`）。录入页开着时这段在 `return` 之后，
  所以通知永远抢不走那张填了一半的表单。

### 11.3 `ui/camera/`（4 个文件：`CameraSource` / `CameraAccess` / `CameraScreen` / `CameraXSource`）与 `data/camera/CameraShutter.kt`

- `CameraSource { val shutter; @Composable fun Preview() } ` `:10`——快门与预览面捆在一起，容器一次换掉整台相机。
- `CameraShutter { suspend fun capture(): ByteArray? }`——**接口里不出现任何 CameraX 类型**，取景屏因此能在没有相机的 JVM 里被测；
  契约是"要么给 JPEG 字节要么 null，**不抛**"。
- `CameraAccess.kt`（32 行）：`gate` 读 `FEATURE_CAMERA_ANY` + `CAMERA` 权限；`settingsIntent` = `ACTION_APPLICATION_DETAILS_SETTINGS`。
- `CameraScreen.kt`（100 行）：`LaunchedEffect(gate)` **自动申请一次**；三态文案
  「这台机器没有可用的相机」/「取景要用相机，先给它授权」+「打开相机权限」/预览；
  失败提示「没拍到，再按一次，或者改用相册」；底排「取消」「拍这张」（`enabled = !busy`）「改用相册选」；`Column` 自带不透明背景。
- `CameraXSource.kt`（110 行）：`CAPTURE_MODE_MINIMIZE_LATENCY`；**每次进屏 `unbindAll()` 再绑**；
  `targetRotation` 交 CameraX 写 EXIF（方向在 `PhotoStore` 烤进像素）；`capture()` **任何失败都回 null**；
  `jpegBytes()` 只认单平面且非 0 字节。KDoc：这一层没有 JVM 用例，由批次 AQ 的真机自验覆盖。

### 11.4 `ui/common/`（5 个文件，被多屏共用）

| 文件 | 行 | 提供什么 |
|---|---|---|
| `UiFormat.kt` | 57 | `expireHint`（三档，`UiFormat.kt:8-10`：负数 "已过期 k 天"、零 "今天到期"、正数 "还剩 k 天"）、`formatBytes`、**`deleteWarningOf(PhotoDeletion)` 四档**、**`failureText(FailureReason)` 11 条**、`PendingDelete(itemId, warning)` `:57` |
| `ItemRow.kt` | 74 | `ItemThumbRow`：48dp 圆角缩略图（无图给 `surfaceVariant` 空块）、名称 `ifBlank { "未命名物品" }`、第二行 `listOfNotNull("x$n", "到期 yyyy-MM-dd", "在 $path").joinToString(" · ")` |
| `LocationPicker.kt` | 86 | `LocationPickerDialog(title, options, selectedId, rootLabel = "暂不归类，先进「待归类」", onCreate?, …)`；`onCreate` 非空时给「在「X」下新建」输入框 +「新建并选中」（`enabled = newName.isNotBlank()`）；确认按钮「关闭」 |
| `LocationFlatten.kt` | 21 | `LocationOption(id, label, depth)` + `flattenLocations(tree)` 前序缩进表（选择器和树页共用） |
| `SnackbarMessage.kt` | 37 | `ComposeUi` 的 snackbar 消费件：**两个 `LaunchedEffect` 缺一不可**（先把消息抄进本地态并消费掉 VM 里的，再挂起等条幅自己消失）。少了第一个会因消费改 key 而取消 `showSnackbar`；少了第二个条幅不消失 |

### 11.5 `ui/theme/Theme.kt`（32 行）

四个色值常量（:10-13）+ 两张 scheme（:15-23），全部逐字对齐代码：

| | light | dark |
|---|---|---|
| `primary` / `tertiary` | `Teal40 = 0xFF00695C` | `Teal80 = 0xFF7FD6C5` |
| `onPrimary` | `Color.White` | `0xFF00201B` |
| `secondary` | `Amber40 = 0xFF9A6A00` | `Amber80 = 0xFFFFD37A` |
| `onSecondary` | `Color.White` | `0xFF332500` |

- ⚠️ **`tertiary` 与 `primary` 同色**（:17,22）是为了压掉 M3 默认的紫红 tertiary——但**当前没有任何界面用 tertiary**
  （全量 grep `ui/` 下除 `Theme.kt` 外无引用）。所以它是防御性设定：改它看不见任何变化，
  **别把它当"调了就有效果"的配色旋钮**；真要加用 tertiary 的界面，先决定它该不该跟 primary 一致。
- `isSystemInDarkTheme()` 选一张。**不做动态取色**（否决项 D4：动态色会让缩略图的色彩判断失真）。
- 🔒 **不覆盖 `typography` 也不覆盖 `shapes`**：`MaterialTheme(...)` 只传 `colorScheme`（:28-31），
  所以字号阶梯、圆角全用 M3 默认值。重做时看到"文档里没写排版规范"不是漏写——是这个 app 就没改它；
  界面里出现的 `.titleMedium` / `.bodyMedium` 等都是 MaterialTheme 自带的。
- `values/themes.xml` 只有一个空壳：`<style name="Theme.Wheremy" parent="android:Theme.Material.NoActionBar" />`，
  供 Manifest 的 `android:theme` 引用（窗口背景/启动那一瞬用），**Compose 里的配色与它无关**。


---

## 12. 三条主链路的调用序列

### 12.1 选一张照片 → 录进去（AI 成功）

```
FAB → askForPhotoSource(ITEM) :212 → 来源弹窗 → startAlbum :167 → PickVisualMedia
 → picker 回调 :147 →(IO) readBytes → deliverPhoto :123
 → pendingCapture=bytes; reviewSeq+=1; reviewOpen=true
 → LaunchedEffect(reviewSeq) :236 → vm.start(pending) :115
 → analyzing=true; launch { guard { apply(capture.capture(jpeg) { bindPhoto(it) }) } }
     CaptureFlow.capture :54
       ├─ items.newDraft(jpeg)              ← 此刻已经落库两行
       ├─ onDraftSaved(id) → bindPhoto :135 ← thumbPath 立刻上图，不等 AI
       └─ analyzeInto(id, pendingAnalysis ?: jpeg)
             ├─ locations.tree() 一次        ← 候选与匹配同一份快照
             ├─ analyzePhoto.analyze(jpeg, paths)  → ApiPhotoAnalyzer
             ├─ items.applyAnalysis(id, results[0])
             ├─ suggestLocation(containerGuess, tree) takeIf { locationId == null }
             └─ others = results.drop(1)     ← 每件各算一次建议
 → apply(out) :191 → slots = [merged] + kept + fresh; selected=0; analyzing=false
 → 用户改字段（markTouched）→ save :326（按钮 `ReviewScreen.kt:101`「保存这一件」）→ analysis?.cancel() → items.confirm(draft) → CONFIRMED
```

### 12.2 搜索一个中文词

```
GalleryScreen 搜索框 → vm.setQuery :89 → safeLoad → items.rankedSearch(query, filters) :209
 → itemDao.searchLike("%q%") [300 条上限] → matches() 内存筛（category/season/tag/onlyInbox/expireUntil）
 → SearchRanker.rank（100/80/60/40/20/0） → locations.tree() → items.summaries(...)（tags + thumb + breadcrumb）
 → state.items → LazyVerticalGrid(Adaptive 156dp) 渲染 GalleryCell
```

### 12.3 通知点进来

```
ReminderWorker.doWork → runOnce(force=false) → ReminderEngine.plan
 → itemDao.expiringBy(windowStart, todayEnd, windowEnd) → 发通知 → markExpireNotified(ids)
用户点通知 → wallIntent(CLEAR_TOP|SINGLE_TOP, EXTRA_ENTRY=expiring)
 → onNewIntent :32 → container.entry = "expiring"
 → WheremyRoot :271 切到 GALLERY + detailId=null（不消费）
 → GALLERY 分支 :346 → vm.showExpiringSoonOnly() :126（清 query 与三个筛选，只留 expireUntil = expireWindowEnd(leadDays)）
                    → entry = null
```

---

## 13. 跨层顺序不变量（改动前必读）

1. `newDraft`：物品行 + 照片行**同事务**；失败删刚写的文件。
2. `CaptureFlow.capture`：**`onDraftSaved` 必须在分析之前**（图先进界面，AI 迟到）；
   `bindPhoto` 必须同时把 `draft.id` 补上（否则分析没回来就保存会带着空串 id 调 `confirm`）。
3. `swapPhoto`：**新文件 → 改行 → 删旧文件**。
4. `save()`：**`analysis?.cancel()` 在任何写入之前**（"按下保存那一刻，在飞的分析结果就不再有权改这件物品"）。
5. `saveAnUnsavedSlot`：`created` 在行存在的那一刻就记下，失败时 `items.delete(created)` 还回去。
6. `closeWithoutAsking`：shells 先快照 → `analysis?.cancel()` → 才 launch 删除 → **`finished = true` 排在删除之后**。
   反了会造出"退出时没东西可删、删完才发现行刚落库"的幽灵记录（批次 X/AR 的事故）。
7. `discardCurrent` 的双守卫：`slot.itemId != null || slots.size < 2` 时**什么都不做**——已落库的行不能从这条路走。
8. `delete(itemId)`：**先删行**（item → tags → photos）再 `deleteUnreferenced`。
9. `LocationRepository.setPhoto`：require 行在 → 写文件 → 事务替换 → 失败删新 → 最后清旧。
10. `BackupRepository.import`：读包 → validate → 事务落库 → 删多余（仅 OVERWRITE）→ 搬照片 → finally 清 `.incoming`。
11. `ReminderWorker`：**通知真发出去了才 `markExpireNotified`**。
12. `GalleryViewModel.load`：所有挂起点在 `update` 之前 + `seq` 护栏；`TreeViewModel` 的 `parentId`（VM 真相）写在读之前，`currentParentId`（state）写在读成功之后。
13. `deliverPhoto`：先读 `photoTarget` 再归零。
14. `MainActivity`：`reminders.schedule()` 在 `setContent` 之前；`onCreate` 读 extra 要过 `savedInstanceState == null` 闸门。
15. 冷启动补算 `search_text` 用 `startupScope`，不跟界面生命周期走。

---

## 14. 测试架构

- **只有 `app/src/test`（JVM）**，**没有 `androidTest`**。Robolectric 4.16.1 让 Compose 界面测试跑在 JVM 上（否决项 D7）。
- **39 个结果类 / 481 例**；另有 3 个 helper（`FakeTrace.kt`、`TestExecutors.kt`、`TestSettings.kt`）**没有 `@Test`**，不计入类数。
  按跑法分：**12 类纯 JVM**（没有 `@RunWith(RobolectricTestRunner)`）+ **27 类 Robolectric**。
  1. **纯 JVM**：`LocationTreeTest`、`SearchRankerTest`、`SuggestLocationUseCaseTest`、`AnalyzePhotoUseCaseTest`、
     `CameraGateTest`、`DatesTest`、`SearchTextBuilderTest`、`AnalysisParserTest`、`AnalysisTraceTest`、
     `ApiPhotoAnalyzerTest`（用 `mockwebserver`，仍不需要 Android 运行时）、`UiFormatTest`、`BuildSmokeTest`。
  2. **Robolectric 数据层**（in-memory Room；要落文件的走 `ctx.filesDir` 下的测试子目录，**不 mock 文件系统**——
     照片回收与备份去重只有真文件才能证伪）：`DatabaseSmokeTest`、`ItemSearchDaoTest`、`PhotoStoreTest`、
     `BackupRepositoryTest`、`ItemRepositoryTest`、`LocationRepositoryTest`、`CaptureFlowTest`、`ReminderEngineTest`。
  3. **Robolectric 系统件**：`ReminderWorkerTest`（通知）、`AppContainerTest`（装配与留痕目录）、
     `LauncherIconTest`（图标资源）、`CameraWiringTest`、`CameraAccessTest`。
  4. **Compose 界面测试**（`createComposeRule` + Robolectric）：`WheremyRootTest`(20)、`ReviewViewModelTest`(48)、
     `ReviewScreenTest`(30)、`SettingsScreenTest`(16)、`GalleryScreenTest`(15)、`GalleryViewModelTest`(12)、
     `TreeViewModelTest`(19)、`TreeScreenTest`(11)、`DetailScreenTest`(10)、`DetailViewModelTest`(8)、
     `CameraScreenTest`(9)、`SettingsViewModelTest`(22)、`LocationPickerDialogTest`(4)、`SnackbarMessageTest`(1)。
- **判绿双证**：`BUILD SUCCESSFUL` **且**本轮 39 份 XML 的 mtime 是新的；跑回归要带 `--rerun`。
- 命令：`gradle :app:testDebugUnitTest`（本机 gradle 来自 version-fox，无 wrapper）。
- 相机那一层（`CameraXSource`）与真机行为（通知、系统选图器、EXIF、图标）**没有自动化覆盖**，见 `REBUILD.md` §6 的人工清单。

---

## 15. as-built 与原 spec 的偏离（以及为什么）

下表里的"spec 的位置"都指**原设计文档** `docs/superpowers/specs/2026-10-02-wheremy-design.md` 的小节号，
不是本文的节号：

| spec 的位置 | 原方案 | 实际做的 | 评价 |
|---|---|---|---|
| spec §7 请求 | AI 默认 `qwen-vl-max` | 默认 `https://api.deepseek.com` + `deepseek-flash` | 用户实际注册的那家；换厂商只改两个字段的性质没变 |
| §7 请求 | `max_tokens = 600` | **2000** | 推理模型的 reasoning token 也算额度，600 会把 JSON 截在半句（实测） |
| §4 目录 | `remind/`、`ui/capture/`、`ui/inbox/`、`AppContainer.kt` 在根 | `data/work/`、`ui/review/`、待归类复用 `GalleryScreen(inboxMode=true)`、`di/AppContainer.kt` | 收件箱与照片墙本就是同一个查询的两种筛法，不该有两个屏 |
| §4 目录 | `domain/SaveItemUseCase`、`SearchItemsUseCase`、`BuildLocationTreeUseCase`、`BackupUseCase` | **没有这四个类**：确认与搜索落在 `ItemRepository`，建树落在 `LocationTree`，备份落在 `BackupRepository` | 少一层空壳转发；domain 只留下真正纯的 8 个 |
| §4 代码 | `PhotoAnalyzer.analyze(jpeg)` | `analyze(jpeg, locationPaths)` | 提示词要带用户真实路径（FR-AI-04） |
| §6.6 导出 | zip 里放 `wheremy.db`（Room 文件） | zip 里放 `data.json`（四表行的序列化）+ `manifest.json` + `photos/**` | **更好**：换机不受 SQLite/Room 版本差异摆布，包可读可 diff。别退回旧方案 |
| §5 设置 | DataStore 里有 `theme` | 没有 theme 设置；固定配色 + 跟随系统深色 | 否决项 D4 |
| §6.3 | 网格卡片显示"位置末两级" | 显示**完整面包屑** | 完整路径才回答得清"到底在哪" |
| §6.3 | 空结果提示"换个词，或去收件箱看看" | 三句分开的空态（按当前筛选 / 待归类 / 还没有物品） | 一句话盖不住三种情况，且会骗人 |
| §7 成本 | 设置页展示累计调用次数 | 展示 **AI 留痕条数**（上限 10） | 未等价，留作 ⬜FR-TRC-04 |
| §8 | 相机权限被拒 → 说明用途的二次对话框 | 自动申请一次 + 屏上常驻「打开相机权限」 | 🟡 留作 FR-PRM-02 |
| §8 | 删非空位置 → 给"把物品移到…"快捷入口 | 只给那句提示 | 🟡 留作 FR-LOC-05 |
| §9 | `createAndroidComposeRule` + in-memory Room 的 DAO 测试 | Robolectric + Compose 测试全在 JVM | 一条命令、无设备，覆盖反而更多 |
| spec §5 照片 | 原图/缩略图/分析图三档 | 三档都在（1600/256/1024），另加 **EXIF 方向烤进像素** | spec 给了原图 1600 与分析图 1024、**没给缩略图尺寸**（256 是 as-built 自己定的）；spec 也没提方向，真机竖拍照会躺倒 |

---

## 16. 文件清单（57 个，按行数排）

```
501 ui/review/ReviewScreen.kt        469 ui/review/ReviewViewModel.kt
403 ui/WheremyRoot.kt                365 data/repo/ItemRepository.kt
341 ui/gallery/GalleryScreen.kt      331 ui/tree/TreeScreen.kt
322 ui/settings/SettingsScreen.kt    302 ui/settings/SettingsViewModel.kt
285 data/backup/BackupRepository.kt  248 ui/detail/DetailScreen.kt
241 ui/tree/TreeViewModel.kt         202 data/repo/LocationRepository.kt
190 ui/gallery/GalleryViewModel.kt   183 data/vision/ApiPhotoAnalyzer.kt
152 ui/detail/DetailViewModel.kt     136 data/photos/PhotoStore.kt
126 domain/CaptureFlow.kt            126 data/db/Daos.kt
123 data/work/ReminderWorker.kt      110 ui/camera/CameraXSource.kt
104 di/AppContainer.kt               100 ui/camera/CameraScreen.kt
 89 data/settings/SettingsRepository.kt
 86 ui/common/LocationPicker.kt       86 data/vision/AnalysisTrace.kt
 85 data/vision/AnalysisParser.kt      81 domain/LocationTree.kt
 81 data/db/Entities.kt                74 ui/common/ItemRow.kt
 70 domain/SuggestLocationUseCase.kt   63 data/backup/BackupFormat.kt
 61 data/repo/Models.kt                58 domain/ReminderEngine.kt
 57 ui/common/UiFormat.kt              53 data/vision/Prompt.kt
 43 MainActivity.kt                    42 util/Dates.kt
 38 data/db/WheremyDatabase.kt          38 data/db/SearchTextBuilder.kt
 37 ui/common/SnackbarMessage.kt        37 WheremyApp.kt
 32 ui/theme/Theme.kt                   32 ui/camera/CameraAccess.kt
 30 di/AppViewModel.kt                  29 data/vision/PhotoAnalyzer.kt
 29 data/backup/BackupIo.kt             27 data/work/ReminderScheduler.kt
 24 domain/AnalyzePhotoUseCase.kt        23 domain/SearchRanker.kt
 21 ui/common/LocationFlatten.kt         21 data/vision/AnalysisResult.kt
 21 data/repo/PhotoReclaim.kt            16 ui/camera/CameraSource.kt
 13 domain/CameraGate.kt                 11 data/camera/CameraShutter.kt
  7 domain/LocationTreePolicy.kt          6 data/vision/OfflinePhotoAnalyzer.kt
```

**两个最大文件是有信号的**：`ReviewScreen`（501）与 `ReviewViewModel`（469）承担了"一张照片多件 + 草稿生命周期 + 退出诚实"这三件事叠加。
`REBUILD.md` §4 把"确认页单独拆成 slot 状态机 + 表单渲染两件"列为重建时值得提前做的分离。
