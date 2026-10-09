# Wheremy 行为规则（RULES）

本文回答一件事：**每条规则到底怎么算**。谓词、阈值、优先级、状态机、顺序要求、逐字文案，
全部对着当前源码取证，并给出 `文件:行`。`PRD.md` 说"要什么"，`ARCHITECTURE.md` 说"在哪"，
本文说"凭什么这样判"。改任何一条规则，先改这里，再改代码，最后改测试。

引用形如 `ItemRepository.kt:282` = `app/src/main/java/com/wheremy/app/data/repo/ItemRepository.kt` 第 282 行。
所有 `文件:行` 都在代码基线 `cf73f5b` 的工作树上验过（其后的提交只动 `docs/`，`app/` 逐字节未变）。

## 0. 怎么读

| 记号 | 含义 |
|---|---|
| 🔒 | **不许改**。改了就有一条界面文案或一条测试变成假话，且通常会先骗到用户 |
| 📐 | 阈值/数字。改之前先看 §14 的来源表，多数数字有第二处在用 |
| 🗣 | 逐字文案。改字必须同步改断言它的界面测试 |
| ⚠️ | 已知偏差：代码这么写了，但它不完全成立。列在 §15，动手前先看 |

规则的来源只有三种：**用户拍板**、**事故**（真机反馈或自审发现的假话）、**实现约束**（平台/库的行为）。
本文在每条规则后标明是哪一种；来源是"事故"的会写清是哪一批。

---

## 1. 五个谓词：什么叫"待归类"、"快到期"、"没保存"、"空壳"、"能保存" 🔒

这五个判断各只有一处定义。界面、SQL、通知三侧必须用同一个，否则同一个物品在两个地方说法不一。

### 1.1 待归类（收件箱）🔒

```
locationId IS NULL OR status = 'DRAFT'
```

- SQL 版：`Daos.kt:34-35`（`inbox()`，`ORDER BY updatedAt DESC`）
- 内存版：`ItemRepository.kt:333`（`matches` 里的 `onlyInbox` 那一行）
- 界面版：`GalleryScreen.kt:324` —— `summary.locationPath ?: "待归类"`

⚠️ **界面走的是内存版，不是 SQL 版**：`ItemRepository.inbox()`（`:218`）在 `app/src/main` 里**没有任何调用点**，
只有测试在用；待归类页实际是 `GalleryViewModel.kt:67-72` —— `onlyInbox = inboxMode || filters.onlyInbox`
喂给 `rankedSearch`。重做时不要把这一页接回 `inbox()`：那样会绕掉搜索框与其余筛选条件的叠加（§10.1），
也绕掉下面那条"待归类不算用户自己加的筛选"。

`ItemRepository.kt:333` 那一行谓词外面，还有一句注释（`GalleryScreen.kt:189`）钉住一件事：`onlyInbox` 在「待归类」页是这一页的定义，
不算用户自己加的筛选（`GalleryScreen.kt:190-193` 的收起态点名因此跳过它），否则每次进这一页
都被提示"你有 1 个筛选"。

**谓词的已知不对称**（📌 钉住现状，非待修）：`locationId` 指向一个**已不存在的节点**时，
`breadcrumb` 回落到空串 → `summaries` 把它算成 `null`（`ItemRepository.kt:233` 的 `ifBlank { null }`），
于是 `GalleryScreen.kt:324` 让卡片上写「待归类」；而 `ItemRepository.kt:333` 看的是 `locationId == null`，那一行**非空**，
所以它出现在卡片上却进不了待归类页。库里正常走不出这个形状（外键 + 删除位置有门槛 §8.2），
只有手工改库或半截恢复备份才会遇到——重做时不必为它加代码，但**别反过来声称"写着待归类就一定在待归类页"**。

**为什么是两个条件的或**（用户拍板，批次 N）：

- 只有 `locationId IS NULL`：AI 猜中了一个位置并自动填上，但用户还没确认，这件就"看起来已经放好了"，
  从待归类里消失——用户再也不会在里面看见它。
- 只有 `status = 'DRAFT'`：用户明确选了"先不放位置"的可确认物品会不见。
- 或起来之后，"还没放好位置"和"还没确认"都算没办完，语义是"这些事儿没到底"。

### 1.2 快到期 / 已过期 🔒

```
expiresAt != null && expiresAt <= expireWindowEnd(leadDays)
```

- 上界唯一来源：`util/Dates.kt:25-26`

```kotlin
fun expireWindowEnd(leadDays: Int, now: LocalDate = LocalDate.now()): Long =
    localStartOfDay(now.plusDays(leadDays.coerceAtLeast(0).toLong() + 1)) - 1
```

三个细节都是必须的，少一个就错一天：

1. `+1` 再 `-1`：提前 N 天 = 覆盖到第 N 天那一整天的**最后一毫秒**。按天取整（直接
   `now.plusDays(N)` 的零点）会把"正好提前 N 天到期"那件漏掉。
2. `leadDays.coerceAtLeast(0)`：设置读回来是脏值（负数）也不能把窗口算成过去。
3. 用 `LocalDate` + 系统时区，不用毫秒加减：跨夏令时/时区变更时毫秒差会偏一天。

用这一处的三方：`GalleryViewModel.kt:70,114,128`（筛选）、`ReminderEngine.kt:38`（通知）、
没有第四方。**"点进通知看到的和通知里说的必须同一批"就靠这一个函数**（`ReminderEngine.kt:37` 注释）。

**已过期也算"该看了"**：`ItemRepository.kt:334-335` 的注释写明——过期件的 `expiresAt` 同样不晚于上界，
且排序上最该在前面。所以那颗芯片叫「快到期/已过期」（`GalleryScreen.kt:240`），不叫"即将到期"。

列表里的颜色分档：`GalleryScreen.kt:332-337` 用 `daysUntil(expires)`，`days <= 30` 用 error 色。
30 是**显示用的强调阈值**，和提醒窗口无关（别把它当 `leadDays`，也别同步改）。📐

### 1.3 没保存的件数（确认页）🔒

`ReviewViewModel.kt`，四个各管一件事的计数器，**不许合并成两个**：

| 属性 | 定义 | 行 | 干什么用 |
|---|---|---|---|
| `notStored` | `slots.count { it.itemId == null }` | :70 | 退出后**不会留下**的件数 |
| `leftBehind` | `slots.count { it.itemId != null && !it.saved && !it.isThrowawayShell }` | :77 | 会以 DRAFT **留在**待归类的件数 |
| `alreadySaved` | `slots.count { it.itemId != null && it.saved }` | :84 | 库里已经存好、离开不动它们的件数 |
| `unsaved` | `slots.count { !it.saved }` | :90 | 芯片上的圆点（"这颗走完没有"） |

`leftBehind` 与 `alreadySaved` 是**两本账**（KDoc :72-84）：合成一个数，就等于对着已经确认的物品
说"它还要去待归类"。批次 AT 的代价就写在这里。

`unsaved` 与 `notStored` 也不是一回事：`unsaved` 包含已落库但没确认的件，那些件离开**会**留下。

### 1.4 空壳（可回收的草稿）🔒

```kotlin
val isThrowawayShell: Boolean
    get() = itemId != null && !saved && draft.name.isBlank() && touched.isEmpty()
```

`ReviewViewModel.kt:45-46`（`ReviewSlot` 的算属性，KDoc :39-44）。四个条件全要：

- `itemId != null`：没落库的件本来就什么都不留，不需要回收。
- `!saved`：已经确认过的是一台真物品，不是壳。
- `draft.name.isBlank()`：没名字。
- `touched.isEmpty()`：用户一个字都没打。打了字（哪怕只改数量）就是"他动过"，
  退出框会问他，不替他决定丢弃（批次 Z 的拍板）。

**这条规则只有两个读者**：退出时的回收器（§6.4）和退出框里那句"会留下什么"（§6.2/§6.3）。
所以它必须待在这一个地方，不许在别处重写一遍"我觉得这叫空壳"。

📌 **回收只发生在"用户退出录入页"那一次**，没有开机清扫：`WheremyApp.onCreate`（`:14-20`）只建通知频道 + 补 `search_text`。
所以用户**强杀进程或系统杀进程**时，一具空壳（有照片行、没名字、没手打内容）会作为「未命名物品」留在待归类里，
只能他自己删。这是拍板过的现状而不是待修项：开机扫库等于在用户没开口的前提下删东西，
而"他还会回来接着填"是常见情形。重做时**不要顺手加一个启动清扫**。

### 1.5 能按保存 🔒

```kotlin
val canSave: Boolean get() = itemId != null || draft.name.isNotBlank()   // :93
```

没落库的槽位（`itemId == null`）必须有名才允许开行；已落库的那件即使名字为空也允许按
——它此刻在库里是 DRAFT，保存是把它**搬出去**（确认），不是凭空造一条。

---

## 2. 物品状态机 🔒

```
      newDraft(jpeg)                confirm(draft)
 ∅ ────────────────────► DRAFT ───────────────────► CONFIRMED
      （选图那一刻）        ▲  │                        │
                          │  └ keepForLater ──────────┘
                          │     （写字段但退回 DRAFT）
                          └ move(itemId, loc) —— 有名字时顺手确认（:245-247）
```

只有两个状态：`ItemStatus.DRAFT` / `CONFIRMED`（`Entities.kt`，存成字符串）。
`blankItem` 造出来的行是 DRAFT + 空名 + 无位置（`ItemRepository.kt:295-301`）。

三条要点：

1. **DRAFT 是"这事没办完"，不是"垃圾"**。它在待归类里可见、可编辑、可提醒（§11），
   导出导入照搬。批次 N 之前想过把草稿藏起来，否掉了：藏起来等于让用户不知道自己有活儿没干完。
2. **状态迁移只有两处**。`confirm` 置 CONFIRMED（:169），`keepAsDraft` 置回 DRAFT（:185）。
   第三处是 `move`（:242-248）：从别的界面把一件 DRAFT 搬到某个位置，如果它有名字，
   就地确认——"我亲手把它放进了衣柜"就是确认动作。
3. **`lastMovedAt` 的两条写路不一样，别当成一条**：
   - `confirm`（表单保存）走 `:159-163` 的三档判断——位置真的变了才写新时刻，**位置没变就保留旧值**，
     新建时填了位置才算第一次搬。
   - `move`（别的界面"搬到"）走 `itemDao.moveTo`（`Daos.kt:40-41`），它把 `lastMovedAt` 与 `updatedAt`
     **无条件**写成同一个 `System.currentTimeMillis()`（`ItemRepository.kt:243`）——
     搬到**同一个**位置也会刷。
   这一条不对称是钉住的现状（"搬到"这颗本来就是用户主动的一次搬动动作），但重做时必须照此实现，
   不要"顺手统一"成两边都判等——`PRD.md` §3.5 末尾记着"没有任何界面读它"，
   把它统一掉之后，那条待办就更没人能发现了。
   它是数据留痕，任何一次无关的 `confirm` 都不该把它搅新，所以第一档判断必须留着。

---

## 3. 录入链路：先落库，且三条硬要求 🔒（来源：spec §6.1 + 批次 C/X 事故）

### 3.1 顺序

`CaptureFlow.kt:54-61`：

```kotlin
val itemId = items.newDraft(jpeg)   // 照片落盘 + 物品行 + 照片行
onDraftSaved(itemId)                // ← 这一刻起「保存」按钮可用
analyzeInto(itemId, items.takePendingAnalysis() ?: jpeg)
```

**先落库再分析**。任何一步 AI 失败只等于少一些预填，绝不等于丢一件物品。
`onDraftSaved` 必须在物品行落地之后、网络请求之前——"保存比 AI 快"（批次 X）能兑现全靠这一格。

