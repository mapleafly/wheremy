# Wheremy 术语对照（GLOSSARY）

**怎么用**：听到/看到/要写一个词，先在这里查它的代码符号与权威出处，再去读那一节。
定义一律**照抄现有文档的措辞**（本表不发明定义）；本表只加两样东西——**代码符号**与**库表/列**。
出处给到 `§`；`文件:行` 按基线 commit `cf73f5b` 核过。行号会漂、符号名不漂（`README.md` §0）。

**两个词说法不同时以 `RULES.md` 为准**（`README.md` §3.2 第 2 条：逐字文案与规则怎么算 → `RULES.md` 优先）。本表遇到的冲突不在这里二选一，全部记在 §11。

## 1. 数据与状态

| 术语 | 代码符号 | 库表/列 | 一句话定义（照抄） | 出处 |
|---|---|---|---|---|
| 物品 Item | `ItemEntity`、`ItemDraft`、`ItemSummary` | `item` | 一条"我有一件 X，它在 Y"的记录 | `PRD.md` §2 |
| 状态 status | `ItemStatus` | `item.status`（存字符串） | `DRAFT`（还没确认）/ `CONFIRMED`（录完了） | `PRD.md` §2；`Entities.kt:9` |
| 待归类 Inbox | `onlyInbox`、`inboxMode`、`inbox()` | —（**不是表，是谓词**） | 没有位置 **或** 还是 DRAFT：`locationId IS NULL OR status = 'DRAFT'`。**别名叫「收件箱」**，散见 `ARCHITECTURE.md`/`PRD.md`/`PROJECT.md`/`REBUILD.md`；正式叫法与屏幕上的 🗣 文字以 `RULES.md` §1.1 的标题「待归类（收件箱）」与 `UI.md` §2.2 的底栏 label「待归类」为准 | `PRD.md` §2；`RULES.md` §1.1；`UI.md` §2.2；`Daos.kt:34` |
| 数量 quantity | `ItemDraft.quantity` | `item.quantity` | 界面上 `x${n}` 且**仅当 n≠1** | `UI.md` §5.3；`DetailScreen.kt:219` |
| 季节 seasons | `ItemDraft.seasons` | `item.seasons`（CSV） | 库里存 CSV，屏幕上 `replace(",", "、")` | `UI.md` §5.3；`DetailScreen.kt:214` |
| 别名 aliases | `ItemDraft.aliases` | `item.aliases`（CSV） | 逗号分隔的"我平时怎么叫它" | `PRD.md` §2；`RULES.md` §15 W9 |
| 空壳 | `ReviewSlot.isThrowawayShell` | —（算属性，不落库） | 可回收的草稿：`itemId != null && !saved && draft.name.isBlank() && touched.isEmpty()` | `RULES.md` §1.4；`ReviewViewModel.kt:45-46` |
| 能按保存 | `ReviewUiState.canSave` | — | `itemId != null \|\| draft.name.isNotBlank()` | `RULES.md` §1.5；`ReviewViewModel.kt:93` |
| 没保存的三笔账 | `notStored` / `leftBehind` / `alreadySaved` | — | 不会留下的件数 / 会以 DRAFT 留在待归类的件数 / 库里已经存好、离开不动它们的件数 | `RULES.md` §1.3 |
| 未走完（圆点） | `ReviewUiState.unsaved` | — | `slots.count { !it.saved }`，芯片上的圆点 | `RULES.md` §1.3 |
| 物品类别词表 | `CATEGORIES` | —（筛选维度，不进搜索文本） | 衣物 数码 电器 药品 食品 工具 文档 饰品 厨具 玩具 运动 其他 | `UI.md` §10；`Models.kt:55` |

## 2. 位置树

