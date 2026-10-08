# Wheremy 视觉与组件规范（DESIGN_SYSTEM）

**这份文档管**视觉与组件：颜色、字号、间距、组件用法、状态样式、无障碍、动效边界。
读者是 UI / 交互设计，**不必先读代码**——每个当前取值都核到源码并给 `文件:行`（基线 commit `cf73f5b`）。
`UI.md` 说"屏幕上有什么、什么次序、怎么点"，本文说"它长什么样、用什么组件做"，也就是 `UI.md` §11 那节 🎨 要补的规范。

引用形如 `GalleryScreen.kt:123` = `app/src/main/java/com/wheremy/app/ui/gallery/GalleryScreen.kt` 第 123 行；`WheremyRoot.kt` 在 `ui/`，`ItemRow.kt`/`LocationPicker.kt`/`SnackbarMessage.kt`/`LocationFlatten.kt` 在 `ui/common/`，`Theme.kt` 在 `ui/theme/`。行号会漂、符号名不漂（`README.md` §0）。记号沿用文档集：🔒 不许改 · 📐 阈值/数字 · 🗣 逐字文案 · ⚠️ 已知偏差（`RULES.md` §15、`UI.md` §14）· 🎨 可自由发挥（`UI.md` §0）。

## 1. 这份文档管什么 / 不管什么

### 1.1 管

**色彩**（四个常量与两张 scheme、深色跟随、语义色分工）、**字号与排版**（样式角色、`weight(1f)`、区块限高、图片格固定高、文字缩放）、
**间距与尺寸**（网格、缩略图、照片框、圆角、页边距、缩进、留白）、**组件**（顶栏、底栏、FAB、搜索框、芯片、卡片单元、列表行、对话框、菜单、空态、条幅、进度、日期框、取景屏）、
**状态样式**（加载 / 空 / 错误 / 无图 / 图丢了 / 未命名 / 截断）、**无障碍与动效**（`contentDescription`、触控目标、允许引入动效的边界）。

### 1.2 不管

| 不管的事 | 权威 |
|---|---|
| 信息架构：一个外壳 + 五屏 + 底栏四格 + 单颗 FAB | `UI.md` §1/§12；🔒 `REBUILD.md` §2 第 5 条 |
| 每屏字段与控件次序、返回键优先级 | `UI.md` §2–§9 |
| 逐字文案的内容 | 源码是唯一事实源，`RULES.md`/`UI.md` 是抄本（`README.md` §3.1） |
| 规则怎么算（谓词、阈值、状态机） | `RULES.md` |
| 哪些类持有状态、谁调用谁 | `ARCHITECTURE.md` §11 |

**一句话边界**：本文可以换掉任何一个像素，但换完必须让 `UI.md` 里那些 🔒 条目仍然成立。

## 2. 设计原则

1. **界面上每句话都要真的成立**（NFR-02，判据 `RULES.md` §16 十条，第 5 条是"写操作成功之前不说已经好了"）——视觉不得延迟或遮蔽这个判定（§10.1）。
2. **error 色只用于"需要你处理"**：位置为 `null`、`days <= 30`、失败。**换色等于改语义**（`REBUILD.md` §2 第 12 条），不许拿它做装饰或品牌色。
3. **没有真图就不画占位格**：位置行只在真记过照片时才画那一格，理由在 `TreeScreen.kt:254`——留个灰框等于在说"这里有过一张图"；补"图丢了"的占位时同样别补灰框（§7.5）。
4. **信息架构冻结、视觉自由**：分界线是 `UI.md` §12 那张表（布局/配色/字号/图标/动效 🎨；五屏 + 底栏四格 + 单颗 FAB、error 语义、Fit/Crop 分工、返回键优先级 🔒）。
5. **能禁用就别让它"点了没反应"**：`LocationPicker.kt:73` 让空名字的按钮按不动（理由 `:72`），对照 `TreeScreen.kt:326` 的反例（⚠️ `RULES.md` §15 W10）。
6. **界面上的每个数字只有一个来源**（`RULES.md` §16.2/§16.8）：天数、件数、条数由数据层算一次，排版层只显示。

## 3. 色彩

### 3.1 当前固定配色

四个常量（`Theme.kt:10-13`）与两张 scheme（`Theme.kt:15-23`），逐字对齐代码：

| 角色 | light | dark | 出处 |
|---|---|---|---|
| `primary` | `Teal40 = 0xFF00695C` | `Teal80 = 0xFF7FD6C5` | `Theme.kt:10-11`、`:16`、`:21` |
| `onPrimary` | `Color.White` | `0xFF00201B` | `Theme.kt:16`、`:21` |
| `secondary` | `Amber40 = 0xFF9A6A00` | `Amber80 = 0xFFFFD37A` | `Theme.kt:12-13`、`:16`、`:21` |
| `onSecondary` | `Color.White` | `0xFF332500` | `Theme.kt:16`、`:22` |
| `tertiary` | `Teal40`（同 primary） | `Teal80`（同 primary） | `Theme.kt:17`、`:22` |

其余角色（`surface`/`background`/`error`/`outline` 等）**全走 M3 默认**：`MaterialTheme(...)` 只传了 `colorScheme`（`Theme.kt:28-31`）。

### 3.2 深色跟随系统，不做动态取色

二选一：`if (isSystemInDarkTheme()) Dark else Light`（`Theme.kt:29`）。
🔒 **不做动态取色**，否决理由原文「单用户工具类 APP，动态取色会让**缩略图的色彩判断**失真（本产品靠颜色筛东西）」（`DECISIONS.md` ADR-04 ← `PRD.md` ⛔D4；同一句也写在 `Theme.kt:27`）。重做时不要加"跟随壁纸"开关。