`replacePhoto`（:69-77）同规矩：换图先落库再重跑，`onDraftSaved` 是同一个语义。

### 3.2 `newDraft` 的三条硬要求（`ItemRepository.kt:31-56`）🔒

1. **物品行与照片行同一个事务**（`db.withTransaction`，:39-48）。
   事故理由写在 :37-38：行成了、照片行没成 → 界面上是一件永远没有缩略图的物品，
   而那两个文件再没人来删（`delete()` 是按照片行找文件的，没行的文件就是孤儿）。
2. **事务失败要回滚磁盘**（:49-54）：`catch` 里删掉刚写的两个文件、清 `pendingAnalysis`，再把异常抛出去。
   只回滚数据库会在 `files/photos` 留下一对谁都不认的字节。
3. **`pendingAnalysis` 是进程内的暂存**（:28，`takePendingAnalysis()` 取一次就置 null）：
   送分析的是**为模型缩过的那一份**（1024 边），不是原图也不是磁盘上的文件。
   取一次就清空，免得迟到的第二次分析拿到一张已经作废的图。

### 3.3 `swapPhoto` 的三步顺序（`ItemRepository.kt:83-106`）🔒

**先写新文件 → 一个事务换行 → 最后删旧文件**。顺序反过来（先删旧的）中途磁盘满就留下一件没有照片的物品，
而"选错了照片"本来是最不该丢数据的一步。事务失败时删掉**新**写的那对（:99-103），
物品带着原图继续用。收尾走 `deleteUnreferenced`（:105），不是无条件删——两件共用一张图时旧图可能还有人指。

源物品行不存在时直接拒（:85），免得写出一对没人认的文件。

---

## 4. 一次分析回来，整批怎么重排 🔒（批次 AJ，`ReviewViewModel.kt:191-226`）

新顺序固定为 `listOf(merged) + kept + fresh`：

| 段 | 来源 | 规则 |
|---|---|---|
| `merged` | 第 1 颗槽位 + `CaptureOutcome` | 按 §5 逐字段合并，用户动过的字段赢 |
| `kept` | `before.slots.drop(1).filter { it.itemId != null }` | **已保存过的其余件原样留着**——它们已经是库里的物品，重跑一次分析没权把它们摘掉 |
| `fresh` | `out.others.map { ReviewSlot(...) }` | 没保存的旧槽位以新结果为准；旧结果对着一张已经换掉的照片，留着只会误导 |

三条附属规则：

- `selected = 0`（:218）：选中的那颗可能刚被摘掉，一律回到第 1 件。用户看得见的是第 1 件，
  "哪几件没了"由 `notice` 负责说（下一条）。
- `notice = "这次分析重新认了物品，之前没保存的那 $dropped 件已经不在了"`（:224，仅当 `dropped > 0`）。
  `dropped` = 重排前 `itemId == null` 的槽位数（:195）。🗣
- 每件的缩略图路径统一取 `items.thumbPathOf(out.itemId)`（:193）——一批共用一张图，
  所以每件显示同一张缩略图是对的，不是 bug（§9.4）。

---

## 5. AI 字段合并：谁赢 🔒

**两级合并，两个地方，规则不同。搞混了就会出现"改了名字被 AI 覆盖"或"保存之后又被迟到结果改写"。**

### 5.1 落库级：`applyAnalysis`（`ItemRepository.kt:109-130`）

```kotlin
if (cur.status != ItemStatus.DRAFT) return          // :112
name = a.name ?: cur.name                            // :114 起，逐字段
```

- **已确认的物品不再被迟到的 AI 结果改写**（:112）。保存之后分析才回来时，用户看到的就是他填的那些。
- 每个字段是 `AI ?: 库里现有`：AI 这个字段为空/没给，**保留**已有值。空字符串按空处理
  （`AnalysisParser.kt:76-79` 把 `""`、`"null"` 都归一成 null）。
- `tags` 特殊：`if (a.tags.isNotEmpty())` 才整组替换（:124,126-129），空数组**保留**库里那组。
- `expiresAt` / `expireKind` **不在这里写**：AI 不产出到期日，写了就必然抹掉用户手填的值。
- `searchText` 在写这一行的同时算出来（:125），见 §10。

### 5.2 表单级：`merge`（`ReviewViewModel.kt:229-249`）

```kotlin
fun <T> pick(field: String, ai: T, mine: T): T = if (field in touched) mine else ai
```

`touched` 里有什么，那个字段就**用户赢**。逐字段清单（:232-247）：
`name` `category` `seasons` `color` `brand` `material` `aliases` `description` `notes`
`quantity` `tags` `locationId` `expiresAt` `expireKind`。

两个坑，都在注释里：

- `setExpires` 一次写两个字段，但 `touched` 里**只有 `"expiresAt"` 这一个键**
  （:244-247；写入点 `:315-318`）。所以 `expireKind` 也用同一个键判定。
  不 `pick` 就会被迟到的分析结果抹掉——这两个字段本来不来自 AI，库里读回必定是 null。
- **谁往 `touched` 里写字**：只有真的用户动作写。
  `markTouched` 的调用点是 `setLocation`(:291) / `toggleSeason`(:296) / `toggleTag`(:303) /
  `setQuantity`(:310) / `setExpires`(:315) / `markTouched(field)`(:113)，以及各输入框自己的
  `onValueChange`。通用的 `update { ... }`（:289）**不**标记——否则一次 AI 回填会把整表标成"动过"。
- `touched` 在保存成功时清空（:338、:359），`keepForLater` 成功时也清空（:378，
  注释："字段已经写进库了，'你还有改动没保存'这句话从此就不该再说"）。

### 5.3 位置建议：只在空着的时候填 🔒

`CaptureFlow.kt:93-97`：

```kotlin
val suggested = suggest.suggest(tree, first?.containerGuess)?.takeIf { draft.locationId == null }
```

已经选好位置绝不覆盖。`suggestedLocationId` 单独带出来给界面说话（§5.4），
但填进表单的是 `draft.locationId ?: suggested`。

### 5.4 匹配规则：整条路径命中优先，然后打分 📐

`SuggestLocationUseCase.kt:10-52`，次序固定：

1. `guess` 空白/为 null → null。
2. **整条路径原样命中**（`pathMatch` :33-42）：把两边的空白全删掉后比字符串。
   理由写在 :29-32：两个不同父节点下都叫"抽屉"时，只认名字会挑错家，而模型这次拿到的本来就是完整串。
   空白不算差异——模型常把 `" / "` 写成 `"/"`。
3. 退回**模糊打分**（`score` :44-52）：`kind == guess || name == guess` → 3；
   `kind` 互为包含 → 2；`name` 互为包含 → 1；否则 0。
4. 取胜者：分数更高；**分数相同且 > 0 时取更浅的**（`depth < bestDepth`，:20）。
   同分又同深度时**不换**——于是拿到的是 `walk` 先序遍历里最早的那一个（:20 的条件是严格小于）。
5. 都对不上 → null。**绝不虚构一个用户家没有的位置**（:6 注释）。
6. 根层级永不参与（`if (node.parentId == null) return@walk`）：根是"家/工作间"那一层，不是容器。

喂给模型的候选是 `LocationTree.selectablePaths()`（`LocationTree.kt:46-57`）：先序、
**根不列**、每个可收纳层级一条完整路径。父节点丢失的脏数据按 `roots()` 的同一判法处理。

界面对这三种结局说三句不同的话，见 §6.5。

### 5.5 解析：候选顺序与去重 🔒（来源：真实模型行为）

`AnalysisParser.kt`：

- `candidates()`（:66-74）依次给出 **围栏内 JSON → 最外层 `{...}` → 整段文本 → 散落的每个小对象**，
  然后 `distinct()`。
  不能"任一解析成功即返回"：`deepseek-flash` 实测会把 `response_format` 先回显成
  `{"type": "json_object"}`（`AnalysisParser.kt:63` 的注释原文）再接真正的 JSON，贪婪的最外层匹配把两段吞成一段非法文本。
  不去重就是一件物品数出三件（同一个对象被"最外层/整段/散落"各命中一次）。
- `parseList()`（:30-38）先找**带 `items` 数组的那个对象**；找不到就退回"所有含已知键的对象"
  （`KNOWN_KEYS` :18-21）。最后 `filter { !it.isEmpty }`。
  旧写法只取一个对象，提示词改成回数组之后它会安静地只留下第一件，看起来像什么都没认出来。
- `echoedItemsKey()`（:44）把两件事分开：模型按提示词回 `items: []`（**它照做了**）
  和它回了一段读不懂的东西。前者报 `NOTHING_FOUND`，后者报 `BAD_RESPONSE`。
- 📌 **标量与数组的清洗不对称**：`string()`（:76-79）去空白、丢空串、并且把字面 `"null"`（不分大小写）
  归一成 null；`strings()`（:81-84）只去空白和丢空串，**不**认字面 `"null"`。
  于是模型回 `tags: ["null"]` 会留下一条写着 null 的标签、`aliases: ["NULL"]` 会进别名与 `search_text`。
  这是钉住的现状（真实模型没在这两个键上犯过这个错，而数组侧一旦照做就要定"整条丢还是单项丢"）。

### 5.6 提示词的固定要求 🗣（`Prompt.kt:18-35`）

`template()` 里那几条是**软约束**（解析侧一条不校验，见 §5.7、§14）：

- **最多 4 件**、只有一件就只回一件、同一件不要重复列（:19）。
- 🔒 **承托面不算一件**（:20）：提示词原文是 `物品放在别的东西上面时，只列物品本身，不要把桌面、地板、床面、抽屉内壁这类承托面也算成一件。`
  这句是批次 AJ 真机跑出来的——桌上放一副耳机，模型会把桌子也列成第二件。
- 认不出物品时 `items` 填 `[]`、只输出一个 JSON 对象、不要 Markdown 围栏（:21-22）。
- 🔗 **`FR-CFG-06`（本实现）**：类别与季节词表**可以被用户改**（设置页 →「词表」），所以提示词里写的是**生效的那份**（`Prompt.build(categories, seasons, paths)`）——「12 选 1」在用户改过之后就是「N 选 1」。不跟的话模型会回词表外的值，芯片行上显示不出选中态（§5.7 那个偏差就是这么来的）。
- 字段行的取值：`name` 必填要口语叫法（:24）、`category` 12 选 1（:25）、`seasons` 只能 春/夏/秋/冬（:26）、
  `brand` 原文是「仅当照片上文字清晰可读时才填，否则填 null。绝对不要猜测品牌。」（:28）、`aliases` 0–3（:30）、
  `description` ≤60 字（:31）、`tags` 3–8（:32）、末了统一"所有不确定的字段一律填 null，不要编造"（:34）。
- 带位置清单那一版把 `container_guess` 那一句**整句替换**成
  "从文末「可选位置」清单里原样抄一条完整路径（连父层级一起，一个字都不要改），没有一个合适就填 null；不要只填末级那个词，也不要发明清单外的位置。"（:15-16，🗣 逐字）。🔒 是替换不是补充：真机第一跑（2026-10-04，马克杯）两句并存时模型听了
  贴着字段那句，回了"橱柜"（注释 :10-12）。空清单时整段不出现、字段行退回通用问法（:45-48）。

🔒 发出去的那份提示词和留痕里的那份必须是同一串字（`ApiPhotoAnalyzer.kt:35-37`）：
在 `analyze` 里算一次、往下传。否则读留痕时核对的是"我们以为发了的"，不是"真发了的"。

### 5.7 词表外的类别/季节：照单入库，但界面筛不到 🔒（重做时必须选一边）

