# 本实现 ↔ 参照实现 差异台账（DELTA）

**这份文档是什么**：逐条记录"本实现（`D:\src\dsh\wheremy`）相比参照实现（`D:\src\qoder\wheremy`，commit `cf73f5b`）做到了什么、差在哪、好在哪。
**它不重复**：产品要什么（`PRD.md`）、规则怎么算（`RULES.md`）、界面怎么摆（`UI.md`）——这里只记**差异与证据**。

**怎么用**：

- 每完成一个切片就填一格；**"证据"列必须写得出具名测试类或真机动作**，不许只写"已完成"。
- 本实现列只有四种取值：`✅ 已复现` / `⭐ 更优` / `⬜ 未做` / `➖ 不做（附原因）`。
- 与 `PLAN.md` §10 的分工：阶段表回答"这一阶段做完了吗"，本文回答"具体做到什么程度、比参照好在哪"。
- 参照状态列是**冻结值**（来自 `PRD.md`，不随本实现变化）；本实现在收尾时才会回写 `PRD.md`（`PLAN.md` §6.4）。

---

## 1. 功能矩阵（87 条 FR；两条工程队列项另列在表后）

| FR | 标题 | 参照状态 | 本实现 | 更优点 / 差异 | 证据（测试类 / 真机动作） |
|---|---|---|---|---|---|
| `FR-SRC-01` | 从系统相册选一张照片录入 | ✅ | ✅ | 同参照；走系统选图器（`PickVisualMedia`），不需要任何存储权限 | 真机 2026-10-09：相册选图 → 回到录入页并显示新图（`device-picked-photo-review.png`）；`ReviewViewModelTest` |
| `FR-SRC-02` | 在应用内取景拍照 | ✅ | ✅ | 同参照；CameraX 1.5.3，取景屏三态 | 真机快门 → 确认页，竖拍照片方向正确（`device-shot-portrait-review.png`）；`CameraGateTest` / `CameraScreenTest` |
| `FR-SRC-03` | 拍照/选图二选一的来源弹窗 | ✅ | ✅ | 逐字同参照 | `CameraScreenTest`；真机两个入口各走一次（FAB 与位置照片） |
| `FR-SRC-04` | 换一张照片而不丢已填字段 | ✅ | ✅ | 同参照：换图只换图，字段一个字不动 | `ReviewViewModelTest`；真机「换一张」→ 相册 → 回录入页 |
| `FR-SRC-05` | 没相机的机器仍然能用 | ✅ | ✅ | 同参照：`uses-feature required=false`，取景屏说「这台机器没有可用的相机」，其余功能照用 | `CameraGateTest`（用 `shadowOf(packageManager).setSystemFeature` 模拟两种机器） |
| `FR-SRC-06` | 一张照片录多件物品 | ✅ | ✅ | 同参照：一张照片多件靠芯片排切换 | `ReviewViewModelTest`（多件槽位与「不要这件」）；真机多件路径随 P3 一并验 |
| `FR-SRC-07` | 「不要这件」 | ✅ | ✅ | 同参照 | `ReviewViewModelTest` |
| `FR-SRC-08` | 同一件物品的多角度（一张照片一件事、多张照片） | ⬜ | ✅ | **本实现开始做**（2026-10-13 用户拍板，`ADR-26`）：一件物品可挂多张、封面=第一张；**参照实现未做**（多角度只能多拍几张、彼此不关联）——所以这一条从「同参照未做」变成**本实现超出参照** | — |
| `FR-SRC-09` | 无照片的手填条目 | ⬜ | ✅ | **做完了**：来源弹窗多了「不拍照，手动填」（只在**新建**时出现——换照片时那条没有意义）；`newBlankDraft()` 只不写 `item_photo` 那一行，其余与拍照那条完全一样，所以确认入库/检索/删除/待归类**都不用为它另开一条路**；确认页给「没有照片（手动填的条目）」占位 +「加一张照片」按钮，状态行改成「没有照片，不跑识别——下面的字段自己填就行。」（**不再说**「找不到这张照片了」——手动填的条目不是照片丢了） | `BlankDraftTest` 5 例（草稿没有照片行也没有待分析照片 / 照样能确认入库并被搜到 / 删它的文案不牵扯照片 / 确认页打开它**不跑分析也不说照片丢了** / 有照片那条路照样会跑分析）；真机：FAB → 不拍照手动填 → 确认页三处文案正确 → 库里新建 `status=DRAFT`、**照片行数 0** → 待归类页显示成「未命名」格子 |
| `FR-AI-01` | 照片 → 结构化字段预填 | ✅ | ✅ | 本轮接上真模型；字段回填只写 AI 给得出的，位置只在空着时填 | 真机 2026-10-09 拍真照片 → 名称「按摩球」/颜色「紫色」/材质「橡胶」（`device-ai-analysis.png`）；`AiLogicTest` 18 例 |
| `FR-AI-02` | AI 不阻塞手填，回来后不覆盖用户改过的字段 | ✅ | ✅ | 分析中不挡表单；回填**不标记** `touched`，所以「你还有改动没保存」不会被 AI 触发 | `ReviewViewModelTest`；`ReviewUiState.canSave` 与状态行的 `Modifier.weight(1f)` |
| `FR-AI-03` | 位置建议只认已有节点 | ✅ | ✅ | 同参照，且**绝不虚构**：对不上就给 null | `AiLogicTest.都对不上就给 null，绝不虚构` / `只给末级词时取更浅的那个` |
| `FR-AI-04` | 提示词带上用户已有的位置清单 | ✅ | ✅ | 同参照：带清单那一版**整句替换** `container_guess` 问法；空清单时整段不出现、问法退回通用 | `AiLogicTest.提示词带位置清单时用替换问法` |
| `FR-AI-05` | 手动重新分析 | ✅ | ✅ | 同参照：按钮只在照片**已落库**时给（没落库时按下去不会有任何反应）；照片文件不在时说明下一步 | `ReviewUiState.canRetry`；真机「重新分析」；`ReviewViewModel.retryAnalysis` |
| `FR-AI-06` | AI 失败永远降级，不阻塞录入 | ✅ | ✅ | 同参照：11 类失败每条都带着下一步，没有一条只说「失败了」 | `AiLogicTest.失败文案十一条逐字且都带下一步` / `超时文案里不写秒数` |
| `FR-AI-07` | 未配置 key 时静默跳过 | ✅ | ✅ | 同参照：`NO_CONFIG` 一句话，字段留空，直接手填 | `AiLogicTest`（NO_CONFIG 文案）；`ReviewViewModelTest` 用「配置返回 null」的假分析器走全程 |
| `FR-AI-08` | 换厂商只改 base_url + model | ✅ | ✅ | 同参照：设置里两栏（接口地址 / 模型名），换厂商不动代码 | 真机当前配置 `https://api.deepseek.com` + `deepseek-flash`；`SettingsRepositoryTest` |
| `FR-AI-09` | AI 预填数量与到期日 | ⬜ | ⬜ | 同参照未做：数量与到期日仍要手填 | — |
| `FR-AI-10` | 对已有物品重跑分析（批量刷新） | ⬜ | ⬜ | 同参照未做：只能一件一件重跑 | — |
| `FR-AI-11` | 词表外的类别/季节：照单入库，但界面筛不到 | 🟡 | ✅ | **按 `RULES.md` §5.7 选了方案②**（保持入库、筛选与搜索都认自由值）：① `search_text` 纳入类别与季节（字段版本 2 → 3）；② 照片墙芯片云 = 生效词表 ∪ 库里真出现过的值（词表在前、自由值缀后）；③ 确认页芯片行带上「当前这个值」，词表外的也显示得出选中态。**顺带修掉一个真 bug（F45）**：`searchTextVersion` 的缺省值原本取当前 `FIELD_VERSION`，键不存在时闸门永远相等 → 字段版本升了对老库完全不生效 | `FreeValueFacetsTest` 6 例（自由值搜得到 / 筛得到 / 与预设值同筛 / 芯片云跟着数据长 / 没自由值时就是词表 / 字段版本是 3）；`SettingsRepositoryTest` 加 1 例钉住缺省值 0；真机：导入一个带「数码产品/冬季」的包 → 滑动芯片行出现「数码产品」→ 点它墙上只剩「移动硬盘」；重启后 `search_text` 变成「移动硬盘 数码产品 冬季 1tb，蓝色外壳」 |
| `FR-REV-01` | 进录入页的第一眼就有图 | ✅ | ✅ | 同参照：进这一屏第一眼就有图（照片落在录入那一刻，不等保存） | `ReviewViewModelTest`；真机 `device-picked-photo-review.png` |
| `FR-REV-02` | 字段次序（表单信息架构） | ✅ | ✅ | 次序同参照：名称* → 分类 → 季节 → 颜色 → 材质 → 品牌 → 别名 → 标签（`UI.md` §6.5） | `ReviewUiStateTest`；真机确认页逐项核对 |
| `FR-REV-03` | 「保存」= 转 CONFIRMED + 名字必填 | ✅ | ✅ | 同参照：名字必填（唯一带星号），保存转 `CONFIRMED` | `ItemRepositoryTest` / `ReviewViewModelTest` |
| `FR-REV-04` | 「稍后处理」= 退回 DRAFT，位置不动 | ✅ | ✅ | 同参照：退回 `DRAFT`，位置**不动**（待归类的谓词本来就是 `OR`） | `ItemRepositoryTest`；真机「稍后处理」→ 待归类里还在（F16） |
| `FR-REV-05` | 退出时说实话：到底会留下什么 | ✅ | ✅ | 同参照的四本账互不合并：`notStored` / `leftBehind` / `alreadySaved` / `unsaved` | `ReviewUiStateTest`（四本账与退出框两支） |
| `FR-REV-06` | 空壳不留在机上 | ✅ | ✅ | 同参照：空壳（没名、没动过、没保存）退出时回收 | `ReviewViewModelTest`；真机退出后待归类里不留空壳 |
| `FR-REV-07` | 中途崩溃/杀进程不丢物品 | ✅ | ✅ | 同参照，且比参照多一条证据：**磁盘库关库再打开**之后数据还在 | `DurabilityTest`（真·磁盘库 + 关库重开）；教训见 F18：别把「看起来没落库」当成事务坏了 |
| `FR-REV-08` | 多件时的「这一件还没保存」与跨件退出确认 | ✅ | ✅ | 同参照：多件时芯片上有圆点、跨件退出要确认 | `ReviewUiStateTest` / `ReviewViewModelTest` |
| `FR-LOC-01` | 位置树浏览（下钻 + 面包屑） | ✅ | ✅ | 同参照：下钻 + 面包屑，根层级不参与收纳 | `LocationTreeTest` / `TreeViewModelTest`；真机下钻与返回 |
| `FR-LOC-02` | 节点操作全套 | ✅ | ✅ | 同参照：新增/改名/移动/删除四件在同一个菜单里 | `LocationRepositoryTest` / `TreeViewModelTest`；真机逐项走过 |
| `FR-LOC-03` | 位置类型是软引导 | ✅ | ✅ | 同参照：类型是软引导——填什么都行，选单只给常见值 | `LocationRepositoryTest`；真机新增位置时改类型 |
| `FR-LOC-04` | 非空位置不许删 | ✅ | ✅ | 同参照：只有空位置能删，弹窗说清「子位置和物品需要先搬走」 | `LocationRepositoryTest`（删除门槛）；真机弹窗 |
| `FR-LOC-05` | 删除失败时给「把物品搬走」的快捷入口 | 🟡 | ✅ | **做完了**：删除弹窗一打开就数清「还有 N 件物品、M 个子位置」，并就地给两颗快捷入口——「把 N 件物品搬到…」「把 M 个子位置移到…」；选完目标**自动重试删除**（搬走的唯一目的就是删掉它，让用户再点一次是多余的）；子位置的目标清单排除自己与自己的子树（防环） | `TreeMoveOutTest` 4 例（弹窗即见现状 / 搬物品后自动删 / 两段都搬完才删 / 子位置不能进自己子树）；真机：删「抽屉」（2 件物品）→ 弹窗写「还有 2 件物品、0 个子位置」→「把 2 件物品搬到…」→ 选「家」→ 抽屉消失、电钻与护照挂到「家」下（连 `-wal` 一起拉库核对） |
| `FR-LOC-06` | 位置照片 | ✅ | ✅ | 同参照：位置也能有照片（`PhotoOwner.LOCATION`） | `TreeViewModelTest` / `PhotoStoreTest`；真机位置页缩略图 |
| `FR-LOC-07` | 防环 / 防重名 / 防自己挂自己 | ✅ | ✅ | 同参照：防环、防重名、防自己挂自己 | `LocationTreeTest`（环与孤儿父子）+ `LocationRepositoryTest` |
| `FR-LOC-08` | 位置候选随树变大要裁剪 | ⬜ | ⬜ | 同参照未做：路径数到几百条时要把候选裁剪或分组 | — |
| `FR-FND-01` | 一个搜索框覆盖全部可检索文本 | ✅ | ✅ | 同参照：一个 `search_text` 覆盖名称/品牌/颜色/材质/别名/标签/描述/备注 | `SearchTextBuilderTest`（`FIELD_VERSION = 2`）+ `SearchRankerTest` |
| `FR-FND-02` | 结果以照片网格呈现 | ✅ | ✅ | 同参照：结果就是照片网格（自适应列数，矮窗口横排） | `GalleryViewModelTest`；真机竖/横两张截图 |
| `FR-FND-03` | 筛选条件：类别 / 季节 / 标签 / 只有待归类 / 快到期-已过期 | ✅ | ✅ | 同参照：类别/季节/标签/只有待归类/快到期-已过期 | `ItemRepositoryTest` / `GalleryViewModelTest`；真机逐个芯片点过 |
| `FR-FND-04` | 待归类是一个 tab，不是一个表 | ✅ | ✅ | 同参照：待归类是 tab，不是另一张表（谓词 `locationId IS NULL OR status='DRAFT'`） | `GalleryViewModelTest`；真机；两条教训 F16（确认≠已归位）与 F19（只改标题不改谓词） |
| `FR-FND-05` | 空态要说清"是没东西还是被筛空了" | ✅ | ✅ | 同参照：空态说清是「一件都还没有」还是「被筛空了」 | `GalleryViewModelTest`；真机两种空态各看一次 |
| `FR-FND-06` | 点搜索结果能看到完整路径并跳过去 | ✅ | ✅ | 同参照：点结果能看到完整路径，并能跳到那个位置 | `DetailViewModelTest`；真机从搜索进详情再跳位置 |
| `FR-FND-07` | 一次查询上限 300 条，超出静默截断 | 🟡 | ✅ | **补上了告知**：撞到上限时照片墙上多一行「只显示了最近更新的 300 条，可能还有更多：再加一个词，或者用筛选取个范围」。分页没做（也不需要：这个量级下缩小范围更快） | `GalleryViewModelTest.搜索撞到三百条上限时界面要说出来`（301 条撞上限 vs 只搜到一条不报）；`ItemRepository.SEARCH_LIMIT` 与 `Daos.kt` 的 `LIMIT 300` 同值 |
| `FR-FND-08` | 多标签 / 多类别组合筛选 | ⬜ | ✅ | **同类内「或」、跨类「与」**：`Filters` 三个字段改成集合（`categories` / `seasons` / `tags`），标签在 SQL 层取「带任一标签的物品 id」再并集，类别与季节走内存谓词；芯片改成多选（再点一次只去掉那一个）；折叠时标签写「季节：冬、秋」「#冬装 #自检种子」 | `FilterCombinationTest` 6 例（同类或 / 跨类与 / 两标签或 / 标签+类别与 / 季节多选 / 空集合不筛 / 与查询词同用）；`GalleryViewModelTest` 加 1 例钉住连点与再点；真机：点「衣物」只剩外套，再点「数码」变成「外套 + 过期电池」（并集） |
| `FR-FND-09` | 「最近动过」排序 | ⬜ | ✅ | **四档排序**：默认 / 最近动过 / 最近更新 / 名称。🔒 **默认那一档一个字都没改**（空查询仍按插入序 `RULES.md` §10.1、有查询仍按相关度 `SearchRanker`），其余三档是用户显式选的、选了就以排序为准；只改顺序不改集合；折叠起来时标签上写着「排序：X」 | `SortOrderTest` 6 例（三种排序 + **两条默认行为的防回归** + 排序与筛选同用）；真机：展开面板点「排序：名称」→ 墙上顺序按字符串序重排，折叠标签变成「更多筛选 · 排序：名称」 |
| `FR-OPR-01` | 详情页 | ✅ | ✅ | 同参照：详情页给完整路径、照片、全部字段 | `DetailViewModelTest`；真机 |
| `FR-OPR-02` | 编辑已有物品 | ✅ | ✅ | 同参照：详情页「编辑」回到确认页，字段齐全 | `ReviewViewModelTest`；真机（F31 修的就是这条路：页面开了却从不 `start`） |
| `FR-OPR-03` | 搬到 / 从详情页搬走 / 卡片菜单搬移 | ✅ | ✅ | 同参照：详情页搬走与卡片菜单搬移是同一段逻辑 | `ItemRepositoryTest`（`move` 与 `lastMovedAt` 口径）；真机两处各走一次 |
| `FR-OPR-04` | 删除有二次确认，且文案按照片共用情况分档 | ✅ | ✅ | 同参照：按照片共用情况分四档说人话，二次确认 | `UiFormatTest.deleteWarningOf`；真机删除确认 |
| `FR-OPR-05` | 手工搬动即视为确认（但有名字门槛） | ✅ | ✅ | 同参照：手工搬动即视为确认，但有名字门槛（空名不给搬） | `ItemRepositoryTest` |
| `FR-OPR-06` | 批量操作（多选后搬移/删除/改类别） | ⬜ | ✅ | **做完了**：长按卡片进多选（长按的那张先选上），点卡片切换；贴底工具条「已选 N 件」+「搬到…」「改类别」「删除」「取消」；三件事都**逐件复用单件那条路**（`moveAll` / `deleteAll` / `setCategoryAll`），不另写一套 SQL——单件的口径（搬动时间戳、按引用计数删文件）已经在测试里钉死了；批量删除的文案把**两类照片分开数**（会跟着没的按文件张数、会留着的按记录件数）；搬完/删完/改完都给一句带件数的结果 | `BatchOperationsTest` 8 例（仓库三方法 + 多选切换 + 删除文案两类分开数 + 共用照片留/删 + 结果句）；真机：长按 → 已选 2 件 → 改类别「衣物」（库里 电钻 工具→衣物）、搬到「家/抽屉」（库里 深灰羽绒外套 衣柜→抽屉）、删除确认框文案正确（未真删，避免动用户数据） |
| `FR-EXP-01` | 每件物品可设一个到期日 + 种类 | ✅ | ✅ | 同参照：每件一个到期日 + 种类（保质期/证件/自定义） | `ItemRepositoryTest`；真机确认页设到期日 |
| `FR-EXP-02` | 每天检查一次，一次只发一条聚合通知 | ✅ | ✅ | 同参照：每天一次（`1, DAYS` + `KEEP`），**一次只发一条聚合通知** | `ReminderRunnerTest`；真机收到通知 |
| `FR-EXP-03` | 提前 N 天可配（0–365，含"当天"） | ✅ | ✅ | 同参照：0–365 天，含「当天」；改完立刻重排 | `SettingsRepositoryTest` / `ReminderEngineTest`；真机改成 30 天、杀掉重开还在 |
| `FR-EXP-04` | 一件物品一个截止日期最多被说两次 | ✅ | ✅ | 同参照：`expiringBy` 三分支把「已过期且说过了」排除掉，因此同一截止日最多说两次 | `ReminderEngineTest`（三分支逐条）；真机调试按钮连按两次不回写 |
| `FR-EXP-05` | 通知点进去就是筛好的照片墙 | ✅ | ✅ | 同参照：点通知进的是**筛好的**照片墙，不是首页 | `MainActivity` 读 `ReminderNotifier.EXTRA_ENTRY`；真机（F25：已运行时走 `onNewIntent`） |
| `FR-EXP-06` | 通知权限没给就降级，不崩溃、不谎报 | ✅ | ✅ | 同参照：权限没给就只跳系统页，不就地申请、不谎报已发 | 真机设置页那句话与按钮各走一次 |
| `FR-EXP-07` | 设置页有个调试入口能立刻跑一次 | ✅ | ✅ | 同参照：设置页 debug 区「立刻跑一次」；**写回是有意的**（与真实发送共用一段逻辑） | `ReminderRunnerTest`；真机连按两次核过 |
| `FR-EXP-08` | 应用内的到期标记 | ✅ | ✅ | 同参照：卡片上的到期行 + 详情页的到期日 | `UiFormatTest`；真机照片墙卡片 |
| `FR-EXP-09` | 其他类提醒（"很久没动过"、借出归还） | ⬜ | ⬜ | 同参照未做：借出归还、很久没动过这类提醒 | — |
| `FR-BAK-01` | 手动导出 zip | ✅ | ✅ | 同参照：手动导出 zip（四张表一个事务里读，同一路径只写一次，读不到的图跳过） | `BackupRepositoryTest`；真机导出到文件并拉到电脑上看过条目 |
| `FR-BAK-02` | 导入两种模式：合并 / 覆盖 | ✅ | ✅ | 同参照：合并（按主键跳过）与覆盖（先清空再重建，并删本机多余照片） | `BackupRepositoryTest`；真机两种模式各走一次（删一件 → 覆盖导入 → 那一件回来） |
| `FR-BAK-03` | 导入失败时库整体回滚；照片文件层刻意不回滚 | 🟡 | ✅ | **是设计不是漏洞，已钉住**：① 库四张表一个事务，校验失败换成一句中文；② 搬图在事务成功**之后**，单张失败被 `runCatching` 吞掉（库是唯一真相源，字节仍躺在用户的 zip 里）；③ 覆盖模式删多余照片也不在事务里——所以**被拒的包一张照片都不动**。完成那句话数的是**入库行数**，不代表同样多个文件真搬进去了 | `BackupAsymmetryTest` 2 例（搬图失败仍成功入库 / 被拒的包不动本机照片）；原有 `BackupRepositoryTest` 已覆盖库回滚与覆盖模式删文件 |
| `FR-BAK-04` | 备份包不含 API key，不含任何设置 | ✅ | ✅ | 同参照：导出通道的构造参数里**没有** `SettingsRepository`，key 物理上进不去 | `DataModule.backupRepository` 的构造参数；真机包内条目只有 `manifest.json` / `data.json` / `photos/**` |
| `FR-BAK-05` | 拒收"看起来像备份包但其实不是"的输入 | ✅ | ✅ | 同参照：不像备份包的输入直接拒，且是**中文人话**（`BackupFormatException.message`） | `BackupRepositoryTest` |
| `FR-BAK-06` | 引用完整性：四条拒，一条放行 | ✅ | ✅ | 同参照：四条拒 + 一条放行；孤儿/环让外键拒掉整个包（`parentsFirst`） | `BackupRepositoryTest`；真机（包里有对不上的引用时那句「备份里有对不上的引用，已取消导入，原来的数据没有动」） |
| `FR-BAK-07` | 备份格式与 Room schema 解耦 | ✅ | ✅ | 同参照：`schemaVersion` 是导入侧**唯一**的兼容闸门，只查上界不查下界 | `BackupRepositoryTest`（更高版本直接拒，本机数据一条不动） |
| `FR-BAK-08` | 定时自动导出 | ⬜ | ✅ | **每天一份**：设置页开关 + 「选择保存位置」（SAF `OpenDocumentTree`，**当场持久化授权**，不然重启后打不开）+ 「立刻导出一份」；WorkManager 周期任务 1 天、唯一名 `wheremy-auto-export`、`KEEP` 不重复排队，**开关在 worker 里判**（这样设置里开关一下立刻生效，也不用管取消）；文件名 `wheremy-auto-<本地日期>.zip`，只留最近 7 份（认前缀，绝不碰同目录里别的文件）；**成功失败都写进设置页那一行** | `AutoExporterTest` 5 例（没配落点要说明 / 名字带日期且是 zip / 写失败要说原因 / **导出的包能原样导回来** / 常量一致）；真机：选 `/sdcard/wheremy-backup` → 「立刻导出一份」→ `wheremy-auto-2026-10-10.zip`（43 KB）真躺在那个目录里，设置页显示「上次（10月10日 10:33）：已导出 …」；WorkManager 库里 `AutoExportWorker` 周期 86,400,000 ms（连 `-wal` 拉库核对） |
| `FR-BAK-09` | 备份加密 | ⬜ | ✅ | **本实现开始做**（2026-10-13 用户拍板，`ADR-27`）：全包加密（AES-256-GCM + PBKDF2，格式 v2），口令自选；**参照实现未做** | — |
| `FR-CFG-01` | 设置页三节：到期提醒 / AI 分析照片 / 备份 | ✅ | ✅ | 同参照：三节（到期提醒 / AI 分析照片 / 备份）+ 开发期工具 | 真机逐节走过 |
| `FR-CFG-02` | AI 开关 + 三个字段（base_url / model / api key） | ✅ | ✅ | 同参照：AI 开关 + 接口地址 / 模型名 / key 三个字段 | 真机（设备上的偏好文件里读到 `base_url=https://api.deepseek.com`、`model=deepseek-flash`） |
| `FR-CFG-03` | key 只存 DataStore | ✅ | ✅ | 同参照：key 只在 DataStore（`files/datastore/wheremy_settings.preferences_pb`），不进备份包、不进留痕、不进日志 | 真机；`BackupRepositoryTest`（包内无设置） |
| `FR-CFG-04` | 提醒提前天数设置 | ✅ | ✅ | 同参照：提前天数八颗预设 + 0–365 手填 | `SettingsRepositoryTest`；真机 |
| `FR-CFG-07` | 设置读不出来时按默认值显示，并说出"这不是存下来的值" | ✅ | ✅ | 同参照：设置读不出来时按默认值显示，并且**说出**这不是存下来的值 | `SettingsRepositoryTest`（读写失败路径） |
| `FR-CFG-05` | 主题/配色设置项 | ⛔ | ⛔ | 同参照：**不提供**主题/配色设置项——跟随系统（含深色与动态色），只有 chrome 用动态色 | `WheremyTheme`；真机深/浅截图与设计系统基线图 |
| `FR-CFG-06` | 类别/季节/位置类型的自定义管理 | ⬜ | ✅ | **四套词表都进了 DataStore**（类别/季节/到期类型/位置类型）：空 = 用代码里的预设，所以「没改过」和「改回默认」是同一件事；写入侧清洗（去空白/丢空项/去重/封顶 24 项）；设置页新增「词表」一节。🔒 **改词表不动已有数据**（`RULES.md` §5.7 的白名单本来就不校验），🔗 **提示词跟着用户那份走**——不跟的话模型会回一堆词表外的值，芯片行显示不出选中态 | `SettingsRepositoryTest` 新增 5 例（默认空/清洗/封顶/清空回预设/恢复默认）；`AiLogicTest.用户改过词表时提示词用用户那份`；真机：设成 Anime/Model 后照片墙与确认页芯片都跟着变，**留痕里发出去的提示词是「从这 2 个里选一个：Anime Model」**，复位后回到衣物/数码 |
| `FR-TRC-01` | 每次 AI 尝试落一条留痕：提示词原文 + 回应原文 | ✅ | ✅ | 同参照：一次尝试一条，重试就是两条；只记提示词原文与回应原文 | `AiLogicTest.留痕上限是十条`；真机 `files/ai-trace/…-NOTHING_FOUND.txt`（就是靠它判出 F37 是模型自己说的） |
| `FR-TRC-02` | 留痕里绝不包含 key 与照片字节 | ✅ | ✅ | 同参照：痕里没有 key、没有那张 base64 的图（`record()` 只收提示词与回应两个字符串） | `AnalysisTrace.record` 的签名；真机留痕文件逐字看过 |
| `FR-TRC-03` | 留痕目录不在 photos 下，因此跟着备份出门不了 | ✅ | ✅ | 比参照更硬：目录写死在 `files/ai-trace`（**不在 `files/photos` 下**），备份打包只走 photos，所以物理上出不了门 | `AiLogicTest.留痕上限是十条`（断言目录名不以 photos 开头）；真机备份包条目里没有它 |
| `FR-TRC-04` | 累计调用次数/花费展示 | ⬜ | ✅ | 设置页 AI 小节多一行「累计调用 N 次（含重试）· 服务端报的 tokens 合计 M · 费用按你的账单为准（本机不折算）」。🔒 **故意不折算成钱**：各家的价格表会变，本机算出来的金额迟早是错的，说错钱比不说更糟 | `SettingsRepositoryTest.每次尝试都记一笔，重试也算`（含重试、接口不报 tokens 时次数照涨）；真机设置页读到「累计调用 1 次 · tokens 合计 929」 |
| `FR-TRC-05` | 留痕里加耗时 | 🟡 | ✅ | 痕里每条多了 `elapsedMs=`：排「超时 vs 模型慢」全靠它 | `AnalysisTraceTest.一次尝试一条，四条信息都在`；真机留痕 `elapsedMs=832`（那次模型 0.8 秒就回了） |
| `FR-PRM-01` | 权限清单最小 | ✅ | ✅ | 比参照少一条也够：自己声明 `POST_NOTIFICATIONS` + `CAMERA` + `INTERNET`（AI 请求，F36），**无任何存储权限**（选图走系统选图器） | `aapt2 dump badging`（debug 与 release 两个 APK）；真机系统「应用信息 → 权限」 |
| `FR-PRM-02` | 相机权限：进屏自动申请一次 + 常驻出口 | 🟡 | ✅ | 比参照多做半步：进屏**自动申请一次** + 常驻「打开相机权限」出口，两件事都在 | `CameraGateTest`；真机自动权限框 + 拒绝后的取景屏 |
| `FR-PRM-03` | 通知权限（Android 13+）被拒时降级 | ✅ | ✅ | 同参照：Android 13+ 通知权限被拒就降级，只引导去系统页 | 真机设置页与调试按钮 |
| `FR-PRM-04` | 竖屏锁定 | ✅ | ✅ | **不锁竖屏**（参照是 ⛔ 锁竖屏）：窄窗口底栏 / 宽 ≥600dp 左侧导航轨 / 矮窗口（高 <480dp）照片墙横排格子 | `WheremyRoot` 的 `BoxWithConstraints` 分级；真机竖/横两张截图（F21 治好的那条） |
| `FR-PRM-05` | 无障碍 | 🟡 | ✅ | **内容描述 + ATF 深扫都做完了**：格子图给物品名（空名给「未命名物品」）、详情照片给「物品照片」、位置缩略图给「<位置名>的位置照片」；**ATF（Android Accessibility Test Framework）深扫进自动化**——取景屏三态、照片墙（有物品）、确认页都在 `CheckLevel.Error` 档判红，**不需要设备、进得了 CI**；扫出来的真问题当场修掉（见 F39） | `AccessibilityScanTest`（2 例）/ `GalleryAccessibilityTest` / `ReviewAccessibilityTest`；真机 `uiautomator dump` 核过 `content-desc`。⚠️ 剩下**只有一条必须人工**：TalkBack 的**焦点次序**走查（ATF 那套规则不覆盖次序） |
| `FR-PRM-06` | 多语言 | ⬜ | ⬜ | 同参照未做：多语言（现在中文硬编码在源码里，按 ADR-16 不抽 `strings.xml`） | — |