### 3.3 `tertiary` 不是配色旋钮

`tertiary` 与 `primary` 同色（`Theme.kt:17`、`:22`），只为压掉 M3 默认的紫红 tertiary；但**当前没有任何界面在用它**（全量 grep `ui/` 除 `Theme.kt` 外无引用，`ARCHITECTURE.md` §11.5）。改它看不见任何变化；真要加用 tertiary 的界面，先决定它该不该与 primary 一致。

### 3.4 error 色承载的三种语义

| 语义 | 条件 | 位置（文案 + 颜色） |
|---|---|---|
| 位置为 null（照片墙） | `summary.locationPath == null` | `GalleryScreen.kt:324` 文案、`:326` 颜色 |
| 位置为 null（详情页） | `breadcrumb == null` | `DetailScreen.kt:185` 文案、`:188` 颜色 |
| 快到期 | `daysUntil(expires) <= 30` | `GalleryScreen.kt:336`（`RULES.md` §14 记同一行） |
| 失败（表单） | `state.error != null` | `ReviewScreen.kt:171` |
| 失败（取景） | 本地状态 `missed` | `CameraScreen.kt:75` |

📐 `days <= 30` 是**纯显示强调**，与提醒窗口（提前天数）无关，别同步改（`RULES.md` §1.2、§14）。
🔒 位置为 `null` 时必须写「待归类」并换 error 色，不能留空（`UI.md` §3.5）。

### 3.5 `surfaceVariant` 的用法

只做"底"和"次要文字"，**不承载任何语义**。做底：无图 48dp 空块（`ItemRow.kt:55`）、无图 1:1 块（`GalleryScreen.kt:294`）、详情页位置卡（`DetailScreen.kt:178`）、字段卡（`DetailScreen.kt:210`，`copy(alpha = 0.4f)`）。
做次要文字色 `onSurfaceVariant`：`ItemRow.kt:69`、`GalleryScreen.kt:301`/`:327`、`TreeScreen.kt:274`、`DetailScreen.kt:142`/`:189`/`:239`。

### 3.6 要换配色时怎么改

1. 只改 `Theme.kt:10-13` 的四个常量，以及 `:16`/`:21-22` 里成对写好的 `onPrimary`/`onSecondary`；
2. 两张 scheme 都必须给，深色不许省（`Theme.kt:29` 只有两条路）；
3. 换完对着 §3.4 逐条验：三种语义仍然只挂在"需要你处理"的位置上；
4. 不许跟着变：error 语义、Fit/Crop 分工、五屏 + 底栏四格 + 单颗 FAB（`UI.md` §12、`REBUILD.md` §2）。

## 4. 字号与排版

### 4.1 现状：全走 M3 默认

**没有自定义 `Typography`、没有改 `Shapes`**：`MaterialTheme(...)` 只传 `colorScheme`（`Theme.kt:28-31`），字号阶梯与圆角都是 M3 默认（`ARCHITECTURE.md` §11.5）。
全项目也没有一处 `fontSize = …sp`、自定义 `TextStyle`、`Card`、`Surface` 或 `elevation`。"文档里没写排版规范"不是漏写——是这个 app 就没改它。

### 4.2 界面实际用到的样式角色

grep 全 `ui/` 后 `MaterialTheme.typography` 出现的全部位置：

| 角色 | 用在哪 | 位置 |
|---|---|---|
| `titleLarge` | 详情页物品名称（整屏宽度、不限行数） | `DetailScreen.kt:117` |
| `titleMedium` | 照片墙无图占位块的名字（前 8 字，`maxLines = 2`） | `GalleryScreen.kt:300` |
| `titleSmall` | 设置页分节标题（`FontWeight.Bold`） | `SettingsScreen.kt:316` |
| `bodyLarge` | 位置页节点名（`FontWeight.Medium`） | `TreeScreen.kt:266` |
| `bodyMedium` | 卡片单元名字、列表行名字、详情页字段值与位置卡值、设置页正文、录入页错误/提示/AI 状态行 | `GalleryScreen.kt:309`、`ItemRow.kt:59`、`DetailScreen.kt:186`/`:244`、`SettingsScreen.kt:321`、`ReviewScreen.kt:171`/`:175`/`:414`/`:418` |
| `bodySmall` | 卡片单元位置行、列表行第二行、树节点第二行、详情页"更新于"与字段标签、录入页"这一件还没保存"与 AI 位置话 | `GalleryScreen.kt:325`、`ItemRow.kt:68`、`TreeScreen.kt:273`、`DetailScreen.kt:141`/`:238`、`ReviewScreen.kt:134`/`:428` |
| `labelLarge` | 区块标签（位置、这里的物品、类型、数量、放在哪里、到期提醒） | `DetailScreen.kt:183`、`TreeScreen.kt:128`/`:305`、`ReviewScreen.kt:282`/`:291`/`:304`/`:441` |
| `labelSmall` | 卡片单元到期行、详情页 `#tag` 排 | `GalleryScreen.kt:335`、`DetailScreen.kt:195` |

其余控件（`TopAppBar` 标题、`Button`/`TextButton`、`FilterChip`、`OutlinedTextField`、`AlertDialog` 标题与正文、`NavigationBarItem` label、`Snackbar`）**没有传 `style`**，用组件自带的 M3 默认。🎨 换字号就是改这一列。