`AnalysisParser.kt:46-56` 对 `category`、`seasons` **不做白名单校验**，原样进 `AnalysisResult` →
`applyAnalysis`（`ItemRepository.kt:114` 起）→ 库里的 `item.category` / `seasons` CSV。
可预设词表只有 `CATEGORIES` 12 个、`SEASONS` 4 个（`Models.kt`，见 `UI.md` §10），于是：

- 录入页与照片墙都只渲染预设芯片（`ReviewScreen.kt:190`、`GalleryScreen.kt:243`），
  词表外的那个值**在表单上显示不出选中态**，用户看不见 AI 其实填了什么；
- 详情页原样显示字符串（`DetailScreen.kt:213-214`），所以它是**看得见（在详情里）却删不掉、也筛不到**；
- 分类/季节筛选走精确相等（`ItemRepository.kt:330-331`：`e.category == f.category`、
  `seasons.splitList().contains(f.season)`），模型回"冬季"而预设是"冬"时这一件永远不会被季节芯片筛中；
- 而 `search_text` 里**没有** `category` / `seasons`（§10.3 的参与字段清单），所以它**搜也搜不到、筛也筛不到**，
  只能靠名字与描述里的字面命中，或在详情页里看见原值（`DetailScreen.kt:213-214`）。

重做时二选一，**不许留着不选**（不选的话，`PRD.md` 的 FR-AI-01「照片 → 结构化字段预填」与成功判据 G3「不记得自己有什么时，能按类别/季节翻出来」就是两句互相拆台的话）：
① 解析侧就夹进词表（词表外的值丢弃，只在 `description` 里留线索）；② 保持入库、把筛选改成"也认自由值"
（那要在季节/分类芯片云里带出库里实际出现过的值，代价是芯片云变高、要和 §10.3 一起改）。
现在代码是第 ② 半（入库）+ 第 ① 半的界面（只给预设芯片），所以两边都不彻底。


---

## 6. 退出确认：判定树与逐字文案 🔒🗣（批次 Z / AT / AU）

### 6.1 要不要问（`ReviewViewModel.kt:415-421`）

```kotlin
if (cur.slots.none { it.itemId != null }) closeWithoutAsking()      // 一行都没落库
else if (cur.notStored == 0 && cur.slots.none { it.touched.isNotEmpty() }) closeWithoutAsking()
else _state.update { it.copy(confirmExit = true) }
```

两条"不问"的理由各不同：

- 没有任何物品行落库时，「保存改动并留下」这句兑现不了（它只写当前一件），问了就是骗。
  此时直接关，并走空壳回收（§6.4）。
- `notStored == 0 && 没有任何 touched`：芯片上每件都已存好，且用户一个手打字段都没改 —— 真没什么可丢。

左上角 ✕ 和系统返回**都先到这里**（:409-413 注释）。

### 6.2 支一：`notStored > 0`（`ReviewScreen.kt:314-336`）

标题 `还有物品没保存，确定离开？`

正文（`Column`，按条件拼）：

```
还有 ${state.notStored} 件没保存，离开后它们不会留下。
另外 ${state.leftBehind} 件已经在库里，会以草稿留在「待归类」。   ← 仅 leftBehind > 0
```

按钮：`继续编辑`（= `cancelExit`）/ `不保存，直接离开`（= `exitWithoutSaving`）。
`onDismissRequest = vm::cancelExit`（点框外=继续编辑）。

🔒 这一支**没有**「保存改动并留下」那颗按钮。注释写在 :315-316：这批里有只活在内存里的件，
那颗按钮只写当前一件，摆出来就是骗人，所以这一支只给两句实话。

第二句也不是可省的（批次 AT）：先落库那一件此刻就在库里，走了也还在；
只说前半句会让人以为这一屏离开之后什么都不多。

### 6.3 支二：`notStored == 0` 且有改动（`ReviewScreen.kt:337-363`）

标题：`leftBehind > 0` → `还没保存，确定离开？`，否则 → `改动还没保存，确定离开？`

正文三句各自可选：

```
照片和 AI 填的信息已经在库里了，状态还是草稿，会出现在「待归类」。   ← 仅 leftBehind > 0
库里那 ${state.alreadySaved} 件是已经存好的，离开不会改动它们。      ← 仅 alreadySaved > 0
但你手打的这些改动还没写进去，不保存就会丢。                        ← 恒定
```

按钮：`保存改动并留下`（= `exitKeepingEdits`，内部走 `keepForLater`，:427-430）/
`不保存，直接离开`（= `exitWithoutSaving`）。

🔒 「保存改动并留下」**不许新造第二种语义**：它就是那颗「稍后处理」。
同一个动作在两个入口有两种落库方式，迟早只有一边是对的。

📌 第一句的开关是 `leftBehind > 0`，而 `leftBehind` 的谓词只看 `!saved`：从详情页 `DetailScreen.kt:83` 那颗「编辑」进来的槽位是 `saved = true`（`ReviewViewModel.kt:262`），
而 `saved` 在三处置真后**没有任何路径复位**，所以"库里那行已确认、槽位却 `!saved`"这个状态在用户动作下造不出来。
这一条曾在 §15 记作偏差 W1，2026-10-06 复核**不成立、已闭合**，由 `ReviewScreenTest.kt:291` 钉住（断言那句 `assertDoesNotExist`）。

### 6.4 关闭时的回收（`closeWithoutAsking`，:443-456）🔒

```kotlin
val shells = _state.value.slots.filter { it.isThrowawayShell }.mapNotNull { it.itemId }
analysis?.cancel()
...
shells.forEach { items.delete(it) }
_state.update { it.copy(finished = true) }   // 关页排在删除之后
```

三条都在事故上换来：

1. **空壳连照片一起摘掉**（批次 AR）。:435-438 注释：用户选"不保存"说的是"别存我的改动"，
   不是"允许这里多一件没内容的物品"。删除走既有引用计数（§9.3），同批另一件在共用那两个字节时它们不会跟着走。
2. **无条件取消在飞的采集**（:440-441）。不取消就会出现"退出时没东西可删、删完了行才落库"的幽灵记录。
   `analysis?.cancel()` 必须排在 `shells` 计算之后、`delete` 之前。
3. **`finished = true` 排在删除之后**（:454）。这一格是"用户看得见页面关了"的唯一凭据，没删成就说关了是撒谎。

### 6.5 不要这件（`discardCurrent`，`ReviewViewModel.kt:395-406`）🔒

按钮文字是 `ReviewScreen.kt:138` 那颗「不要这件」。三条边界：

- `ReviewViewModel.kt:398`：`if (slot.itemId != null || st.slots.size < 2) return@update st` —— **已落库的那件不许走这条路**，
  那意味着顺手删掉一条库里的记录（那是详情页的事）；最后一件也不许摘（摘了就空屏）。
- **没有二次确认**（用户拍板，PRD ⛔ D14）：它丢的是一份 AI 预填的草稿，点错的那件本来也还没保存。
- `ReviewViewModel.kt:403` 那句 `notice`：`"「${slot.draft.name.ifBlank { "这一件" }}」没保存，库里不会有它"` 🗣
  必须说"库里不会有它"——只报名字会让人以为它已经在库里了。
- `ReviewViewModel.kt:402` 的 `selected` 用 `coerceAtMost(rest.lastIndex)`：摘掉末尾那颗时不越界。

### 6.6 AI 状态行的三句位置话 🗣（批次 Z，`ReviewScreen.kt:402-437`）🔒

`analyzing` 时：`AI 正在看照片，可以先自己填` + 一条 `LinearProgressIndicator`。

不在分析时，猜中的位置按三种情况说三句（注释 :420-421：只说成"它给了建议而界面没用"，
用户就是这么报的 bug）：

```
AI 猜的位置：$guess（已填进「放在哪里」）                                        ← guessApplied
AI 猜的是「$guess」，这件已经选好位置了，没有改动                                 ← placeAlreadyPicked
AI 猜的是「$guess」，你的位置里没有对得上的，需要的话点「选择」新建一个            ← 其余
```

- 「重新分析」只在 `canRetry` 时出现（:433）。照片没落库时没有可重跑的对象，
  按钮按下去不会有任何反应 —— 所以不许无条件摆出来。
- `retryAnalysis()`（`ReviewViewModel.kt:160-172`）还挡了另一种空：**照片文件不在了**
  （手工清过目录、或只恢复了库没恢复图）时 `reanalyze` 返回 `null`，界面必须自己收场：
  转圈停下 + `找不到这张照片了，重新选一张试试`（:169 🗣）。
  🔒 这一句给的是**下一步**，不是状态描述——少了它用户只能盯着一个永远的加载圈。
  `analyzing` 的复位在这里是显式写的（`copy(analyzing = false, …)`），而 `apply(out)` 那条成功路
  由 `apply` 自己收，两条都要走到"圈停"，重做时别只补成功那一路。

- 🔒 那句 `Text` 必须带 `Modifier.weight(1f)`（:429-430）：不给权重的话它会先按整行宽度被量走，
  「重新分析」剩 0 宽竖成一列，真机上的形状 bug。

---

## 7. 三颗收尾按钮的边界 🔒

| 按钮 | 方法 | 库里发生什么 | 页面 |
|---|---|---|---|
| 保存这一件 / 保存 | `ReviewViewModel.kt:326-344` `save()` | `items.confirm(draft)`，槽位 `saved = true`、`touched` 清空 | 只有一件时 `finish()`；**多件时不关页** |
| 稍后处理 | `ReviewViewModel.kt:370-385` `keepForLater()` | `keepAsDraft`：字段写进去，状态退回 DRAFT，位置保留 | 同上 |
| 不要这件 | `ReviewViewModel.kt:395-406` `discardCurrent()` | **什么都不做**（库里从来没有过它） | 芯片消失 |

- `save()` 在**写库之前**先 `analysis?.cancel()`（`ReviewViewModel.kt:330`，它前面两句只是读 `cur`/`slot`，没有副作用）：**用户按下保存的那一刻，在飞的分析结果就不再有权改这件物品**。
- 多件时不关页（`ReviewViewModel.kt:321-324`、:340、:380）：关页等于替用户把芯片上其余没保存的件扔了。
- 未落库槽位走 `saveAnUnsavedSlot`（`ReviewViewModel.kt:346-367`）：`newDraftSharingPhoto(source)` 开行 →
  `confirm` 写字段；**写失败就把刚开的行删掉**（:363）。留着就是一具没名字的壳（批次 X 清掉过的那种）。
- `newDraftSharingPhoto`（`ItemRepository.kt:65-75`）照片行为**复制源行**的 `filePath/thumbPath`，
  所以两件共用同一对文件；源没有可共用的照片时抛 `IllegalStateException`（消息中文，会原样贴到界面，见 §16.10）。
- **`ReviewViewModel.kt:361` 那句「已保存这一件」只属于"未落库那件"那一路**：`notice` 写在 `saveAnUnsavedSlot` 的成功分支。
  已经落库的槽位保存成功（:337-342）**不设 notice**——单件直接 `finish()` 关页，多件留在芯片上接着走。
  重做时不要在 `save()` 顶部统一设一次，那样每存一件都冒一句。
  稍后处理在多件批次里的另一句是 `这一件先放回待归类了`（:381，只在 `slots.size > 1` 时设；单件直接关页）🗣
- **两颗按钮的禁用门槛在界面上，不在 VM**：`ReviewScreen.kt:98` 那颗「稍后处理」用 `enabled = state.itemId != null`
  ——未落库那一件按不动它（VM 侧 `keepForLater` 的 `cur.itemId ?: return`（:372）
  只是兜底，光看 VM 会以为它能按）；保存那颗（`ReviewScreen.kt:100-102`）`enabled = state.canSave`，
  判据在 §1.5。两颗都不许改成"能按但按了没反应"。