| 术语 | 代码符号 | 库表/列 | 一句话定义（照抄） | 出处 |
|---|---|---|---|---|
| 位置节点 Location | `LocationNodeEntity`、`LocationNode` | `location_node` | 层级容器（家 > 主卧 > 衣柜 > 抽屉 > 收纳盒） | `PRD.md` §2 |
| 路径 path | `LocationTree.breadcrumb()` | — | 从根到该节点的显示串，` / ` 连接 | `PRD.md` §2 |
| 面包屑 breadcrumb | `TreeUiState.breadcrumb`、`DetailUiState.breadcrumb` | — | 树页标题就是它（`joinToString(" / ")`，`maxLines = 2`） | `UI.md` §4.1；`TreeScreen.kt:77-81` |
| 位置类型 kind | `LocationNodeEntity.kind` | `location_node.kind` | **自由标签**不是枚举；label 里「也可自己填」不能省 | `RULES.md` §8.7；`UI.md` §4.7 |
| 同级排序 sortOrder | `LocationRepository.shiftWithinParent` | `location_node.sortOrder` | 重排号，不交换；同级相同则退回按 `name` | `RULES.md` §8.4、§14 |
| 位置照片 | `PhotoOwner.LOCATION`、`LocationPhotoUi` | `item_photo.ownerType = 'location'` | "这层长什么样"的一张参照图，不进录入页、不碰 AI | `RULES.md` §8.6；`UI.md` §4.6 |
| 缩进表 | `LocationOption(id, label, depth)`、`flattenLocations` | — | 前序展开成缩进列表，选择框与树页共用同一份 | `UI.md` §9；`LocationFlatten.kt:8-21` |
| 删除门槛 | `LocationNotEmptyException` | 外键 + 数据层判定 | 只有空位置可以删除：子位置和物品需要先搬走 | `RULES.md` §8.2；`UI.md` §4.5 |
| 防环 | `descendantIds`、`parentsFirst` | `location_node.parentId`（FK→self） | 候选里过滤掉后代；导入时按"父先于子"插入 | `RULES.md` §8.5、§12.4 |

## 3. 照片

| 术语 | 代码符号 | 库表/列 | 一句话定义（照抄） | 出处 |
|---|---|---|---|---|
| 照片 Photo | `ItemPhotoEntity` | `item_photo` | 一次写入产出三档：原图 1600 / 缩略图 256 / 分析用 1024（后者不落盘） | `PRD.md` §2；`RULES.md` §9.4 |
| 主人 owner | `PhotoOwner` | `item_photo.ownerType`（`"item"` / `"location"`） | 照片可以多态地挂在**物品**或**位置**上 | `PRD.md` §2；`Entities.kt:11-14` |
| 两个路径列 | `filePath` / `thumbPath` | `item_photo.filePath`、`item_photo.thumbPath` | 原图与缩略图各一列，两目录文件名互不重合 | `ARCHITECTURE.md` §4.3 |
| 三档尺寸 | `PhotoStore.write()` | 磁盘 `originals/<uuid>.jpg`、`thumbs/<uuid>.jpg` | 1600 / 256 / 1024，质量 85/80/80；1024 只送模型、不落盘 | `RULES.md` §9.4 |
| 引用计数 | `deleteUnreferencedPhotoFiles`、`PhotoDao.refsTo` | 查 `item_photo` 两列 | **再没有任何照片行指着它，才把它从磁盘上删**——唯一的回收规则 | `RULES.md` §9.2；`PhotoReclaim.kt:15-21` |
| 删除前那句话 | `deleteWarningOf`、`PhotoDeletion`、`PendingDelete` | 查 `otherOwnersOnPath` | 按"这件的照片还有谁在用"分四档说话 | `RULES.md` §9.3；`UiFormat.kt:25-33` |
| 换一张 | `ItemRepository.swapPhoto`、`CaptureFlow.replacePhoto` | 改 `item_photo` 两列 | 先写新文件、再改行、最后删旧文件 | `RULES.md` §3.3；`PRD.md` FR-SRC-04 |
| 照片路由 | `deliverPhoto` | — | 相册与相机两条来源回来后**都**落到这里，没有第二条路 | `UI.md` §2.4；`REBUILD.md` §2 第 7 条 |
| 选图来源 | `PhotoTarget { NONE, ITEM, LOCATION }` | — | 在飞的那张回来该给谁；非 NONE 就是"有一张还在飞" | `UI.md` §2.1；`WheremyRoot.kt:66` |

## 4. 录入与确认页