### 4.3 三条排版层的规矩（都来自真机形状 bug）

| # | 规矩 | 出处 |
|---|---|---|
| 1 | 一句话与一颗按钮并排时，那句话必须 `Modifier.weight(1f)`，否则按钮被挤成竖排 | `ReviewScreen.kt:135`、`:429-430`；`UI.md` §13 |
| 2 | 一个区块只占一行，可滚或可折，不能不限高（标签是全库累积的，芯片云不限高会把网格压成一条缝） | `GalleryScreen.kt:206` 注释、`:232` 的 `heightIn(max = 180.dp)` |
| 3 | 解码中的图片要有固定高度的格子，不能让表单跳一下 | `ReviewScreen.kt:151-153` |

### 4.4 长名字 / 多标签 / 字体缩放下的现状

| 位置 | 现状 | 出处 |
|---|---|---|
| 详情页名称 | `titleLarge`，整屏宽、**不限行数** | `DetailScreen.kt:114-118` |
| 详情页字段值 | 不限行数（长描述要能看完），标签列固定 `width = 64.dp` | `DetailScreen.kt:240`、`:244` |
| 树页标题（面包屑） | `maxLines = 2` | `TreeScreen.kt:80` |
| 详情页标题 | `Text("物品详情", maxLines = 1)` | `DetailScreen.kt:78` |
| 卡片单元名字 / 位置行 | `maxLines = 1` + `TextOverflow.Ellipsis` | `GalleryScreen.kt:311-312`、`:328-329` |
| 无图占位块 | `item.name.take(8).ifBlank { "未命名" }`、`maxLines = 2` | `GalleryScreen.kt:299`、`:302` |
| 树节点名与第二行、列表行两行 | 不限行数 | `TreeScreen.kt:266`/`:273`、`ItemRow.kt:59`/`:68` |

**要求**：字体放大时，"不限行数"的必须继续换行而不是被裁；`maxLines = 1` 的必须继续省略号而不是压掉右边的 ⋮。
⚠️ 实际排版只能真机看（`UI.md` §15 第 7 条、`REBUILD.md` §6 第 7 条）。

### 4.5 新增界面时的字号建议

层级不超过三级：主信息 → 次要信息 → 标签；用现有角色（`titleLarge`/`bodyLarge` → `bodyMedium` → `bodySmall`/`labelSmall`），不引入新角色。按钮、芯片、输入框不传 `style`，只有"区块标签"用 `labelLarge`。一行文字旁边有控件时，先给 `weight(1f)`（§4.3 第 1 条）再谈字号。

## 5. 间距与尺寸 token

### 5.1 现有取值（全部核到源码）

| 值 | 用在哪 | 出处 |
|---|---|---|
| `GridCells.Adaptive(minSize = 156.dp)` | 照片墙网格列宽 | `GalleryScreen.kt:123` |
| 网格间距 8dp / 10dp；`contentPadding(bottom = 88.dp)`；列 `weight(1f)` | 水平 8、垂直 10；底部 88dp 给 FAB 让位；网格吃掉剩余高度 | `GalleryScreen.kt:124`、`:125`、`:126`、`:127` |
| 48dp | 列表行缩略图、树节点缩略图 | `ItemRow.kt:46`、`TreeScreen.kt:260` |
| 180dp | 录入页照片框高、位置照片高 | `ReviewScreen.kt:149`、`TreeScreen.kt:227` |
| 200dp 宽 + 1:1 | 详情页照片横滚每张 | `DetailScreen.kt:129-130` |
| 圆角 8 / 10 / 12dp | 8：48dp 缩略图（`ItemRow.kt:47`/`:54`、`TreeScreen.kt:261`）；10：卡片单元（`GalleryScreen.kt:275`/`:286`）；12：录入页照片（`ReviewScreen.kt:150`）、位置照片（`TreeScreen.kt:228`）、详情页照片与两张卡（`DetailScreen.kt:131`/`:177`/`:209`） | 见左列 |
| 页边距 12dp / 16dp | 12：照片墙正文左右与卡片单元顶部（`GalleryScreen.kt:100`、`:89`）；16：树页、详情页、录入页、取景屏左右与设置页正文、对话框内（`TreeScreen.kt:96`、`DetailScreen.kt:111`、`ReviewScreen.kt:112`、`CameraScreen.kt:60`、`SettingsScreen.kt:133`、`ReviewScreen.kt:493`） | 见左列 |
| 竖向 2 / 6 / 8 / 12dp | 2：树页行间距（`TreeScreen.kt:97`）；6：照片墙块间距与筛选层间距（`GalleryScreen.kt:101`/`:205`/`:234`）；8：详情页正文块（`DetailScreen.kt:112`）；12：录入页表单块、设置页分节（`ReviewScreen.kt:113`、`SettingsScreen.kt:134`） | 见左列 |
| 芯片间距 6 / 8dp | 照片墙筛选行 6dp、录入页芯片行 8dp | `GalleryScreen.kt:209`/`:221`/`:242`/`:251`、`ReviewScreen.kt:118`/`:187`/`:493` |
| `depth * 16.dp` | 位置挑选框的层级缩进 | `LocationPicker.kt:50`（`depth` 由 `LocationFlatten.kt:8-21` 给） |
| `width = 64.dp` | 详情页字段卡的标签列 | `DetailScreen.kt:240` |
| `height(240.dp)` | 位置挑选框的列表区 | `LocationPicker.kt:48` |
| `heightIn(max = 180.dp)` | 展开后的筛选面板限高 | `GalleryScreen.kt:232` |
| `width(160.dp)` | 录入页「加标签」输入框 | `ReviewScreen.kt:463` |
| 末尾留白 80dp / 24dp；空态 `padding(vertical = 24.dp)`；行内边距 8 / 10 / 3dp | 树页常驻最后一项 80dp、详情页 24dp；空态文字；列表行竖向 8dp、树行竖向 10dp、详情页字段行 3dp | `TreeScreen.kt:133`、`DetailScreen.kt:145`、`GalleryScreen.kt:119`、`TreeScreen.kt:115`、`ItemRow.kt:36`、`TreeScreen.kt:250`、`DetailScreen.kt:235` |

