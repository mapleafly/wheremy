# Wheremy 界面文档（UI，as-built）

本文回答一件事：**屏幕上有什么、按什么次序排、谁盖在谁上面、返回键归谁**。
`PRD.md` 说"要什么"，`ARCHITECTURE.md` 说"代码在哪"，`RULES.md` 说"每条规则怎么算"，本文只说"长什么样、怎么点"。

引用形如 `WheremyRoot.kt:123` = `app/src/main/java/com/wheremy/app/ui/WheremyRoot.kt` 第 123 行。
所有 `文件:行` 与所有「逐字文案」都在代码基线 `cf73f5b` 的工作树上 grep 出来并核对过（该基线的 `app/` 与实现仓库当前 HEAD 逐字节一致）；文案一律从源码抄，不凭记忆。
引用解析规则：`ui/` 前缀的按 `app/src/main/java/com/wheremy/app/ui/` 解析；其它包（`data/`、`domain/`、`di/`、`util/`、`MainActivity.kt`）按文件名在 `app/src/main/java/com/wheremy/app/` 下解析；测试类在 `app/src/test/java/com/wheremy/app/` 下解析。

## 0. 怎么读

| 记号 | 含义 |
|---|---|
| 🔒 | **信息架构与交互不许改**。换技术栈、换组件库都要照这一套复现，否则 `RULES.md` 里的某条规则就没有落点 |
| 🗣 | **逐字文案**。重建时要么原样抄，要么同步改断言它的界面测试 |
| 🎨 | **可自由发挥**。颜色、字号、圆角、间距、图标、动效都可以换；列出当前值只作参考 |
| ⚠️ | 已知偏差，见 §14 |

一条总规矩：**界面文案不放在 `res/values/strings.xml` 里**。整个 `res/` 只有 5 个文件（图标三份 + `strings.xml` 只有一句 `app_name` + `themes.xml`），所有面向用户的中文都硬编码在 Kotlin 里（🔒 这一点本身不许改：界面测试按文字断言，抽进资源反而多一层同步）。所以**本文的逐字文案就是唯一权威来源之外的第二份抄本，冲突时以源码为准**。

---

## 1. 信息架构 🔒

### 1.1 一个外壳 + 五屏 + 两类叠层

```
MainActivity（主题 Theme.Wheremy，无 ActionBar）
└── WheremyRoot.kt  ← 唯一的路由器，没有 Navigation 库
    ├── 底栏四格（NavigationBar）+ 右下角一颗 FAB
    │   ├── 照片墙   GalleryScreen（inboxMode = false）
    │   ├── 位置     TreeScreen
    │   ├── 待归类   GalleryScreen（inboxMode = true）  ← 同一个组件，另一个模式
    │   └── 设置     SettingsScreen
    ├── 物品详情  DetailScreen        ← 用 return 提前接管整屏
    ├── 录入/确认 ReviewScreen        ← 用 return 提前接管整屏
    └── 叠层（不替代底下的屏）
        ├── 照片来源弹窗 AlertDialog
        └── 取景屏   CameraScreen
```

`RootTab { GALLERY, TREE, INBOX, SETTINGS }`（`WheremyRoot.kt:63`），当前那一格存成字符串（`tab`，`WheremyRoot.kt:76`），解析不出来就当 GALLERY（`WheremyRoot.kt:106`）。

**为什么录入页和详情页是"整屏接管"而不是叠层**：`WheremyRoot.kt:264` 和 `:293` 各有一句 `return`，底栏和 FAB 在那两屏根本不进组合——那两屏没有"再切个 tab"的语义，留着底栏反而给了用户一条绕过退出确认的后门。

**为什么取景屏必须是叠层**：`WheremyRoot.kt:253-254` 注释写得很直白——录入页一退出组合，`liveReviewVm` 就空了，拍完那张会被当成**新建一件物品**，而不是换掉这一件的照片。所以取景屏 `viewfinder()` 在两个位置各挂一次：录入页里面（`:262`）和 Scaffold 外面（`:394`，连底栏和 FAB 一起盖住）。

### 1.2 屏 → 文件 → VM → 测试

| 屏 | Composable | ViewModel | 界面测试（`app/src/test/.../ui/`） |
|---|---|---|---|
| 外壳 | `WheremyRoot.kt:69` | 无（持有各屏 VM 的活引用） | `WheremyRootTest.kt` 20 例 |
| 照片墙 / 待归类 | `GalleryScreen.kt:65` | `GalleryViewModel`（`inboxMode` 区分） | `GalleryScreenTest.kt` 15 例 |
| 位置 | `TreeScreen.kt:54` | `TreeViewModel` | `TreeScreenTest.kt` 11 例 |
| 详情 | `DetailScreen.kt:51` | `DetailViewModel` | `DetailScreenTest.kt` 10 例 |
| 录入 / 确认 | `ReviewScreen.kt:71` | `ReviewViewModel` | `ReviewScreenTest.kt` 30 例 |
| 设置 | `SettingsScreen.kt:69` | `SettingsViewModel` | `SettingsScreenTest.kt` 16 例 |
| 取景 | `CameraScreen.kt:36` | 无（`gate` 由外壳给） | `CameraScreenTest.kt` 9 例 |
| 位置挑选框 | `LocationPicker.kt:29` | 无 | `LocationPickerDialogTest.kt` 4 例 |

测试用 `createAndroidComposeRule<ComponentActivity>()` + Robolectric（`WheremyRootTest.kt:83-84`，屏幕尺寸 `w411dp-h891dp` 由 `:80` 的 `@Config(qualifiers = …)` 给定）。

VM 的取用统一走 `appViewModel(container, key = "…") { … }`（`AppViewModel.kt:16`）：不引 Hilt，也不手写枚举工厂。

---

## 2. 外壳 WheremyRoot

### 2.1 状态格：哪一格能扛住配置变更 🔒

`WheremyRoot.kt:74-93`。这几格是"旋转/深色切换/进程被杀后不丢"的唯一保障；
**但里面有四格不是 saveable**（其中三格是**刻意**不能存，`snackbar` 只是没必要留），重建时把它们也 `rememberSaveable` 会是错的。

| 格 | 行 | 存不存 | 含义 |
|---|---|---|---|
| `snackbar` | :74 | `remember` | `SnackbarHostState`，跨配置变更没必要留，留了会重复显示同一条 |
| `tab` | :76 | saveable（**String**） | 当前底栏那一格，默认 `RootTab.GALLERY.name` |
| `detailId` | :77 | saveable（`String?`） | 详情页开着是哪件；null = 没开 |
| `reviewOpen` | :78 | saveable | 录入页在不在 |
| `reviewItemId` | :80 | saveable（`String?`） | null = 拍照新增；非空 = 编辑已有物品 |
| `reviewSeq` | :81 | saveable（Int） | 每开一次录入页 +1，用来换 VM |
| `liveReviewVm` | :83 | `remember { mutableStateOf }` | 录入页开着时那台活的 VM。**绝不能存**：VM 里连着仓库与协程作用域，存了就是存了一整棵依赖图 |
| `liveTreeVm` | :85 | `remember { mutableStateOf }` | 位置页活着的那台 VM，同一理由 |
| `askingFor` | :87 | saveable（String） | 来源弹窗为谁开着（`PhotoTarget` 的名字） |
| `photoTarget` | :89 | saveable（String） | 在飞的那张回来该给谁；非 NONE 就是"有一张还在飞" |
| `viewfinderOpen` | :91 | saveable | 取景屏盖没盖着（为谁开写在 `photoTarget`） |
| `gate` | :93 | `remember` | 取景屏进门那一态，`CameraAccess.gate(context)`。**存它会把"已经拒过"当成长期事实**，而它必须在回到前台时重读 |

`PhotoTarget { NONE, ITEM, LOCATION }`（`WheremyRoot.kt:66`）、`RootTab { GALLERY, TREE, INBOX, SETTINGS }`（`:63`）。

**存的全是 String / Boolean / Int**：`tab`、`askingFor`、`photoTarget` 三格存的是枚举的 `.name` 而不是枚举本身。
两种读法不一样，别混：`tab` 是**解析**（`WheremyRoot.kt:106` `RootTab.entries.firstOrNull { it.name == tab } ?: RootTab.GALLERY`，
解析不出来回落照片墙），而 `askingFor` / `photoTarget` 是**逐串比较**（`:124`、`:214`）——没有回落那一手，
因为它们只会被本文件自己写进三个合法值之一。`gate` 每次回到前台重读一次（`WheremyRoot.kt:95-102`）：
去系统设置里手动开授权不会走应用内回调。授权请求本身的回调在 `:103-105`。

### 2.2 Scaffold：底栏 + FAB 🗣

`WheremyRoot.kt:298-332`（`297` 是外层 `Box`；整块 Scaffold 到 `:393`）。