**只在队列里的两条工程项**（不在 §3 的 FR 编号里）：

| 项 | 参照状态 | 本实现 | 说明 | 证据 |
|---|---|---|---|---|
| ⭐FR-DB-01 真实 Room Migration | ⬜ 发布前必做 | ⬜ | 本实现**从第一天就不引入破坏性迁移**，所以这条天然成立；但"升级路径"仍要写 migration + 测试 | |
| ⭐FR-RLS-01 release 签名包 | ⬜ 未做 | ✅ **已完成（2026-10-10，用户自己做）** | 用户生成 keystore、仓库外放 `keystore.properties`；`signingReport` 为 `Config: release`，真机装的是正式签名包（`KEYSTORE.md`） | |

---

## 2. 细节清单（参照实现"记录不改"的偏差 + 文案与边界）

这一节是本实现**最容易赢**的地方：参照实现在 `RULES.md` §15 里明确记为"照实记录、未必正确"的 W1–W11，我们要逐条给出"成立"的实现。

| 编号 | 参照现状（出处） | 本实现目标 | 证据 |
|---|---|---|---|
| W1 | 支二退出框那句"状态还是草稿"（已复核为不可达分支，已闭合） | 保持"不可达就不写代码"的处理，并用一条测试钉住 | |
| W1b | `alreadySaved` 那句对 DRAFT 也说"已经存好"，措辞可议 | 改成两档说法（"已经在库里" / "已经存好"），并补一条 DRAFT 进出的用例 | |
| W2 | `fallbackToDestructiveMigration(true)` + schema 冻结 | 从第一天写真实 Migration；`DB_SCHEMA_VERSION` 从 1 起就有升级路径 | |
| W3 | 频道静音仍算"已提醒" | 沿用（用户拍板）；测试钉住 | |
| W4 | 搜索 `LIMIT 300` 截断在打分之前且界面不说 | 保留上限但**界面说清**（"只看前 300 条，再补几个条件"） | |
| W5 | `GalleryViewModel.load()` 把 effective filters 写回 | 保持语义，但把"写回"写成显式注释 + 一条测试 | |
| W6 | `DetailViewModel` 无 seq 守卫 | 从第一天加 seq 守卫 | |
| W7 | `notice` 与 `error` 并存无优先级 | 明确优先级（error 压 notice）并写测试 | |
| W8 | 异常 `message`（英文）原样上屏 | 白名单映射，**界面零英文异常**；补一条"磁盘满"的用例 | |
| W9 | 别名输入框无 `singleLine`，换行会成一个别名 | `singleLine = true` + 换行按分隔符处理，并写测试 | |
| W10 | "名字没填"两个弹窗两种反馈 | 统一为"按钮禁用"（`RULES.md` §15 W10 自己给的建议） | |
| W11 | 六处 `AsyncImage` 无 `placeholder`/`error`，图丢了那一格空白 | 统一组件 `ItemImage`：有"图丢了"的明确占位与一句文案 | |
| 文案 | 逐字文案两处抄本（`RULES.md`/`UI.md`）易漂 | 本实现用 `strings.xml` 资源（T1）+ 一个"文案对齐"测试：资源值与文档逐字表比对 | |
| 边界 | — | 空库 / 筛空 / 超长名字 / 无照片 / 文件丢失 / 超大照片 / 坏备份包 / 断网 / 额度耗尽，各有一条用例 | |