### 5.2 新增界面时的取值建议

- **沿用最小集合**：4 / 6 / 8 / 10 / 12 / 16 / 24 dp。已出现过的值优先，不引入 7、13、18 这类新数。
- **触控目标 ≥ 48dp**：全 `ui/` 没有一处 `sizeIn`/`defaultMinSize`，下限靠组件默认值兑现（`IconButton` `GalleryScreen.kt:315`/`TreeScreen.kt:278`、`NavigationBarItem` `WheremyRoot.kt:302-325`、`FloatingActionButton` `WheremyRoot.kt:329`）——自绘或改尺寸时自己保底 48dp。短列表用 `spacedBy`、长列表用 `contentPadding`：后者管"最后一格不被 FAB/底栏盖住"（`GalleryScreen.kt:126`）。

## 6. 组件库

### 6.1 导航与容器

| 组件 | 用法与当前取值 | 禁止项 |
|---|---|---|
| 顶部栏 `TopAppBar` | 五屏各一条（`GalleryScreen.kt:81`、`TreeScreen.kt:75`、`DetailScreen.kt:74`、`ReviewScreen.kt:90`、`SettingsScreen.kt:124`）。标题：照片墙/待归类按模式取词（`GalleryScreen.kt:82`）；树页标题**就是面包屑**（`joinToString(" / ")`，`maxLines = 2`，`TreeScreen.kt:77-81`）；详情页固定「物品详情」（`maxLines = 1`，`DetailScreen.kt:78`）。返回用文字按钮「返回」（`DetailScreen.kt:80`），不用箭头图标；动作全是文字按钮/芯片，最多三颗（`DetailScreen.kt:83-85`） | 🔒 详情页三颗动作全挂在 `state.summary != null` 上（`:82`），读取中/记录没了时只有「返回」——不许改成"常驻 + 点了报错"（`UI.md` §5.1）；🔒 名称不挂详情页顶栏（`:76-77` 注释） |
| 底部导航 `NavigationBar` | 四格（`WheremyRoot.kt:302-325`）：`Home`「照片墙」、`AutoMirrored.List`「位置」、`Warning`「待归类」、`Settings`「设置」；图标一律 `contentDescription = null`（label 已说了它是谁） | 🔒 label 文字不许换——界面测试按文字找节点（`UI.md` §2.2）；🎨 图标可换 |
| FAB | 一颗 `Icons.Filled.Add`，描述「选照片录入」（`WheremyRoot.kt:330`），点击 = `askForPhotoSource(PhotoTarget.ITEM)`（`:329`）；网格底部靠 `contentPadding(bottom = 88.dp)` 让位 | 🔒 **全应用唯一的新增入口**（`UI.md` §2.2、`REBUILD.md` §2 第 5 条）：顶栏不许再加"+"，不许加第二颗 FAB |
| 搜索框 | `OutlinedTextField` + `singleLine = true`，label 逐字「搜名称、颜色、材质、品牌、备注等文字」（`GalleryScreen.kt:103-108`）；搜索语义在 `RULES.md` §10 | 不换 `SearchBar`：会带出建议项与独立状态，与"每次进页重读"的 `vm.refresh()` 打架 |
| 对话框 `AlertDialog` | 全应用 12 处弹框（11 个 `AlertDialog` + 1 个 `DatePickerDialog`）：`WheremyRoot.kt:215`、`GalleryScreen.kt:147`/`:178`、`DetailScreen.kt:161`、`TreeScreen.kt:153`/`:195`/`:293`、`ReviewScreen.kt:317`/`:338`/`:479`、`SettingsScreen.kt:103`、`LocationPicker.kt:40`。统一形状：**标题 + 正文 + 两颗按钮**（确认 `confirmButton`、取消/关闭 `dismissButton`；个别只有一颗「关闭」） | 🔒 每处 `onDismissRequest` **只关自己、不写库**（12 处都是这个规矩，`UI.md` §2.3），别把任何一处写成"顺手保存/顺手删除"；🔒 标题与按钮文字是 🗣 |
| 菜单 | 一律**那颗 ⋮**（`IconButton` + `Icons.Filled.MoreVert`）打开对话框：卡片单元 `GalleryScreen.kt:315`（描述「操作」）、树节点 `TreeScreen.kt:278`（描述「位置操作」） | 禁止长按；容器可换（`BottomSheet` 也行，`UI.md` §12），但入口必须是显式控件；⚠️ 边界项要**藏起来**不置灰（`TreeScreen.kt:158-167`） |
| Snackbar | 宿主挂各屏 `Scaffold(snackbarHost = …)`（`WheremyRoot.kt:299`、`GalleryScreen.kt:79`、`TreeScreen.kt:73`、`DetailScreen.kt:72`）；消费走 `SnackbarMessage(host, message, onConsumed)`（`SnackbarMessage.kt:17-37`） | 🔒 **两个 `LaunchedEffect` 缺一不可**（`SnackbarMessage.kt:14-17` 注释）：合成一个，消费会让 key 变化、正在挂起的 `showSnackbar` 被取消，什么都看不见；🔒 录入页开着时 `say` 干脆不说（`WheremyRoot.kt:112-113`），别把条幅挪进录入页 |
| 进度 | AI 分析中：`LinearProgressIndicator` + 「AI 正在看照片，可以先自己填」，**不挡表单**（`ReviewScreen.kt:413-416`）；备份中：`CircularProgressIndicator` + 「正在读写备份包…」，三颗按钮 `enabled = !state.busy`（`SettingsScreen.kt:291-299`、`:277`/`:284`/`:288`） | 🔒 忙时忽略新手势（`RULES.md` §16）；🔒 不许全屏遮罩——分析中必须能继续手填 |
| 日期框 `ExpireDialog` | `DatePickerDialog` + `EXPIRE_KINDS` 芯片行 + 「确定」/「清除」（`ReviewScreen.kt:470-501`） | 🔒 「确定」`enabled = pickerState.selectedDateMillis != null`（`:484`，理由 `:483` 注释）；🔒 UTC 零点毫秒必须换回本地零点（`:486`、`Dates.kt:35`） |
| 取景屏 | 按 `gate` 三态：无相机一句话 / 要授权一句话 +「打开相机权限」/ 预览面（`CameraScreen.kt:62-71`）；失败行 `missed` 用 error 色（`:72-78`）；底行「取消」「拍这张」（仅 `Ready`，`enabled = !busy`）「改用相册选」（`:79-98`） | 🔒 自己必须不透明（`:55-56` 注释：它是叠层，另两态底下没有预览面垫着）；🔒 「改用相册选」与「打开相机权限」常驻（`UI.md` §8） |