| 格 | 图标 | label（逐字） | 行 |
|---|---|---|---|
| 1 | `Icons.Filled.Home` | 「照片墙」 | :305-306 |
| 2 | `Icons.AutoMirrored.Filled.List` | 「位置」 | :311-312 |
| 3 | `Icons.Filled.Warning` | 「待归类」 | :317-318 |
| 4 | `Icons.Filled.Settings` | 「设置」 | :323-324 |

四格图标都是 `contentDescription = null`（label 已经说了它是谁）。FAB 是 `Icons.Filled.Add`，`contentDescription = "选照片录入"`（🗣 `:330`），点它 = `askForPhotoSource(PhotoTarget.ITEM)`（`:329`）——**这是全应用唯一的新增入口**，五屏里没有第二颗"新建物品"的按钮。

`SnackbarHost` 挂在 Scaffold 上（`:299`），所以录入页接管整屏时它不在组合树里（见 §2.10）。

🎨 图标可换，但 label 文字不许换：界面测试按文字找节点。

### 2.3 返回键优先级 🔒

系统返回键在这套架构里只有四个拦截者，按"谁在最后组合谁先接"排：

| 优先级 | 条件 | 归谁 | 行 |
|---|---|---|---|
| 1 | `viewfinderOpen` | `closeViewfinder`：盖掉取景屏、`photoTarget` 归零 | :193（`:182-185`） |
| 2 | `reviewOpen` | `vm::requestExit`——**和那颗 ✕ 同一个门** | :252 |
| 3 | `detailId != null` | `detailId = null` | :280 |
| 4 | 位置页且已下钻 | `vm.up()`，只退一层 | :366 → `:400-403` |
| 5 | 其余 | 交回系统（退出应用） | — |

`TreeBackHandler` 的 `enabled` 就是"面包屑非空"（`WheremyRoot.kt:402`）：根层不拦，所以在根层按返回是退出应用，这是有意的。

第 2 条那行注释（`:251`）是事故换来的：**系统返回和那颗 ✕ 必须走同一个门**，否则手打的改动还没写库时，返回键会直接关页、绕过退出确认。

上面那张表只管"屏"这一层。全应用 12 处弹框的 `onDismissRequest` 一律**只关自己、不写库**，所以无论返回键落到哪儿都不会顺手改数据：`ReviewScreen.kt:318`/`:339` 的 `vm::cancelExit`、`DetailScreen.kt:162` 与 `GalleryScreen.kt:179` 的 `vm.closeDeleteAsk()`、`GalleryScreen.kt:148` 与 `TreeScreen.kt:154` 的 `menuFor = null`、`TreeScreen.kt:196` 的 `confirmDelete = null`、`SettingsScreen.kt:104` 的 `confirmOverwrite = false`、`TreeScreen.kt:294` 的 `onDismissRequest = onDismiss`（`LocationEditDialog`，落地为 `TreeScreen.kt:140` 的"只关框"）、`WheremyRoot.kt:216` 的 `askingFor = PhotoTarget.NONE.name`，加上 `LocationPicker.kt:41` 与 `ReviewScreen.kt:480` 透传的 `onDismiss`。🔒 重做时别把任何一处 `onDismissRequest` 写成"顺手保存"或"顺手删除"。

### 2.4 照片路由：`deliverPhoto` 是唯一收口 🔒

`WheremyRoot.kt:123-145`。相册和相机两条来源回来之后**都**落到这里，没有第二条路。

```
bytes 为空/null            → 说「读不到这张照片，换一张试试」（:127）
photoTarget == LOCATION    → liveTreeVm.setPhoto(bytes)（:130）：不进录入页、不碰 AI
reviewOpen && hasDraft     → replacePhoto(bytes)（:137）：同一 itemId，填过的字段都留着
其余                        → pendingCapture=bytes; reviewItemId=null; reviewSeq+=1; reviewOpen=true（:139-142）
```

第三条分支的注释（`:134-135`）说明为什么不能省：走下面那条会新建一件物品，老的变成收件箱里一具没名字的壳。

归零发生在最前面（`:125`），于是 `photoTarget` 的读法保持字面意思。而"下一次点 FAB 的图不会被上一张借走"靠的是发起请求时重写那一格：`startAlbum`（`:168`）与 `openViewfinder`（`:176`）各写一次。

相册那条路（`:147-165`）：`uri == null` 是用户取消，**只清那一格、不教训一句"换一张试试"**（`:150-152`）。读字节走 `Dispatchers.IO`（`:156-160`），因为选到的可能是几 MB 原图。

### 2.5 照片来源弹窗 🗣

`WheremyRoot.kt:214-222`，`askingFor != NONE` 才存在。

- 标题：「这张照片从哪来？」
- 正文：「拍照就在 Wheremy 里取景，按下快门才拍；它只要相机权限，不进相册。」
- 右（confirm）：「拍照」→ `openViewfinder`
- 左（dismiss）：「从相册选」→ `startAlbum(askingFor)`

两个入口共用这一颗弹窗：FAB 为 `ITEM`（`:329`），位置页的位置照片为 `LOCATION`（`:371`）。

### 2.6 取景屏叠层

`WheremyRoot.kt:187-209`。签名一共八个参数：`gate` 是状态，其余七个是回调（下表七行）：

| 槽 | 干什么 | 行 |
|---|---|---|
| `gate` | §2.1 那一格 | :195 |
| `shutter` / `preview` | 容器的 `camera.shutter` 与 `camera.Preview()` | :196-197 |
| `onRequestPermission` | `askCamera.launch(CAMERA)` | :198 |
| `onOpenSettings` | 跳相机权限的系统页 | :199 |
| `onUseAlbum` | `startAlbum(photoTarget)`——沿用这一张的目标 | :200 |
| `onCancel` | `closeViewfinder` | :201 |
| `onShot` | **不复用** `closeViewfinder`：先 `viewfinderOpen = false` 再 `deliverPhoto(it)`，否则读不出这张给谁（:203 注释） | :202-205 |

### 2.7 录入页托管 🔒

`WheremyRoot.kt:224-264`。

- VM key 是 `"review-$reviewSeq"`（`:225`）：**每次开录入页换一个 VM**。共用一个会把上一件的字段带进来。
- `liveReviewVm` 用 `DisposableEffect` 登记/摘掉（`:228-233`），§2.4 的"换一张"分支靠它。
- `LaunchedEffect(reviewSeq)`（`:236-245`）三分派：`pendingCapture → vm.start(bytes)` / `reviewItemId → vm.load(id)` / 都没有 → `reviewOpen = false`。
  最后一支是进程被系统杀掉后的恢复：字节只活在内存里，续不上就退回原页面。**这一支里不能 `showSnackbar`**（`:243` 注释）。
  key 必须是 `reviewSeq` 而不是 `Unit`（`:234-235` 注释）：录入页开着时再选一张，VM 换了新的，key 为 `Unit` 的话这个 effect 不再重跑，新 VM 永远不 `start`，表单空白且两颗按钮都点不动。
- `leaveReview`（`:247-250`）清 `pendingCapture` 再关页；保存/退出都走它。
- 取景屏叠在 `ReviewScreen` 之上（`:255-262`），见 §1.1。

### 2.8 详情页托管

`WheremyRoot.kt:278-294`。`onEdit`（`:286-291`）= 清空 `pendingCapture` + `reviewItemId = id` + `reviewSeq += 1` + `reviewOpen = true`：编辑已有物品也是"换一个新 VM 走 load 那条路"，与新增同一条通道。

### 2.9 通知点进来的那一跳 🔒

两段分开，缺一不可（`WheremyRoot.kt:267-276` + `:346-350`）：

1. 外壳这一跳：`entry == ENTRY_EXPIRING` → `detailId = null`、`tab = GALLERY`（`:272-275`）。
2. 墙里消费：GALLERY 分支的 `LaunchedEffect(entry)` 里 `vm.showExpiringSoonOnly()`，然后 `container.entry.value = null`（`:348-349`）。

第 2 段必须在墙那一格内：筛什么由墙上的 VM 决定，意图到了、墙也组合出来了才把它消费掉（`:344-345` 注释）。

第 1 段挂在 `reviewOpen` 之后是故意的（`:268-269` 注释）：录入页开着时这段压根没进组合，**意图就欠着**，等用户退出录入页再落地，不抢那张填了一半的表单。

`ENTRY_EXPIRING = "expiring"` 在 `MainActivity.kt:41`，`onNewIntent` 也会写那一格（`MainActivity.kt:34`）。

### 2.10 `say()`：录入页开着时选择沉默 🔒

`WheremyRoot.kt:112-113`，注释在 `:108-111`：录入页开着时外壳的 `SnackbarHost` 不在组合树里，`showSnackbar` 会一直挂起，消息几分钟后再冒出来反而吓人——所以那句话在这种情况下**干脆不说**（表单原样保留）。

---

## 3. 照片墙与「待归类」（同一个组件，两种模式）