| 术语 | 代码符号 | 库表/列 | 一句话定义（照抄） | 出处 |
|---|---|---|---|---|
| 槽位 slot | `ReviewSlot`、`ReviewUiState.slots` | — | 一张照片里认出的多件物品，一件一个槽位 | `PRD.md` §2；`ARCHITECTURE.md` §11.1 |
| 容器猜测 containerGuess | `AnalysisResult.containerGuess`、`ReviewSlot.containerGuess` | —（不落库） | 模型说"这东西一般放在哪种容器里"，本地拿去对树 | `PRD.md` §2；`AnalysisResult.kt:13` |
| 建议位置 | `SuggestLocationUseCase.suggest`、`ReviewSlot.suggestedLocationId` | — | 整条路径命中优先，然后打分；只在空着的时候填 | `RULES.md` §5.3/§5.4 |
| 录入链路 | `CaptureFlow.capture`、`CaptureOutcome` | 先写 `item` 再写 `item_photo` | 先落库：照片到位那一帧物品行已经在库里 | `RULES.md` §3.1；`PRD.md` G1 |
| 动过没 | `ReviewSlot.touched`、`markTouched` | — | 用户一个字都没打 = `touched.isEmpty()`，空壳判定的一环 | `RULES.md` §1.4 |
| 落库没 | `ReviewSlot.saved` | — | 只在 `load()`/`save()`/`saveAnUnsavedSlot` 三处置真，没有任何路径复位 | `RULES.md` §15 W1 |
| 稍后处理 | `ReviewViewModel.keepForLater` | 写字段、退回 `DRAFT` | 保持 DRAFT，进待归类 | `PRD.md` §2；`UI.md` §6.1 |
| 退出确认两支 | `requestExit`、`confirmExit` | — | 支一 `notStored > 0`、支二"有改动但都已落库" | `RULES.md` §6.1–§6.3；`UI.md` §6.6 |
| 关页只有一个出口 | `ReviewUiState.finished`、`consumeFinished` | — | 先 `consumeFinished()` 再 `onDone()`，全屏唯一出口机制 | `UI.md` §6.7 |
| 多件芯片 | `chipLabel`、`discardCurrent` | — | 没走完的前面加「● 」；「不要这件」从不进库 | `UI.md` §6.2；`PRD.md` ⛔D14 |

## 5. AI

| 术语 | 代码符号 | 库表/列 | 一句话定义（照抄） | 出处 |
|---|---|---|---|---|
| 分析接缝 | `PhotoAnalyzer`、`ApiPhotoAnalyzer`、`OfflinePhotoAnalyzer` | — | AI 是加速器不是闸门：慢、坏、没配 key 都不影响录入与查找 | `PRD.md` FR-AI-06；`ARCHITECTURE.md` §8.1 |
| 失败 11 类 | `FailureReason`、`failureText`、`classify` | — | 每种失败都要给可读中文和下一步；`classify` 是唯一映射 | `RULES.md` §13.1/§13.2；`UiFormat.kt:35-56` |
| 未配 key | `NO_CONFIG` | — | 未启用 AI（还没配 API Key），字段留空，直接手填就行 | `RULES.md` §13.2 |
| 提示词 | `Prompt`、`Prompt.systemFor` | — | 把用户自己的位置路径原样喂进去，要求原样抄一条 | `RULES.md` §5.6；`ARCHITECTURE.md` §8.4 |
| 解析 | `AnalysisParser`、`AnalysisResult` | — | 同时认数组与单对象两种形状；不认识的那件跳过而不是整批失败 | `ARCHITECTURE.md` §8.3；`RULES.md` §5.5 |
| 留痕 trace | `TraceEntry`、`AnalysisTrace`、`ANALYSIS_TRACE_MAX = 10` | 磁盘 `files/ai-trace`（**不在** `files/photos` 下） | 每次尝试的提示词原文 + 模型回应原文，只留最近 10 条 | `PRD.md` §2；`RULES.md` §13.3 |
| 重试 | `MAX_ATTEMPTS = 2` | — | 可重试的最多两次，所以最坏约 30 秒后必然给结果或降级手填 | `PRD.md` G1；`RULES.md` §14 |

## 6. 搜索与派生列