---

## 3. 界面对比（截图并排）

**采集规矩**（`PLAN.md` §6.3）：同一台真机、同一尺寸/字体缩放/深浅色；每次记两行环境快照；**参照包的截图需用户同意**，产物不进 git、不贴进正文。

**环境快照（2026-10-08 首次采集）**：Xiaomi 13 / HyperOS `V816`，`1080×2400 @440dpi`，序列号 `9aa4b43e`；字体缩放与深浅色取系统默认（浅色）；自动旋转开着——横屏那张是**顺手抓的额外证据**。

**P0/P1 现状**：本实现有骨架屏与真机自检可对比，**产品屏还没有**（照片墙/确认页等要 P2 起才出现）。参照实现已于 2026-10-08 在**同一台真机**上装好并采到四张空态截图（见下），两边的截图规范就此对齐。

**参照实现是怎么拿到的**（2026-10-08）：源码**只读**复制到 `.reference/wheremy`（gitignore）→ 在副本里用 PATH 上的 Gradle 8.14.3 构建（参照无 wrapper）→ `BUILD SUCCESSFUL`，APK 18,960,302 字节 → 装成 `com.wheremy.app`（与本实现的 `.dsh` 包同机共存）。全过程与账在 `artifacts/REF-build-report.md`；**原目录零改动**（`git status --porcelain` 空 + 20:24 之后无任何文件被写）。