`GalleryScreen.kt:65`，`inboxMode` 由外壳传（`WheremyRoot.kt:342` / `:377`）。

### 3.1 顶栏 🗣

`GalleryScreen.kt:81-91`：

- 标题：`if (vm.inboxMode) "待归类" else "照片墙"`（`:82`）
- actions：只在**非** inbox 模式给一颗「只看待归类」芯片（`:121-125`）。待归类页自己不再给这颗（它是这一页的定义，见 `RULES.md` §1.1）。**2026-10-13 调整**：这颗芯片从 `AssistChip` 换成 `FilterChip(selected = state.filters.onlyInbox)` —— 它是**开关行为**，`FilterChip` 自带 `selected` 语义（读屏念「只看待归类，已选中」），文案不再随状态变（原先靠「只看待归类 · 开」这种改文案的写法），也和这一屏其余筛选芯片同一种控件。**它的行为由语义断言钉住**（`GalleryAccessibilityTest` 的「芯片的标签与状态在同一个无障碍节点上」）；照片墙本身**没有**截图基线——试过，但本机（Windows）录的图在 Linux 真 CI 上像素不一致，撤了（见 `CHANGELOG.md` 2026-10-13 那节）。

### 3.2 搜索框 🗣

`GalleryScreen.kt:103-108`，label「搜名称、颜色、材质、品牌、备注等文字」（`:106`），`singleLine`。搜索语义见 `RULES.md` §10。

### 3.3 筛选区三层 `FilterBar` 🔒

`GalleryScreen.kt:197-263`。**层次与次序不许变**：

| 层 | 内容 | 行 |
|---|---|---|
| 1 | `CATEGORIES` 十二颗分类芯片，单行 `horizontalScroll` | :207-218 |
| 2 | 一颗芯片：没选任何东西时是「更多筛选」，选了之后变成「更多筛选 · 」+ 已选词用空格连起来 | :219-228（label :226） |
| 3（展开） | 先「快到期/已过期」，再 SEASONS `FlowRow`，再 `#tags` `FlowRow` | :229-259（到期 :237-241，季节 :242-250，标签 :251-259） |

到期条件排在季节、标签**之前**的理由在 `:236` 注释：它是从通知点进来时唯一开着的那一个。

收起态也要点名开了哪些条件（`:199-203`），顺序同上：`快到期/已过期 → 季节：X → #tag`。否则"列表少了一半却找不出哪个条件开着"。

🎨 芯片组件与配色可换；`verticalScroll`（`:233`）那层限制不许去掉——注释在 `:206`：标签是全库累积的，芯片云一旦不限高就会把下面的网格压成一条缝（批次 AA 的事故）。

### 3.4 空态三句 🗣🔒

`GalleryScreen.kt:111-120`，三句互斥，顺序即优先级：

1. `isNarrowing(...)` → 「按当前的搜索和筛选没找到物品，去掉几个条件再看看。」
2. `inboxMode` → 「没有待归类的物品，全都放好了。」
3. 其余 → 「还没有物品。点右下角的按钮选第一张照片。」

第 1 句存在的原因写在 `:113`：有筛选条件时不能说"还没有物品"，库里可能满满当当，只是这一屏被筛空了。
`isNarrowing` 的定义在 `:190-193`（`:189` 是它的 KDoc），其中 `(!inboxMode && onlyInbox)` 那半句就是"待归类页的 `onlyInbox` 不算用户自己加的筛选"。

### 3.5 网格与单元 🎨

`GalleryScreen.kt:122-136`：`GridCells.Adaptive(minSize = 156.dp)`（`:123`）、间距 8/10dp、`contentPadding(bottom = 88.dp)`（`:126`，给 FAB 让位）、`Modifier.weight(1f)`（`:127`）。

单元 `GalleryCell`（`:266`）自上而下：

| 块 | 内容 | 行 |
|---|---|---|
| 图 | `thumbPath` 有 → `AsyncImage`，1:1、圆角 10dp、`ContentScale.Crop` | :278-288 |
| 无图 | 灰底 1:1 方块里放 `item.name.take(8).ifBlank { "未命名" }` | :290-304（注释「没照片时拿名称占位，照片墙不会是一片空白格子」:297） |
| 名字 + ⋮ | `item.name.ifBlank { "未命名物品" }` + 一颗 `Icons.Filled.MoreVert`，`contentDescription = "操作"` | :306-322 |
| 位置行 | `summary.locationPath ?: "待归类"`；null 时用 error 色 | :323-330 |
| 到期行 | 仅有 `expiresAt` 才画；`expireHint(days)`，`days <= 30` 用 error 色 | :331-339 |

🔒 不许变的两条：菜单不是长按而是那颗 ⋮（`IconButton(onClick = onMenu)`，`:315`）；位置行为 null 时**换成 error 色并写「待归类」**（`:324` 文案、`:326` 颜色），不能留空——留空等于说"这件没有位置是正常状态"。
🎨 1:1 缩略用 `Crop` 是这里允许的（墙上看的是"哪一件"，不是"拍全了没有"）；录入页那张禁止裁，见 §6.3。

### 3.6 单元菜单 🗣

`GalleryScreen.kt:141-165`：标题是那件的名字（`summary.item.name.ifBlank { "未命名物品" }`，`:149`），正文两颗按钮按次序「搬到…」（`:155`）、「删除」（`:159`），右下角「关闭」（`:162`）。

`:142-145` 那一格不能省：列表在菜单打开期间被刷新（例如删除后重读），旧 id 要**直接丢掉菜单状态**，否则会拿旧名字配新数据。

### 3.7 搬到 / 删除框 🗣

- 搬到：`LocationPickerDialog(title = "搬到")`（`:167-174`，标题 :171），默认 `rootLabel` = 「暂不归类，先进「待归类」」（`LocationPicker.kt:35`）。
- 删除：`GalleryScreen.kt:177-186`，标题「删除这件物品？」（`:180`），正文 `ask.warning`——**按"这件的照片还有谁在用"分档**，共用时不能承诺照片会一起没（`:181` 注释；四档见 `RULES.md` §9.3）。按钮「删除」/「取消」。

照片墙、详情页、待归类用的是同一句 `ask.warning`（`DetailScreen.kt:164` 同一注释），一处生成三处显示。

---

## 4. 位置页

`TreeScreen.kt:54`。

### 4.1 顶栏：面包屑就是标题 🗣🔒

`TreeScreen.kt:75-89`：

- 标题：面包屑为空 → 「全部位置」；否则 `breadcrumb.joinToString(" / ") { it.name }`，`maxLines = 2`（`:77-82`）。
- actions：面包屑非空才给「上一层」（`:84-85`），「新建」一直在（`:87`）。

**面包屑不单独占一行**（它是标题）。所以"退一层"只有那一颗按钮和系统返回键两条路（§2.3 第 4 条）。

### 4.2 正文次序 🔒

`TreeScreen.kt:92-133`，一个 `LazyColumn`：

| 次序 | 内容 | 条件 | 行 |
|---|---|---|---|
| 1 | 位置照片区 | 仅在**非**根层级（`currentParentId != null`） | :99-108 |
| 2 | 空态两句 | 非 loading 且子节点与物品都空 | :109-117 |
| 3 | 子位置行 | 每个一行 | :119-126 |
| 4 | 「这里的物品」+ 物品缩略行 | 非根层级且当前层有物品 | :127-131 |
| 5 | 80dp 空白 | 常驻 | :133 |

空态两句（🗣 `:112-114`）：根层「还没有位置。点右上角「新建」建第一个，例如「家」「主卧」「衣柜」。」/ 子层「这个位置还是空的，点「新建」加个子位置，或去照片墙录入物品。」

`:68` 有一句要紧的：每次进入本页（含从详情页返回）都 `vm.refresh()`——详情页可能刚搬走或删掉物品。

### 4.3 节点行 `LocationRow`

`TreeScreen.kt:240-282`，从左到右：

1. 缩略图：**只有真记过照片才画这一格**（🔒 注释 `:254`"留个灰框等于在说'这里有过一张图'"），48dp、圆角 8dp、`Crop`（`:255-264`）。
2. 名字 + 第二行 `listOfNotNull(node.kind, counts).joinToString(" · ")`（`TreeScreen.kt:272`）。计数拼在 `TreeScreen.kt:267-270`：先 `TreeScreen.kt:268` 的 `${directCount} 件`，**只有子树数与直属数不同**才追加 `:269` 那句括号（"（含子位置 … 件）"，件数是插值）。
3. 有子节点才给「▸」（`:277`）。
4. ⋮ 菜单，`contentDescription = "位置操作"`（`:278-280`）。

🔒 「整棵子树多少件」不能用括号外的第二个数字表达；等值时不追加，否则会出现「3 件（含子位置 3 件）」。

### 4.4 节点菜单 🗣