| 术语 | 代码符号 | 库表/列 | 一句话定义（照抄） | 出处 |
|---|---|---|---|---|
| 派生列 | `SearchTextBuilder.build` | `item.searchText`（列名 `search_text`） | 写入时算出来的派生值，加了字段不会让已录物品自动带上 | `RULES.md` §10.3 |
| 字段版本 | `FIELD_VERSION = 2` | 设置里存 `searchTextVersion` | 版本不等才在启动时补算一遍全表 | `RULES.md` §10.3；`SearchTextBuilder.kt:9` |
| 搜索三段 | `rankedSearch` | `searchLike` | SQL 取候选 → 谓词过滤 → 内存打分 | `RULES.md` §10.1 |
| 候选上限 | `LIMIT 300` | `Daos.kt:32-33` | 超过 300 件命中时靠后的不参与排序 | `RULES.md` §10.1、§15 W4 |
| 打分 | `SearchRanker.rank` | — | 名称全等 100 / 前缀 80 / 包含 60 / 别名 40 / 搜索文本 20 | `RULES.md` §10.2 |
| 标签筛选 | `itemIdsWithTag`、`allTags` | `item_tag(itemId, tag)` | 标签筛选认的是 `item_tag` 里的行，不是 `search_text` 里的字面 | `RULES.md` §10.1 |
| 筛选维度 | `Filters(category, season, tag, onlyInbox, expireUntil)` | — | 五个条件；`onlyInbox` 在待归类页是这一页的定义 | `RULES.md` §1.1；`Models.kt:23-33` |

## 7. 到期提醒

| 术语 | 代码符号 | 库表/列 | 一句话定义（照抄） | 出处 |
|---|---|---|---|---|
| 到期 Expire | `ItemDraft.expiresAt` / `expireKind` | `item.expiresAt` / `item.expireKind` | 保质期/保修/证件有效期，一个日期 + 一个种类 | `PRD.md` §2 |
| 快到期 / 已过期 | `expiringBy`、`toggleExpiringSoon` | — | `expiresAt != null && expiresAt <= expireWindowEnd(leadDays)` | `RULES.md` §1.2 |
| 提醒窗口上界 | `expireWindowEnd(leadDays)` | — | 唯一上界：提前 N 天覆盖到第 N 天那一整天的最后一毫秒 | `RULES.md` §1.2、§16.8；`Dates.kt:25-26` |
| 已提醒时刻 | `markExpireNotified` | `item.expireNotifiedAt` | 每件物品每个截止日期最多说两次；**发不出去就不回写** | `RULES.md` §11.2、§11.4 |
| 提前天数 | `leadDays`、`LEAD_PRESETS` | DataStore（默认 3，区间 0/365） | 0 天说成「当天到期提醒」，不说"提前 0 天" | `RULES.md` §11.5、§14；`SettingsScreen.kt:57-60` |
| 聚合通知 | `ReminderEngine`、`ReminderWorker`、`NOTIFICATION_ID = 1001` | — | 到期提醒每天检查一次，几件物品也只发一条聚合通知 | `RULES.md` §11.3；`UI.md` §7.1 |
| 排班 | `ReminderScheduler`、`UNIQUE_NAME = "wheremy_expire_daily"` | — | 一天一次 + `KEEP`；改设置不需要也不许重新排班 | `RULES.md` §11.1 |
| 到期那一行 | `expireHint(days)`、`formatDate` | — | 三档：已过期 k 天 / 今天到期 / 还剩 k 天 | `UI.md` §9；`UiFormat.kt:7-11` |

## 8. 备份

| 术语 | 代码符号 | 库表/列 | 一句话定义（照抄） | 出处 |
|---|---|---|---|---|
| 备份包 | `ENTRY_MANIFEST`/`ENTRY_DATA`/`PHOTOS_PREFIX` | `manifest.json` + `data.json` + `photos/**` | 旧包必须还能导入（硬性不可变项） | `REBUILD.md` §2 第 2 条；`ARCHITECTURE.md` §10.1 |
| 清单 | `BackupManifest(schemaVersion, exportedAt, itemCount, appVersion)` | `manifest.json` | 写 `DB_SCHEMA_VERSION` 与 App 版本；导入侧不因版本不同而拒 | `ARCHITECTURE.md` §10.1；`RULES.md` §14 |
| 数据 | `BackupData(locations, items, photos, tags)` | `data.json` | **直接是四表 entity 列表**，不额外定义 DTO | `ARCHITECTURE.md` §10.1、§4.2 |
| 两种导入 | `ImportMode { MERGE, OVERWRITE }` | — | MERGE：本机已有的原样保留；OVERWRITE：先四表 `deleteAll` 再整包写入 | `RULES.md` §12.4 |
| 导入顺序 | `export` / `import` / `writeRows` | — | 读包 → 整体校验 → 一个事务落库 → 事务成功之后才动磁盘上的照片 | `RULES.md` §12.2 |
| 校验 | `validate` | — | **四条拒 + 一条放行**：物品的位置、照片的 owner、空路径、标签的 itemId 各一条；**文件不在包里不拒**（另有一道版本拒在读包阶段，不在 `validate()` 里） | `RULES.md` §12.3 |
| 导出计数 | `BackupSummary(locations, items, photos, bytes)` | — | 计数与"完成"那句话说的是不同的文件对；导入路径 `bytes = 0` | `ARCHITECTURE.md` §10.1；`RULES.md` §12.4 |
| 不做云备份 | `allowBackup="false"` | `AndroidManifest.xml:13` | 应用已被系统设置为不做云备份，导出是唯一的迁移手段 | `REBUILD.md` §2 第 4 条；`UI.md` §7.3 |
| 不写进包 | `SettingsRepository` 不在导出通道上 | — | 备份文件里不含 API key，也不含任何设置 | `REBUILD.md` §2 第 3 条；`PRD.md` ⛔D12 |