### 6.2 内容单元

| 组件 | 用法与当前取值 | 禁止项 |
|---|---|---|
| 筛选芯片与限高 | 三层次序见 `UI.md` §3.3（🔒）。分类芯片与「更多筛选」各占**单行 `horizontalScroll`**（`GalleryScreen.kt:207-228`）；展开面板里的季节与标签用 `FlowRow`（`:242`、`:251`），整块 `heightIn(max = 180.dp)` + `verticalScroll`（`:232-233`） | 🔒 芯片云不许不限高（`:206` 注释：会把网格压成一条缝，批次 AA 事故）；换折叠/抽屉可以谈，"不限高"不行 |
| 卡片网格单元 `GalleryCell` | 自上而下四块（`GalleryScreen.kt:266-340`）：图 = `AsyncImage` + `aspectRatio(1f)` + 圆角 10dp + `Crop`（`:280-288`）；无图 = `surfaceVariant` 1:1 块 + 名字前 8 字（`:290-304`）；名字 + ⋮ = `bodyMedium`/`Medium`、`maxLines = 1` + `Ellipsis`、⋮ 描述「操作」（`:306-322`）；位置行 = `locationPath ?: "待归类"`，null 用 error 色（`:323-330`）；到期行仅 `expiresAt != null` 才画，`days <= 30` 用 error 色（`:331-339`） | 🔒 菜单是 ⋮ 不是长按（`UI.md` §3.5）；🔒 墙上 1:1 用 `Crop` 允许（看"哪一件"），录入页那张禁止裁（`UI.md` §6.3） |
| 列表行 `ItemThumbRow` | 48dp 缩略图 + 名字 + 第二行；第二行三块拼、`joinToString(" · ")` 连，`extra` 为空整段不给（`ItemRow.kt:60-71`）：`"x${quantity}"` 仅 >1（`:61`）、`"到期 ${formatDate}"`（`:62`）、`"在 $it"`（`:63`）；无图给同尺寸 `surfaceVariant` 空块（`:50-56`） | 🔒 三块的内容与次序不许改（`UI.md` §9） |
| 空态 | 照片墙三句互斥（`GalleryScreen.kt:114-118`），树页两句分根层/子层（`TreeScreen.kt:112-114`）；排版为一段普通文字 + `padding(vertical = 24.dp)` | 🔒 逐字与优先级不许改（`UI.md` §3.4）；不许在空态里塞操作按钮——唯一录入入口是 FAB |

## 7. 状态设计

### 7.1 加载中

详情页给一句「读取中…」（`DetailScreen.kt:96`），可以换骨架屏或转圈，但那三态互斥关系不许破（§7.3）。
照片墙与树页在"列表空且 `loading`"时**什么都不画**（`GalleryScreen.kt:111`、`TreeScreen.kt:109` 只判 `!state.loading`），可以补骨架格，但别让"加载中"与空态同屏。备份与分析中的样式见 §6.1 的"进度"一行。

### 7.2 空（三句互斥）

照片墙按优先级：有筛选 → 「按当前的搜索和筛选没找到物品，去掉几个条件再看看。」；待归类模式 → 「没有待归类的物品，全都放好了。」；其余 → 「还没有物品。点右下角的按钮选第一张照片。」（`GalleryScreen.kt:114-118`）。
🔒 第 1 句的理由在 `:113`：有筛选条件时不能说"还没有物品"。**不许把三句合并成一句带参数的话术**（`UI.md` §3.4）。

### 7.3 错误（三态互斥、`error` 与 `notice` 并存）

