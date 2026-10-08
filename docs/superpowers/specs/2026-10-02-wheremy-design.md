# Wheremy（随身物品管理）设计文档

日期：2026-10-02
状态：待用户评审
目录：`D:\src\qoder\wheremy`

---

## 0. 已确认的需求（来自用户）

| 决定项 | 结论 |
|---|---|
| 使用者 | 只有用户一个人，单机使用 |
| 账号 / 云同步 | 不需要 |
| 平台 | 安卓，自建 APK 侧载安装 |
| 技术方案 | 原生 Kotlin + Jetpack Compose + Room（用户选定，非 Flutter） |
| AI 接入 | 国内多模态 API，自己注册按量付费，APP 直连 |
| 位置模型 | 层级容器树（家 > 主卧 > 衣柜 > 抽屉 > 收纳盒） |
| 查找方式 | 关键词搜索 + 位置树浏览 |
| 录入确认强度 | AI 预填 + 一键确认 |
| MVP 额外功能 | 待归类收件箱、保质期/保修到期提醒、数据导出导入备份 |
| 用户特别补充 | "我可能全然忘记了有哪些东西，比如想找一件深秋穿的外套，希望 APP 给出库存和照片" |

最后一条把需求从"记得住东西在哪"扩展到"记得住自己有什么"，因此**照片墙 + 按类别/季节筛选**是核心功能，不是锦上添花。AI 必须输出可筛选的结构化属性，而不是一段自由描述文字。

## 1. 目标与非目标

### 目标
1. 3 秒内完成一件物品的录入：拍照 → 等 AI → 按确认。
2. 30 秒内找到任意一件已录入物品的存放位置路径。
3. 不记得自己有什么时，能通过照片墙按类别/季节翻出来。
4. 无网络、AI 额度耗尽、API 报错时，录入与查找仍然可用（AI 是增强，不是依赖）。
5. 全部数据在本地，可一键导出备份、换机导入。

### 非目标（明确不做）
- 账号体系、云端同步、多人共享。
- iOS 版、Web 版、微信小程序版。
- 语义向量搜索、以图搜图、拍照找物。
- 端侧大模型。
- 商品条码扫码、电商价格、库存进出流水。
- 回收站 / 软删除（删除用确认对话框兜住）。

## 2. 为什么不用微信小程序

用户原始想法里包含小程序，此处记录否决理由，避免后续反复：

- 小程序每用户本地文件存储配额 200MB，照片墙场景几百张原图就会撑爆，只能改用云存储 → 必须有后端和月费。
- 到期提醒依赖"订阅消息"，一次性授权额度，无法可靠推送。
- 个人主体需走类目审核，且含 AI 生成内容合规审查。
- 用户仅一人在自己的常开安卓机使用，"零安装"的收益换不回上述约束。

## 3. 技术选型

| 层 | 选择 | 理由 |
|---|---|---|
| 语言/UI | Kotlin + Jetpack Compose，单 Activity | 用户选定 |
| 依赖注入 | 手写 `AppContainer`（一个普通 class 装配 repo/analyzer） | 个人项目，Hilt 的收益不抵其复杂度 |
| 持久化 | Room（SQLite） | 关系查询 + 事务 + 迁移工具体系成熟 |
| 设置存储 | Jetpack DataStore Preferences | apiKey/baseUrl/model/提醒提前天数 |
| 相机 | CameraX（`PreviewView` + `ImageCapture`） | 自绘快门键，连拍体验好。备选：`TakePicture` 系统相机 intent，代码更少但体验差、不可控尺寸 |
| 图片加载 | Coil | 从私有目录 `File` 加载缩略图 |
| 网络 | OkHttp + kotlinx.serialization，OpenAI 兼容 `chat/completions` | 换厂商只改两个配置字段 |
| 后台与提醒 | WorkManager 周期任务 + 系统通知 | 无需常驻服务 |
| 构建 | Gradle（AGP），`minSdk 26`、`targetSdk 36` | 本机 SDK 已有 build-tools 36.1.0 / platforms android-36 |

### 搜索：用 LIKE，不用 FTS5（重要取舍）
SQLite FTS5 默认 `unicode61` 分词器把连续中文当作单个 token，中文检索直接失效；要做对需要自建 bigram 索引列。个人物品量级为几千条，单字段 `LIKE '%kw%'` 扫描 + 内存排序在几十毫秒内完成，代码量少一大半，中文子串天然命中。物品量真到万级以上再引入索引，届时改动被封装在 `ItemDao` 一个方法内。