`TreeScreen.kt:150-181`：标题是节点名（`:155`），条目按次序：「上移」（`:161`）、「下移」（`:166`）、「改名 / 设置类型」（`:170`）、「移动到…」（`:173`）、「删除」（`:176`），右下「关闭」（`:179`）。

🔒 `:151`：边界那两行**不给**方向按钮——"点了什么都没发生的按钮，比少一个按钮更糟"。所以这一屏的菜单条目数会变，判定用 `state.children.indexOfFirst { it.id == node.id }`（`:152`）。

移动到：`LocationPickerDialog(title = "移动到", rootLabel = "移到根层级")`（`:184-191`）。

### 4.5 删除位置框 🗣

`TreeScreen.kt:194-203`：标题「删除「${node.name}」？」（`:197`），正文固定一句「只有空位置可以删除：子位置和物品需要先搬走。」（`:198`），按钮「删除」/「取消」。

🔒 门槛在数据层（`LocationNotEmptyException`，见 `RULES.md` §8.2），这句话只是提前说清规则；不许改成"确定要删除吗"。

### 4.6 位置照片区 🗣

`TreeScreen.kt:212-237`，`:209` 注释是这条规矩的全部理由：**没有照片时只给选图那一步，有照片才给换与删——一张没有图的空位上不该出现"移除照片"**。

- 无图：只有「给这个位置选一张照片」（`:220`）→ 外壳的来源弹窗（`PhotoTarget.LOCATION`）。
- 有图：「换一张」（`:232`）/「移除照片」（`:233`）。

### 4.7 新建 / 编辑位置框 🗣

`TreeScreen.kt:286-331`：标题 `新建位置` / `编辑位置` 按 `initial` 定（`:295`），字段次序「名称」（`:301`）→「类型（可选，也可自己填）」（`:305`，`LOCATION_KINDS` 芯片 + 自由输入），按钮「保存」/「取消」（`:327`/`:329`）。

🔒 类型是**自由标签**不是枚举（`RULES.md` §8.7）；那颗 label 里「也可自己填」四个字不能省。

⚠️ **空名字这一条，两个框用了两种做法**。`TreeScreen.kt:327` 那颗「保存」不带 `enabled`，空名字时走的是 `:326` 里那个 `if (name.isNotBlank())` 的假支——不关框、不写字、也不给任何反馈；而 `LocationPicker.kt:80` 那颗「新建并选中」用 `enabled = newName.isNotBlank()`（`:73`）直接让它按不动，`LocationPicker.kt:72` 注释写明了为什么：空名字按下去没反应，最容易让人以为"这按钮坏了"。重做时取后一种。两处都会 `trim()`：`TreeScreen.kt:326` 对名字 `name.trim()`、对类型 `kind?.trim()?.ifBlank { null }`（整串空格算没填），`LocationPicker.kt:76` 只 trim 名字。

---

## 5. 物品详情页

`DetailScreen.kt:51`。

### 5.1 顶栏 🗣

`DetailScreen.kt:74-88`：标题「物品详情」（`:78`，`maxLines = 1`），`navigationIcon` 是文字按钮「返回」（`:80`），
actions 三颗「编辑」（`:83`）/「搬到」（`:84`）/「删除」（`:85`）。

🔒 **三颗全挂在 `state.summary != null` 上**（`:82`）：读取中、记录没了、读抛了这三种情况下，
顶栏只有「返回」。不许改成"按钮常驻、点了再报错"——那会让用户对着一条已经不存在的记录按「编辑」。

🔒 **名称不挂顶栏**（`:76-77` 注释：顶栏要容下「返回 + 编辑 + 搬到 + 删除」，整屏宽度都归名称才看得全）。名称在正文第一行，`titleLarge`，`ifBlank { "未命名物品" }`（`:114-118`）。批次 AA 改过来的，不许改回去。

### 5.2 三态互斥 🗣🔒

`:93-98`：「读取中…」/「没有这条记录。」/「这条记录读不出来，返回上一页再试一次。」
`:94` 注释：读的时候抛了不能写成"没有这条记录"，那是另一回事。第三态是 `else` 兜的，**它同时兜住"读的时候抛了"和
`missing` 与 `loading` 都为 false 的空窗**，所以这句话必须含糊到不会说谎（VM 里 `missing` 与"抛了"故意分开，见 `ARCHITECTURE.md` §11.1）。

🔒 **删掉之后是这一屏自己退场**：`LaunchedEffect(state.deleted)`（`:64-69`）里先 `vm.consumeDeleted()` 再 `onBack()`，
两句话都在同一个块里。VM 的 `remove` 成功后只置 `deleted = true`、**不 reload**（`DetailViewModel.kt:140`，
库里已经没有这一行，没有可 load 的东西），把"退屏"这一步交给界面。
⚠️ 退屏这个**结果**是被测试钉住的：`DetailScreenTest.kt:254`（`deleteAsksOnceThenLeavesThePage`，`backCount == 1`）断言"删完必须被推回上一页"，
VM 侧另有 `DetailViewModelTest.kt:140` 断言 `deleted` 置位。
**没被钉住的是 `consumeDeleted()` 与 `onBack()` 的先后**——重做时若要动这两行的次序，请先补一条断言。

### 5.3 正文次序 🔒

| 次序 | 内容 | 行 |
|---|---|---|
| 1 | 名称 | :113-118 |
| 2 | 照片横滚 `LazyRow`，200dp 宽 1:1、`Crop`；**没有照片就整块不给** | :119-136 |
| 3 | 位置卡 `LocationCard` | :172-201 |
| 4 | 字段卡 `FieldCard` | :203-230 |
| 5 | 更新于 + 日期（`DetailScreen.kt:140`，"更新于 ${formatDate(...)}"） | :139-144 |

位置卡（定义 `:172`，调用点 `:137`）：左 `DetailScreen.kt:183` 「位置」+ `breadcrumb ?: "待归类（还没放好）"`（`:184-190`，null 时加粗 + error 色），右边 `#tag` 排（`:192-199`）。

字段卡次序（🔒 `:213-228`，**十行**）：**分类 / 季节 / 颜色 / 材质 / 品牌 / 别名 / 数量 / 到期 / 描述 / 备注**。
- 季节 `replace(",", "、")`（`:214`，库里存的是 CSV，屏幕上换成顿号）。
- **颜色在材质前面**（`:215`、`:216`）：这两行是批次 N 加搜索字段时一起补进来的，别只补其一——
  `SearchTextBuilder` 里也是颜色排在材质前（`ARCHITECTURE.md` §4.4）。
- 数量 `x${n}` 且**仅当 n≠1**（`:219`）。
- 到期 `${formatDate}（${expireHint}）` + 有 kind 再 ` · ${kind}`（`:220-225`）。
- `DetailRow` 对 `isNullOrBlank` 的值**整行不给**（`:234`）：不许出现"品牌：（空）"。
  标签列固定 `width = 64.dp`（`:240`），值列吃剩下的宽度并且**不限行数**——长描述要能看完。

### 5.4 搬到 / 删除框 🗣

`:150-159` `LocationPickerDialog(title = "搬到")`；`:160-168` 删除框与照片墙同一句分档 `ask.warning`（见 §3.7）。

### 5.5 编辑的去处 🔒

顶栏那颗「编辑」（`DetailScreen.kt:83`）→ 外壳的 `onEdit`（`WheremyRoot.kt:286-291`），也就是§2.8 那条"换新 VM 走 load"的路。**详情页自己没有可编辑字段**，也不该有。

---

## 6. 录入 / 确认页

整屏 `verticalScroll` + `imePadding`（`ReviewScreen.kt:134` 一带）——字段多、一屏装不下；`imePadding` 放在 `verticalScroll` 之前，键盘弹起时可视区缩到键盘上方，Compose 会把正在编辑的那一格自动滚进来（2026-10-12 真机验证，见 `PLAN.md` F48）。

`ReviewScreen.kt:71`。这是全应用最长的一屏，也是规矩最多的一屏。

### 6.1 顶栏 🗣🔒

`ReviewScreen.kt:90-104`：标题「确认物品信息」（`:91`），左边 ✕（`Icons.Filled.Close`，`contentDescription = "关闭"`，`:93-95`），右边两颗：「稍后处理」（`:98`，`enabled = state.itemId != null`）与「保存这一件」/「保存」（`:99-101`，多件时才带"这一件"；`enabled = state.canSave` 在 `:100`）。

🗣 `:99` 注释：一批不止一件时这颗按钮只管当前这件，名字要说清楚，**不然会以为"保存"=整批都存了**。
🔒 ✕ 和系统返回键都调 `vm::requestExit`（`:93` + `WheremyRoot.kt:252`），是同一个门。
🔒 「稍后处理」在物品还没落库时按不动：那一颗说的必须是"它真的在库里"。

### 6.2 多件芯片排 🔒

`ReviewScreen.kt:115-140`。仅 `slots.size > 1` 才出现（`:116`），横滚一行 `FilterChip`（`:117-128`）。