| 屏 | 参照截图 | 本实现截图 | 结论（谁更好 / 差在哪 / 改不改） |
|---|---|---|---|
| **骨架 Home**（P0，非产品屏） | 无此屏 | `artifacts/screenshots/P0/device-home-portrait-light.png`、`device-home-landscape-light.png` | 仅证明链路可跑；横屏不锁（参照锁竖屏），布局未自适应，留待 P6 |
| **设计系统画廊**（P0，开发期样本） | 无此屏 | `device-gallery-portrait-light.png`、`device-gallery-landscape-light.png` + Roborazzi `design-gallery-{light,dark}.png` | token/组件已可视化，可作后续每屏的样式基准 |
| **数据层自检**（P1，开发期脚手架） | 无此屏 | `artifacts/screenshots/P1/device-selfcheck-portrait.png` | P1 的 7 项真机自检结果上屏，替代"口头说跑过了" |
| **P2 闭环自检**（P2，开发期脚手架） | 无此屏 | `artifacts/screenshots/P2/device-selfcheck-p2.png` | 10 项真机自检（P2 六项 + P1 四项）；用**合成照片**跑，不碰相册里的私人照片 |
| **照片墙（有数据）** | 参照只有空态可拍（无数据） | `artifacts/screenshots/P3/device-wall-portrait.png`、`device-wall-filtered.png` | 本实现：两列自适应网格、名字 / 位置行（没位置写红字「待归类」）/ 到期行、⋮ 菜单、FAB、底栏 |
| **待归类** | `REF/02-pending-empty.png`（空态逐字） | `device-inbox-portrait.png` | 同一个屏幕的 `inboxMode`；本实现已能列出"没位置或还是草稿"的件 |
| **删除框** | 未采（参照需要造数据） | `device-delete-dialog.png` | 逐字对上 `RULES.md` §9.3 的第四档：「照片和记录会一起删除，无法恢复。」 |
| **设置页三节** | `REF/06-settings.png`（参照的设置页） | `artifacts/screenshots/P5/device-settings-reminder.png`、`device-settings-backup.png` | 到期提醒（当前值行/±/八颗预设/每天一次说明/调试按钮/通知问题行）、AI 分析照片（开关 + 三态加粗 + 三字段 + 隐私句）、备份（三颗按钮 + 四句说明）逐字对齐 |
| **备份闭环** | 未采（参照需要人操作） | `device-overwrite-import.png`、`device-after-overwrite-import.png` | 本实现真机跑通"导出 → 删一件 → 覆盖导入 → 那一件回来"；结果句「覆盖导入完成 · 位置 3 个 · 物品 5 件 · 照片 5 张」 |
| **照片墙 · 横屏** | — | `device-gallery-populated-portrait.png`（横屏实拍） | **本实现的问题**：横屏只露图片、文字行被挤出可视区、FAB 压图 → 已入 P6 自适应清单（F21） |
| 照片墙空态 | `artifacts/screenshots/REF/01-home-empty.png`（dump：`还没有物品。点右下角的按钮选第一张照片。`） | P2 才有 | 待比 |
| 待归类空态 | `REF/02-pending-empty.png`（`没有待归类的物品，全都放好了。`） | P2 才有 | 待比 |
| 设置页（三节） | `REF/03-settings.png`（`到期提醒` 提前 3 天/当天…一年、`还缺 API key…`、`接口地址 base_url`） | P5 才有 | 待比 |
| 位置页（空态 / 根 / 下钻） | `REF/04-locations.png`（`还没有位置。点右上角「新建」建第一个，例如「家」「主卧」「衣柜」。`） | `artifacts/screenshots/P4/device-tree-root.png`、`device-tree-home.png`、`device-tree-menu.png` | 本实现根层空态与参照**逐字相同**；有数据时的形态（`家` / `房间 · 0 件（含子位置 3 件）` / ▸ / ⋮）与下钻后（面包屑即标题、无图时只给「给这个位置选一张照片」、边界节点只有「下移」）均已真机验证 |
| 照片墙（浅色 · 手机竖屏） | | | |
| 照片墙（深色 · 手机竖屏） | | | |
| 照片墙（平板/横屏，参照无此形态） | — | | |
| 确认页（单件 / 多件） | | | |
| 详情页 | | | |
| 取景屏 | | | |
| 空态三种 / 失败态 | | | |