详情页三态（`DetailScreen.kt:95-99`）：`loading` → 「读取中…」；`missing` → 「没有这条记录。」；否则 → 「这条记录读不出来，返回上一页再试一次。」。🔒 第三态是 `else` 兜的，必须含糊到不会说谎（`UI.md` §5.2）。
录入页 `state.error`（error 色，`ReviewScreen.kt:171`）与 `state.notice`（普通色，`:175`）**可以同屏并存**、没有优先级——⚠️ `RULES.md` §15 W7。设计上两者的样式必须**看得出区别**，别画成同一种提示条。

### 7.4 无照片（`thumbPath == null`）

| 处 | 现在长什么样 | 出处 |
|---|---|---|
| 列表行 | 同尺寸 `surfaceVariant` 空块 | `ItemRow.kt:50-56` |
| 卡片单元 | `surfaceVariant` 1:1 块 + 名字前 8 字（`maxLines = 2`） | `GalleryScreen.kt:290-304` |
| 树节点行 | **不画这一格** | `TreeScreen.kt:255`（注释 `:254`） |
| 位置照片区 | 换成一颗 `TextButton`「给这个位置选一张照片」 | `TreeScreen.kt:219-220` |
| 录入页照片区 | 整块不给（`state.thumbPath?.let`） | `ReviewScreen.kt:143` |

允许改成任何"明确表示这里本来就没有照片"的样式（更清楚的图标、更准确的话）。
必须先做：**保住"从不假装那里有过一张图"这条区分**——`TreeScreen.kt:254` 的理由不能丢。

### 7.5 路径有值但文件丢失（⚠️ 六处 `AsyncImage` 都没有占位）

六处 `AsyncImage` 的入参只有 `model`/`contentDescription`/`modifier`/`contentScale` 四个，**全应用没有一处传 `placeholder` 或 `error`**（`ItemRow.kt:42`、`GalleryScreen.kt:280`、`DetailScreen.kt:125`、`ReviewScreen.kt:144`、`TreeScreen.kt:222`、`:256`）。于是文件丢了或解不开时，那一格尺寸照旧占着、里面**不画任何东西**（`RULES.md` §15 W11、`UI.md` §14 W11）。

| 现状 | 允许改成 | 必须先做 |
|---|---|---|
| 空格子，原地不给任何说明 | 一个**有话说**的占位（自绘占位或 `error` 槽位） | ① 别补灰框（`TreeScreen.kt:254`）；② 那句新话必须真的成立，走 `REBUILD.md` §5 五步门；③ 现有三句"读不出照片"各管一段、**都不在显示它的那一格上**（`WheremyRoot.kt:127`、`PhotoStore.kt:134`、`ReviewViewModel.kt:169`），别抢它们的活 |

### 7.6 未命名物品的显示名

| 场景 | 显示 | 出处 |
|---|---|---|
| 卡片单元名字、列表行名字、详情页名称、单元菜单标题 | 「未命名物品」 | `GalleryScreen.kt:308`/`:149`、`ItemRow.kt:59`、`DetailScreen.kt:116` |
| 照片墙**无图占位块里** | 名字前 8 字，空则「未命名」（`take(8)`） | `GalleryScreen.kt:299` |
| 备份校验那句里 | 「未命名物品」（`ifBlank` 插值） | `RULES.md` §12.3 |

🔒 两处措辞不同是现状（一处是格子里的视觉填充、一处是文字行里的身份），不要"统一"成一个词而不改测试。

### 7.7 长文本截断

现状见 §4.4。视觉要求只有一条：**截断必须让人看得出被截断了**（`Ellipsis`，`GalleryScreen.kt:312`/`:329`），不许无声裁掉（批次 AA 的事故就是名字被顶栏无声裁掉）。`take(8)` 是无图占位的**内容**规则（`GalleryScreen.kt:299`），不是排版参数，别当截断样式推广到别处。

## 8. 图标与品牌

### 8.1 依赖边界

🔒 只用 `material-icons-core`（`gradle/libs.versions.toml:28`），**不引 `material-icons-extended`**（包体 + 无按需裁剪，`REBUILD.md` §2 第 10 条）。要的图标 core 里没有时：换一个 core 里有的，或自绘——**不加依赖**（加依赖先答辩，`PROCESS.md` §3.6）。

### 8.2 当前用到的图标（全部核到源码）

| 图标 | 用在哪 | 出处 |
|---|---|---|
| `Icons.Filled.Home` | 底栏第 1 格 | `WheremyRoot.kt:305` |
| `Icons.AutoMirrored.Filled.List` | 底栏第 2 格 | `WheremyRoot.kt:311` |
| `Icons.Filled.Warning` | 底栏第 3 格 | `WheremyRoot.kt:317` |
| `Icons.Filled.Settings` | 底栏第 4 格 | `WheremyRoot.kt:323` |
| `Icons.Filled.Add` | FAB | `WheremyRoot.kt:330` |
| `Icons.Filled.MoreVert` | 卡片单元 ⋮、树节点 ⋮ | `GalleryScreen.kt:317`、`TreeScreen.kt:279` |
| `Icons.Filled.Close` | 录入页左上 ✕ | `ReviewScreen.kt:94` |
| 系统 `android.R.drawable.ic_popup_reminder` | 到期通知的小图标 | `ReminderWorker.kt:114` |

📌 「▸」（有子节点才给）与「＋」（加标签那颗的 trailing）是**文字**不是 `Icon`（`TreeScreen.kt:277`、`ReviewScreen.kt:464`）。

### 8.3 启动图标