- 芯片文字 `chipLabel`（`:392-396`）：名字空 → 「第 ${index + 1} 件」；**没走完的前面加「● 」**。
- 🔒 `:125` 注释：圆点说的是"这颗还没走完"，**不是**"库里还没有它"（第 1 件即使已落库，用户没点保存也算没走完）。
- 芯片行下面，当前件 `itemId == null` 时给一行：「这一件还没保存」（`:133`）+「不要这件」（`:138`）。
  `ReviewScreen.kt:135` 注释：不给权重这句话会把右边那颗按钮挤成竖排（批次 AI 的真机 bug）。
- 摘掉一颗之后选中落在哪：`ReviewViewModel.kt:402` 的 `selected = st.selected.coerceAtMost(rest.lastIndex)`——摘掉的是末位时选中退到新的末位，不会停在越界的下标上。
- 那颗「不要这件」（`ReviewScreen.kt:138`）在两种情况下按下去什么都不发生（`ReviewViewModel.kt:398` 直接 `return@update`）：这一件已经落库（那要走详情页的删除，是另一件事），或者芯片只剩一颗。后者界面本来就不摆这颗按钮——`:130` 那一整块在 `slots.size > 1` 里面，所以 VM 那道挡是兜底（判据见 `RULES.md` §6.5）。
- 换一张照片或重跑分析回来之后，选中**一律回到第 1 颗**（`ReviewViewModel.kt:218`，`:216-217` 注释：选中的那颗可能刚被摘掉，用户看得见的是第 1 件，"哪几件没了"由 `:224` 那句话负责说）。已经落库的其余件原样留在芯片排上（`ReviewViewModel.kt:204` 的 `kept = before.slots.drop(1).filter { it.itemId != null }`），没落库的那些以新结果为准——整批怎么重排见 `RULES.md` §4。

### 6.3 照片区 🔒🗣

`ReviewScreen.kt:143-163`：

- 有 `thumbPath` 才画那一格（`:143`），固定高 180dp、圆角 12dp、`ContentScale.Fit`（`:154`）。
- 🔒 `:151-153` 注释是全部理由：这一屏要的是"拍全了没有"，不是好看；`Crop` 在这个约 2:1 的框里会把 4:3 裁掉约 1/3 高、竖拍的裁掉大半，物品不在正中时根本看不出缺了什么。框高固定是为了图还没解码完时这一格不塌成 0、表单不跳一下（批次 AS）。
- 选图那颗 `OutlinedButton`（`:160-163`）：**没落库也必须给**。`:158-159` 注释记录旧行为为什么不行——以前只有物品还没落库时才给入口，而那一刻几乎不存在，于是换照片只能"关闭 → 在收件箱里删掉空壳 → 重录一遍"，填过的字段全丢。
- 🗣 文案 `state.hasStoredItem ? 「换一张」 : 「选择照片」`（`:162`）。`:161` 注释：问的是"这一批选过图没有"，不是"这一件落库没有"——切到没保存的那件时图就在上面。

### 6.4 AI 状态行 `AiStatusLine` 🗣🔒

`ReviewScreen.kt:402-436`。

- 分析中：「AI 正在看照片，可以先自己填」（`:414`）+ `LinearProgressIndicator`（`:415`）。**分析中不挡表单**，用户可当场自己填（`RULES.md` §3、§5）。
- 分析完，位置话三句（`:422-426`），互斥：
  1. 「AI 猜的位置：$guess（已填进「放在哪里」）」
  2. 「AI 猜的是「$guess」，这件已经选好位置了，没有改动」
  3. 「AI 猜的是「$guess」，你的位置里没有对得上的，需要的话点「选择」新建一个」
  🔒 `:420-421` 注释：光说"AI 猜的位置：橱柜"会被读成"它给了建议而界面没用"，**用户就是这么报的 bug**（批次 Z）。三件不同的事必须说成三句。
  🔒 `:429` 注释 + `Modifier.weight(1f)`（`:430`）：不给权重这句会在 Row 里先按整行宽度被量走，「重新分析」剩 0 宽竖成一列——真机上的形状 bug（批次 AI）。
- 「重新分析」（`:433`）只在 `canRetry` 时给；`:432` 注释：照片没落库时没有可重跑的对象，按下去不会有任何反应。
- 失败文案由 `failureText(failure)` 给（`:418`），11 类见 `RULES.md` §13。

### 6.5 表单字段次序 🔒

`ReviewScreen.kt:178-311`，从上到下（行号为该字段的 label 所在行）：

| 次序 | 字段 | 行 | 组件 |
|---|---|---|---|
| 1 | 名称 *（🗣 唯一带星号的必填） | :181 | `OutlinedTextField` |
| 2 | 分类 | :186 | 芯片 `CATEGORIES` |
| 3 | 季节 | :201 | 芯片 `SEASONS`（可多值，逗号存） |
| 4 | 颜色 | :217 | `OutlinedTextField` |
| 5 | 材质 | :224 | `OutlinedTextField` |
| 6 | 品牌 | :233 | `OutlinedTextField` |
| 7 | 别名（顿号分隔，方便日后搜到）🗣 | :246 | `OutlinedTextField` |
| 8 | 标签（点一下删除）🗣 | :250 | 芯片 + 加标签行 |
| 9 | 描述 | :262 | `OutlinedTextField` |
| 10 | 备注（只有你自己看得到的）🗣 | :270 | `OutlinedTextField` |
| 11 | 数量 | :282 | ± 步进 |
| 12 | 放在哪里 →「选择」 | :291 | `LocationPickerDialog(title = "选择位置")`（`:369`） |
| 13 | 到期提醒 → `expireLabel` | :304 | `ExpireDialog` |

🔒 次序不许重排：前三项是 AI 一定会填的，颜色/材质/品牌是后加的搜索面（批次 N），备注永远在最底下（它是唯一不进搜索的字段）。

**文本输入的三条事实**（照着抄键盘行为要用得上）：

- 键盘动作只有一个地方挂了：`ReviewScreen.kt:462` 的 `KeyboardActions(onDone = { add() })`（配 `ReviewScreen.kt:461` 的 `imeAction = ImeAction.Done`），也就是只有「加标签」那颗 `ReviewScreen.kt:459` 支持敲回车/完成等于"加上"。其余字段全不带 `keyboardOptions`（`ReviewScreen.kt:238-248` 的别名、`:259-273` 的描述与备注也一样），所以这一屏上没有"跳到下一格"的行为。
- 单行的是这五颗：`ReviewScreen.kt:182` 名称、`:218` 颜色、`:225` 材质、`:234` 品牌、`:460` 加标签；`ReviewScreen.kt:263` 描述与 `:271` 备注各带 `minLines = 2`。别名那颗**既不写 `singleLine` 也不写 `keyboardOptions`**。
- 别名的输入与显示不对称：`ReviewScreen.kt:243` 按三个分隔符拆（`split('、', '，', ',')`，每段 `trim()`、空段丢掉），`ReviewScreen.kt:239` 只用「、」拼回去给人看；落库是英文逗号一列（`ItemRepository.kt:340`），读回来只认英文逗号（`ItemRepository.kt:337`）。⚠️ 换行不在 `:243` 那个集合里，而那颗又允许多行，所以粘一段多行文本进去会当成**一个**别名存下来。
- `ReviewViewModel.kt:303-308` 的 `toggleTag` 是"有就摘、没有就加"：`ReviewScreen.kt:250` 的 label 说的「标签（点一下删除）」正是这件事的另一半，而在加标签那颗重敲一个已存在的标签 = 把它删掉。标签文字走的是另一条存储路（`ItemRepository.kt:303-305` 一标签一行，`distinct()`），**不经过** `csv()`，所以标签里带逗号不会被拆成两个。

`expireLabel`（`ReviewScreen.kt:397-400`）：未设置 → `ReviewScreen.kt:398` 「未设置」；否则 `:399` 拼 `${kind ?: "到期"} · ${formatDate}（${expireHint}）`，即 `到期 · 2026-11-03（还剩 28 天）` 这个形状。括号里的三种说法来自 `UiFormat.kt:7-11`（天数为插值），全应用共用。

加标签那颗（`AddTagField`，`ReviewScreen.kt:447-466`）：label `ReviewScreen.kt:459` 「加标签」+ trailing `ReviewScreen.kt:464` 「＋」；🔒 `ReviewScreen.kt:449` 注释：敲回车必须算"加上"——字只活在局部 state 里，不点那颗 ＋ 就去保存的话标签静默消失。`:451` 只挡 `isNotBlank()`，`:452` 存进去的是 `text.trim()`，`:453` 加完清空。

`ExpireDialog`（`ReviewScreen.kt:470-501`）= `DatePickerDialog` + `EXPIRE_KINDS` 芯片行（`ReviewScreen.kt:494`）+「确定」（`:488`）/「清除」（`:490`）。
🔒 `ReviewScreen.kt:484` 的 `enabled = pickerState.selectedDateMillis != null`（`:483` 注释：没选日期时点了没有任何反馈，直接让它按不动）。
🔒 UTC 零点毫秒必须换回本地零点（`Dates.kt:35` 注释；换算在 `ReviewScreen.kt:486`）。