- `keepForLater` 成功**不**把槽位置 `saved`（:378 只清 `touched`）：那件在库里此刻是 DRAFT，
  所以它仍算 `leftBehind`（§1.3）——这是对的，多件批次里它是"会以草稿留下"的那一件。

---

## 8. 位置树：命名、门槛、防环、同级排序 🔒

### 8.1 命名（`LocationRepository.kt:185-196`）

```
require(trimmed.isNotEmpty()) { "位置名称不能为空" }              // :187
require(!clash) { "同一层级下已有「$name」" }                     // :195
```

同级同名的判定：`it.id != excludeId && it.parentId == parentId && it.name == name`。
**区分大小写、不做拼音/别名归一**，`trim()` 之后比字符串。不同父层级下可以同名（这正是 §5.4
要整条路径命中的原因）。

### 8.2 删除门槛（`delete`，:175-183）🔒

```kotlin
if (children > 0 || items > 0) throw LocationNotEmptyException(children, items)
```

文案：`该位置下还有 $childCount 个子位置和 $itemCount 件物品，请先搬空再删除`（:20）🗣

**门槛只看子位置和物品，不看照片**（注释 :181）：照片不在门槛里，但删之前必须 `clearPhoto(id)`
——不清就是两个永远没人认领的文件。

`changeParent` 的三道 `require`（:75-77）：`不能把位置移动到它自己下面` / `目标位置不存在` /
`不能把位置移动到它的子位置下面`（这句用 `descendantIds` 判，见 :77）。
`node.parentId == newParentId` 时**返回 false 且不写**（:73）——界面据此说"已经在这一层级，没有改动"。

### 8.3 一次写两个字段（`update`，:53-68）🔒

改名和设类型必须一次读完再写回。分两笔读改写的话，第二笔拿到的是第一笔落地前的旧行，
**改名会被悄悄盖回去**（注释 :54-56；批次 K 的真机 bug）。

### 8.4 同级排序：重排号，不交换（`shiftWithinParent`，:140-167）🔒

返回 `Boolean`（是否真的动了）。做法：按**显示次序**给整条同级重新编号 `0..n-1`，
把目标位置的那条挪一格。

注释 :142-146 是要钉住的根因：同级里出现**相同 `sortOrder`** 时（备份包手改过、或旧版本留下的行），
显示靠 name 定次序（`LocationTree.kt:71-72` 的 `compareBy({ sortOrder }, { name })`），
交换两个相同的值等于什么都没换——"按了上移但那一行没动"就是这么来的。重排顺带把重复值清干净。

找兄弟时：`node.parentId == null || tree.node(parentId) == null` 按 `roots()`，
否则 `childrenOf(parentId)`（:152-157）——**父节点丢失的脏数据必须和界面用同一套规则**，
否则界面把它列在根层而这里找不着兄弟。

走到边界返回 false（:160），界面文案 `已经在这一层边上，上移 没有改动`（`TreeViewModel.kt:160-`）🗣

### 8.5 建树与防环（`LocationTree.kt`）🔒

- 一次全量读入内存建树，**不用递归 SQL**（:75-81；PRD ⛔ D2）。
- `roots()`：`parentId == null` **或父节点不存在**的都当根（:20-22）。脏数据不藏起来，也不丢。
- `pathOf`：`seen` 集合断环（:26-36），父子被写坏时返回到环入口为止，不无限上溯。
- `descendantIds`：`LinkedHashSet` + 栈，同样靠 `add` 返回值挡环（:59-69）。
- 显示次序：`sortedWith(compareBy({ it.sortOrder }, { it.name }))`（:71-72）。
- **防环在源头**：`TreeViewModel.prepareMove`（:140）把 `descendantIds(id)` 从候选里滤掉，
  数据层 `changeParent` 再 `require` 一遍。界面不给按 + 数据层不认，两层。

### 8.6 位置照片 📐（批次 AG/AL）

- **一个位置就一张**"这层长什么样"（`photoOf` 取 `firstOrNull()`，:92-93）。
- `setPhoto` 顺序与 `swapPhoto` 同一套：先写新文件 → 一个事务**整类清掉再写一行** → 最后按引用计数删旧
  （:102-128）。整类清掉的理由写在 :108：手改过的备份包可能塞进来多条，留着就永远删不到它们的文件。
- 位置行已经没了直接拒：`这个位置已经不在了，照片没处可挂`（:103）🗣
- `clearPhoto` 没照片时也是空操作（:130-138）：**界面不用先判断就能给这颗按钮开着**。
- 根层级没有可挂的持有者：`TreeViewModel.setPhoto` 里 `val id = parentId ?: return`（:211）。

### 8.7 `kind` 是自由标签 📐

`LOCATION_KINDS = listOf("房间", "衣柜", "抽屉", "收纳盒", "箱子", "挂钩", "货架", "其他")`
（:16-17）只是输入框的**建议值**，允许填这些之外的任意词。别把它当枚举去校验。

---

## 9. 删除与照片回收：只有一条规则 🔒

### 9.1 物品删除的顺序（`ItemRepository.delete`，:282-289）

```kotlin
val entity = itemDao.byId(itemId) ?: return
val photos = photoDao.forOwner(PhotoOwner.ITEM, itemId)
itemDao.delete(entity)                 // 1 行
itemDao.clearTags(itemId)              // 2 标签
photoDao.deleteForOwner(..., itemId)   // 3 照片行
deleteUnreferenced(photos.flatMap { ... })  // 4 文件
```

**先删行再删文件**（`:281` 注释）：行没了、文件成了孤儿，也不会被当成有效物品；
反过来就会短暂出现"有行没图"。

`item_photo` 的行**不会级联**——表定义里没有 `ON DELETE CASCADE`，靠这四步显式做完。
`clearTags` 同理。重建时如果改成级联，第 2、3 步可以省，但第 4 步必须留在事务之外。

### 9.2 唯一的回收规则（`PhotoReclaim.kt:15-21`）🔒

```kotlin
internal suspend fun deleteUnreferencedPhotoFiles(photoDao, photoStore, paths) =
    paths.filter { it.isNotBlank() }.distinct().forEach {
        if (photoDao.refsTo(it) == 0) photoStore.delete(it)
    }
```

`refsTo`（`Daos.kt:106-107`）：`SELECT COUNT(*) FROM item_photo WHERE filePath = :path OR thumbPath = :path`。

- **再没有任何照片行指着它，才把它从磁盘上删**。没有第二条回收规则，也没有别的删文件的口子。
- 物品侧和位置侧必须走这一条（`PhotoReclaim.kt:6-14`）：两类照片同表、靠 `ownerType` 分家，
  "按照片行无条件删文件"会变成删掉一处、把另一处正在用的图也带走。
- 🔒 **调用方要先把自己的行删掉或换掉**再进来，否则这条查询数到的就是自己。
- 路径判空 + `distinct()`：一行有两个路径，多行可能重复，去重后一次删。

### 9.3 删除前那句话怎么算（批次 AK 事故）🔒

`photoDeletionOf`（`ItemRepository.kt:265-279`）是数据层职责，**因为界面要说这句话**：

```kotlin
if (paths.isEmpty()) return PhotoDeletion(hasPhoto = false, otherItems = 0, otherOwners = 0)
paths.forEach { path ->
    photoDao.otherOwnersOnPath(ITEM, itemId, path).forEach { row ->
        if (row.ownerType == ITEM) otherItems = maxOf(otherItems, row.owners)
        else otherOwners = maxOf(otherOwners, row.owners)
    }
}
```

`otherOwnersOnPath`（`Daos.kt:114-119`）按 `ownerType` 分组数 `COUNT(DISTINCT ownerId)`，
排除自己。取 **max** 不是 sum：问的是"最多还有几个人共用"，不是"几个路径上各有几个人"。

`deleteWarningOf`（`UiFormat.kt:25-32`）四档，顺序即优先级：

| 条件 | 文案 🗣 |
|---|---|
| `!hasPhoto` | `这条记录会被删除，无法恢复。` |
| `otherItems > 0` | `这张照片还有 ${otherItems} 件物品在用，删掉这一件只会去掉这一件的记录，照片留着。` |
| `otherOwners > 0` | `这张照片还有别的记录在用，删掉这一件只会去掉这一件的记录，照片留着。` |
| 其余 | `照片和记录会一起删除，无法恢复。` |

`PendingDelete(itemId, warning)`（`UiFormat.kt:57`）把 **id 和那句话一起**存进界面状态。
理由（:53-56 注释）：弹框期间列表可能刷新（照片墙尤其如此），只存 id 的话就没人知道该说哪句实话。

第二、三档要分开：`otherItems` 说的是"另一件**物品**"，`otherOwners` 涵盖的是位置记录等别的持有者。
对着一张只被某个位置共用的图说"还有 N 件物品在用"就是假话。

### 9.4 三档尺寸 📐（`PhotoStore.kt:125-133`）

| 用途 | 长边 | JPEG 质量 | 存放 |
|---|---|---|---|
| 原图 | 1600 | 85 | `originals/<uuid>.jpg` |
| 缩略图 | 256 | 80 | `thumbs/<uuid>.jpg` |
| 送给模型 | 1024 | 80 | **不落盘**，进程内字节 |

`write()`（:26-48）三条硬要求：

1. 解码用 `inSampleSize` 逐步降采样（:57 `while (longEdge / (sample * 2) >= ORIGINAL_EDGE) sample *= 2`）
   ——直接解一张 12MP 原图会 OOM。
2. **EXIF 方向烤进像素**（批次 AC）：存下来的图不再依赖 `ExifInterface` 的方向标签，
   任何不做方向处理的解码路径（Compose/Coil 的兜底、备份包在别的机器上打开）都不会看到躺着的图。
3. 失败自清理：写中途抛异常要删掉已经落盘的半边，别留孤儿。

冷启动之后 `analysisJpeg` 已经没了，「重新分析」只能用 1600 那张原图
（`photoBytesOf` `ItemRepository.kt:204-206`；`CaptureFlow.kt:79` 注释：代价是多一点 token，行为仍然正确）。

---

## 10. 搜索 🔒

### 10.1 三段：SQL 取候选 → 谓词过滤 → 内存打分

`rankedSearch`（`ItemRepository.kt:209-216`）：

```kotlin
val candidates = if (query.isBlank()) itemDao.all()
                 else itemDao.searchLike("%${query.trim().lowercase()}%")
val tagged = filters.tag?.let { itemDao.itemIdsWithTag(it).toSet() } ?: emptySet()
return SearchRanker.rank(query, candidates.filter { matches(it, filters, tagged) })
```

- SQL 只负责**缩小**，不负责判定：`searchLike`（`Daos.kt:32-33`）
  `WHERE searchText LIKE :pattern ORDER BY updatedAt DESC LIMIT 300`。
- 📐 `LIMIT 300`：候选上限，个人量级够用。**已知后果**：同一时间点超过 300 件命中时，
  排在后面的会不参与打分（`updatedAt` 序）。要做对得翻页或换 FTS5（PRD ⛔ D1）。
- **空搜索词时不存在"默认排序"**：无词走 `itemDao.all()`（`Daos.kt:31` `SELECT * FROM item`，
  **没有 `ORDER BY`**），而 `SearchRanker.kt:8` 遇到空词把进来的列表**原样退回**。于是照片墙浏览态
  的顺序就是 SQLite 的返回顺序（实践上是 rowid 序 = 插入序），而 `@Upsert`（`Daos.kt:22` 的单行 `upsert`）按主键
  原地更新、不改 rowid，**编辑一件物品不会把它挪到前面**。`ORDER BY updatedAt DESC` 只存在于
  `searchLike`（:32）、`inbox`（:34）、`atLocation`（:36）、`byStatus`（:38）这四条查询里；
  只有**非空词**才走 `SearchRanker.kt:9` 的 `sortedByDescending`（稳定排序，同分之间保持 `:32` 给的
  `updatedAt DESC`）。别把"最近动过的排前面"当成现状——那是 PRD FR-FND-09 ⬜。