**逐项判据**（每屏都按这五条打分）：信息层次、间距与留白、可读性（长名字/长路径/字缩放 200%）、状态完整度（加载/空/错误/无图）、无障碍（语义标签与焦点次序）。

---

## 4. 工程对比

| 项 | 参照实现 | 本实现目标 | 证据 |
|---|---|---|---|
| 构建入口 | PATH 上的 gradle（8.14.3），无 wrapper | **自带 wrapper（9.8.1）** ✅ 已落地 | 首个提交 `4628a9b`；AGP 9 要求 Gradle ≥ 9.1，wrapper 是必需项（发现 F5） |
| CI | 无 | GitHub Actions：`spotlessCheck` + `testDebugUnitTest`（⏳ 文件已写，**未在真 GitHub 跑过**） | `.github/workflows/ci.yml` |
| 静态检查 | 无 | Spotless 8.10.3（内含 ktlint 1.4.0）✅ 已落地；**不引 detekt** | `:app:spotlessCheck` 通过；Compose 函数命名用 `editorConfigOverride` 放行（发现 F3） |
| 截图回归 | 无 | Roborazzi 1.76.0 ✅ 已落地（当前 2 张：浅/深色画廊基线） | `app/build/outputs/roborazzi/design-gallery-{light,dark}.png`；`recordRoborazziDebug` / `verifyRoborazziDebug` |
| 测试分层 | JVM + Robolectric 39 类 / 481 例 | 分层相同；**P0 基线 4 类 / 6 例 → 现在 42 类 / 261 例**（2026-10-10） | `BuildSmokeTest`、`DatabaseSmokeTest`、`DesignGalleryTest`、`DesignGalleryScreenshotTest` |
| 编译目标 | compileSdk 36 / targetSdk 36 | **compileSdk 37** / targetSdk 36 ✅（Compose 1.12.1 的硬要求，发现 F1） | `app/build.gradle.kts` |
| 数据 | Room 2.8.5（`androidx.room`） | **Room 3.0.3（`androidx.room3`）** ✅ 四表/三 DAO 已落地 | `DatabaseSmokeTest` 等 8 类 32 例；**导出的 schema 与参照逐字段一致**（F11）。⚠️ 3.x 把 `@TypeConverter/@TypeConverters` 改名为 `@ColumnTypeConverter/@ColumnTypeConverters`（F10） |
| 照片管线 | `PhotoStore` + `PhotoReclaim`（1600/256/1024 三档） | 同档位同常量 ✅ 已落地 + 公开了三档常量供测试断言 | `PhotoStoreTest` 6 例；真机自检 `EXIF 800×400 → 400×800` ✅ |
| 迁移策略 | `fallbackToDestructiveMigration(true)`（记 ⭐FR-DB-01 发布前必做） | **不写破坏性迁移** ✅（W2 提前修掉）：版本一升就必须交 `Migration` + 迁移测试 | `WheremyDatabase.kt` 的类注释写明了这条纪律 |
| 导航 | 手写外壳 + `rememberSaveable` | **Navigation 3 1.2.0** ✅ 已落地（`NavDisplay` + `entryProvider` + 返回栈） | `ui/WheremyRoot.kt` |
| DI | 手写 `AppContainer` | **Hilt 2.60.1 + KSP** ✅ 已落地（`@HiltAndroidApp` / `@HiltViewModel` / `@Provides`） | `WheremyApp.kt`、`di/AppModule.kt` |
| 无障碍 | 🟡 芯片组与装饰件缺 description、焦点次序未梳理 | 全交互元素有语义、触控 ≥48dp、TalkBack 走查、200% 字缩放不破版 | |
| 自适应布局 | 锁竖屏（⛔FR-PRM-04） | 手机竖/横 + 平板：窄窗口 `NavigationBar`、宽窗口 `NavigationRail`、矮窗口横排格子 | ✅ 已实现（`WheremyRoot` 的 `BoxWithConstraints` 分级；真机横屏见 `artifacts/screenshots/P6/device-adaptive-landscape.png`）。⚠️ 用 M3 的 `NavigationBar`/`NavigationRail` 手搭，没有引 `NavigationSuiteScaffold`——两者都是官方组件，前者不必多一个依赖 |
| 配色 | 固定青绿+琥珀；不做动态取色（⛔D4） | chrome 允许动态色，**照片墙保持中性固定色**（需 ADR）；P0 已先把 token 集中到 `ui/theme/Theme.kt` | 见 PLAN §2.3 |
| 文案资源 | 硬编码 Kotlin | `strings.xml`（T1）+ 文案对齐测试 | |
| 备份格式 | 自造 zip（四表 JSON） | 保持同一格式（**旧包必须还能导入**），只加不改 | |

---

## 5. 防漂移规则

1. **只增不改**：已经写下的结论要改，必须带"为什么改"的一句话，并在 `docs/CHANGELOG.md` 记一行；
2. **⭐ 必须给理由**：每条"更优"要写清"比参照好在哪"；写不出就降级为 `✅ 已复现`；
3. **证据列不许空**：填了状态就必须能指出测试类或真机动作；
4. **不重复正文**：这里只写差异；规则细节一律指回 `RULES.md`/`UI.md`。