### 6.6 退出确认框：两支 🔒🗣

`ReviewScreen.kt:313-364`。两支的**逐字全文与判定**在 `RULES.md` §6.2/§6.3，本文只说形状：

| 支 | 触发 | 标题 | 给哪两颗按钮 |
|---|---|---|---|
| 一 | `notStored > 0`（这批里有只活在内存里的件） | `ReviewScreen.kt:319` 「还有物品没保存，确定离开？」 | `ReviewScreen.kt:331` 「继续编辑」/ `:334` 「不保存，直接离开」 |
| 二 | 有改动但都已落库 | `ReviewScreen.kt:341`：有落库未保存的 → 「还没保存，确定离开？」；否则 → 「改动还没保存，确定离开？」 | `ReviewScreen.kt:357` 「保存改动并留下」/ `:360` 「不保存，直接离开」 |

🔒 支一**故意不给**`ReviewScreen.kt:357` 「保存改动并留下」：那颗只写当前一件，在这一支里兑现不了，摆出来就是骗人（`ReviewScreen.kt:315-316` 注释）。

### 6.7 关页只有一格：`state.finished` 🔒

`ReviewScreen.kt:81-86` 是这一屏唯一的出口机制——`LaunchedEffect(state.finished)` 里**先** `vm.consumeFinished()` **再** `onDone()`：

```kotlin
LaunchedEffect(state.finished) {
    if (state.finished) {
        vm.consumeFinished()
        onDone()
    }
}
```

`finished` 住在 `ReviewUiState`（`ReviewViewModel.kt:56`，默认 `false`），不住在 `ReviewScreen` 的局部 state 里；写回 `false` 的那颗是 `consumeFinished()`（`:458`），置 `true` 的 `finish()` 是 `private`（`:387`）。全文件只有三处会把它拧成 `true`：

| 谁 | 行 | 条件 |
|---|---|---|
| `save()` 成功 | `ReviewViewModel.kt:340` | 仅 `cur.slots.size == 1` |
| `keepForLater()` 成功 | `:380` | 仅 `cur.slots.size == 1` |
| `closeWithoutAsking()` | `:448` / `:454` | 没空壳立刻置；有空壳则排在 `items.delete()` 之后 |

🔒 一批两件以上时前两条**不关页**（`:323` 与 `:379` 注释都是同一句话：关页等于替用户把芯片上其余没保存的件扔了），改说一句话：`ReviewViewModel.kt:361` 「已保存这一件」 / `:381` 「这一件先放回待归类了」。

🔒 `onDone` 是外壳注入的 `leaveReview`（`WheremyRoot.kt:259`，定义与它清 `pendingCapture` 的次序见 §2.7）。这一屏自己不碰 `tab`、不碰导航栈，也不自己 `reviewOpen = false`——它只负责把"办完了"写进 `finished`，怎么落地由外壳决定。

✅ 这一格是被界面测试钉住的，不是只能真机看：`ReviewScreenTest.kt:263`（注释"没改过任何东西就别拦人"，断言 `doneCount == 1`）、`:276`（注释"问过之前不能关页"，断言 `0`）、`:277`（弹窗 `ReviewScreen.kt:341` 「还没保存，确定离开？」 在场）、`:280`（点了那颗「保存改动并留下」= `ReviewScreen.kt:357` 之后 `1`）。

---

## 7. 设置页

`SettingsScreen.kt:69`，签名带 `showDebugTools: Boolean = BuildConfig.DEBUG`（🔒 `:63-66` 注释：正式包不许出现"立刻跑一次后台检查"的入口）。

整屏 `verticalScroll` + `imePadding`（`:129-132`）——多了 AI 这一节之后一屏装不下，键盘弹起来也得能滚到正在编辑的那一格。**2026-10-12 补齐**：`imePadding` 放在 `verticalScroll` 之前；此前漏了它，真机上键盘会把靠下的输入框盖住（见 `PLAN.md` F48）。三节按次序：

**这一屏自己的四格状态**（`:74-77`），只有一格必须存：

| 格 | 存不存 | 为什么 |
|---|---|---|
| `pendingMode` | `rememberSaveable`（`:74`） | `:73` 注释原话「选择器打开期间 Activity 可能被重建，模式要用可保存的状态记住」。**存的是 `ImportMode.…name` 字符串**，回调里 `ImportMode.valueOf(mode)`（`:93`）。它是这一屏唯一的"跨进程生死"状态：系统文件选择器弹出来时本 App 的 Activity 是可能被回收的，选完回来如果模式丢了，`uri != null && mode != null` 那一支就不成立，**用户选好了包但导入压根没发起** |
| `confirmOverwrite` | `remember`（`:75`） | 覆盖确认框开没开。重建后不该继续显示一个"你确定要清空本机吗" |
| `showKey` | `remember`（`:76`） | key 明文显示着没。重建后回到遮蔽 |
| `notifyProblem` | `remember`（`:77`） | 见 §7.1 第 6 项，靠 ON_RESUME 重读，不需要存 |

两个 launcher（`:88-97`）：导入 `OpenDocument` 启动时传的是 `arrayOf("*/*")`（`:113`/`:282`，**不是 `arrayOf("application/zip")`**，
即不按 MIME 收窄，任何文件都能被选中，是否是个包交给 `readBundle` 去判）；导出 `CreateDocument("application/zip")`（`:96`）。
`pendingMode` 在回调第一行就被清空（`:92`），所以**一次选择只兑现一次导入**，重来必须重新点按钮。

### 7.1 「到期提醒」（`SettingsScreen.kt:136`）

1. 当前值一行：`SettingsScreen.kt:144` 「当天到期提醒」，否则 `:145` 「提前 ${state.leadDays} 天提醒」（🗣 `:142` 注释：0 天说"提前 0 天"是最容易让人误会的写法，预设按钮那里它就叫「当天」）。
2. 「−」/「＋」（`SettingsScreen.kt:148-155`，各自 `enabled` 卡住上下界：`:150` 是 `> LEAD_DAYS_MIN`、`:154` 是 `< LEAD_DAYS_MAX`）。
3. 八颗预设按钮，每行四颗（`SettingsScreen.kt:157-170`；映射表 `:57-60`：当天 / 1 天 / 3 天 / 7 天 / 30 天 / 90 天 / 半年 / 一年）。🔒 `SettingsScreen.kt:157` 注释：上限放到 365 之后 ±1 按不到那儿去，所以「一年」这类得是现成的按钮。
4. `SettingsScreen.kt:171` 「到期提醒每天检查一次，几件物品也只发一条聚合通知。」
5. debug：`SettingsScreen.kt:173` 「调试：立刻检查一次到期提醒」。
6. 通知发不出去时才有的那一行 + `SettingsScreen.kt:186` 「去系统设置打开」（`:175-188`）。🔒 `:182-183` 注释：就地申请运行时权限在 HyperOS 上会被静默驳回，按下去连弹框都没有（2026-10-03 真机）；所以两种理由共用同一颗按钮跳系统页。
   **那一行"回到前台就自己变"靠的是设置页自己的观察者**：`notifyProblem` 是一格 `remember`（`:77`），
   `SettingsScreen.kt:80-86` 的 `DisposableEffect(lifecycleOwner)` 在 `ON_RESUME` 时重读 `ReminderWorker.notifyProblem(context)`。
   外壳里那个同名形状的观察者（`WheremyRoot.kt:95-102`）**重读的是相机门禁 `gate`，与通知无关**——两处的注释都写着同一句理由
   （`SettingsScreen.kt:78` 「系统设置里手动开关通知不会走应用内的授权回调」），但它们是**两个独立的观察者，不是一个**。
   重做时只留一个，就会有一处回到前台不刷新。

### 7.2 「AI 分析照片」（`SettingsScreen.kt:190`）

1. `Switch` + `SettingsScreen.kt:200` 「选完照片让 AI 先把名称、类别、季节填一遍，你再改」（那一行带 `Modifier.weight(1f)`）。
2. 三态说明，加粗（🗣 `SettingsScreen.kt:204-206`）：
   - `:204` 「开关关着，选完照片直接进手填。」
   - `:205` 「还缺 API key：填好保存之前，选照片都会走手填。」
   - `:206` 「已就绪：走 ${state.savedModel}。」