排序规则（内存中）：名称完全相等 > 名称前缀匹配 > 名称包含 > 别名/标签命中 > 品牌/描述命中。

## 4. 架构与模块

单 Gradle module，按包分层。四个接缝（可独立理解、独立测试）：

```
app/
└─ src/main/java/com/wheremy/app/
   ├─ AppContainer.kt             // 手工装配：db、dao、analyzer、photoStore、repo
   ├─ data/
   │  ├─ db/                    // Entity, Dao, WheremyDatabase, Migrators, SearchTextBuilder
   │  ├─ photos/                // PhotoStore：存原图/缩略图、删文件、路径解析
   │  ├─ vision/                // PhotoAnalyzer 接口 + ApiPhotoAnalyzer + OfflinePhotoAnalyzer + AnalysisResult + Prompt + JsonParser
   │  └─ repo/                  // ItemRepository, LocationRepository（组合 db + photos + analyzer）
   ├─ domain/                   // 纯 Kotlin 用例，无 Android 依赖
   │  ├─ AnalyzePhotoUseCase    // 调 analyzer，失败返回空结果
   │  ├─ SaveItemUseCase        // DRAFT -> CONFIRMED
   │  ├─ SearchItemsUseCase     // LIKE + 排序 + 筛选
   │  ├─ BuildLocationTreeUseCase
   │  ├─ SuggestLocationUseCase // 模型容器猜测 -> 本地节点模糊匹配
   │  └─ BackupUseCase          // 导出/导入 zip
   ├─ remind/                   // ReminderWorker, ReminderScheduler
   ├─ ui/                       // Compose screens + ViewModels
   │  ├─ capture/  ├─ tree/  ├─ gallery/  ├─ inbox/  ├─ detail/  └─ settings/
   └─ MainActivity.kt
```

`PhotoAnalyzer` 接口是整个设计中最关键的一处：

```kotlin
interface PhotoAnalyzer {
    suspend fun analyze(jpeg: ByteArray): AnalysisResult
}
```

- `ApiPhotoAnalyzer`：真实调用多模态 API。
- `OfflinePhotoAnalyzer`：立即返回 `AnalysisResult.empty()`。

无网、无 key、额度耗尽、解析失败时，`AnalyzePhotoUseCase` 回落到 `OfflinePhotoAnalyzer` 的结果，确认页照常弹出、字段留空，用户手填即可。AI 因此永远是增强项而非阻塞项，且单测直接喂 fake 实现。

## 5. 数据模型

### `location_node`
| 列 | 类型 | 说明 |
|---|---|---|
| `id` | TEXT PK | UUID |
| `parent_id` | TEXT? FK→self | null 表示根（"家"） |
| `name` | TEXT | 显示名 |
| `kind` | TEXT? | 自由容器类型标签：房间/衣柜/抽屉/收纳盒/箱子/其他 |
| `sort_order` | INTEGER | 同级手动排序 |
| `created_at` / `updated_at` | INTEGER | epoch millis |

树的显示路径**不用递归 SQL**：位置总量几十到几百个，一次性全量查出，在 Kotlin 内存中建树、算路径。规避不同 Android 版本内置 SQLite 对 recursive CTE 支持差异的风险。

删除位置：有子节点或有物品时**拒绝删除**并提示先迁移，避免孤儿数据。不做级联删除。

### `item`
| 列 | 类型 | 说明 |
|---|---|---|
| `id` | TEXT PK | UUID |
| `name` | TEXT | 可为空（DRAFT 阶段），确认后必填 |
| `category` | TEXT? | 枚举字符串：衣物/数码/电器/药品/食品/工具/文档/饰品/厨具/玩具/其他 |
| `seasons` | TEXT? | 逗号分隔：春,秋,冬（照片墙筛选要用，独立列而非标签） |
| `color` | TEXT? | |
| `brand` | TEXT? | |
| `material` | TEXT? | |
| `aliases` | TEXT? | 逗号分隔别名，"美林"→"布洛芬混悬液" |
| `description` | TEXT? | AI 生成的自然语言描述，≤60 字 |
| `notes` | TEXT? | 用户自己的备注 |
| `quantity` | INTEGER | 默认 1 |
| `location_id` | TEXT? FK→`location_node` | **null = 待归类收件箱** |
| `status` | TEXT | `DRAFT` / `CONFIRMED` |
| `expires_at` | INTEGER? | 到期日 epoch millis |
| `expire_kind` | TEXT? | 保质期/保修/证件有效期/其他 |
| `expire_notified_at` | INTEGER? | 已发通知时间，避免重复提醒 |
| `search_text` | TEXT | **派生列**：写入时重算，见下 |
| `created_at` / `updated_at` | INTEGER | |
| `last_moved_at` | INTEGER? | 换位置时间，用于"最近动过"排序（不做完整搬动历史） |