## 9. 界面与外壳

| 术语 | 代码符号 | 库表/列 | 一句话定义（照抄） | 出处 |
|---|---|---|---|---|
| 五屏 | `WheremyRoot`、`GalleryScreen`/`TreeScreen`/`DetailScreen`/`ReviewScreen`/`SettingsScreen` | — | 一个外壳 + 五屏 + 两类叠层；没有 Navigation 库 | `UI.md` §1.1 |
| 底栏四格 | `RootTab { GALLERY, TREE, INBOX, SETTINGS }` | —（存成 `.name` 字符串） | 照片墙 / 位置 / 待归类 / 设置；解析不出来回落 GALLERY | `UI.md` §2.1；`WheremyRoot.kt:63`、`:106` |
| 待归类页 | `GalleryScreen(inboxMode = true)` | — | 与照片墙是同一个组件，另一种模式 | `UI.md` §3 |
| 来源弹窗 | `askingFor`、`askForPhotoSource` | — | 「这张照片从哪来？」；FAB 为 ITEM、位置照片为 LOCATION | `UI.md` §2.5 |
| 取景屏 | `CameraScreen`、`Viewfinder { Unavailable, NeedsPermission, Ready }`、`CameraGate` | — | 叠层而不是替换；三态由 `gate` 决定 | `UI.md` §1.1、§8 |
| 装配容器 | `AppContainer` | — | 一个普通 class 装配完所有依赖，无 DI 框架 | `ARCHITECTURE.md` §3.1；`PRD.md` ⛔D5 |
| 在飞的照片字节 | `AppContainer.pendingCapture` | —（内存 `StateFlow`） | 选图与确认页在两个 composable 之间换手；MB 级字节，消费即清 | `ARCHITECTURE.md` §3.1；`WheremyRoot.kt:136`/`:139` |
| 通知入口意图 | `AppContainer.entry`、`ENTRY_EXPIRING = "expiring"` | —（内存 `StateFlow`） | 通知点进来那一跳：外壳切 tab，墙里消费 | `UI.md` §2.9；`ARCHITECTURE.md` §3.1 |
| 录入编排 | `AppContainer.captureFlow` | — | 落库 + 分析 + 建议位置那条链的编排者 | `ARCHITECTURE.md` §3.1；`RULES.md` §3.1 |
| 位置挑选件 | `LocationPickerDialog`、`rootLabel` | — | 挑选位置的唯一对话框，四处复用 | `UI.md` §9 |
| 条幅消费件 | `SnackbarMessage` | — | 两个 `LaunchedEffect` 缺一不可 | `UI.md` §9；`SnackbarMessage.kt:14-17` |
| 状态恢复九格 | `WheremyRoot.kt:76-93` + `SettingsScreen.kt:74` | — | 旋转/深色切换/进程被杀后不丢；`WheremyRoot` 里八格 + `pendingMode` 一格，其中四格刻意不是 `saveable` | `UI.md` §2.1/§12；`REBUILD.md` §2 第 6 条 |

## 10. 记号与编号体系