自适应图标三份 xml：`res/mipmap-anydpi-v26/ic_launcher.xml`（background + foreground 两个槽）、`res/drawable/ic_launcher_foreground.xml`、`res/values/ic_launcher_background.xml`（`#00695C`，与 `Teal40` 同值）。
Manifest 只有 `android:icon="@mipmap/ic_launcher"`，**没有 `roundIcon`**：`mipmap-anydpi-v26` 自己填圆形槽位，加 `roundIcon` 反而要在各密度目录再塞一套位图（`ARCHITECTURE.md` §2）。
🔒 渲染成什么样只能真机看（`UI.md` §15 第 5 条、`REBUILD.md` §6 第 6 条）；`LauncherIconTest` 只验画进 Bitmap 的形状。

### 8.4 `res/` 纪律

`res/` 只有 5 个文件（`ARCHITECTURE.md` §2）：图标三份 + `values/strings.xml`（只有 `app_name`）+ `values/themes.xml`（`Theme.Wheremy` 继承 `android:Theme.Material.NoActionBar`，Compose 里的配色与它无关）。
🔒 **界面文案不抽 `strings.xml`**（`REBUILD.md` §2 第 11 条）：所有中文硬编码在 Kotlin，界面测试按文字断言——**改一句文案 = 改代码 + 改断言**，走 `REBUILD.md` §5 五步门。🎨 颜色与间距也不进 `res/`。

## 9. 无障碍

### 9.1 `contentDescription` 现状（逐处核实）

| 描述 | 元素 | 出处 |
|---|---|---|
| 「选照片录入」 | FAB | `WheremyRoot.kt:330` |
| 「操作」/「位置操作」 | 卡片单元 ⋮ / 树节点 ⋮ | `GalleryScreen.kt:318`、`TreeScreen.kt:279` |
| 「物品照片」 | 录入页照片 | `ReviewScreen.kt:146` |
| 「位置照片」/「位置缩略图」 | 位置照片区 / 树节点 48dp 缩略图 | `TreeScreen.kt:224`、`:258` |
| 「关闭」 | 录入页 ✕ | `ReviewScreen.kt:94` |
| 物品名 `item.name` | 照片墙卡片图、列表行缩略图 | `GalleryScreen.kt:282`、`ItemRow.kt:44` |
| `null` | 底栏四个图标、详情页照片横滚每张 | `WheremyRoot.kt:305`/`:311`/`:317`/`:323`、`DetailScreen.kt:127` |

### 9.2 已知缺口

- **详情页照片传 `null`**（`DetailScreen.kt:127`）；触发条件与代价见 `PRD.md` FR-PRM-05（§3.11、§5.1）：有视障使用场景才做，代价是逐屏补 description + 焦点次序。⚠️ 该条说缺的是"网格里 48dp 缩略图与芯片组"，但源码里 48dp 两处**都有** description（`ItemRow.kt:44` 物品名、`TreeScreen.kt:258`「位置缩略图」），芯片是自带文字 label 的 `FilterChip`；真正缺的是详情页那几张照片，以及**名字为空时描述是空串**（`ItemRow.kt:44`、`GalleryScreen.kt:282` 会得到 `""`）。此条不在本文单方面裁定，已记入 §13。

### 9.3 触控目标与文字缩放

项目没有显式 `sizeIn`/`defaultMinSize`，48dp 下限靠组件默认值（§5.2），**新增自绘控件时自己保底**；文字缩放的现况与要求见 §4.4（真机验证 `REBUILD.md` §6 第 7 条）。

### 9.4 新增界面时的最低要求

**每个可点元素要么有文字、要么有 `contentDescription`。**

1. 纯图标按钮 → 给描述（例：`GalleryScreen.kt:318`「操作」）；
2. 带文字的按钮/芯片 → 不给描述（文字本身是标签，给两遍会念两次，例：底栏四格 `contentDescription = null`）；
3. 装饰性图片 → `null`；承载信息的图片 → 给一句能代替它的话；
4. 一句话与一颗按钮同行时，先按 §4.3 第 1 条给文字 `weight(1f)`。

## 10. 动效与反馈

### 10.1 现状与允许引入的边界

**没有自定义动效、没有自绘组件**（`UI.md` §11）；全应用可见动画只有 M3 组件自带的（对话框进出、条幅、进度、涟漪）。
🎨 过渡、微交互、共享元素都可以加，但**不得延迟或遮蔽** `RULES.md` §16 第 5 条那条判定：**写操作成功之前不说"已经好了"**（`:1090`）。落到设计上：

- 关页或报"已完成"的时机由状态决定，不由动画时长决定（例：`ReviewScreen.kt:81-86` 的 `finished` 那一支）；
- 不许用乐观动画先把成功态画出来（`SettingsViewModel.setLeadDays` 那种"乐观写 UI 再落盘"只适用于设置项，不适用于物品/位置/备份）；
- 转圈必须能自己停，停下来的同时要说下一步（`ReviewViewModel.kt:166-167` 注释）。

### 10.2 按钮禁用优先于"点了没反应"

同一个"名字没填"，项目里两种做法：`LocationPicker.kt:73` 让按钮变灰（对），`TreeScreen.kt:326` 永远亮着、点了走 `if` 的假支（错，⚠️ `RULES.md` §15 W10、`UI.md` §4.7）。**新界面一律取前一种**：`enabled` 由状态算，不给"无反馈的点击"。

## 11. 交付物：新界面 / 改界面要交给开发什么