- `LIKE` 的 pattern 是 `lowercase()` 的，`searchText` 列**写入时已 lowercase**（§10.3），
  所以大小写不敏感；中文本来就无所谓。SQLite 的 `LIKE` 对非 ASCII 字母**区分大小写**，
  这一列全小写就是为了绕开它。
- **标签筛选认的是 `item_tag` 里的行，不是 `searchText` 里的字面**（:212-213 注释）：
  描述里提过一次"冬装"不代表这件打了冬装标签，chip 点下去多出一堆没打标的东西是骗人。
  `itemIdsWithTag` 用 `LOWER(tag) = LOWER(:tag)`（`Daos.kt:46-47`）。
- **筛选芯片那一排来自 `allTags()`**（`GalleryViewModel.kt:77` → `ItemRepository.kt:222`）：
  SQL 是 `SELECT DISTINCT tag … ORDER BY tag`（`Daos.kt:42-43`），**BINARY 排序**所以大写在前，
  而 `DISTINCT` 分大小写；仓库层再补一次 `distinctBy { it.lowercase() }`。
  净效果：库里同时有 "MacBook" 与 "macbook" 时，芯片只有「MacBook」一颗（留的是 SQL 的第一条），
  点下去两件都出来（`LOWER` 那条谓词）。卡片上列出的标签则按 `tagsFor` 的 `ORDER BY tag`（:25）原样给，
  **不做大小写合并**——同一件物品可以两个写法都摆着。重做时这两处的口径别统一成一处：
  芯片要的是"少而能点"，卡片要的是"用户/ AI 当初打了什么"。


### 10.2 打分（`SearchRanker.kt:12-22`）📐

```kotlin
name == q -> 100; name.startsWith(q) -> 80; name.contains(q) -> 60
aliases.contains(q) -> 40
searchText.contains(q) -> 20
否则 0
```

- `query.trim().lowercase()`，空查询**原样返回**（`SearchRanker.kt:8` 的 `if (q.isEmpty()) return items`）——
  不打分、不重排。⚠️ 于是**空查询时的屏幕次序就是 SQL 的次序**，而照片墙走的是 `itemDao.all()`，
  它是 `SELECT * FROM item`，**没有 `ORDER BY`**（`Daos.kt:31`）→ SQLite 按 rowid 升序给，
  也就是**按插入先后**，新建的那件排在**最后**。真机上这条被踩过两次（两列网格下第 5、6 格在屏幕外，
  看起来像"没录进去"），所以：
  - 重做时**不要**想当然写成 `ORDER BY updatedAt DESC`——那会改变现有机器上用户已经习惯的卡片次序，
    也会让"屏幕上第 N 格是谁"这种对账失效（`REBUILD.md` §7 C 组记的按格子删除事故就靠这个次序核对）；
  - 要加"最近动过排前面"是 `PRD.md` ⬜ FR-FND-09 那条待办，动它就得同时给照片墙加一个排序说明。
- 有词那一路才是 `ORDER BY updatedAt DESC LIMIT 300`（`Daos.kt:32-33`）。
- 用 `sortedByDescending`，它是**稳定**排序：**有词时**同分的两件保持 SQL 给的 `updatedAt DESC`（新的在前）。
  空查询时不参与打分，所以这条不适用（见上一条）。
- 📌 **0 分不剔除**（`SearchRanker.kt:9` 只 `sortedByDescending`，不过滤）：SQL 的 `LIKE` 命中了
  但内存打分算出 0 分的行**照样显示**，只是垫底。这两件事在文档里必须分开说，
  否则"筛出来比 SQL 少"会被当成 bug。
- 类别/季节/位置**不参与打分**，只参与过滤（`matches`）。理由：用户搜"相机"想要的是名字像相机的东西，
  不是"所有电子设备"。

### 10.3 `search_text` 是派生列 🔒（`SearchTextBuilder.kt`）

参与字段（:25-34）：`name, brand, color, material, aliases, tags, description, notes`
→ `joinToString(" ")` → `lowercase()` → 连续空白压成一个空格 → `trim()`。

`category` / `seasons` / `locationId` 不在里面（它们是过滤维度，不是搜索文本）。

🔒 **`FIELD_VERSION = 2`**（:9）：这一列是写入时算出来的派生值，**加了字段不会让已录物品自动带上**。
所以：

1. 改 `build` 的参数就把这个版本号加一；
2. `ItemRepository.rebuildSearchText()`（:254-259）在启动时拿它跟设置里存的值比，不一样就补一遍全表；
3. 补算时**只在内容真的变了才写**（:257），避免为一次启动把整库的 `updatedAt` 搅新——
   `updatedAt` 是 `LIMIT 300` 的次序依据，也是"多久没动"的间接线索。

---

## 11. 到期提醒 🔒

### 11.1 排班（`ReminderScheduler.kt:14-22`）📐

`PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)` + `enqueueUniquePeriodicWork(KEEP)`。

- `UNIQUE_NAME = "wheremy_expire_daily"`。
- 🔒 **`KEEP` 而不是 `UPDATE`**（注释 :18）：提前几天是 Worker 每次现读设置（`ReminderWorker.kt:56`），
  重排只会白白丢掉已排好的班次。改设置**不需要**也不许重新排班。
- WorkManager 自己会在重启后继续跑，所以**没有** BootReceiver。

### 11.2 "每件物品每个截止日期最多说两次"🔒（`Daos.kt:55-72`）

窗口三值（`ReminderEngine.kt:35-38`）：

```
windowStart = localStartOfDay(now)                 // 今天 00:00
todayEnd    = localStartOfDay(now.plusDays(1))     // 明天 00:00
windowEnd   = expireWindowEnd(leadDays, now)       // 第 leadDays 天 23:59:59.999
```

`expiringBy` 的谓词（`Daos.kt:68-72`）：

```sql
expiresAt IS NOT NULL AND expiresAt <= :windowEnd
AND (expireNotifiedAt IS NULL
     OR (expireNotifiedAt < :windowStart AND expiresAt >= :windowStart AND expiresAt < :todayEnd))
ORDER BY expiresAt LIMIT 50
```

三条各挡一种骚扰：

| 分支 | 允许什么 | 挡住什么 |
|---|---|---|
| `expireNotifiedAt IS NULL` | 进窗口后的第一次；**包括过期很久但从没说过的** | 之后每天重复 |
| `expireNotifiedAt < windowStart AND expiresAt ∈ [windowStart, todayEnd)` | "提前提醒过、而今天就是到期日"→ 允许再说一次 | 同一天第二次 |
| 过期已过且已说过的**不再进来** | —— | `ORDER BY expiresAt` 会把它们排最前，久了占满 `LIMIT 50`，新到期的挤不进来 |

🔒 注释 :57-59 记了一个真踩过的坑：旧的写法只要求"早于本次窗口起点"，那会把"最多两次"变成
**"每天一次"**——提前 30 天就是 31 条。所以第二分支的三个条件缺一不可。

📐 `LIMIT 50` 是给**单条聚合通知**用的（注释 :66-67）：个人物品一次超过 50 件本身就说明需要先清库。

**改过到期日会重新武装**（`reArm`，`ItemRepository.kt:144-150`）：`expiresAt` 变了就把
`expireNotifiedAt` 清空。没有这个动作，把保修日从 3 月改到 9 月就等于永远不会再被提一句。
`confirm` 与 `keepAsDraft` 都过 `reArm`（:166、:185）。

**DRAFT 也在范围内**（`Daos.kt:64-65`）：`expiresAt` 只可能由用户手填（AI 不产出它），
"稍后处理"没道理让提醒哑掉。

### 11.3 正文四变体 🗣（`ReminderEngine.kt:52-57`）

| 条件（按顺序判） | 文案 |
|---|---|
| `nearestDays < 0` | `$count 件物品到期或已过期，最早的已过期 ${-nearestDays} 天` |
| `todayCount == count` | `$count 件物品今天到期` |
| `todayCount > 0` | `$count 件物品临近到期，其中 $todayCount 件今天到期` |
| 其余 | `$count 件物品临近到期，最近的还剩 $nearestDays 天` |

🔒 两条判定纪律（注释 :48-51）：已经过期和还剩几天是两件事，不许写成"临近到期，最近的已过期 3 天"；
"N 件今天到期"**只在真的每一件都是今天时**才成立。

- `nearest` = `daysUntil(hits.first().expiresAt)`，因为 DAO 按 `expiresAt` **升序**（:42-43）。
- `daysUntil` 按**日历天**算（`Dates.kt:29-33`），和详情页/列表里"还剩 N 天"同一个换算。
- `ReminderPlan.ids` 带着命中集合，Worker 拿它回写（`ReminderWorker.kt:62`）——
  免得"数了 20 件"却标记了另 20 件（`ReminderEngine.kt:9-11`）。

### 11.4 发不出去就不回写 🔒（`ReminderWorker.kt:21-27,52-64`）

```kotlin
val problem = notifyProblem(ctx)
if (problem != null) return "${plan.count} 件临近到期，但通知发不出去：$problem"
post(ctx, plan.body)
dao.markExpireNotified(plan.ids, System.currentTimeMillis())   // 只在真发出去之后
```

理由写在 :24-26：安装后先拒了通知权限、几天后才开开的用户，需要**那几件物品还被说出来**；
在被拒的那几天里把它们标记成"已提醒"，等于让提醒功能静默地把账欠掉。
代价只是权限没开时每天白算一次窗口——一条 `LIMIT 50` 的查询，不值得为它省。

- `runOnce` 返回的那句话**就是设置页 debug 区的全部内容**，四档各有唯一 reachable 状态（:58,60,63）🗣：

| 档 | 逐字串 | 什么时候出现 |
|---|---|---|
| 窗口空 | `窗口里没有临近到期的物品（提前 $leadDays 天）` | `plan()` 返回 null（:58）——把当前提前天数念出来，用户不必自己去数 |
| 发不出去 | `${plan.count} 件临近到期，但通知发不出去：$problem` | `notifyProblem` 非 null（:60），`$problem` 见下面两句 |
| 成功 | `已发一条通知：${plan.body}` | 贴的是 §11.3 那四变体之一，**不是第二份真值**（§16.8） |

  `notifyProblem` 的两种理由（:88-89）🗣：`通知未开启，到期只在应用内标记`（运行时权限没给）/
  `通知在系统里被关掉了：到 设置 → 应用 → Wheremy → 通知 里打开，不然到期只能靠你自己想起来。`
- `force`（`expiringIgnoringNotified`，`Daos.kt:79-82`）**只给设置页那个调试按钮**：

  它放宽的是"今天说过几次"，不是把沉默的旧账翻回来骚扰——`过期很久且已说过的仍然不进来`
  （条件：`expiresAt >= windowStart OR expireNotifiedAt IS NULL`）。
  日常排班永远传 `false`（`doWork()` :33）。
- ⚠️ **按调试按钮不是只读操作**：`markExpireNotified` 在 :62 位于 `force` 判定之外，
  所以那一次按下去照样回写 `expireNotifiedAt`。代价是这件物品"提前知道"那一次额度被调试吃掉一次
  （`Daos.kt:69` 的 `expireNotifiedAt IS NULL` 分支从此关死）；到期当天那一次仍会来，因为 :70 的
  第二分支只看 `expiresAt` 是否落在 `[windowStart, todayEnd)`。
  对**已经过期但从没说过的**物品，这一次就是它唯一的一次——之后两个分支都进不来。
  📌 重做时若要按钮纯只读，就把回写挪进 `if (!force)`；**当前实现不是这样，别当成 bug 顺手改**，
  验收手册里"反复按能反复出通知"靠的正是 `force` 放宽的那条谓词，而不是额度还在。