`search_text` = `name` + `brand` + `aliases` + 标签 + `description` + `notes`，空格连接、小写化。搜索只针对这一列做 `LIKE`，一条 SQL 覆盖全部可检索内容。

AI 返回的数组字段（`seasons`、`aliases`、`tags`）在写入时统一以逗号连接（`seasons`/`aliases`）或展开成 `item_tag` 行（`tags`），读取时再拆回列表。全库只在这一处做数组↔字符串的转换。

`status = DRAFT` 的意义：按下快门的瞬间就落库一条 DRAFT（无位置、无名称）。AI 回来后更新字段但仍为 DRAFT；用户点确认才转 CONFIRMED。若中途崩溃、杀进程或退出页面，物品不丢失，以"待确认"出现在收件箱。

### `item_photo`
| 列 | 类型 | 说明 |
|---|---|---|
| `id` | TEXT PK | |
| `owner_type` | TEXT | `item` / `location` |
| `owner_id` | TEXT | 多态外键（位置也能拍照记录，例如给一个抽屉拍照） |
| `file_path` | TEXT | APP 私有目录相对路径 |
| `thumb_path` | TEXT | 缩略图相对路径 |
| `sort_order` | INTEGER | |
| `created_at` | INTEGER | |

照片文件存 `filesDir/photos/<uuid>.jpg` 与 `.../thumbs/<uuid>.jpg`，私有目录不需存储权限。原图压到长边 1600（JPEG q85），AI 上传用长边 1024（q80）的临时缩放版以省 token。

### `item_tag`
| 列 | 类型 | 说明 |
|---|---|---|
| `item_id` | TEXT FK | 索引 |
| `tag` | TEXT | 索引；AI 产出的自由标签（材质、场合、场景、用途） |

照片墙支持点选标签筛选，因此标签单独成表而非塞进文本列。

### 设置（DataStore，不进 Room）
`api_key`、`base_url`、`model`、`vision_enabled`、`reminder_lead_days`(默认 3)、`theme`。

**API key 明文存 DataStore**，不引入加密偏好库。风险与控制：数据在 APP 私有目录，非 root 设备其他应用不可读；泄露时可在云服务控制台一键吊销重建；key 不参与备份导出。需在设置页向用户展示该风险说明。开发期 key 可写 `local.properties`（已 gitignore），构建时不注入、仅在设置页手填。

## 6. 核心流程

### 6.1 拍照录入
```
取景(CameraX) → 快门 → 保存 JPEG + 缩略图 → 插入 item(DRAFT) + item_photo
   → 协程调 AnalyzePhotoUseCase(1024 缩放版)
   → 成功：更新 item 的 AI 字段；SuggestLocationUseCase 猜位置
   → 失败：AnalysisResult.empty()，字段留空
   → 打开确认页（所有字段预填，可改）
   → 用户点"保存" → status=CONFIRMED，重算 search_text，写 item_tag
   → 用户点"稍后处理" → 保持 DRAFT，进收件箱
```
界面在等待 AI 时显示占位骨架而不是全屏 loading，超时 15s 自动落到可编辑状态。

### 6.2 位置树下钻
根节点列表 → 展开子节点 → 叶子节点显示该位置物品。顶部显示完整路径面包屑（"家 / 主卧 / 衣柜 / 第二层抽屉"）。节点长按可新增子节点、改名、排序、拍照记录该位置。

### 6.3 搜索与照片墙
两个入口共用一套筛选条件（`category` / `seasons` / `tags` / `只有待归类` / `快到期`）：
- 输入关键词 → `search_text LIKE` + 排序规则。
- 不输入时按筛选条件浏览，默认按 `updated_at` 倒序。

结果以**照片网格**呈现（这是"我忘了自己有这件外套"的主场景），点开看大图与位置路径。网格卡片显示：缩略图、名称、位置末两级。空结果时提示"换个词，或去收件箱看看"。

### 6.4 待归类收件箱
查询 = `location_id IS NULL` 或 `status = DRAFT`。逐条处理：选位置 → 确认。清空后显示"已全部归类"。