| 记号 / 编号 | 含义 | 权威出处 |
|---|---|---|
| 🔒 / 📐 / 🗣 / ⚠️ | 不许改 / 阈值与数字 / 逐字文案 / 已知偏差（列在 §15） | `RULES.md` §0 |
| 🎨 | 可自由发挥（颜色、字号、圆角、间距、图标、动效） | `UI.md` §0 |
| ✅ / 🟡 / ⬜ / ⛔ | 已落地 / 部分落地 / 待做（必须带触发条件与代价）/ 已否决（必须带理由） | `README.md` §6 第 4 条；`PRD.md` §0 |
| ⭐ | 发布前必做的前置项（当前两条：⭐FR-DB-01 真实 Migration、⭐FR-RLS-01 release 签名） | `PRD.md` §5 |
| `W1–W11` | 已知偏差编号：代码这么写了、但不完全成立 | `RULES.md` §15、`UI.md` §14 |
| `FR-<域>-<序号>` | 需求 id；`ARCHITECTURE.md`/`RULES.md`/`UI.md`/`REBUILD.md` 用它反向引用 | `PRD.md` §0 |
| `D1–D14` | 明确否决项编号与理由（如 ⛔D4 不做动态取色） | `PRD.md` §6 |
| `ADR-01 … ADR-27` | 架构决策记录；写法 `ADR-04 ← PRD ⛔D4` 表示它由哪条否决项成文。ADR-25 是**技术选型准则**（官方/成熟优先、自造要给证据） | `DECISIONS.md` §3/§4 |
| `G1–G5` | 五条可测的成功判据 | `PRD.md` §1.1 |
| `M0–M7` | 从零重建的里程碑切分，出口判据过了才进下一个；M7 是**发布就绪**（⭐真实 Migration + ⭐签名包 + 发布演练，`PROJECT.md` §5） | `REBUILD.md` §3、`PROJECT.md` §5 |
| 批次号（AT/AU、AK、AA…） | 真机验收与修复的批次标签；规则里写"哪一批的事故"就指它 | 引用散见 `RULES.md` §15、`UI.md` §14、`PRD.md`；历史记录在 `docs/superpowers/plans/`（**只读存档**，不参与裁决，`README.md` §8） |
| spec | 原设计文档 `docs/superpowers/specs/2026-10-02-wheremy-design.md`；"spec §N"是**那份**的节号 | `PRD.md` §0 |

| 词 | 代码符号 / 库表列 | 一句话 |
|---|---|---|
| 加密包 | `BackupManifest.encryption` / `formatVersion = 2` | 明文小头 + AES-256-GCM 负载的备份包；**口令不写在包里**（`ADR-27`） |
| 口令 | `AppSettings.backupPassword` | 导出加密包时用户自己设的字符串（≥ 8 位）；只在本机记住，换机导入要手输 |
| verifier | `BackupEncryption.verifier` | 用派生密钥加密的一小段固定明文；导入时先验它，**错了就说是口令不对** |

## 11. 写作时发现的差异（2026-10-07 已全部处理）

| 文档 A 位置 | 文档 B 位置 | 冲突点 | 处理 |
|---|---|---|---|
| `PRD.md` §3.11 FR-PRM-05 与 §5.1：「网格里 48dp 缩略图与芯片组没有 `contentDescription`」 | `UI.md` §9 + 源码：`ItemRow.kt:44`、`TreeScreen.kt:258`、`GalleryScreen.kt:282` **都有**；芯片自带文字 label | 原句不成立 | **判源码对** → PRD 已改成"偏差只剩筛选芯片组与焦点次序" |
| `REBUILD.md` §2 第 6 条：「状态恢复的**九格**语义｜`WheremyRoot.kt:76-93`（全 `rememberSaveable`）」 | `UI.md` §12 + 源码：九格 = §2.1 的八格 + `SettingsScreen.kt:74` 的 `pendingMode`；`WheremyRoot.kt:93` 的 `gate` 是 `remember` | 括注与范围都不准 | **判 `UI.md` 对** → `REBUILD.md` 已改 |
| `UI.md` §3.4 空态第 2 句「没有待归类的物品，全都放好了。」 | `docs/superpowers/plans/2026-10-02-wheremy-core-loop.md`（批次 AS 段）仍留旧句「收件箱是空的，都已归类。」 | 旧句只活在**只读存档**里 | **不改**（`README.md` §8：存档不参与裁决）。留在这里只为提醒：照存档里的验收手册抄文案会抄到作废的那句 |