- 通知固定 `NOTIFICATION_ID = 1001`、`setAutoCancel(true)`，一条聚合通知只占一格（:41-42,118）。
- 频道 `CHANNEL_ID = "wheremy_expire"` / `CHANNEL_NAME = "到期提醒"` / `IMPORTANCE_DEFAULT`（:38-39,67）。
  ⚠️ 频道静音但系统级"通知已启用"仍为真时，这条会被算作"已提醒"（批次 V 拍板**钉住现状**，见 §15）。
- 点击落点：`ENTRY_EXPIRING` → 筛过"快到期/已过期"的照片墙（`wallIntent` :100-104，
  `GalleryViewModel.showExpiringSoonOnly` :126-132）。🔒 `showExpiringSoonOnly` 会把
  `query` 和 category/season/tag 全清掉——用户从通知点进来要的是"这些到期的"，不是上一屏的筛选。
- `notifyProblem` 两种理由（:86-90）：运行时权限没给 / 系统总开关关着。**不给就地申请**
  （2026-10-03 真机否掉：HyperOS 在总开关关着时会静默驳回那次申请，按下去连弹框都没有），
  两种共用同一颗按钮跳 `ACTION_APP_NOTIFICATION_SETTINGS`。

### 11.5 提前天数（`SettingsRepository.kt:70`）📐

`DEFAULT_LEAD_DAYS = 3`。它由**每次运行现读**（`ReminderWorker.kt:56`），不进排班请求；
照片墙那颗芯片也是现读：点开芯片时算上界（`GalleryViewModel.kt:114`），而筛选已经开着时
**每一轮读库再算一次**（:69-70，注释原文「上界每次读库现算」）——隔夜之后窗口跟着挪，
不会出现"昨天开的芯片今天筛出过期两天的东西"。所以设置改了立刻生效，两处也不会打架。


---

## 12. 备份 🔒

### 12.1 导出口径（`BackupRepository.kt:37-47`）

四张表**在同一个事务里读**（`db.withTransaction`）。理由（:38-39）：`newDraft` 是"先写物品、
再写照片"两条语句，夹在中间读就会导出一个**所属物品不在包里**的照片行，那种包导入时被 `validate` 判成损坏。

- 照片文件**同一个路径只写一次**（`putOnce` :249-255）：两件共用一张图时第二件指的是同一对文件。
- **读不到的图跳过就行**（`putFile` :257-265）：一张丢失的缩略图不该让整次导出失败。
  这一条决定了导入侧必须"缺文件要放行"（§12.3）。
- 包里的计数与"完成"那句话说的是**不同的文件对**（:192-193）。

🔒 **绝不写进包**：API key、任何设置项（spec §6.6 与 §5、PRD ⛔ D12、`ARCHITECTURE.md` §10）。
`allowBackup="false"` 保持不动；`ai-trace` 目录刻意不在 `files/photos` 底下，所以不随备份出门。

### 12.2 导入的固定顺序（:81-96）🔒

```
读包 → 整体校验 → 一个事务落库 → 事务成功之后才动磁盘上的照片
```

任何一步失败都不留下半个库（spec §6.6）。事务里抛 `SQLiteConstraintException` 会被包成
`BackupFormatException("备份里有对不上的引用，已取消导入，原来的数据没有动")`（:92-93）🗣

⚠️ **但"半个库"只保证库**。`removeLocalPhotosNotIn`（:95）跑在事务**提交之后**、
`moveStagedPhotos`（:98）**之前**：进程在这两行之间被杀，库已经是新包的、磁盘上的图是删了一半的。
唯一的恢复来源还是用户选的那个包（重导一次就再搬一遍），这也正是 :96-97 注释的理由。
界面对这件事有一句承重文案，必须原样保住它把话说全：
`先把现在的物品、位置和照片全部删掉，再按备份包重建。中途断开不会留下半个库，但清空之后就只能靠这个包了。`
（`SettingsScreen.kt:107` 🗣）。重做时若把删除挪到搬图之后可以缩掉这个窗口，但**不许把这句改成"只替换数据"**。

### 12.3 校验：四条拒 + 一条放行（`validate` :149-168）🔒

| # | 拒什么 | 文案 🗣 |
|---|---|---|
| 1 | 物品的 `locationId` 不在包里 | `「${name.ifBlank { "未命名物品" }}」所在的位置不在包里，备份不完整` |
| 2 | 照片行的 `ownerId` 找不到所属条目 | `有照片找不到它所属的条目，备份不完整` |
| 3 | 照片行 `filePath` 或 `thumbPath` 为空白 | `有照片没写它对应的文件，备份不完整` |
| 4 | 标签挂在不在包里的物品上 | `有标签挂在一件不在包里的物品上，备份不完整` |

🔒 **放行**：**文件不在包里不拒**。:158-160 注释是这条的命门：
"空路径"与"文件缺了"不是一回事——文件缺了要放行（导出本来就放过丢了的图，
否则一台**已经丢了文件的机器连自己的备份都恢复不了**）；而空路径这一行连该指哪儿都没说，
`newDraftSharingPhoto` 在自己家里都拒绝造这种哑物品，导入不能收进来。
批次 AM 就是为这一条加的测试。

⚠️ **另有第五道拒，但不在 `validate()` 里**：读包阶段的 `manifest.schemaVersion > DB_SCHEMA_VERSION` 就直接拒
（`BackupRepository.kt:139-141`，文案见 `PRD.md` FR-BAK-05），发生在**一张表都没写过**之前。
所以导入侧的"拒"一共五条、分两处（读包 1 条 + 校验 4 条）——重做时别把这条塞进 `validate()`，
那条路此刻已经拿到了一个数据包，语义不同。

**解包路径白名单**（`isPhotoPath` :236-241）：只认 `PhotoStore` 的两个目录、
必须只有一层分隔、字符集限 `字母数字 - _ . /`、不许含 `..`。其余条目一律拒。
理由写在 :235：解一个来路不明的 zip 时，`..` 或绝对路径会写到应用外面去。🔒 这是安全边界，不许放宽。

### 12.4 MERGE 与 OVERWRITE（`writeRows` :171-194）

- **MERGE**：各表按自己的主键逐行比对，**本机已有的原样保留**（先读 `knownX` 集合再过滤）。
  标签的键是 `itemId to tag` 这一对（:182,187）。
  位置插入前过 `parentsFirst`（:197-216）：SQLite 逐条语句立刻检查外键，而包里位置条目的顺序
  是 `all()` 给的，不可控。`ready.isEmpty()` 时把剩下的原样交回，**让外键去拒**成环的包（:209-210）。
- **OVERWRITE**：先四表 `deleteAll` 再整包写入，然后 `removeLocalPhotosNotIn(keep)`（:95,218-224）
  ——**只删没被新包声称的文件**，逐目录遍历 `PHOTO_DIRS`。
  📌 判据是"**包里有没有这条字节**"（`keep` 只在解出 `photos/` 条目时累加，:122-125），
  **不是**"新库里有没有行引用它"。两者会分叉：导出时读不到而被 `putFile` 跳过的图（§12.1）不进 `keep`，
  覆盖导入就把本机那一份删掉——即使它后来又能读了。反过来，包里有字节但库里没有对应行的文件**不会被删**，
  于是覆盖导入可能留下几页新库根本不指的孤儿文件（回收只有一条规则：`deleteUnreferencedPhotoFiles`，§9.2，
  它在删除物品/位置时触发，**导入路径不会调它**，所以孤儿文件不会被顺手清掉）。
- **MERGE 的成环/孤儿位置会整包拒**：`parentsFirst`（:197-216）把 `parentId !in ids`（父节点既不在包里、
  也不在本机）的节点归入"第一批就绪"（:207），因为 `location_node.parentId` 有指向本表 `id` 的外键
  （`Entities.kt:18-21`），SQLite 逐条语句立刻检查，于是抛 `SQLiteConstraintException` →
  走 :92-93 那句 `备份里有对不上的引用`。
  ⚠️ 这与**读侧**是两半：`LocationTree.roots()` 把"父节点不存在"当根（§8.5，:20-22），同一份脏数据
  躺在库里能正常显示，作为导入进来却整包失败。而 `validate`（:149-168）只查物品的 `locationId`、
  照片的 `ownerId` 与标签的 `itemId`，**不查位置的 `parentId`**，所以这种包能过校验、死在事务里，
  报的是那句较笼统的引用错。重做时要么在 `validate` 加第四条（给具体名字），要么让 `parentsFirst`
  把孤儿降级为根再插入——**别留着让它靠外键报错**，那条文案指不出是哪个节点。
- 📐 单张照片搬不进去**不回滚库**（`:96` 注释：DB 是唯一真相源），代价是那件缩略图空白。
- `BackupSummary.bytes` 在导入路径是 `0`（:193），所以界面对 `bytes == 0` 不显示"0 字节"
  （`SettingsViewModel.kt:277-280`）。

---

## 13. AI 失败 11 类：判定与文案 🔒🗣

### 13.1 状态码 → 结局（`ApiPhotoAnalyzer.classify` :82-94）

**只有这一处映射**（注释 :81）：界面报的那句话和留痕里的标签都从它拿，不许有两份说法。

| 判定 | 结局 | 可重试 |
|---|---|---|
| `code == 401 \|\| 403` | `UNAUTHORIZED` | ✗ |
| `code == 429` | `RATE_LIMITED` | ✓ |
| `code == 402` | `INSUFFICIENT_BALANCE` | ✗ |
| `code == 400` 且 `isBalanceComplaint(text)` | `INSUFFICIENT_BALANCE` | ✗ |
| `code == 400` 其余 | `BAD_REQUEST` | ✗ |
| 非 2xx 其余 | `SERVER` | ✓ |
| 2xx 且 `finish_reason == "length"` | `TRUNCATED` | ✓ |
| 2xx 且解析不出 `choices/message/content` | `BAD_RESPONSE` | ✓ |
| 2xx、解析出内容、`items` 键出现过但空 | `NOTHING_FOUND` | ✗ |
| 2xx、内容读不懂 | `BAD_RESPONSE` | ✓ |
| 抛 `InterruptedIOException` | `TIMEOUT` | ✓ |
| 抛 `IOException` | `OFFLINE` | ✓ |
| `configProvider()` 返回 null | `NO_CONFIG` | ✗ |

`RETRYABLE = {SERVER, OFFLINE, TIMEOUT, RATE_LIMITED}`（:176-181）。
`MAX_ATTEMPTS = 2`（:174）——📐 **鉴权与配置类重试没有意义**（:46 注释），只有服务端/网络抖动值得再试一次。
重试共享同一份 `body` 与 `systemPrompt`（不重新序列化、不重读设置）。

两个必须钉住的分类细节：

- `402` 与"400 + 一句 balance"：`INSUFFICIENT_BALANCE` 走两条路（:85-90 注释）。
  **只认状态码的话用户会被派去核对模型名，而真正要办的是充值**——DeepSeek 报的是 400 加一句 balance。
  `isBalanceComplaint`（:120-121）在 body 里找 `balance` / `quota` / `余额` / `额度`
  （前两个是实测两家的英文，后两个是同一句报错的本地化版本）。