3. 三个字段，次序 `SettingsScreen.kt:213` 「接口地址 base_url」→ `:224` 「模型名 model」→ `:235` 「API key」；key 那颗 `:243` 用 `Text(if (showKey) "隐藏" else "显示")` 切换。
4. `SettingsScreen.kt:252` 「保存 AI 配置」+ `:254` 「填完按保存才生效。」。
5. 隐私句 🗣🔒（`SettingsScreen.kt:256-258`）：「key 只存在这台手机的本地设置里：不进备份包，也不跟着任何云端同步走。换手机请先在旧机上把它清空，再重装。」
6. debug：`SettingsScreen.kt:260-267` 那一整块只在 `showDebugTools` 时给，正文分两段字符串拼（`:264` 「AI 留痕：${state.traceCount} 条（发出去的提示词与模型回应原文，」+ `:265` 「只留最近 $ANALYSIS_TRACE_MAX 条）」）。`SettingsScreen.kt:261-262` 注释说明它是干什么用的：按过 `ReviewScreen.kt:433` 「重新分析」再回来看这一行，条数在涨就说明留痕真落了盘，不用连 adb。

🔒 key 永不出现在备份包、留痕与日志里（`RULES.md` §13.3、§12）。这一节不许因为"方便"而加任何云端同步。

### 7.3 「备份」（`SettingsScreen.kt:269`）

1. 三颗按钮一行：`SettingsScreen.kt:278` 「导出到文件」/ `:285` 「合并导入」/ `:289` 「覆盖导入」，`enabled = !state.busy`。「覆盖导入」不直接执行，`:287` 的 `onClick = { confirmOverwrite = true }` 先把 `confirmOverwrite` 立起来（`:75`）。
2. 忙时：`SettingsScreen.kt:291-299` 进度圈 + `:297` 「正在读写备份包…」。🔒 忙的时候忽略新手势（`RULES.md` §16）。
3. 说明四句（`SettingsScreen.kt:300-309`，第二句加粗 `:303`）：`:300` 讲 zip 内容 / `:302` 「备份文件里不含 API key，也不含任何设置。」/ `:305` 讲合并与覆盖的语义 / `:307` 「应用已被系统设置为不做云备份（allowBackup=false），」（这句与 `:308` 是两段字符串拼起来的）。
4. 覆盖确认框（`SettingsScreen.kt:102-119`）：标题 `:105` 「覆盖导入会清空本机数据」、正文 `:107` 「先把现在的物品、位置和照片全部删掉，再按备份包重建。中途断开不会留下半个库，但清空之后就只能靠这个包了。」、按钮 `:114` 「确定覆盖导入」/ `:117` 「取消」。那颗「确定覆盖导入」在关框之后才立 `pendingMode = ImportMode.OVERWRITE.name`，然后 `importer.launch(arrayOf("*/*"))`（`:110-113`）。

导出建议文件名 `wheremy-backup-${LocalDate.now()}.zip`（`:276`），导入 `arrayOf("*/*")`（`:282`）。结果那句中文按 `operate` 五种收场生成（`RULES.md` §16），不在本文重复。

---

## 8. 取景屏

`CameraScreen.kt:36-100`。签名一共八个参数（`:36-45`），其中 `gate` 决定这一屏给什么，`preview` 是外面填进来的槽——**这一屏不认识 `PreviewView`**（`:30-33` 注释）。

| 区 | 内容 | 行 |
|---|---|---|
| 中栏（weight 1f） | `Unavailable` → 「这台机器没有可用的相机」；`NeedsPermission` → 「取景要用相机，先给它授权」+「打开相机权限」；`Ready` → `preview()` | :62-71 |
| 失败行 | 仅 `missed` 时，error 色「没拍到，再按一次，或者改用相册」 | :72-78 |
| 底行 | 「取消」/（仅 `Ready`）「拍这张」/「改用相册选」 | :79-98 |

🔒 四条：
1. 自己得是**不透明**的（`:55-56` 注释：它是盖在别的屏上面的叠层，而 `NeedsPermission`/`Unavailable` 那两态没有预览面垫着）。
2. 快门那颗只在 `Ready` 时给（`:85`），且 `enabled = !busy`（`:93`）。
3. 「改用相册选」常驻——权限被永久驳回时这是唯一出路。
4. 「打开相机权限」那颗也**一直都在**（`:50` 注释：会不会被驳回不写死）。

`LaunchedEffect(gate)` 进门先问一次权限（`:51-53`）；`missed` 是本地状态，不写库、不进 VM。

---

## 9. 共用件

| 件 | 用途 | 行 |
|---|---|---|
| `LocationPickerDialog` | 挑选位置的唯一对话框，四处复用（墙/详情/录入/树） | `LocationPicker.kt:29-86` |
| `flattenLocations` | 前序展开成缩进列表；选择框与树页共用同一份 | `LocationFlatten.kt:8-21`（缩进 `depth * 16.dp`，`LocationPicker.kt:50`） |
| `ItemThumbRow` | 位置页里"这里的物品"那一行 | `ItemRow.kt:27-74` |
| `SnackbarMessage` | 把 VM 里"只显示一次"的话交给 snackbar | `SnackbarMessage.kt:18-37` |
| `expireHint` / `formatDate` | 到期那一行的三种说法的唯一出处（天数插值，原文见 `UiFormat.kt:8`、`:9`、`:10`） | `UiFormat.kt:7-11`、`Dates.kt:12` |

🔒 `LocationPickerDialog` 的 `onPick(null)` 就是"不放在任何位置"，文案由 `rootLabel` 决定：物品 = `LocationPicker.kt:35` 的默认值「暂不归类，先进「待归类」」，位置节点 = `TreeScreen.kt:190` 「移到根层级」。两种语义必须分开，不能都叫"根层级"。
🔒 底部那颗 `LocationPicker.kt:80` 「新建并选中」在名字空的时候**按不动**（`:73` 的 `enabled = newName.isNotBlank()`，`:72` 注释给了理由），而 `onCreate` 拿到的是 `newName.trim()`（`:76`）——`TreeScreen.kt:327` 那颗「保存」用的是另一种做法，见 §4.7。
🔒 `onCreate` 非空时底部才给"在选中项下新建"的输入框（`LocationPicker.kt:26`、`:63-79`）：**确认页要顺手建位置，照片墙和详情页只要挑选**。
🔒 `ItemThumbRow` 的第二行由三块拼（`ItemRow.kt:60-64`）：`"x${summary.item.quantity}"` 只在 >1 时才给（`:61`）、`"到期 ${formatDate(it)}"`（`:62`）、`"在 $it"`（`:63`），中间用 `ItemRow.kt:67` 的 `joinToString(" · ")` 连；`extra` 为空整段不给（`:65`）。
🔒 `SnackbarMessage` 那两个 `LaunchedEffect` **缺一不可**（`SnackbarMessage.kt:14-17` 注释：合成一个的话，消费会让 key 变化、正在挂起的 `showSnackbar` 立刻被取消，用户什么条幅都看不见）。

**照片格没有共用件**：六处各自内联一个 `AsyncImage`，全应用没有任何一处传 `placeholder` 或 `error`（参数只有 `model` / `contentDescription` / `modifier` / `contentScale` 四个）：

| 处 | 尺寸 | 圆角 | `contentScale` | `contentDescription` |
|---|---|---|---|---|
| `ItemRow.kt:42` | `size(48.dp)` | 8dp | `Crop`（`:48`） | 物品名（`:44`） |
| `GalleryScreen.kt:280` | `fillMaxWidth` + `aspectRatio(1f)` | 10dp | `Crop`（`:287`） | 物品名（`:282`） |
| `DetailScreen.kt:125` | `width(200.dp)` + `aspectRatio(1f)` | 12dp | `Crop`（`:132`） | `null`（`:127`） |
| `ReviewScreen.kt:144` | `fillMaxWidth` + `height(180.dp)` | 12dp | **`Fit`**（`:154`，理由见 §6.3） | 物品照片 |
| `TreeScreen.kt:222` | `fillMaxWidth` + `height(180.dp)` | 12dp | `Crop`（`:229`） | 位置照片 |
| `TreeScreen.kt:256` | `size(48.dp)` | 8dp | `Crop`（`:262`） | 位置缩略图 |

🔒 **"没有照片"和"照片读不出来"是两件事，界面只处理了前者**：`thumbPath == null` 那一支各自有东西垫着——`ItemRow.kt:50-55` 给一块同尺寸 `surfaceVariant` 方格，`GalleryScreen.kt:290-297` 在同一块底色上写字（`:297` 注释：没照片时拿名称占位，照片墙不会是一片空白格子；名字取前 8 字、`ifBlank { "未命名" }`、`maxLines = 2`），`TreeScreen.kt:256` 干脆不画这一格（`:254` 注释：留个灰框等于在说"这里有过一张图"），`TreeScreen.kt:220` 换成一颗 `TextButton`「给这个位置选一张照片」。
⚠️ 但路径有值而文件已经不在（或解码失败）时，六处都落进 `AsyncImage` 自己内部：那一格尺寸照旧占着，里面**不画任何东西**——没有转圈、没有"照片丢了"这句话。说到"读不出照片"的三句话都不在这一格上，且各管一段：`WheremyRoot.kt:127` 「读不到这张照片，换一张试试」管的是刚选回来/刚拍回来的字节是空的（`deliverPhoto` 的两个来路：相册 :161、取景 :205），而录入页开着时 `WheremyRoot.kt:113` 的 `say` 干脆不说这一句；`PhotoStore.kt:134` 「照片解码失败」从 `decode()` 的两处抛出（:54 尺寸读不出、:60 解不开），经 `ReviewViewModel.kt:182` 的 `guard` 原样贴成 error；`ReviewViewModel.kt:169` 「找不到这张照片了，重新选一张试试」只在"重新分析"那一条路上（`retryAnalysis` 里 `capture.reanalyze` 返回 `null` 那一支，:166-167 注释：转圈必须自己停，并且要说下一步——不然用户只能盯着一个永远的加载圈）。后两句走 `state.error`，画在 AI 状态行下面、名称字段上面（`ReviewScreen.kt:170-172`，`error` 色），第一句走 snackbar。照片墙、位置页、详情页三处**没有**这一层。