### 6.5 到期提醒
`ReminderWorker` 每 24h 跑一次（WorkManager `PeriodicWorkRequest`，网络无关）：
查询 `expires_at` 在 `now .. now + lead_days` 且 `expire_notified_at` 为空或早于本次窗口的物品 → 发一条聚合通知（"3 件物品临近到期"），点击进入筛选后的照片墙 → 回写 `expire_notified_at`。
Android 13+ 需 `POST_NOTIFICATIONS` 运行时授权；被拒时设置页显示引导，提醒功能降级为仅在 APP 内标记，不崩溃。

### 6.6 导出 / 导入备份
- 导出：` SAF` 选目录 → zip 包含 `wheremy.db`（Room 数据库文件，先做 checkpoint）+ `photos/**` + `manifest.json`（版本、导出时间、物品数、App 版本）。**不包含 API key，不包含任何设置**。
- 导入：读 zip → 校验 `manifest.schema_version` ≤ 当前版本 → 两种模式：`合并`（按 id 跳过已存在，其余插入）和 `覆盖`（清空后导入）。默认合并。导入失败整体回滚，不留半个库。

## 7. AI 集成细节

### 请求
OpenAI 兼容 `POST {base_url}/chat/completions`：
- `model`：默认 `qwen-vl-max`（阿里云百炼）。豆包 / 智谱 / OpenAI 只需改 `base_url` + `model`。
- `temperature = 0.1`，`max_tokens = 600`，支持则带 `response_format: {"type":"json_object"}`。
- `content` = `image_url`（`data:image/jpeg;base64,...`）+ 文本 prompt。

### Prompt 要点
中文系统提示，要求：只输出 JSON、不输出解释、不确定字段填 null、绝不编造品牌型号、`name` 用日常口语叫法而非商品全称、`description` ≤60 字、给 3–8 个用于日后检索的标签、给 `container_guess`（该物品通常被收纳在什么容器：抽屉/收纳盒/柜子/箱子/挂钩/货架）。

期望结构：
```json
{
  "name": "深灰色羊毛外套",
  "category": "衣物",
  "seasons": ["秋", "冬"],
  "color": "深灰",
  "brand": null,
  "material": "羊毛",
  "aliases": ["厚外套"],
  "description": "中长款深灰羊毛外套，翻领，两口袋。",
  "tags": ["秋冬", "外出", "保暖"],
  "container_guess": "衣柜"
}
```

### 解析与降级
1. 直接 `json.loads`；失败则正则抽取首个 `{...}` 块（兼容 ```json 围栏）再试；
2. 仍失败或字段类型非法 → 丢弃该字段，保留能用的；
3. 全不可用 → 重试 1 次；再失败 → 返回 `AnalysisResult.empty()` 并在确认页顶部显示"AI 未识别，手动填写"。
网络错误 / 401 / 429 / 超时 → 同样降级，且 401 与 429 各自给出可读文案（key 无效 / 额度或用率限制）。

### 位置建议的本地对齐（关键设计）
模型不知道用户的家是什么结构，所以只让它输出 `container_guess` 这种通用容器词。`SuggestLocationUseCase` 拿该词去本地树里模糊匹配节点名或 `kind`（包含即算分，取最高分节点），匹配不到就留空 → 物品进收件箱。**绝不虚构不存在的位置。**

### 成本
单张图片按长边 1024 上传，个人量级下每次调用为"几分钱"数量级（以所选厂商实际计费为准，实现完成后在设置页展示累计调用次数供参考）。

## 8. 错误处理与边界

| 情况 | 处理 |
|---|---|
| 无网络 / DNS 失败 | 降级为 `OfflinePhotoAnalyzer`，确认页提示"离线，可稍后重跑 AI"；详情提供"重新分析"按钮 |
| 用户未填 key | 录入流程正常，AI 静默跳过；首次进入设置页给出注册指引链接 |
| 401 / 429 | 可读文案区分 key 无效与限额，不重试轰炸 |
| 模型返回非法 JSON | 见 7.解析与降级 |
| 相机权限被拒 | 说明用途的二次对话框 + 跳系统设置；不闪退 |
| 通知权限被拒（13+） | 提醒降级为应用内标记 |
| 删除含物品/子节点的位置 | 拒绝并提示先迁移；给出"把物品移到…"快捷入口 |
| 删除物品 | 二次确认；照片文件按引用计数回收，只有没有其他主人（另一件物品或一条位置记录）指着同一对文件时才删（批次 AJ），确认框那句话也按这个分档说（批次 AK）；文件删除失败时保留 DB 行并回报，避免路径悬空 |
| 备份导入 id 冲突 | 合并模式跳过已存在 id；覆盖模式需二次确认 |
| 备份 `schema_version` 高于当前 APP | 拒绝导入并提示先升级 |
| 导入中途失败 | 事务回滚，保留原库 |
| Room 版本升级 | 每个版本提供 `Migration`，`exportSchema=true`，schema JSON 入库目录便于排查 |
| 超大照片 / 解码失败 | 压缩前判尺寸，解码失败则报"照片损坏"并保留原文件不入库 |

## 9. 测试策略

- **domain 纯 JVM 单测**：`SearchItemsUseCase` 排序规则、`SuggestLocationUseCase` 匹配、`BuildLocationTreeUseCase` 建树与环检测、`AnalysisResult` 字段合并。喂 fake repo / fake analyzer，不用 Robolectric。
- **AI 解析器单测**：固定 JSON fixture 覆盖——正常、```json 围栏、多余前后文字、字段类型错误、空响应。
- **Room DAO 测试**：in-memory Room，覆盖 `search_text` 中文 LIKE、DRAFT 与收件箱查询、位置删除约束、合并导入去重。
- **Compose UI 测试**：`createAndroidComposeRule`，覆盖录入确认页预填与保存、照片墙筛选、收件箱清空流程（fake analyzer 注入）。
- **手工真机验收清单**：拍 10 件真实物品；搜 5 个中文词（含一个只出现在别名里的）；断网跑一次完整录入；杀进程后确认 DRAFT 仍在；导出一再导入另一台机/模拟器；到期提醒把 lead_days 调到 365 触发一次通知。
- 实现每个里程碑必须本地测试通过后再进下一个；AI 真实链路因依赖用户的 key，标记为"待用户下午验证"。