- `finish_reason == "length"` 必须在解析 `content` **之前**判（:128-131 注释）：
  被切断时 `content` 是半句 JSON 甚至干脆没有，这时候要告诉用户的是"答案太长被切断"，
  不是"模型返回的内容无法解析"（批次 U）。
- `NOTHING_FOUND` 与 `BAD_RESPONSE` 的分界是 `echoedItemsKey`（§5.5）：
  提示词让模型一件都不认时回 `items: []`，**它照做了**，就不能报"无法解析"。

### 13.2 11 条文案（`UiFormat.failureText` :35-51）🗣

| 原因 | 原文 |
|---|---|
| `null` | （不显示） |
| `NO_CONFIG` | `未启用 AI（还没配 API Key），字段留空，直接手填就行` |
| `OFFLINE` | `网络不可用，AI 没连上，先手填，联网后可以重试` |
| `TIMEOUT` | `AI 分析超时，先手填，稍后可以重试` |
| `UNAUTHORIZED` | `API Key 无效或被拒，先手填，换 key 后再点重新分析` |
| `RATE_LIMITED` | `AI 服务限流，稍后再试一次` |
| `SERVER` | `AI 服务暂不可用，可点重新分析` |
| `BAD_RESPONSE` | `AI 返回内容无法解析，可点重新分析` |
| `BAD_REQUEST` | `请求被服务端拒了，多半是设置里的模型名或接口地址填错，改对后再点重新分析` |
| `INSUFFICIENT_BALANCE` | `AI 账户没有可用余额或额度，充值或换账户后再点重新分析` |
| `TRUNCATED` | `AI 的回应太长被切断了，可点重新分析，或换一张只拍一件物品的照片` |
| `NOTHING_FOUND` | `AI 没从照片里认出物品，字段留空，直接手填就行` |

🔒 三条写法纪律：

1. `TIMEOUT` **故意不写秒数**（:40 注释）：超时由 `AppContainer.CALL_TIMEOUT_SECONDS` 说了算，
   抄一份在文案里就是等着说错。
2. `OFFLINE` **别说"已跳过"**（:38 注释）：这条链路真发了请求（可重试的话两次），只是 `IOException` 收场。
3. 每条都带**下一步**：没有一条只说"失败了"（判据在 §16 第 4 条与 §17 第 2 条；spec §8 只给了错误情形表，没有这条措辞要求）。

### 13.3 留痕（`ApiPhotoAnalyzer.record` :97-118 + `AnalysisTrace.kt`）🔒

- **一次尝试一条**，重试就是两条。留痕是**旁路**：`runCatching { ... }.getOrDefault(Unit)`（:117）——
  目录写不了（磁盘满、权限被撤）也必须把分析结果还给用户。
- 痕里**只有提示词原文和回应原文**：没有 key、没有那张 base64 的图（:112 注释）。
  `outcome` 存的是 `success` 或 `FailureReason.name`。
- 限额：`ANALYSIS_TRACE_MAX = 10`（滚动删最旧的，:33/74）、单字段 `MAX_CHARS = 8_000`
  超出写成 `…（截断，原长 N 字）`（:59-60）、同毫秒序号 `MAX_SEQ = 1_000`（撞满了复用最后一格，:65/83-84）。
- 位置：`files/ai-trace`（`AppContainer.kt` 注释：**刻意不在 `files/photos` 底下**，
  因为备份只搬数据库行和那个目录；留痕里带着用户的完整位置清单，不该跟着备份包出门）。🔒

---

## 14. 数字与阈值一览 📐

改任何一个之前先确认第二处会不会跟着错。

| 值 | 定义处 | 还有谁在用 |
|---|---|---|
| 提前天数默认 3 | `SettingsRepository.kt:70` | 每次现读：`ReminderWorker.kt:56`、`GalleryViewModel.kt:70,114,128`；`SettingsViewModel` 初值 |
| 提前天数区间 0 / 365 | `SettingsRepository.kt:77-78` | **四处夹它**：写库 `coerceIn`（:46）、`SettingsViewModel.kt:142` 再夹一次、界面 `+`/`-` 的 `enabled`（`SettingsScreen.kt:150,154`）、以及窗口算法自己的 `leadDays.coerceAtLeast(0)`（`Dates.kt:26`——这一处挡的是"有人绕过前两层直接传负数"）。0 有专门的说法「当天到期提醒」（`SettingsScreen.kt:144`） |
| 窗口上界算法 | `Dates.kt:25-26` | 筛选、通知、两者必须一致（§1.2） |
| 重试 2 次 | `ApiPhotoAnalyzer.kt:174` | 留痕条数 = 尝试次数 |
| 调用超时 15 秒 | `AppContainer.CALL_TIMEOUT_SECONDS` | **不许**出现在界面文案里（§13.2） |
| `LIMIT 50` | `Daos.kt:71,81` | 单条聚合通知的规模上限 |
| `LIMIT 300` | `Daos.kt:32` | 搜索候选上限（§10.1） |
| `searchText` 字段版本 2 | `SearchTextBuilder.kt:9` | 启动时与设置里存的值比对，决定是否补算 |
| 1600 / 256 / 1024；85/80/80 | `PhotoStore.kt:128-133` | 1024 只送模型、不落盘 |
| 留痕 10 条 / 8000 字 | `AnalysisTrace.kt:33,81` | 设置页 debug 显示条数 |
| 留痕同毫秒序号上限 1000 | `AnalysisTrace.kt:84` | :65 的循环到上限就直接返回那个文件名、不再查存在性 → 同一毫秒第 1001 条会盖掉第 1000 条。每秒一千次分析不会发生，记在这儿是**别把它当 bug 顺手加检查** |
| 解包暂存目录 `.incoming` | `BackupRepository.kt:22` | 位于 `files/photos/.incoming`；进门先删（:86）、`finally` 再删（:103）。`removeLocalPhotosNotIn` 只遍历 `PHOTO_DIRS`（:219），所以它不会被覆盖导入误删 |
| `formatBytes` 用 1000 进制且**整除截断** | `UiFormat.kt:14-18` | `1_999_999` 显示成 `1 MB`。导出口径唯一用它；导入返回 `bytes = 0`，所以界面按 `bytes == 0` 不显示（§12.4） |
| 同级 `sortOrder = 兄弟最大值 + 1` | `LocationRepository.kt:198-200` | 新建和换父级都排到最后；同级 `sortOrder` 相同则退回按 `name`（§8.5 `LocationTree.kt:71-72`） |
| 提示词软上限：4 件、`description` ≤60 字、`tags` 3–8、`aliases` 0–3、`category` 12 选一、`seasons` 只有春夏秋冬 | `Prompt.kt:19,25-32` | 🔒 **一条都不在解析侧校验**（§5.3、§5.7）：模型给 9 个标签就存 9 个，给词表外的类别就照单入库。写提示词时别指望"后面会被过滤掉" |
| `max_tokens = 2000`、`temperature = 0.1` | `ApiPhotoAnalyzer.buildBody` | 600 会被推理模型的 `reasoning` 吃掉并截断回应（注释在代码里） |
| `days <= 30` 用 error 色 | `GalleryScreen.kt:336` | 纯显示强调，与提醒窗口无关 |
| `NOTIFICATION_ID = 1001` | `ReminderWorker.kt:42` | `PendingIntent` 用同一个 requestCode（:109） |
| `versionName` 进备份清单 | `AppContainer.kt` `backup` 的 `BuildConfig.VERSION_NAME` | 导入侧不因版本不同而拒 |


---

## 15. ⚠️ 已知偏差与"钉住现状"

这几条是**代码这么写了、但不完全成立**的地方。列在这里是为了下一次不重新踩。

| # | 偏差 | 影响 | 现状 |
|---|---|---|---|
| W1 | ~~支二退出框第一句对**已确认**物品也说"状态还是草稿，会出现在待归类"（§6.3）~~ **2026-10-06 复核：不成立，已闭合** | 那一句的开关是 `leftBehind > 0`，而 `leftBehind` 要 `!saved`；`saved` 只在三处置真——`load()`（`ReviewViewModel.kt:262`）、`save()` 成功（:338）、`saveAnUnsavedSlot` 成功（:359），**没有任何路径把它复位**，所以"库里那行已确认而槽位 `!saved`"这个状态在用户动作下造不出来。W1 原来给的触发（保存失败一次再退出）走的是 :342 的失败分支，它只写 `error`，不动 `saved` | 已由 `ReviewScreenTest.kt:291 editingAStoredItemThenLeavingDoesNotCallItADraft` 钉住：`ReviewScreenTest.kt:306` 断言「库里那 1 件是已经存好的，离开不会改动它们。」整句存在，同一测试的 `:305` 用 `onNodeWithText("会出现在「待归类」", substring = true).assertDoesNotExist()` 把"草稿"那半句钉死（断的是那句的**后半子串**，不是整句） | §16.3 的角度看它现在是**不可达分支**：不要因为"读不出 status"就补一次库查询，那是在给一条走不到的路写代码 |
| W1b | `alreadySaved` 那句（源码 `ReviewScreen.kt:351`，"库里那 N 件是**已经存好**的"，N 是插值）对**从待归类进来编辑的 DRAFT** 也说 | 那件离开后仍在待归类，而"已经存好"三个字容易被读成"已经办完"。句子的后半（"离开不会改动它们"）是真的 | ⚠️ 措辞可议，**不是假话**，所以没动它。要改的话：先写一条能到达的用例（DRAFT 进 → 改一个字 → ✕），再决定说"库里那 N 件已经在待归类或照片墙里，离开不会改动它们"这种长句还是分两档说 |
| W2 | `fallbackToDestructiveMigration(true)` + `DB_SCHEMA_VERSION` 恒为 1（`WheremyDatabase.kt`） | 真改 schema 会静默清空整机数据 | 钉住：验收期间冻结 `@Entity` 字段；解禁条件是 PRD ⭐FR-DB-01（真实 Migration）落地 |
| W3 | 频道静音但系统总开关开着时，仍算"已提醒"（`ReminderWorker.kt:78-79` 的 `canNotify` = 运行时权限 **且** 应用级 `areNotificationsEnabled()`；它**不看频道重要度**） | 用户把 `ReminderWorker.kt:39` 那个「到期提醒」频道调成静音 → 收不到，且这一批被标记成已说过 | 批次 V 拍板**保持现状**：只有一格通知，静音是用户自己的选择；测试钉住这个行为（`ReminderWorkerTest.kt:161` 起） |
| W4 | 搜索候选 `LIMIT 300` 截断在打分**之前**（§10.1） | 超过 300 件命中时靠后的不参与排序 | 个人量级够用；PRD ⛔ D1 记录了为什么现在不做 FTS5 |
| W5 | `GalleryViewModel.load()` 把 `effective` filters 写回 state（:81） | `onlyInbox` / `expireUntil` 读一次就"粘住"；`inboxMode` 每轮强制 true | 是刻意的（待归类页的定义），但改筛选逻辑时必须记得这一写回 |
| W6 | `DetailViewModel` 没有 seq 守卫（对比 `GalleryViewModel.kt:51,78`） | 快速切换两件时，理论上先发的读后到 | 单件读一次很快，未观察到；重建时可以照 Gallery 的写法补 |
| W7 | `ReviewScreen` 里 `state.notice` 与 `state.error` 两套提示并存 | 一句话可能出现在 snackbar 而不是弹框 | 语义分开了：notice = 可继续的操作结果，error = 需要用户知道失败。别合并 |
| W8 | `guard`（`ReviewViewModel.kt:176-183`）把 `t.message` **原样贴到界面**，而 §16.10 要求非自家中文异常只给固定中文 | 只有磁盘写满那一类能到达：`PhotoStore.write` 的 `compress` 抛 `IOException`（英文，形如 `write failed: ENOSPC (No space left on device)`），界面就把英文念给用户。AI 那一路不受影响（11 类已映射成中文，§13.2），解码失败也是自家的中文「照片解码失败」（常量在 `PhotoStore.kt:134`，抛出点 :54） | ⚠️ **偏差已知、未修**：`?: "处理照片失败"` 这个兜底只在 `message == null` 时生效，挡不住有 message 的英文异常。修法是在 `guard` 里照 `operate()` 的做法收一件事先——已知只有解码失败与磁盘满两类，给两句中文并把 `t` 打进 logcat；**别为了这一条把 `guard` 改成 catch-all 静默**，那会让"转圈停下来却说不出为什么"重新出现 |
| W9 | 别名那一格（`ReviewScreen.kt:238-248`）是录入页唯一一处**每按一键就重新切分、却没写 `singleLine` 也没设 `maxLines`** 的输入格：`ReviewScreen.kt:243` 的切分字符只有 `、` `，` `,` 三个，换行不在里面 | 粘进一段带换行的文字会得到**一个含换行的别名**，而这一格会自己折出第二行，看起来像两个别名。存库时 `ItemRepository.kt:340` 的 `csv()` 用逗号连接，`ReviewScreen.kt:239` 再按 `、` 拆回去，那一行始终是同一行；搜单个词照样命中（`ItemRepository.kt:211` 的 `LIKE`） | ⚠️ 记录不改。**别去扩 `ReviewScreen.kt:243` 的切分集合**——那等于宣布"换行也是分隔符"，而 `:246` 那句标签没有任何地方这么说。真要修就加 `singleLine = true`（`ReviewScreen.kt:182`、:218、:225、:234 那一套），并在真机先确认粘贴带换行的文本时它到底落成什么 |
| W10 | 空名字在两个地方是两套做法：`LocationPicker.kt:73` 的 `enabled = newName.isNotBlank()` 让按钮变灰，`TreeScreen.kt:326` 的确认按钮永远亮着，点了走 `if (name.isNotBlank())` 的 false 分支 | 同一个错误（名字没填）在两个弹窗里两种反馈：一处变灰，一处点了什么都没发生。后者最容易让人以为按钮坏了，于是再点一次 | ⚠️ 记录不改，`UI.md` §4.7 已标注。重做时取前一种，理由就写在 `LocationPicker.kt:72` 那行注释里 |
| W11 | 六处 `AsyncImage` 都没有 `placeholder` / `error`：`ItemRow.kt:42`、`GalleryScreen.kt:280`、`DetailScreen.kt:125`、`ReviewScreen.kt:144`、`TreeScreen.kt:222`、`TreeScreen.kt:256` | 照片文件丢失或解不开时那一格是**空的**，界面在原地不给任何说明。说到"读不出照片"的三句都不在那一格上，且各管一段：`WheremyRoot.kt:127` 管的是刚选回来/刚拍回来的字节是空的，而录入页开着时 `WheremyRoot.kt:113` 的 `say` 干脆不说这一句；`PhotoStore.kt:134` 从 `decode()` 的两处（:54 尺寸读不出、:60 解不开）抛出，经 `ReviewViewModel.kt:182` 原样贴成 error；`ReviewViewModel.kt:169` 只在"重新分析"那一条路上，说的是库里有行、文件没了。**没有任何一处管的是"正在显示的那一格空着"** | ⚠️ 记录不改：照片是应用内部文件，正常操作不会让它消失；导入缺图那一路由导入侧的账说话（§12）。要补占位图时别留一个灰框——`TreeScreen.kt:254` 的注释已经说过为什么 |