---

## 10. 屏幕上的常量 🗣🔒

| 常量 | 值 | 出处 |
|---|---|---|
| `CATEGORIES` | 衣物 数码 电器 药品 食品 工具 文档 饰品 厨具 玩具 运动 其他 | `Models.kt:55` |
| `SEASONS` | 春 夏 秋 冬 | `Models.kt:59` |
| `EXPIRE_KINDS` | 保质期 保修 证件有效期 其他 | `Models.kt:61` |
| `LOCATION_KINDS` | 房间 衣柜 抽屉 收纳盒 箱子 挂钩 货架 其他（自由标签的建议值，输入框允许填别的词） | `LocationRepository.kt:17` |
| `LEAD_PRESETS` | 当天 / 1 天 / 3 天 / 7 天 / 30 天 / 90 天 / 半年 / 一年 | `SettingsScreen.kt:57-60` |
| `ANALYSIS_TRACE_MAX` | 留痕上限，界面上说成"只留最近 10 条" | `SettingsScreen.kt:265` |

🔒 这些是**用户看得见的名词表**，不是内部枚举：改了 `CATEGORIES` 就等于改了照片墙第一行和录入页第二行的所有芯片，同时要让 `search_text` 里的历史值仍能被搜到（`RULES.md` §10.3）。

## 11. 视觉基线 🎨（这一节整节都可以推翻）

- 主题：`WheremyTheme`（`Theme.kt:26-32`），**固定配色**，`isSystemInDarkTheme()` 二选一，不用动态取色。`:27` 注释给了理由：单用户工具类 APP，动态取色会让缩略图色彩判断失真。
- 亮色 primary `Teal40 = 0xFF00695C`、secondary `Amber40 = 0xFF9A6A00`；暗色 `Teal80 = 0xFF7FD6C5`、`Amber80 = 0xFFFFD37A`（`Theme.kt:10-23`）。
- 字号一律走 `MaterialTheme.typography`，没有自定义 `Typography`；没有动效、没有自绘组件。
- 尺寸参考值：网格最小 156dp、缩略图 48dp、录入页照片框高 180dp、详情页照片 200dp 宽、圆角 8/10/12dp、页边距 12/16dp。
- `res/` 只有 5 个文件：`drawable/ic_launcher_foreground.xml`、`mipmap-anydpi-v26/ic_launcher.xml`、`values/ic_launcher_background.xml`、`values/strings.xml`（只有 `app_name`）、`values/themes.xml`（`Theme.Wheremy` 继承 `android:Theme.Material.NoActionBar`）。Manifest 用 `@mipmap/ic_launcher`（`AndroidManifest.xml:11-14`）。

🔒 但这一节里有三件事**不是审美**，改了就变成假话，列在 §12 的"不许变"里：error 色的语义、照片 Fit / Crop 的分工、"没有真图就不画那一格"。

---

## 12. 可自由发挥 vs 不许变

| 主题 | 可自由发挥 🎨 | 不许变 🔒 |
|---|---|---|
| 布局 | 每屏内部排版、间距、圆角、分栏方式 | 五屏 + 底栏四格 + 单颗 FAB 的信息架构（§1.1）；FAB 是全应用唯一新增入口 |
| 视觉 | 配色、字号、图标、暗色方案、动效 | error 色承担"这件事没办完"（位置为 null、`days <= 30`、AI 位置建议之外的失败）；没有真图不画占位格 |
| 组件 | AlertDialog / BottomSheet / 新页面框架随便换 | 各对话框的**标题与按钮文字**；`LocationPickerDialog` 是唯一位置挑选件，四处复用 |
| 交互 | 手势、滚动细节 | 返回键优先级表（§2.3）；✕ 与返回键必须同一个门；筛选区不许吃掉网格；长文本不许把按钮挤成竖排 |
| 照片 | 缩略图尺寸与圆角 | 墙上 `Crop` / 录入页 `Fit` 的分工（§3.5、§6.3）；框高固定 |
| 文案 | 未被 🗣 标注的内部提示 | 所有 🗣 逐字串；三句互斥的位置话（§6.4）；两支退出确认框（§6.6）；空态三句（§3.4）；三态互斥（§5.2） |
| 技术栈 | 可以不用 Compose | `rememberSaveable` 那九格的恢复语义（§2.1 的八格 + §7 里 `SettingsScreen.kt:74` 的 `pendingMode` 一格）；`deliverPhoto` 单一收口（§2.4）；`entry` 两跳落地（§2.9） |

## 13. 界面文案总则

写文案的 NFR 与五条真实裁决在 `RULES.md` §16–§17，不在此重复。本节只留三条**排版层**的规矩，它们都来自真机形状 bug：

1. 一句话与一颗按钮并排时，那句话必须 `Modifier.weight(1f)`，否则按钮被挤成竖排（`ReviewScreen.kt:135`、`:429-430`）。
2. 一个区块只占一行，可滚或可折，不能不限高（`GalleryScreen.kt:206`）。
3. 解码中的图片要有一个固定高度的格子，不能让表单跳一下（`ReviewScreen.kt:151-153`）。

## 14. 已知偏差 ⚠️

| 编号 | 现象 | 位置 |
|---|---|---|
| W1 | ~~支二退出框对已经确认（`CONFIRMED`）的物品仍说"……状态还是草稿，会出现在「待归类」"~~ **2026-10-06 复核：造不出那个状态，已闭合** | 那两句在 `ReviewScreen.kt:346-348`、`:351`（整句是「照片和 AI 填的信息已经在库里了，状态还是草稿，会出现在「待归类」。」）；成立条件与钉住它的测试见 `RULES.md` §15 W1 |
| W7 | `state.error` 与 `state.notice` 在录入页可以同屏并存（`ReviewScreen.kt:170-176`），没有优先级 | `RULES.md` §15 W7 |
| W9 | 别名那一格没有 `singleLine`、也没有 `maxLines`，而它的值每按一键就被切分一次，换行不在切分字符里 | `ReviewScreen.kt:238-248`；为什么不动它见 `RULES.md` §15 W9，§6.5 有这一格的三条输入事实 |
| W10 | 同一个"名字没填"在两个弹窗里两种反馈：一处按钮变灰，一处点了什么都没发生 | `LocationPicker.kt:73` 与 `TreeScreen.kt:326`；见 §4.7，`RULES.md` §15 W10 |
| W11 | 六处照片格在文件丢失或解不开时是**空的**，那一格自己一句话都不说——说到"读不出照片"的三句分别在选图回传、解码抛出和重新分析三条路上，都不在显示它的那一格上 | `ItemRow.kt:42`、`GalleryScreen.kt:280`、`DetailScreen.kt:125`、`ReviewScreen.kt:144`、`TreeScreen.kt:222`、:256；三句各管哪一段见 §9，为什么不动它见 `RULES.md` §15 W11 |

这四条（W7、W9、W10、W11）都是**记录不改**的状态：本文照实描述，不代表它们是对的。W1 已经不是一种偏差，留在表里只是因为那两句的开关值得说清楚。

## 15. 只能真机看的界面事实

Robolectric 的 `w411dp-h891dp` 覆盖不到的，重建时必须在真机验收（完整手册见 `REBUILD.md`）：

1. CameraX 取景与快门口感（`CameraScreen` 的三态在真机上由 `CameraAccess.gate` 决定，HyperOS 会静默驳回权限申请）。
2. 系统选图器（`PickVisualMedia`）的第一格行为与各品牌相册差异。
3. 通知是否真弹出、点通知那一跳是否落到筛选后的照片墙。
4. EXIF 方向：照片烤平在存盘时做（`RULES.md` §9.4），界面上只看结果。
5. 桌面图标在启动器里的实际渲染（`res/` 那三份 xml）。
6. 键盘弹起后设置页能否滚到正在编辑的那一格（`SettingsScreen.kt:131-132`）。
7. 长名字与多标签在不同字体缩放下的排版（§13 那三条规矩的实际效果）。