| # | 交付物 | 说明 | 参照 |
|---|---|---|---|
| 1 | **信息架构位置** | 在哪一屏、是不是叠层；叠层要说明为什么不能是替换（取景屏是叠层，理由 `WheremyRoot.kt:187-190`） | `UI.md` §1.1、§12 |
| 2 | **字段与控件次序** | 从上到下的完整清单，含"什么条件下不出现" | `UI.md` §5.3、§6.5 是标准样例 |
| 3 | **每句逐字文案** | 空态、错误、禁用、加载中的话一句不落，用「」标出 | 逐字串的唯一事实源是源码（`README.md` §3.1） |
| 4 | **全部状态的样式** | 加载 / 空 / 错误 / 无图 / 图丢了 / 未命名 / 长度溢出 / 字体放大，每种一张图或一段说明 | §7 |
| 5 | **无障碍标签** | 每个可点元素与每张承载信息的图，给"有文字或给描述"的结论 | §9.4 |

**逐字文案的改动另有五步门**：`REBUILD.md` §5（找出这句话在哪些分支上会被说 → 数它承诺了什么 → 先写一条会红的测试 → 看形状 → 同步四处文档），流程入口 `PROCESS.md` §3.4。
**本文的改动只走一条通道**：改视觉/组件/间距 = 改 `DESIGN_SYSTEM.md`，可能连带 `UI.md` §11（`PROCESS.md` §5）。

## 12. 一致性自检：改视觉时哪些东西不许跟着变

| 不许变 | 判据 | 出处 |
|---|---|---|
| **error 色的三种语义** | 位置为 null、`days <= 30`、失败；换色等于改语义 | `REBUILD.md` §2 第 12 条、§3.4 |
| **Fit / Crop 的分工** | 录入页那张 `Fit`（要看清"拍全了没有"），墙上与缩略图 `Crop` | `UI.md` §3.5/§6.3、`REBUILD.md` §2 第 13 条 |
| **五屏 + 底栏四格 + 单颗 FAB** | FAB 是全应用唯一新增入口 | `UI.md` §1.1/§12、`REBUILD.md` §2 第 5 条 |
| **返回键优先级四层** | 取景屏 → 录入页（与 ✕ 同一个门）→ 详情页 → 树页退一层 | `UI.md` §2.3、`REBUILD.md` §2 第 8 条 |
| **`deliverPhoto` 单一收口** | 换组件/换框架都不许把照片分派拆成两处 | `REBUILD.md` §2 第 7 条、`UI.md` §2.4 |
| **九格的恢复语义**（`UI.md` §2.1 的八格 + `SettingsScreen.kt:74` 的 `pendingMode` 一格） | 进程被杀后回到原来那一屏；其中四格刻意不是 `saveable` | `UI.md` §2.1/§12、`REBUILD.md` §2 第 6 条 |
| **所有 🗣 逐字文案** | 界面测试按文字找节点 | `REBUILD.md` §2 第 9 条 |
| **`LocationPickerDialog` 是唯一位置挑选件** | 四处复用；`onPick(null)` 两种语义由 `rootLabel` 分开 | `UI.md` §9 |
| **筛选区不许吃掉网格** | 任何芯片云都必须限高 | `GalleryScreen.kt:206`、`:232` |
| **没有真图不画占位格** | 位置行不给灰框 | `TreeScreen.kt:254` |
| **`SnackbarMessage` 的两个 `LaunchedEffect`** | 少一个就什么都看不见 | `SnackbarMessage.kt:14-17` |
| **各对话框的标题与按钮文字** | 容器可换、文字不许换 | `UI.md` §12 |

## 13. 写作时发现的差异（2026-10-07 已全部处理）

写本文时按基线 `cf73f5b` 核对了源码与既有文档，撞出下面三处。按 `README.md` §3.2（先判定、再改错的那一边），**三处都已由主文档改掉**，留表作裁决留痕：

| 文档 A 位置 | 文档 B 位置 | 冲突点 | 处理 |
|---|---|---|---|
| `PRD.md` §3.11 FR-PRM-05（与 §5.1）：「关键控件有 `contentDescription`……但网格里 48dp 缩略图与芯片组没有」 | `UI.md` §9 照片格表 + 源码：`ItemRow.kt:44`、`TreeScreen.kt:258`、`GalleryScreen.kt:282` **都有** description；芯片是自带文字 label 的 `FilterChip` | 原句不成立——真正缺的只是筛选芯片组、几处装饰件与焦点次序（另有 `DetailScreen.kt:127` 传 `null`、名字为空时描述是空串） | **判源码对** → `PRD.md` FR-PRM-05 与 §5.1 已改成"偏差只剩筛选芯片组与焦点次序" |
| `REBUILD.md` §2 第 7 条：`deliverPhoto`（`WheremyRoot.kt:115-145`） | `UI.md` §2.4 与 `ARCHITECTURE.md` §12.1：`:123-145` | 范围起点（`:115` 是空行，KDoc 从 `:116` 起） | **判 B 对** → `REBUILD.md` 已改成 `:123-145` 并注明 KDoc 区间 |
| `REBUILD.md` §2 第 6 条：「状态恢复的**九格**语义｜`WheremyRoot.kt:76-93`（全 `rememberSaveable`）」 | `UI.md` §12 与源码：九格 = §2.1 的**八格** + `SettingsScreen.kt:74` 的 `pendingMode`；`WheremyRoot.kt:93` 的 `gate` 是 `remember` | 括注与范围两处都不准 | **判 B 对** → `REBUILD.md` 已改成"8 格 saveable + 3 格刻意 `remember`"并指向 `UI.md` §2.1 |