---

## 16. "界面每一句话都必须真的成立"的可检验判据 🔒（NFR-02）

`PRD.md` 的 NFR-02（"界面上每一句话都必须真的成立"）在本项目落成了一组**具体判据**。每一条都能对着代码问"是或不是"：

📌 下面的条目**只在这一节内部编号**：本文与 `REBUILD.md` 用 §16.N 指下面第 N 条（例如 §16.3 是"每档说法都要有能到达它的数据状态"）；`UI.md`/`PRD.md` 只引整节 §16，不引子条。加条目往末尾加，别在中途插——插入会让所有 §16.N 引用集体挪位。

1. **报"丢了什么"之前，先把"现在还剩什么"读一遍。**（批次 AU 的教训；`leftBehind`/`alreadySaved` 就是这次读）
2. **一个数字只有一个来源。** 计数要么来自 SQL，要么来自内存谓词，不许两个地方各数一遍再对齐。
3. **每档说法都要有能到达它的数据状态。** 到不了的分支是死码；两个分支共用一句话通常是漏了一档
   （`deleteWarningOf` 的四档、`failureText` 的 11 条、位置话的三句都是这么长出来的）。
4. **界面不许承诺一个按钮在它当前状态下做不到的事。** 于是：
   - 没有落库物品时**不给**「保存改动并留下」（§6.1、§6.2）
   - 照片没落库时**不给**「重新分析」（§6.6）
   - 摘掉最后一颗芯片时**不给**「不要这件」（§6.5）
5. **写操作成功之前不说"已经好了"。** `finished = true` 排在删除之后（§6.4）；
   `expireNotifiedAt` 排在通知发出之后（§11.4）；`已新建「X」` 只在 `add()` 成功分支里设。
6. **数字为 0 的不显示。** `leftBehind`/`alreadySaved`/`notStored` 都是 `if (… > 0)`；
   `bytes == 0` 不显示"0 字节"（§12.4）。
7. **文案里不出现密钥，连后几位都不要。** `AI_SAVED = "AI 配置已存好，下次选照片就按它走"`
   （`SettingsViewModel.kt:295-296` 注释）🗣
8. **不抄第二份真值。** 超时秒数不进文案（§13.2）；提前天数不进文案常量；
   `classify` 是唯一映射（§13.1）；`expireWindowEnd` 是唯一上界（§1.2）。
9. **"没做"和"做了但空"要分开。** `import` 的 `action` 返回 null 只有一种含义——连文件都没打开，
   既不算异常也不算成功（`SettingsViewModel.kt:249` 注释）；
   `shift` 返回 false 说的是"走到边界"，不是失败（§8.4）；
   `changeParent` 返回 false 说的是"本来就在这层"（§8.2）。
10. **异常给的是英文 `message` 时不许直接贴给用户。** `operate()` 只在
    `err is BackupFormatException` 时贴 `err.message`，其余贴这一种操作自己的 `fallback`
    中文（`SettingsViewModel.kt:263-266`，spec §8）。
    ⚠️ 这条**只有 `operate()` 做到了**：`guard()` 走的是 `t.message ?: 中文`
    （`ReviewViewModel.kt:182`），磁盘满时的英文 `IOException` 会原样上屏——记在 §15 W8，
    重做时按 `operate()` 的白名单写法补，而不是反过来把 `operate()` 放宽。


`SettingsViewModel.kt:248-251` 那四行 KDoc 原文，把这条 NFR 说清了，抄在这里当作写文案的模板：

```
导出与导入共用一个收场：忙的时候忽略新手势，失败给可读中文，成功报条数。
`action` 返回 null 只有一种含义——连文件都没打开，既不是异常也不算成功。
`fallback` 是每种操作各自的下一步提示：非 [BackupFormatException] 的异常消息是英文的
IOException/RuntimeException，直接贴出来对用户没有意义（spec 第 8 节）。
```

`operate` 的五种收场 🗣（`SettingsViewModel.kt:253-275`）：

| 收场 | 文案 |
|---|---|
| 忙 | （不响应这一次手势） |
| `BackupFormatException` | `${label}失败：` + 异常自己的中文 message |
| 其他异常 | `${label}失败：` + 该操作的 `fallback` |
| 返回 null | `${label}失败：打不开那个文件，换个位置再试` |
| 成功 | `${label}完成 · 位置 ${locations} 个 · 物品 ${items} 件 · 照片 ${photos} 张` + 仅 `bytes > 0` 时 ` · ${formatBytes(bytes)}` |

其余五个设置页常量（`SettingsViewModel.kt:292-300`）🗣：

```
READ_FAILED  = 读不到已存的设置，先按默认值显示
SAVE_FAILED  = 这个提前天数没存进去，下次打开设置页会看到真正生效的值
AI_SAVED     = AI 配置已存好，下次选照片就按它走
AI_SAVE_FAILED = AI 配置没存进去，多半是磁盘满了
RUN_FAILED   = 这一跑没能完成，多半是读库或读设置出了问题：重新打开设置页再按一次
```

`READ_FAILED` 那一句对应一条**行为规则**：DataStore 读失败时界面按默认值显示，
**但不许把默认值当作用户已存的值写回去**（`aiUsable`/`savedModel` 因此只讲**已存的那份**，
不讲输入框草稿——`SettingsViewModel.kt:34-44` KDoc）。

---

## 17. 规则冲突时怎么裁（五条真实裁决）

1. **"先落库"vs"别留垃圾"** → 先落库赢，垃圾由**退出时的空壳回收**处理（§3.1 + §6.4）。
   不反过来（不落库、等 AI 完事再开行），因为那会把"保存比 AI 快"这条主路径废掉。
2. **"诚实"vs"简单"** → 诚实赢。多写两档文案、多带一个 `leftBehind` 计数器、
   多查一次 `photoDeletionOf`，都比一句"照片和记录会一起删除"骗人便宜。
3. **"少一次多余通知"vs"别静默欠账"** → 别欠账赢：通知发不出去就**不回写**（§11.4），
   代价是每天白算一次窗口。
4. **"复用既有语义"vs"给按钮造捷径"** → 复用赢：「保存改动并留下」= `keepForLater`（§6.3），
   不为弹窗新造第二种落库方式。
5. **"接受更多数据"vs"严格校验"** → 分界在**能不能修**：缺文件放行（还能用，§12.3 #放行），
   空路径拒（这一行连该指哪儿都没说）；成环的位置交给外键去拒，不在校验里写第二套图算法（:209-210）。

---

## 18. 本文与测试的对应关系

每条 🔒 规则都该有一条会因为它而红的测试。改规则时的检查顺序：

| 规则 | 守门测试 |
|---|---|
| §1 五个谓词 | `ItemRepositoryTest`、`ReviewViewModelTest`、`GalleryViewModelTest`、`DatesTest` |
| §3 先落库与事务 | `ItemRepositoryTest`（照片行同生同死）、`CaptureFlowTest`（`onDraftSaved` 时机） |
| §4/§5 重排与合并 | `ReviewViewModelTest`（换一张、迟到结果）、`ItemRepositoryTest`（`applyAnalysis` 不覆盖 CONFIRMED） |
| §6 退出框 | `ReviewScreenTest`（两支部的逐字断言）、`ReviewViewModelTest`（计数、回收时机） |
| §8 位置规则 | `LocationRepositoryTest`（27 例）、`LocationTreeTest`（11 例） |
| §9 回收与删除话术 | `ItemRepositoryTest`、`UiFormatTest`、`LocationRepositoryTest`（位置照片侧） |
| §10 搜索 | `SearchRankerTest`、`SearchTextBuilderTest`、`ItemSearchDaoTest` |
| §11 到期 | `ReminderEngineTest`（16）、`ReminderWorkerTest`（12）、`DatesTest` |
| §12 备份 | `BackupRepositoryTest`（18）、`DatabaseSmokeTest` |
| §13 失败分类 | `ApiPhotoAnalyzerTest`（31）、`AnalysisParserTest`（15）、`UiFormatTest` |

完整的 39 类 / 481 例分布见 `PRD.md` §7；测试架构（哪些纯 JVM、哪些 Robolectric、
怎么落文件）见 `ARCHITECTURE.md` §14。