## 10. 里程碑

1. **M0 骨架**：Gradle + AGP + Compose 空壳，`gradlew assembleDebug` 出 APK，`adb install` 成功。证明本机工具链可用。
2. **M1 数据层**：Room 四张表 + DAO + 迁移 + 位置树构建 + `search_text`；domain 用例与单测全绿。
3. **M2 AI 层**：`PhotoAnalyzer` 接口、`ApiPhotoAnalyzer`、`OfflinePhotoAnalyzer`、prompt、解析器 + fixture 单测；配置读写；"重新分析"。
4. **M3 录入闭环**：CameraX 取景拍照、DRAFT 落库、预填确认页、收件箱。
5. **M4 查找闭环**：位置树下钻、照片墙搜索与筛选、物品详情。
6. **M5 周边**：到期提醒 Worker 与通知、导出/导入备份、设置页。
7. **M6 打磨**：真机验收清单、性能与包体、图标与启动页。

## 11. 由我代为拍板、等用户下午确认的点

标 **[待确认]**，用户否决则回退，改动代价已在括号内注明：

1. **[待确认]** 搜索用 `LIKE` + 派生 `search_text` 列，不引入 FTS5（改动小，封装在 `ItemDao` 一个方法）。
2. **[待确认]** 位置树在内存中构建，不用递归 SQL（改动小）。
3. **[待确认]** 待归类收件箱复用 `location_id IS NULL`，不建独立表（改动中）。
4. **[待确认]** 搬动历史只做 `last_moved_at` 一个字段，不做完整历史表（改动中）。
5. **[待确认]** AI 厂商默认 `qwen-vl-max`，用 `base_url + model` 配置化，用户可改（改动小，仅需注册时选一家）。
6. **[待确认]** API key 明文存 DataStore，不入备份（改动小）。
7. **[待确认]** 照片墙为查找主视图（网格优先于列表）。
8. **[待确认]** `minSdk 26`。若用户手机是 Android 8 以下需下调——实际几乎不可能。
9. **[待确认]** APP 名称暂定 Wheremy / "哪儿了"，可改。

## 12. 环境事实（已在本机验证）

- `ANDROID_HOME = D:\Lib\Android\Sdk`，含 build-tools 33.0.1/34/35/36.1.0、platforms android-29/34/35/36、platform-tools(adb.exe)、emulator。
- `gradle 8.14.3`（PATH 上，来自 version-fox），`java 21.0.8 Temurin`。
- `sdkmanager`、`adb` 不在 PATH（用 SDK 目录内绝对路径调用即可）。
- 未安装 Android Studio；构建走命令行 Gradle 可行。
- 项目目录原为空且非 git 仓库；为设计文档与后续代码提交已 `git init`。
- 平台 Windows 11 / Git Bash。
