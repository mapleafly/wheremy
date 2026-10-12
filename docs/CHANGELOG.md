# 文档集变更记录（CHANGELOG）

**这是本工作区唯一的变更日志**：软件的阶段进展（P0…P6、每批功能与修复）与文档集的改动都记在这里，按日期倒序、每批一节。⚠️ 2026-10-10 更正：本文件此前写着「软件版本的发布记录在`docs/CHANGELOG.md`」——**那个文件从来不存在**，工作区里只有这一份；`RELEASE.md` §8.1 与本文的指路已按事实改掉。
每一条都写清：**谁、改了什么、为什么、代价**。

---

## 2026-10-12 · 真机人工清单七条 **7/7 通过**（G5 签收）

用户本人按 `templates/device-acceptance.md` 重走了一遍（上一轮第 3 步卡在键盘弹不出来，见下面那节 F48）：

| # | 步骤 | 结果 |
|---|---|---|
| 1 | 打开看一眼 | ✅ |
| 2 | 拍一张或选一张 | ✅ |
| 3 | 存进去一件 | ✅（F48 修好后） |
| 4 | 再找回来 | ✅ |
| 5 | 建一个位置 | ✅ |
| 6 | 导出 | ✅ |
| 7 | 通知这条路 | ✅ |
| 8 | 回我三件事 | ✅ 已回 |

**导出回执（用户逐字回的）**：🗣「导出完成 · 位置1个 · 物品1件 · 照片1张 · 273KB」——
位置 1 个（第 5 步建的）、物品 1 件（第 3 步存的）、照片 1 张（第 2 步拍的），三个数字与走查动作一一对应，**自洽**。

**第 8 步的关键答复**：🗣「每一步都一样完成，导出回执：…，**没有任何其他弹窗**」——
没有账号/登录/密码/验证类弹窗（模板把它列为必答项，因为它意味着应用要了不该要的东西）。

**⚠️ 本轮没覆盖到的**：**AI 预填**。重装后 API key 丢失（设置页显示「还缺 API key」），
第 2 步走的是"没配 key"的分支（照片能进来、字段留空手填）。AI 预填的真机验证在更早轮次做过
（`PLAN.md` §10 的 P2 行）；要本轮也覆盖，需在设置页重填 key 后再走一次第 2 步。

**记进 `RELEASE.md` §10.7**，并把一批过期状态对齐事实：`PROJECT.md` §11 快照（功能 79/0/7/1、
测试 42 类 / 261 例、发布"已具备交付条件"）、`README.md` 的「当前形态」、
`DELTA.md` 与 `PRD.md` 的 ⭐FR-RLS-01（已完成）、`PROJECT.md` 的 R1/R2 风险行
（R1 的"静默清空"在本实现不存在——从第一天就没引入破坏性迁移；R2 已消解）。

---

## 2026-10-12 · 真机走查抓到「键盘弹不出来」并修掉（F48）

**现象**：功能验收第 3 步（存进去一件）卡住——点「名称」输入框能聚焦、光标在闪，但**键盘不出现**，
于是填不了名称、也存不进去。第 1、2 步（打开、拍照）都通过。

**排查**：先排除 TalkBack（关掉后仍然复现），再在同一台机器上做对照实验，只改一个变量：

| 组合 | 键盘 | 可见高度 |
|---|---|---|
| 微信输入法 + 系统设置 | ✅ | 864 |
| 微信输入法 + 本应用（`adjustResize`） | ❌ | **-43** |
| 搜狗输入法 + 本应用（`adjustResize`） | ✅ | 885 |
| **微信输入法 + 本应用（`adjustPan`）** | ✅ | 864 |
| 搜狗输入法 + 本应用（`adjustPan`） | ✅ | 885 |

系统状态一直报 `mInputShown=true`（"键盘已显示"），只有 `mInputMethodWindowVisibleHeight`
是负值、截图里也确实没有键盘——所以**不能只看系统标志位**，必须截图。

**修法**（按官方对 targetSdk 35+ 的建议，不是"绕过去"）：

1. `AndroidManifest.xml` 里**不再声明** `windowSoftInputMode`（原来是 `adjustResize`）——
   平台已强制 edge-to-edge，官方就是要求别靠它缩放窗口；
2. 需要打字的可滚动屏加 `Modifier.imePadding()`（`ReviewScreen` / `SettingsScreen`，放在
   `verticalScroll` **之前**：缩的是滚动视口，于是 Compose 会把正在编辑的那一格自动滚进可视区）。

**真机复核**：名称框 ✓；靠下的「颜色/材质」✓；设置页 y≈2010 的「接口地址」框点下去后
**自动滚到键盘上方完整可见** ✓（没有 `imePadding` 时键盘只是盖在内容上，靠下的输入框会被挡住）。

**附带补上一处规格偏差**：`UI.md` §7 本来就写着设置页要 `verticalScroll + imePadding`
（参照实现有、我们之前没实现），这次一并补齐，并在 `UI.md` 里把两屏都记上。

**顺带证实**：文字**能**进到框里（走查过程中名称框里真的出现了字符），所以"填不进去"纯粹是
键盘没出来导致的。

**顺带确认**：这一条**不是** TalkBack 的问题——关掉 TalkBack 一样复现；也没有为了迁就 TalkBack
改任何界面或交互（按"TalkBack 不能影响正常使用"的原则）。

---

## 2026-10-10 · CI 首次真跑全绿（并修掉它抓出来的三处问题）+ TalkBack 走查单

**第一次在真实 GitHub 上跑 CI**：私人仓库 [`mapleafly/wheremy`](https://github.com/mapleafly/wheremy)，
run [`38056478443`](https://github.com/mapleafly/wheremy/actions/runs/38056478443)，**8m32s，四道门全 success**（Spotless → 单元测试 → 截图回归 → R8 发布构建）。

**这一轮最大的价值是"真跑抓出了三个本地永远发现不了的问题"**（都记进 `PLAN.md` §3.4）：

1. **`gradlew` 没有可执行位**（Windows 上 git 不保留 Unix 权限位）→ runner 上 `./gradlew` 报 **exit 126**
   （找到了但不可执行）。本地一直用 `gradlew.bat`，所以永远撞不到；
2. **仓库配置里硬编码的阿里云镜像把 CI 搞红了（F46）**：报「KSP 插件在所有仓库里找不到」。
   在 runner 上 curl 实测——Maven Central **200**、plugins.gradle.org **303**、**两个阿里云镜像都是 502**。
   镜像排在 `pluginManagement` 第一位，插件解析整体失败；而且对本机也没用（本机另有 init 脚本挂了同样的镜像，
   真正生效的是那一份）。**修法**：仓库配置只用官方三源（可移植），镜像归本机 init 脚本。
   教训：**「本机加速」不能写进仓库配置**；
3. **两条无障碍测试的 flake（F47）**：`verifyRoborazziDebug` 在 runner 上挂在 `ReviewAccessibilityTest`
   的 `waitUntil` 超时，而本地同一条命令通过。根因是这两条测试没把 `Dispatchers.Main` 设成 `Unconfined`，
   VM 协程靠主 Looper 推进，runner 慢/忙时就等不到。已与其它 VM 测试统一。

**顺带**：把仓库推到了 GitHub 之后，`.gitignore` 里 `artifacts/tools/` 的脚本也一并入库（文档点名引用它们）。

**新增 `docs/templates/talkback-walkthrough.md`**：`FR-PRM-05` 里唯一必须人工的一条——
**TalkBack 焦点次序走查**（ATF 自动化只覆盖「有没有名字、触摸目标够不够大」，**次序只能靠人听**）。
清单按屏分 7 段（照片墙 / 位置 / 待归类 / 确认页 / 详情 / 多选态 / 设置），每段写清"应该听到什么次序"与"要盯哪几件事"；
已登记进 `PROCESS.md` §5（G5b）、`TESTING.md`、`PROJECT.md`、`RELEASE.md` §9 第 22 条。

---

## 2026-10-10 · 工程侧收尾：冷启动基线复测 + Baseline Profile / targetSdk 37 两项评估

**冷启动复测**（`am start -W` TotalTime，5 次中位数，同一台机器）：

| 包 | 中位数 | 范围 | 体积 |
|---|---|---|---|
| release（R8） | **218 ms** | 215–231 | **2.62 MB** |
| debug | 951 ms | 939–983 | 16.49 MB |
| 参照实现（记录值） | 980 ms | — | 18.96 MB |

加完批量操作、组合筛选、自动导出、无照片手填这一批功能之后**没有回退**（早期记录 233 ms / 2.69 MB）。
新增 `artifacts/tools/measure-startup.ps1`：每次先 `am force-stop` 保证冷启动、连续 N 次、报全部值与中位数
——把量法固定下来，避免"挑一次好看的"。

**两项评估都给了"做/不做"与触发条件**（`RELEASE.md` §10.5）：

- **Baseline Profile：现在不加**。release 冷启动 218 ms、包体 2.62 MB，为几十毫秒引入
  `:baselineprofile` 模块 + macrobenchmark + 每次发版维护 profile，成本不划算。
  触发条件：冷启动回退到 **400 ms 以上**、或出现肉眼可感的首帧卡顿、或启动路径显著变重；
- **`targetSdk` 保持 36**。设备是 **Android 16 / API 36**，targetSdk 37 的行为在唯一那台真机上**跑不到**。
  Android 17 已知的主要变更是**大屏上方向/可调整大小限制被忽略**——我们**不锁方向**（清单里没有
  `screenOrientation`/`resizeableActivity`/`configChanges` 覆盖，横屏与矮窗口早按自适应做过），
  16 KB 页大小也不适用（**没有原生库**）。等设备 OTA 到 37 再按四条清单复查（§10.5 里列了）。

**顺手修掉一句过期结论**：§10.5 原文写着「⭐FR-RLS-01 没做、release 现在是调试签名」——
用户已经生成 keystore 并签名、真机装的是正式包，这句已按事实改掉。

---

## 2026-10-10 · CI 收口：修不存在的 action 版本 + 本地等价验证 + 首跑步骤交给人工

**为什么这一轮不是"CI 真跑"**：本工作区**没有 git 远端**（`git remote -v` 为空），
GitHub Actions 跑不了；建仓库与首次推送**必须由人做**（凭据在你手里）。所以这一轮做的是"把 CI 变成可信的"：

- **修掉两个不存在的 action 大版本**：原写的 `actions/checkout@v7` 与 `actions/setup-java@v6` 都**不存在**
  （核对到的最新分别是 **v6 / v5**，v5.3.0 已发布）。写错版本号会让第一次跑**直接红**。
  `gradle/actions/setup-gradle` 保守取 **v4**、`upload-artifact` 保持 **v4**；
- 补两条 GitHub 官方推荐：`concurrency`（同分支后推取消前一次）与最小 `permissions: contents: read`；
- 加一步「打印环境」（`java -version` + `./gradlew --version`）——第一次真跑红了靠它定位；
- **新增 `artifacts/tools/ci-local.ps1`**：与 `ci.yml` **同序同参**跑四道门，末尾给判决与测试合计。
  `-Cold` = clean + 禁构建缓存 + 强制重跑，最接近 runner 的干净状态。
  **实测**：`spotlessCheck` OK / `testDebugUnitTest` OK（96s）/ `verifyRoborazziDebug` OK（86s）/ 42 份 XML / 261 例 / 0 失败 ✓
- `RELEASE.md` §9 加了第 10 条：**CI 首次真跑的三行命令**（建仓库 → `git remote add` → `push`），
  跑通后把结论记回 `PLAN.md` §3.3；顺手修掉 §9 里过期的「39 份 XML」（现在是 42 份）。

---

## 2026-10-10 · 词表外的类别/季节（`FR-AI-11`）+ 备份那条不对称（`FR-BAK-03`）+ 修 F45

**`FR-AI-11` 按 `RULES.md` §5.7 选了方案②**（保持入库、筛选与搜索都认自由值）——不丢模型的答案：

- `search_text` 纳入**类别与季节**，字段版本 2 → 3（启动时自动补算老数据）；
- 照片墙**芯片云 = 生效词表 ∪ 库里真出现过的值**（词表在前、自由值缀在后，一眼看得出哪些是词表外的）；
- 确认页的分类/季节芯片行带上「当前这个值」，模型回了「数码产品」也显示得出选中态。

**真机验证**：造了一个带「数码产品 / 冬季」的包导入 → 滑动芯片行出现「数码产品」→ 点它墙上只剩
「移动硬盘」（**筛得到**）；重启后 `search_text` 变成「移动硬盘 数码产品 冬季 1tb，蓝色外壳」（**搜得到**）。

**顺带修掉一个真 bug（F45）**：`searchTextVersion` 的**缺省值原本取了当前的 `FIELD_VERSION`**——
键不存在时闸门恒假，于是**字段版本升了对老库完全不生效**（这就是为什么第一次重启后
`search_text` 里还是没有类别）。改成缺省 **0**（= 从没补算过）后，重启立刻补算 ✓。
教训：闸门类字段的缺省必须是「未知」，不能是「最新」。

**`FR-BAK-03` 是设计不是漏洞，现在被钉住了**（`BackupAsymmetryTest` 2 例）：

- 搬图失败**不回滚库**：库里有行、磁盘上没文件 → 缩略图空白，导入仍算成功；
- **被拒的包一张本机照片都不动**：删多余照片发生在事务成功之后，所以校验失败时本机照片原封不动；
- 完成那句话数的是**入库行数**，不代表同样多个文件真搬进去了（界面口径）。

**判绿双证**：**42 类 / 261 例**、`freshXml=42/42`、`failures=0 errors=0`、Spotless 通过；`assembleRelease` 一并过。

---

## 2026-10-10 · 无照片的手填条目（`FR-SRC-09`）

**不想拍的东西也能记一条**：「身份证放在抽屉第二格」这类，以前只能靠拍一张纸。

- 来源弹窗多了第三个选项 **「不拍照，手动填」**——只在**新建**时出现，换照片时不给（那条记录本来就有照片）；
- 实现上只做了一件事：`newBlankDraft()` **不写 `item_photo` 那一行**。其余（空名称、`DRAFT`、进待归类）
  与拍照那条完全一样，所以**确认入库、检索、删除、待归类都不用为它另开一条路**；
- 确认页：给「没有照片（手动填的条目）」占位 + 「加一张照片」按钮；
- **AI 那条线要特别处理**：手动填的条目不是「照片丢了」，所以进门**不跑分析**、也不弹
  「找不到这张照片了」——改成一句「没有照片，不跑识别——下面的字段自己填就行。」。

**真机验证**：FAB → 来源弹窗三个选项都在 → 「不拍照，手动填」→ 确认页三处文案正确 →
库里新建一条 `status=DRAFT`、**照片行数 0** 的记录 → 待归类页把它显示成「未命名」格子。
（「在输入框里打字保存」这一步受设备输入法限制没能自动化：**微信输入法 + 小鹤双拼**会把
`adb input text` 的字母吞掉——保存那条路已由 `BlankDraftTest` 的单测覆盖。）

**判绿双证**：**40 类 / 252 例**、`freshXml=40/40`、`failures=0 errors=0`、Spotless 通过；`assembleRelease` 一并过。

---

## 2026-10-10 · 批量操作（`FR-OPR-06`）

**换季整理那条路通了**：长按一张卡片进多选 → 点其他卡片加选 → 贴底工具条上做三件事。

- **长按进多选**（长按的那张先选上），多选态里点卡片 = 选中/取消；贴底条写着「已选 N 件」；
- **搬到…** / **改类别** / **删除**，加一颗「取消」；改类别的清单读**生效词表**（与确认页那颗芯片同源）；
- 三件事都**逐件复用单件那条路**（`moveAll` / `deleteAll` / `setCategoryAll`），不另写一套 SQL——
  单件的口径（搬动时间戳、按引用计数删文件）已经在测试里钉死了，批量再写一遍就是抄两遍，迟早抄歪；
- **批量删除的文案把两类照片分开数**：会跟着没的按**文件张数**（一条文件没别人用才算），
  还有别的记录在用会留着的按**记录件数**（同一条文件可能被两件共用）——含糊说「照片可能被删」等于没说；
- 搬完/删完/改完都回一句带件数的结果（「已把 2 件搬到「家 / 抽屉」。」）。

**真机验证**：长按 → 已选 2 件 → 改类别「衣物」（库里核对：电钻 工具→衣物）→ 搬到「家 / 抽屉」
（库里核对：深灰羽绒外套 衣柜→抽屉）→ 删除确认框文案正确（**没有真删**，避免动用户数据；
共用照片的留/删语义由单测钉住：还有一件在用就必须留着，没人用了才删）。

**真机又抓到一个布局问题（已记进 `PLAN.md` §3.4 的 F43）**：工具条四颗按钮一行在 411dp 宽屏上放不下，
最右的「取消」正好被右下角的 **FAB** 盖住。改法：按钮行换 `FlowRow` + 工具条尾部留 88dp 给 FAB。
教训：**布局问题要看截图**——dump 的 bounds 只能证明节点在，证明不了它没被别的层压住。

**判绿双证**：**39 类 / 247 例**、`freshXml=39/39`、`failures=0 errors=0`、Spotless 通过；`assembleRelease` 一并过。

---

## 2026-10-10 · 组合筛选（`FR-FND-08`）

**筛选芯片改成多选**：同一类里选多个是**或**，不同类之间是**与**。

- 例：「衣物 + 数码」= 衣物**或**数码；「衣物 + 冬」= 衣物**且**冬；标签同理（「#冬装 + #自检种子」是或）；
- 再点一次只去掉**那一个**，不是清空这一类；
- 折叠起来时标签写清选了什么：「更多筛选 · 季节：冬、秋 #冬装 #自检种子」；
- 实现：`Filters` 三个字段改成集合；标签走 SQL 取「带任一标签的物品 id」再并集，类别与季节走内存谓词；
- **真机**：点「衣物」→ 墙上只剩深灰羽绒外套；再点「数码」→ 变成「深灰羽绒外套 + 过期电池」（并集）✓。

**顺带一条工具教训（已记进 `PLAN.md` §3.4 的 F42）**：
`uiautomator dump` **失败时会保留上一次的文件**，直接读就会看到旧界面——我因此一度误判「多选没生效」（其实逻辑是对的，
单测先证明了这一点）。现在真机取屏统一走 `artifacts/tools/ui.ps1`：先删 dump 文件、再 dump、再 pull，并核对是刚写的。

**判绿双证**：**38 类 / 239 例**、`freshXml=38/38`、`failures=0 errors=0`、Spotless 通过；`assembleRelease` 一并过。

---

## 2026-10-10 · 定时自动导出（`FR-BAK-08`）

**每天自动写一份包到你选的文件夹**，不用记得手动导出。

- 设置页「备份」里多了：开关「每天自动导出一份」、「选择保存位置」、「立刻导出一份」，
  以及两行实话——**保存位置**（解码后显示成 `wheremy-backup`，不是 `%3A` 那种怪东西）与**上次结果**；
- 落点走 SAF：`OpenDocumentTree` 选目录，**当场 `takePersistableUriPermission`**——
  不持久化的话重启后这个 URI 就打不开了，而"每天自动导出"恰恰要跨重启；
- WorkManager 周期任务：1 天、唯一名 `wheremy-auto-export`、`KEEP`（不重复排队）。
  **开关在 worker 里判**，不是在入队时判——周期任务一旦入队就一直在，这样设置里开关一下立刻生效，
  也不用管"关掉时要不要取消"；
- 文件名 `wheremy-auto-<本地日期>.zip`，**只留最近 7 份**（认前缀，绝不碰同目录里别的文件）；
- **失败要说出来**：自动导出失败而用户不知道，等于没有备份——无论成败都写进设置页那一行。

**真机验证**（debug 包）：选 `/sdcard/wheremy-backup` → 授权 → 「立刻导出一份」→
`/sdcard/wheremy-backup/wheremy-auto-2026-10-10.zip`（44,290 字节）真的躺在那里；
设置页显示「上次（10月10日 10:33）：已导出 wheremy-auto-2026-10-10.zip（43 KB）」；
WorkManager 库里 `AutoExportWorker` 周期 86,400,000 ms（**连 `-wal` 一起拉库**核对）。

**真机环境两条坑（已记进 `PLAN.md` §3.4）**：

- **F40**：MIUI/HyperOS 的 SAF 选择器**不让选存储根目录与 `Download`**（「无法使用此文件夹／为保护您的隐私」）。
  先用 `adb shell mkdir` 建 `/sdcard/wheremy-backup` 再选它；用户侧同理——在选目录器里「新建文件夹」建自己的目录；
- **F41**：真机输入法是**微信输入法 + 小鹤双拼**，`adb shell input text` 打不出英文（字母被双拼转成汉字，
  所以"新建文件夹"里得到的是「－」）。真机自动化**不再用输入法打字**：要目录/文件就先 `adb shell` 建好再点选。

**判绿双证**：**37 类 / 232 例**、`freshXml=37/37`、`failures=0 errors=0`、Spotless 通过；`assembleRelease` 一并过。

---

## 2026-10-10 · 结果排序四档（`FR-FND-09`）+ 删位置就地搬走（`FR-LOC-05`）

**`FR-FND-09` 结果排序**：筛选面板里多一行四颗芯片——**默认 / 最近动过 / 最近更新 / 名称**。

- 🔒 **默认那一档一个字都没改**：空查询仍按插入序（`RULES.md` §10.1 的 `all()` 没有 ORDER BY）、有查询仍按相关度（`SearchRanker`）。
  加排序最容易顺手把默认行为改掉，而那是锁死的契约——所以 `SortOrderTest` 里有**两条专门防这个回归**的用例；
- 其余三档是用户**显式**选的，选了就以排序为准；**只改顺序、不改集合**；再点当前那颗 = 回默认；
- 折叠起来时第 2 层芯片上写着「更多筛选 · 排序：名称」，不会出现"顺序变了但看不出为什么"；
- 真机：展开面板点「排序：名称」→ 墙上顺序按**字符串序**（不是拼音序）重排，折叠标签同步变化。

**判绿双证**：**36 类 / 227 例**、`freshXml=36/36`、`failures=0 errors=0`、Spotless 通过；`assembleRelease` 一并过。

**顺带记一条工具**：`artifacts/tools/pull-db.ps1`——核手机上的库必须**连 `-wal` 一起拉**，
只拉主库会看到旧快照（当天因此误判过一次"搬走没生效"）。

---

## 2026-10-10 · 无障碍深扫进自动化（`FR-PRM-05` 收口）+ 恢复数据到手机

**按用户要求恢复数据**：用**最近那份**导出包（`wheremy-backup-2026-10-09.zip`，12 条目 = 清单 + 数据 + 10 张图）
在**用户自己签名的 release 包**上走「设置 → 备份 → 覆盖导入」→ SAF 选包 →「覆盖导入完成 · 位置 3 个 · 物品 5 件 · 照片 5 张」，
照片墙随即显示出这些物品。**这一步顺带验证了 release（R8 + 正式签名）的完整恢复链路**。
（另两份 zip 仍在 `Download` 里：10-05 1.06 MB / 10-04 394 KB，需要时说一声。）

**无障碍深扫（`FR-PRM-05` 从 🟡 到 ✅）**：

- 用 **ATF**（Android Accessibility Test Framework）+ `roborazzi-accessibility-check` 跑在 JVM 上——
  **不需要设备、进得了 CI**；
- 三屏在 `CheckLevel.Error` 档**判红**：取景屏（`AccessibilityScanTest`）、照片墙有物品时（`GalleryAccessibilityTest`）、
  确认页（`ReviewAccessibilityTest`）；
- 扫出来的**真问题**（F39）：横向滚动的芯片行里，被视口裁掉的那颗芯片被 ATF 按**可见宽度**量成「触摸目标只有 13dp」。
  判定实验（把词表缩到 2 项 → 报警消失）确认了原因；**修法不是抑制检查，而是让芯片换行**（`Row` → `FlowRow`）：
  12 颗类别芯片分三行铺开，谁都不会被裁——对被裁掉一半的芯片，读屏与手抖的用户本来就不友好；
- 剩下**只有一条必须人工**：TalkBack 的**焦点次序**走查（ATF 的规则集不覆盖次序）。

**判绿双证**：**34 类 / 217 例**、`freshXml=34/34`、`failures=0 errors=0`、Spotless 通过；`assembleRelease` 一并过。

---

## 2026-10-10 · 用户签了名：日志核过 + 修掉 F38（开发期入口在 release 里可达）+ 一条 flake

**用户的日志（逐段判定）**：

| 段 | 判定 | 依据 |
|---|---|---|
| `keytool -genkeypair` | ✅ 正常 | 「生成 4,096 位 RSA 密钥对和自签名证书 (SHA384withRSA)（有效期 10,000 天）」+「正在存储 D:\keys\wheremy-release.jks」 |
| `signingReport` | ✅ 正常 | `Variant: release` → `Config: release`、`Store: D:\keys\wheremy-release.jks`、`Alias: wheremy-release`、`SHA-256: 81:18:B9:…`、`Valid until: 2054年2月25日`（= 生成日 + 10,000 天 ✓）；debug/r8smoke/debugAndroidTest 仍是调试签名（设计如此） |
| `assembleRelease` | ✅ 正常 | `validateSigningRelease` 通过、`BUILD SUCCESSFUL`；`Deprecated Gradle features` 那句来自插件，不是错误 |
| `apksigner verify` | ✅ 正常 | DN = 用户填的 `CN=Wheremy,…`；`SHA-256: 8118b97d…` 与 `signingReport` 的 `81:18:B9:…` **是同一个摘要**（一个带冒号大写、一个不带冒号小写） |
| 第一次 `install` 失败 | ✅ 预期 | `INSTALL_FAILED_UPDATE_INCOMPATIBLE`：手机上那个 `com.wheremy.app` 是**参照实现**（另一把密钥）——正是 `KEYSTORE.md` §7 坑 1 |
| **第二次 `install` 成功** | ⚠️ **要留意** | 设备上 `com.wheremy.app` 的 `firstInstallTime` 变成当天 08:02 → 旧包（参照实现）是**被卸载**而不是被覆盖，**它的数据已经不在手机上**；好在 `/sdcard/Download` 里还留着 10-04 / 10-05 / 10-09 三份导出 zip，数据可恢复 |

**据此修的两处（都是这次核对带出来的）**：

- **F38（release 卫生）**：开发期入口在 release 包里**仍然可达**（`--es dev seed` 会种演示数据、`--es dev words` 能改设置）——路由注册与 extra 解析都没按 `BuildConfig.DEBUG` 门控。修法：把 extra→路由抽成纯函数 `devRouteFor(dev, debugBuild)`（release 一律返回 null），五条开发期路由的注册整体放进 `if (BuildConfig.DEBUG)`，release 里连这些屏一起被 R8 裁掉。证据：`DevRouteGateTest` 3 例 + 真机装 release 后 `--es dev seed` **毫无反应**（照片墙仍空）。
- **一条 flake**：`ItemRepositoryTest.confirm 位置没变时 lastMovedAt 不动` 用真实时间比两次操作的时间戳，机器一忙就可能落在同一毫秒 → 随机红（本次并发跑构建时真的红了）。改成**可控假时钟**（每次读推进 1 毫秒）。

**`KEYSTORE.md` 补了坑 3**：第一次 `install` 失败之后别急着再按一次——第二次成功往往意味着旧包已被卸载、数据已丢；看到失败要先决定"并存（改 `applicationIdSuffix`）还是替换（先导出再卸载）"。

**判绿双证**：**31 类 / 213 例**、`freshXml=31/31`、`failures=0 errors=0`、Spotless 通过；`assembleRelease` 出的包用用户自己的密钥签好（`apksigner` 验签通过）。

---

## 2026-10-10 · `docs/KEYSTORE.md`：生成与保管 release 密钥的操作手册（+ 构建侧签名就绪）

**给用户的东西**：一份**照着敲就能做完**的文档，从"keystore 是什么、为什么必须自己生成"到
"生成 → 三份备份 → 写 `keystore.properties` → 接进构建 → 三条验证命令 → 装机两个坑 → 换密钥不可逆"，
附常见坑表与检查清单。**不是抄来的**：我在本机用一把**一次性**密钥把整条链跑通了两遍（含代码格式化之后再跑一遍），
密钥放在 `%TEMP%`、验证完已删除，仓库里 `git status` 干净、零密钥文件。

**构建侧（让用户少改代码）**：`app/build.gradle.kts` 现在会按顺序找两处配置，找到就用、找不到就**退回调试签名**并在真要出 release 包时**喊一声**：

| 顺序 | 位置 |
|---|---|
| 1 | `<仓库父目录>/keystore.properties`（本机即 `D:\src\dsh\keystore.properties`，**仓库外**） |
| 2 | `~/.wheremy/keystore.properties` |

- `signingConfigs` 新增 `release`（`storeFile` 支持相对路径，按 properties 文件所在目录解析；PKCS12 下 `keyPassword` 缺省时自动用 `storePassword`）；
- `buildTypes.release.signingConfig` 有配置就用正式签名、没有就用调试签名；
- 末尾加了一句只在出 release 包时打印的提醒：`⚠️ release 用的是**调试签名**：没找到 keystore.properties。正式交付前请按 docs/KEYSTORE.md 生成自己的 keystore。`
- `.gitignore` 补 `keystore.properties`（**推荐放仓库外**；这行只是防误提交）。

**实测证据（本机，最终代码）**：`signingReport` 里 release 段 `Config: release`、`Store` 指向测试密钥；
`assembleRelease` 出的包经 `apksigner verify --print-certs` 验签为 `CN=Wheremy Test…`（`SHA-256: 41cc6474…`）；
删掉 `keystore.properties` 再出包 → 打印那句提醒并 `BUILD SUCCESSFUL`（回落到调试签名）。

**文档接线**：`RELEASE.md` §3.2 指向手册（并写明"文件放好之后不需要改任何代码"）、§6.3 顶部加了装机两个坑的警告、
§6.2 的签名行改成"有配置用你的、没有用 debug"；`docs/README.md` 文档地图新增 `KEYSTORE.md` 一行。

**判绿双证**：**30 类 / 210 例**、`freshXml=30/30`、`failures=0 errors=0`、Spotless 通过（含 `build.gradle.kts` 的格式化）。

---

## 2026-10-10 · `FR-CFG-06` 四套词表自定义（用户批准的首版六条**全部落地**）

**做了什么**：

| 层 | 内容 |
|---|---|
| 存储 | 四套词表（物品类别 / 季节 / 到期类型 / 位置类型）进 DataStore：**空 = 用代码里的预设**，所以「没改过」与「改回默认」是同一件事；写入侧清洗（去空白、丢空项、去重、**封顶 24 项**）；存成多行字符串（用户自己的叫法里很可能就有逗号） |
| 设置页 | 新增「词表」一节：四行输入框 + 各自一颗「保存」+「四套词表全部恢复默认」；分隔符顿号/逗号/空格都认；清空再保存 = 回到默认 |
| 芯片 | 确认页的分类/季节/到期类型芯片、照片墙的筛选芯片、位置页的类型候选**全部读生效词表** |
| **提示词** | `Prompt.build(categories, seasons, paths)`——模型拿到的是**用户那份**词表。不跟的后果很具体：模型回词表外的值，芯片行显示不出选中态（`RULES.md` §5.7 那个偏差就是这么来的） |

**判绿双证**：**30 类 / 210 例**、`freshXml=30/30`、`failures=0 errors=0`、Spotless 通过。
新增 6 例：`SettingsRepositoryTest` 5 例（默认空/清洗/封顶/清空回预设/恢复默认）+ `AiLogicTest.用户改过词表时提示词用用户那份`。

**真机（9aa4b43e）**——每一步都有屏上文字或留痕原文：

1. 新增开发期入口 `--es dev words`（**为什么**：真机用输入法往设置页敲词表不可靠——`input text` 只认 ASCII，光标与 IME 会打架，试了两次都留下半截内容），进门先报当前四套词表 + 提示词里的类别行；
2. 设成 `Anime / Model` + `Dry / Rainy` → 该页立刻显示「从这 2 个里选一个：Anime Model」；
3. 照片墙筛选芯片变成 **Anime / Model**；确认页分类芯片 **Anime / Model**、季节芯片 **Dry / Rainy**；
4. 拍一张真照片触发真模型 → **留痕里发出去的提示词**写着「- category：从这 2 个里选一个：Anime Model」（`outcome=NOTHING_FOUND`、`elapsedMs=1358`）——**"提示词与词表同步"这条在真机上被原文证明**；
5. 点「恢复默认」→ 四套词表回到预设，照片墙芯片回到 衣物 / 数码。

**归档**：`DELTA.md` 的 `FR-CFG-06` 翻绿（**✅ 70 / 🟡 4 / ⬜ 12 / ⛔ 1**），`PRD.md` 标记与待做队列同步，对账脚本 **87 对 87、零不一致**（exit 0）；`RULES.md` §5.6 补了一句：词表可改之后提示词里写的是生效的那份（「12 选 1」→「N 选 1」）。

**至此：用户批准进首版的六条（`FR-PRM-04` / `FR-PRM-05` / `FR-FND-07` / `FR-TRC-04` / `FR-TRC-05` / `FR-CFG-06`）全部落地。** 剩下的 ⬜ 12 条与 🟡 4 条都在 `PRD.md` §5 里带着触发条件与代价，`⭐FR-RLS-01`（release 签名）等用户的 keystore。

**一处操作失误记档**：用 `run-as cat` 拉设备偏好文件核验时，我的过滤条件写得太松，把 API key 明文打进了终端输出。**该 key 只存在于设备上**，没有进任何文档、日志或提交；后续核验只按字段名过滤（`word_*` / `base_url` / `model`）。

---

## 2026-10-09 · 归档收口 + 补齐三条已批准的首版条目

**归档（把 87 条 FR 的结论落到文档里）**：

- `DELTA.md` §2：**87 行"本实现"列全部填完**——✅ 69 / 🟡 4 / ⬜ 13 / ⛔ 1，每行的证据都是**具名测试类或真机动作**；
- `PRD.md`：状态标记切换为"本实现"（正文行号仍指参照基线），§3.11 / §5 / §5.1 按本实现重排；
- **对账脚本** `artifacts/tools/check-prd-delta.py`：两份文档的标记逐条比对，**87 对 87、零不一致**（exit 0，可当门禁）。

**补齐的条目**（都在用户批准的首版清单里）：

| id | 做了什么 | 证据 |
|---|---|---|
| `FR-FND-07` | 搜索撞到 300 上限时，照片墙多一行实话「只显示了最近更新的 300 条，可能还有更多：再加一个词，或者用筛选取个范围」 | `GalleryViewModelTest.搜索撞到三百条上限时界面要说出来`（301 条撞上限 vs 只搜到一条不报，两边都钉） |
| `FR-TRC-04` | 设置页显示「累计调用 N 次（含重试）· 服务端报的 tokens 合计 M · 费用按你的账单为准（本机不折算）」 | `SettingsRepositoryTest.每次尝试都记一笔，重试也算`；真机读到「累计调用 1 次 · tokens 合计 929」 |
| `FR-TRC-05` | 留痕每条多一行 `elapsedMs=` | `AnalysisTraceTest`（5 例）；真机留痕 `elapsedMs=832` |

🔒 两处刻意的克制：**费用不折算成钱**（价格表会变，说错钱比不说更糟）；**分页不做**（这个量级下缩小范围更快）。

**判绿双证**：**30 类 / 204 例**、`freshXml=30/30`、`failures=0 errors=0`、Spotless 通过。

**顺手修掉一条本来就存在的竞态**：`TreeViewModelTest.上一层回到父节点` 只等 `currentParentId`，而 `open()` 会先翻它、面包屑随后才到 → 之前碰巧过，这次并发跑构建时暴露。改成等完整状态（`currentParentId && !loading && breadcrumb`）。**教训记档**：测试等状态要等"用户能看到的那一份"，别等中间态。

**还没做的**：`FR-CFG-06`（类别/季节/位置类型的自定义管理，四条词表目前是源码常量）；⭐`FR-RLS-01`（release 签名，等用户的 keystore）；`FR-PRM-05` 剩的 ATF 深扫与 TalkBack 走查。三条都写在 `PRD.md` §5 的待做队列里。

---

## 2026-10-09 · P2 收尾：AI 分析接上真模型 + 真机七条全部签收

**交付**：

| 项 | 内容 |
|---|---|
| **AI 层**（`data/ai/`） | `Prompt`（§5.6 那几句逐字：承托面不算一件、品牌不许猜、位置清单整句替换问法）｜`AnalysisParser`（候选顺序 + 去重、先找 `items` 数组、`echoedItemsKey` 分界、标量与数组清洗不对称）｜`ApiPhotoAnalyzer`（**唯一**的状态码映射、只重试四类、最多两次、超时与 `MAX_ATTEMPTS` 都是常量）｜`AnalysisTrace`（一次尝试一条、只留提示词与回应原文、上限 10 条、单字段 8000 字截断） |
| **位置建议** | `SuggestLocationUseCase`（§5.4 六条判据：整条路径原样命中优先 → 模糊打分 → 同分取更浅 → 都对不上给 null → 根不参与） |
| **界面** | 确认页 AI 状态行：分析中「AI 正在看照片，可以先自己填」+ 进度条；位置话**三句互斥**；失败给 11 类文案之一 + 「重新分析」；那一句带 `Modifier.weight(1f)`（不给权重会把按钮挤成竖列——真机上的形状 bug） |
| **回填** | `ItemRepository.applyAnalysis`：只写 AI 给得出的字段、位置**只在空着时**填、重算 `search_text`；**不标记** `touched` |
| **测试** | 新增 `AiLogicTest`（18 例）：11 条文案逐字、超时文案不写秒数、状态码映射、400+balance、可重试四类、回显 JSON 不吞坏、围栏与散落对象、`items: []` 与读不懂是两件事、清洗不对称、位置建议取舍、提示词带不带清单两版 |

**判绿双证**：**29 类 / 196 例**、`freshXml=29/29`、`failures=0 errors=0`、Spotless 通过。

**真机（9aa4b43e）**：

1. **REBUILD.md §6 第 2 条补上了**：真机相册选图 → 回到录入页并显示新图，库里 +1 物品 +1 照片（截图 `device-picked-photo-review.png`）。
2. **AI 连真模型**（用户已填 key，`base_url=https://api.deepseek.com`、`model=deepseek-flash`）：真机拍一张真照片 → 确认页自动填出**名称「按摩球」、颜色「紫色」、材质「橡胶」**（截图 `device-ai-analysis.png`）。
3. 留痕真落了盘：`files/ai-trace/…-NOTHING_FOUND.txt` 里 `outcome=NOTHING_FOUND`、`response={"items": []}` —— **一次"AI 说没认出物品"的现场，靠留痕一眼判出是模型自己说的，不是我们解析坏了**。

**两条新发现**：

- **F36**：清单里一直**没有 `INTERNET`**。没有它，`openConnection` 抛的 IO 异常被归类成 `OFFLINE`，真机上看到的是「网络不可用，AI 没连上」，而网络好好的——这个假话只能靠真机连模型那一跑撞出来。已补权限，`RELEASE.md` §2.1 的权限行同步。
- **F37**：从相册选进来的那张恰好是"相机界面截图"，模型回 `{"items": []}`。**这不是缺陷**：照片里确实没有可归置的物品，模型的判断是对的；留痕把这件事变得可判定。

**阶段状态**：`PLAN.md` §10 → P0 ✅、P1 ✅、**P2 ✅（AI 已接、真机连模型验证通过）**、P3 ✅、P4 ✅、P5 ✅、P6 ✅。**至此七段全绿**（唯一外部依赖：⭐FR-RLS-01 的 keystore，用户已拍板"后续再做"）。

**归档完成（把每个 FR 的结论落到文档里）**：

- `DELTA.md` §2：**87 行"本实现"列全部填完**（✅ 66 / 🟡 6 / ⬜ 14 / ⛔ 1），每行的证据都是**具名测试类或真机动作**，没有一行写"已完成"三个字；
- `PRD.md` §0：写明**状态标记已切换为"本实现"的状态**，正文行号仍指参照基线（那份代码只读）；
- `PRD.md` §3.11 / §5 / §5.1：按本实现重排——`FR-PRM-04`（横屏/平板）做完了从待做队列移走、`FR-PRM-02` 在本实现里是 ✅（同样不做自定义 rationale）、`FR-DB-01` 标注"本实现不存在这个债"；
- **对账脚本**（`artifacts/tools/check-prd-delta.py`）：把 PRD 标题上的标记与 DELTA 的"本实现"列逐条比对，当前 **87 对 87、零不一致**（exit 0，可当门禁用）。
- ⚠️ **如实记下三条"用户已批准进首版但还没做"的**：`FR-FND-07`（搜索上限的告知）、`FR-TRC-04`（调用次数/花费）、`FR-CFG-06`（词表自定义管理）——已写进 `PRD.md` §5 的待做队列，下一批做。

---

## 2026-10-09 · P6 收口：发布检查单签收 + 自适应图标（发现 F35）+ 真机七条

**交付**：

| 项 | 内容 |
|---|---|
| `RELEASE.md` §2.1 / §3 / §6.1 / §6.2 | 从"参照实现的事实"改成**本实现的事实**（wrapper 9.8.1、AGP 9.4.1、compileSdk 37、R8 开、`.r8smoke` 变体、debug 包名 `.dsh`、合并清单里 WorkManager 带来的四条传递权限、CI 四步）；§3 七条逐条写状态（3.1 ✅ 提前还清、3.2 ⬜ 要用户的 keystore、3.3 走 B 路线打开 R8、3.4–3.6 ✅、3.7 ⬜ 首版无旧包） |
| `RELEASE.md` §10（新增） | **0.1.0 签收记录**：发布物与两个 APK 的 SHA-256、§9 的 28 项逐条结果、G0–G5 六门、性能与体积对照、结论（"可以给自己侧载用；还不能当给别人用的正式包"） |
| **应用图标**（F35） | `mipmap-anydpi-v26/ic_launcher.xml` + `drawable/ic_launcher_foreground.xml`（收纳箱：箱盖 + 箱体）+ `values/ic_launcher_background.xml`（`#00695C` = Teal40）+ 清单的 `android:icon`。前景**只用 M/L/Z**，好让"每个坐标到圆心 ≤33"这条安全区不变项能被测试逐点检查 |
| `LauncherIconTest`（新） | 三条：安全区逐点 ≤33、背景色与主题的 Teal40 一致、清单指向自适应图标 |

**判绿双证**：**28 类 / 178 例**、`freshXml=28/28`、`failures=0 errors=0`、Spotless 通过。

**真机（9aa4b43e）**：

1. **#6 图标**：抽屉里第一格就是新图标（深青底 + 白色收纳箱），蒙版干净、没切边；同屏参照实现是"青底定位针"，一眼能分辨。查这条之前**发现本实现根本没有图标**（F35）。
2. **#4 EXIF 方向**：真机竖屏拍一张 → 确认页里照片**方向正确**（不是歪的），顺带在真实拍摄路径上复验了 F31 的修复。
3. **#7 字体缩放**：`font_scale = 1.3` 下照片墙排版正常（搜索标签折两行、芯片横排、名字与位置行都完整、底栏标签没被挤掉），已还原 1.0。
4. **冷启动**（4 次中位数）：R8 包 **233 ms** / debug 包 926 ms（参照实现记录 980 ms）。
5. release APK **2,691,833 字节**，SHA-256 `B13EB88E…848B`。

**新增一条发现（F35）**：`res/` 里没有 mipmap、清单里没有 `android:icon` → 桌面显示系统默认图标。这条是"单元测试永远绿、只有看桌面才知道"的那类，和第前面几条一样。

**阶段状态**：`PLAN.md` §10 → P0 ✅、P1 ✅、P2 🟡（AI 未接）、P3 ✅、P4 ✅、P5 ✅、**P6 ✅（相机/自适应/无障碍/截图回归/R8 与发布准备全过）**。

**剩下的两件事**（都需要用户拍板，已记档）：
- **⭐FR-RLS-01**：release 现在是调试签名；要正式交付得用你自己的 keystore（口令与保管归你，代理不代做）。
- **P2 的 AI 分析**：要你把 `DEEPSEEK_API_KEY` 填进手机上的「设置 → AI 分析照片」才能真机连模型验证。
另有一条**故意不做**的：`REBUILD.md` §6 第 2 条"选图器选完显示新图"要往你的相册里放一张图，越过隐私边界，因此不做并如实记录。

---

## 2026-10-09 · P6 后半：自适应 + 无障碍 + 截图回归 + R8 与发布准备

**交付**：

| 项 | 内容 |
|---|---|
| **自适应** | 底栏与 FAB 从各屏收到**外壳一层**（`UI.md` §2.2 本来的形状）：`BoxWithConstraints` 判窗口尺寸 → 窄窗口 `NavigationBar`、**宽 ≥600dp 换左侧 `NavigationRail`**；矮窗口（高 <480dp，手机横屏）照片墙格子换**横排**（缩略图左 + 名字/位置/到期三行右）并把网格最小宽度提到 260dp → **F21 治好** |
| **无障碍** | 照片墙格子图 `contentDescription` 改成物品名（名字为空时给「未命名物品」，不再给空串）、详情页照片给「物品照片」、位置缩略图给「<位置名>的位置照片」；触摸目标沿用 M3 的 `minimumInteractiveComponentSize`（芯片/按钮 ≥48dp） |
| **截图回归** | Roborazzi **5 张基线入库**（`app/src/test/screenshots/`：取景屏三态里的两态 + 矮窗口一版 + 设计系统浅/深两版）；CI 增加 `verifyRoborazziDebug` |
| **R8 与发布准备** | release 打开 `isMinifyEnabled` + `isShrinkResources`；`proguard-rules.pro` 写了三条"删了会在真机炸"的 keep（kotlinx.serialization 的 `$$serializer`、Room 3 实体、Hilt 的 Worker）；新增 **`.r8smoke` 变体**（规则同 release、只多包名后缀）用于真机冒烟——因为 release 的包名与参照应用同名，**装上去会顶掉用户手机上那个应用** |
| **CI** | 静态检查 → 单测 → **截图回归** → **`assembleRelease`**；报告与基线一起上传 |

**判绿双证**：**27 类 / 175 例**、`freshXml=27/27`、`failures=0 errors=0`、Spotless 通过；`verifyRoborazziDebug` 通过。

**真机验证（9aa4b43e）**：
1. 竖屏：底栏 `NavigationBar` 四项（照片墙/位置/待归类/设置）+ FAB。
2. **横屏：左侧 `NavigationRail` + 横排格子**，文字行不再被挤出视野（F21 治好）。
3. 无障碍：照片墙节点的 `content-desc` 是**物品名**（`深灰羽绒外套` / `电钻`）、⋮ 是「操作」、FAB 是「选照片录入」。
4. **R8 冒烟**：装 `.r8smoke` 包 → `--es dev backupcheck` → 「导出：位置 3 · 物品 5 · 照片 5 · **44331 字节**」+「合并导入：0/0/0（按主键跳过）」+「结论：R8 之后序列化与反序列化都活着」，无崩溃。
5. release APK **2.69 MB**（debug 13.9 MB）。

**四条新发现（`PLAN.md` §3.4 F32–F34）**：各屏自建底栏让自适应无从下手（收到外壳）、横排格子治 F21、`recordRoborazziDebug` 会被 Gradle 判"最新"而**静默不录**（要 `--rerun-tasks`，且基线必须放源码树）。

**阶段状态**：`PLAN.md` §10 → P0 ✅、P1 ✅、P2 🟡（AI 未接）、P3 ✅、P4 ✅、P5 ✅、**P6 🟡（四块里已过三块半）**。

**最后一公里（P6 收尾）**：`RELEASE.md` §3 检查单逐条签收、`accessibility-check` 深扫与 TalkBack 走查、Baseline Profile/启动性能、`targetSdk 36 → 37` 评估、以及 PRD 状态列一次性归档（`DELTA.md` 的分工不变）。

---

## 2026-10-09 · P6 前半：相机取景 + 照片来源弹窗（补上 P3 欠的那一处）

**交付**：

| 文件 | 内容 |
|---|---|
| `ui/camera/CameraGate.kt` | `Unavailable` / `NeedsPermission` / `Ready` 三态，**现算**（硬件 + 运行时权限）；外壳在 `ON_RESUME` 时重读 |
| `ui/camera/CameraScreen.kt` | 中栏三态（「这台机器没有可用的相机」/「取景要用相机，先给它授权」+「打开相机权限」/ 预览槽）、失败行「没拍到，再按一次，或者改用相册」（仅 `missed`，error 色）、底行「取消」/ 仅 `Ready` 给「拍这张」且 `enabled = !busy`/「改用相册选」**常驻**；背景不透明；**这一屏不认识 `PreviewView`**（预览面由外壳填 `preview` 槽） |
| `ui/camera/CameraSession.kt` | CameraX：`PreviewView` + `ImageCapture` 绑 `LifecycleOwner`，快门从 `ImageProxy` 直接取 JPEG 字节（不落临时文件），离开组合自动 `unbindAll` |
| 外壳 | **照片来源弹窗**（标题「这张照片从哪来？」+ 正文逐字 + 「拍照」/「从相册选」）；`deliverPhoto` **唯一收口**四条分支（空字节→说「读不到这张照片，换一张试试」；`LOCATION`→位置照片；确认页开着→显影槽 `replaceWith`；其余→新建）；`startAlbum`/`openViewfinder` 各写一次 `photoTarget`；返回键优先级第 1 条用 **`BackHandler`** 显式接住 |
| 接线 | 位置页的位置照片改走来源弹窗（`TreeViewModel` 提到外壳）；确认页的「换一张」也走来源弹窗 + 那一格显影槽（同一个 `itemId`，填过的字段都留着） |
| 构建 | CameraX 1.5.3（core/camera2/lifecycle/view）；`CAMERA` 权限 + `uses-feature required=false`（没相机的机器其余功能照用） |

**判绿双证**：**26 类 / 172 例**（新增 `CameraGateTest` 4 条 + `CameraScreenTest` 6 条界面测试）、`freshXml=26/26`、`failures=0 errors=0`、Spotless 通过。

**真机验证**（截图 `artifacts/screenshots/P6/`）：
1. FAB → 来源弹窗**逐字**（「这张照片从哪来？」/「拍照就在 Wheremy 里取景，按下快门才拍；它只要相机权限，不进相册。」/「从相册选」/「拍照」）。
2. 选「拍照」→ 取景屏 `NeedsPermission`：「取景要用相机，先给它授权」+「打开相机权限」+「取消」+「改用相册选」，**进门自动弹系统权限框**（「允许"Wheremy"拍摄照片或录制视频？」）。
3. 允许后进 `Ready`（「取消 | 拍这张 | 改用相册选」）+ 实时预览（截图 4.27 MB，是真实取景画面）。
4. 取景屏里按**返回键只关掉取景屏**、留在本应用（修之前会直接退出、还露出底下那个应用）。
5. 「拍这张」→ 进确认页；**详情页 →「编辑」**这条更可靠的路复验确认页：名称/分类/季节/颜色/品牌全部在位。

**三条新发现（`PLAN.md` §3.4 F29–F31）**：
- **F29**：给外壳补 `SnackbarHost` 时顺手加的 `Scaffold` 把自己盖在 `NavDisplay` 上 → **整屏白屏**（真机截图一眼看出）。
- **F30**：`NavDisplay(onBack=…)` **只在栈深 > 1 时才拦**，所以叠在栈底那一屏上的取景屏按返回没人接 → 直接退出应用。补显式 `BackHandler`。
- **F31**：确认页打开后**从不 `start(itemId)`** → 顶栏在、正文空白。P2 那次验证走的是自检屏的程序化路径，没覆盖这个界面集成点。

**阶段状态**：`PLAN.md` §10 → P0 ✅、P1 ✅、P2 🟡（AI 未接）、P3 ✅、P4 ✅、P5 ✅、**P6 🟡（相机已通）**。

**下一步**：P6 后半——无障碍（`accessibility-check` 跑一遍 + 触摸目标 + 文案朗读）、自适应（横屏/平板，含 F21：横屏照片墙只露图、FAB 压图）、Roborazzi 截图回归进 CI、发布准备（签名、版本号、`RELEASE.md` 检查单）。

---

## 2026-10-09 · P5 收口：备份导出 / 导入（含四条拒与 MERGE/OVERWRITE）

**交付**：

| 文件 | 内容 |
|---|---|
| `data/backup/BackupModels.kt` | `manifest.json`（`formatVersion` / `schemaVersion` / `appVersion` / `exportedAt`）+ `data.json`（四表行，entity 直接 `@Serializable`，不额外定义 DTO）+ `ImportMode` / `BackupSummary` / `BackupFormatException`（**message 就是要给用户看的中文**）+ `isPhotoEntry` 路径白名单（**恰好一层**、字符集受限、不许 `..`）+ `formatBytes`（1000 进制、整除截断） |
| `data/backup/BackupRepository.kt` | **导出**：四表在**同一个事务里读**（否则会导出"所属物品不在包里"的照片行）；同一路径只写一次；读不到的图跳过；计数按**文件对**。**导入**：读包（缺件/解不开/schema 更高/非法文件名各有各的说法）→ 四条拒 → **一个事务落库**（MERGE 按主键跳过、OVERWRITE 先清空；位置 `parentsFirst` 排父在前）→ **事务成功之后**才 `removeLocalPhotosNotIn` + 搬图；`.incoming` 暂存目录进门先删、`finally` 再删。🔒 构造参数里**刻意没有** `SettingsRepository` |
| 设置页第三节 | 三颗按钮（导出到文件 / 合并导入 / 覆盖导入，忙时 `enabled=false`）+ 进度行「正在读写备份包…」+ 四句说明（含加粗的「备份文件里不含 API key，也不含任何设置。」与 `allowBackup=false` 那句）+ 覆盖确认框（标题与正文**逐字**）+ `pendingMode` 在回调第一行清空（一次选择只兑现一次导入）；导入 MIME 用通配、导出 `CreateDocument("application/zip")`、建议名 `wheremy-backup-${LocalDate.now()}.zip` |
| `SettingsViewModel` | `operate` **五种收场**：忙→忽略；`BackupFormatException`→贴中文；其他异常→贴该操作的 `fallback`；`null`→「打不开那个文件，换个位置再试」；成功→`${label}完成 · 位置 N 个 · 物品 N 件 · 照片 N 张`（仅 `bytes > 0` 才加字节数） |
| 构建 | `buildFeatures.buildConfig = true`（备份清单要写 `VERSION_NAME`） |

**判绿双证**：**24 类 / 161 例**（新增 `BackupRepositoryTest` 17 条）、`freshXml=24/24`、`failures=0 errors=0`、Spotless 通过。

**真机闭环**（截图 `artifacts/screenshots/P5/`）：
1. 「导出到文件」→ SAF 保存 → 结果句 `导出完成 · 位置 3 个 · 物品 5 件 · 照片 5 张 · 44 kB`，zip 落在「下载」（44,361 字节）。
2. 在照片墙上**删掉一件**（深灰羽绒外套）→ 墙上剩 4 件。
3. 「覆盖导入」→ 确认框逐字 → 选回**我们自己那个包**（同一目录里那两个参照应用的旧包属于用户真实数据，全程只读不碰）→ 结果句 `覆盖导入完成 · 位置 3 个 · 物品 5 件 · 照片 5 张`。
4. 拉库点数：四表 3/5/5/5，第一件就是被删掉的那件；回到照片墙第一格显示 `深灰羽绒外套` —— **删掉的物品经备份回来了**。

**三条新发现（`PLAN.md` §3.4 F26–F28）**：
- **F26**：底栏页签原来用 `clear()+add()`，把照片墙也清掉了 → 「设置页按返回」直接退出应用。改成 `switchTab`：**照片墙永远留在栈底**，返回键自然回到它。
- **F27**：照片墙回到前台不刷新 → 覆盖导入后墙上还是旧数据。补 `LaunchedEffect(Unit) { vm.refresh() }`，与位置页那条"每次进入本页都刷新"对齐。
- **F28**：导出的"计数流"只加字节数没转发字节 → 导出的包是空的；往返测试当场抓住。

**阶段状态**：`PLAN.md` §10 → P0 ✅、P1 ✅、P2 🟡（AI 未接）、P3 ✅、P4 ✅、**P5 ✅**。

**下一步**：P6（相机取景屏 + 权限门禁 + 来源弹窗、无障碍、自适应含 F21 横屏问题、Roborazzi 截图回归、发布准备）；P2 的 AI 分析要等你把 key 填进设置页才好在真机上验。

---

## 2026-10-09 · P5 前半：设置页 + 到期提醒

**交付**：

| 文件 | 内容 |
|---|---|
| `data/settings/SettingsRepository.kt` | DataStore（`wheremy_settings`）六项：`visionEnabled` / `baseUrl` / `model` / **`apiKey`** / `reminderLeadDays` / `searchTextVersion`；默认值 `https://api.deepseek.com`、`deepseek-flash`、`3` 天；**上下界在写入侧就 `coerceIn(0, 365)`**；`hasUsableKey`＝四样齐；`currentConfig()` 不可用时给 null |
| `data/db/Daos.kt` | `expiringBy`（三分支各挡一种骚扰：`IS NULL` / `<windowStart AND expiresAt ∈ [今天,明天)` / 过期且已说过的**不进来**）、`expiringIgnoringNotified`（**只放宽"今天说过几次"**，不翻沉默的旧账）、`markExpireNotified`（只写命中集合） |
| `domain/ReminderEngine.kt` | 窗口三值（`windowStart` / `todayEnd` / `expireWindowEnd`）、按 `expiresAt` 升序取最早、正文**四变体逐字** |
| `reminder/ReminderNotifier.kt` | 频道 `wheremy_expire`「到期提醒」、`NOTIFICATION_ID = 1001`、`setAutoCancel`、点通知带 `entry=expiring`；`notifyProblem` 两种理由**逐字**；🔒 **不就地申请权限**，两种理由共用「去系统设置打开」 |
| `reminder/ReminderRunner.kt` + `ReminderWorker.kt` | 跑一次的**同一段逻辑**（Worker 与调试按钮共用）；🔒 **发不出去就不回写**；`runOnce` 返回的那句话就是 debug 区全部内容；`PeriodicWorkRequestBuilder<ReminderWorker>(1, DAYS)` + `enqueueUniquePeriodicWork(…, KEEP)`，**改设置不重排班**，没有 BootReceiver |
| `ui/settings/SettingsScreen.kt` + VM | 两节：**到期提醒**（当前值行「当天到期提醒」/「提前 N 天提醒」、±、八颗预设、每天一次的说明、调试按钮、通知发不出去时的提示与按钮）与 **AI 分析照片**（开关 + 三态说明加粗 + 三个字段 + 保存 + 隐私句逐字）；开发期工具收进「开发期工具」卡 |
| 外壳 | `SettingsKey` 接上真设置页（删掉占位屏）；`MainActivity` 读通知落点并补 `onNewIntent`；`WheremyRoot` 按 `entry` 先回照片墙；`WheremyApp` 建频道 + 排班 + **搜索字段版本变了才补算** |
| 构建 | 新增 `work-runtime-ktx` + `hilt-work`/`hilt-compiler`（`@HiltWorker`）+ 摘掉 WorkManager 默认初始化器（`Configuration.Provider`） |

**判绿双证**：**23 类 / 145 例**、`freshXml=23/23`、`failures=0 errors=0`、Spotless 通过。

**真机验证**（截图 `artifacts/screenshots/P5/`）：

1. 设置页两节文案全对（含「还缺 API key：填好保存之前，选照片都会走手填。」与「通知未开启，到期只在应用内标记」+「去系统设置打开」）。
2. 调试按钮两条路径：没权限 → `2 件临近到期，但通知发不出去：通知未开启，到期只在应用内标记`；`pm grant` 之后同一条 → `已发一条通知：2 件物品到期或已过期，最早的已过期 3 天`。**同一批 2 件第二次仍在窗口里**——这就是"发不出去就不回写"的端到端证据。
3. 通知真落到了系统：`dumpsys notification` 里有 `id=1001`、`channel=wheremy_expire`、`flags=AUTO_CANCEL`。
4. 提前天数改成「30 天」→ 杀进程 → 重进仍是「提前 30 天提醒」（**文件形态的持久化**，补上 F23 那个测试环境缺口）。
5. `entry=expiring` 冷启动 → 照片墙自动筛「更多筛选 · 快到期/已过期」，只剩 `牛奶`（还剩 2 天）与 `过期电池`（已经过期 3 天）。

**三条新发现（`PLAN.md` §3.4 F23–F25）**：DataStore 在 Windows 上的改名覆盖坑（测试用内存 store、真机兜持久化）、两个时钟（统一成一个 `now`）、运行中点通知的落点（补 `onNewIntent`）。

**阶段状态**：`PLAN.md` §10 → P0 ✅、P1 ✅、P2 🟡（AI 未接）、P3 ✅、P4 ✅、**P5 🟡（设置页 + 提醒已完成，备份未做）**。

**下一步**：P5 后半——备份导出/导入（四表 JSON + manifest、四条拒、MERGE/OVERWRITE、`allowBackup=false` 的说明句）与设置页第三节。

---

## 2026-10-09 · P4 位置页（树 / 计数 / 菜单 / 删除门槛 / 位置照片）

**交付**：

| 文件 | 内容 |
|---|---|
| `ui/tree/TreeViewModel.kt` | `open`/`up`（面包屑即标题，退一层只有「上一层」与系统返回）、`shift`（同级上移/下移）、`startCreate`/`startEdit`/`saveEdit`（**空名字直接 return**，按钮在界面层禁用）、`askDelete`/`confirmDelete`（门槛在数据层，兜底那句说清"还剩几个子位置、几件物品"）、`askMove`/`move`（候选里剔除自己与后代）、`setPhoto`/`clearPhoto`；行数据 = 直属数 + 子树数 + 是否有子 + **只在真记过照片时**才给的缩略图；`refreshSeq` 守卫 |
| `ui/tree/TreeScreen.kt` | 顶栏（面包屑 / 「全部位置」+ 上一层 + 新建）、正文五块次序、`LocationRow`（名字 + `kind · N 件（含子位置 M 件）` + ▸ + ⋮「位置操作」）、节点菜单（**边界那两行不给方向按钮**）、移动到框（「移到根层级」）、删除框（固定那句）、新建/编辑框（名称 → 类型（可选，也可自己填）、`LOCATION_KINDS` 芯片 + 自由输入、空名字禁用「保存」）、位置照片区（无图只给「给这个位置选一张照片」；有图才给「换一张」「移除照片」） |
| 外壳 | 新增 `TreeKey`，底栏四页签补上「位置」；`LocationRepository.photoStore` 改为公开（界面要把相对路径换成绝对路径） |

**判绿双证**：**20 类 / 123 例**、`freshXml=20/20`、`failures=0 errors=0`、Spotless 通过。

**真机验证**（截图 `artifacts/screenshots/P4/`）：根层一行 `家` / `房间 · 0 件（含子位置 3 件）` + ▸ + ⋮，**没有照片就不留灰框**；下钻后标题变「家」+「上一层」，照片区只给选图那颗，子行 `衣柜 · 0 件` / `抽屉 · 3 件`；目录里第一个节点的 ⋮ 菜单只有**下移**（没有上移）——`UI.md` §4.4 那条"边界不给方向按钮"在真机上生效；全程无崩溃。

**阶段状态**：`PLAN.md` §10 → P0 ✅、P1 ✅、P2 🟡（AI 未接）、P3 ✅、**P4 ✅**。

**下一步**：P5 提醒与备份（`RULES.md` §11 到期窗口与"每件每截止日最多说两次"、§12 导出/导入与四条拒；`UI.md` §7 设置页三节）。

---

## 2026-10-09 · P3 收口：详情页 + 点格子进详情（查找闭环完成）

**触发**：用户"继续推进"。

**交付**：

| 文件 | 内容 |
|---|---|
| `ui/detail/DetailViewModel.kt` | `load`（带 `loadSeq` 守卫）、三态分开（`loading` / `missing` / `failed`）、`askDelete`+`confirmDelete`（**成功后只置 `deleted`，不 reload**）、`askMove`/`move`（搬完重读）、`consumeDeleted`/`consumeNotice` |
| `ui/detail/DetailScreen.kt` | 顶栏（标题「物品详情」`maxLines=1`、左「返回」文字按钮、右三颗**全挂在 `summary != null`**）、三态互斥三句话、正文五块：名称（正文第一行、`ifBlank {"未命名物品"}`）→ 照片 `LazyRow`（200dp 1:1 `Crop`，没照片整块不给）→ 位置卡（`breadcrumb ?: "待归类（还没放好）"`，null 时加粗 + error 色；右边 `#tag`）→ 字段卡（**十行**：分类/季节/颜色/材质/品牌/别名/数量/到期/描述/备注；季节 CSV→顿号；数量仅 `n≠1`；`isNullOrBlank` 整行不给）→ 「更新于」；删完自己退屏（`consumeDeleted()` 再 `onBack()`） |
| 外壳 | 新增 `DetailKey(itemId)`；**格子点一下进详情**（图与名字都可点），详情页「编辑」才进确认页 |

**判绿双证**：**19 类 / 113 例**、`freshXml=19/19`、`failures=0 errors=0`、Spotless 通过。

**真机验证**（截图 `artifacts/screenshots/P3/device-detail-portrait.png`）：点第一格进详情 → 名称/位置卡（`家 / 衣柜` + `#自检种子`）/字段卡（分类 衣物、季节 冬、颜色 深灰、品牌 Uniqlo，空行整行不见）/更新于 2026-10-09；顶栏四颗齐在；「搬到」对话框列出默认项「暂不归类，先进「待归类」」+ 位置候选，选 `家 / 抽屉` 后详情页位置卡**即时刷新**；全程无崩溃。

**阶段状态**：`PLAN.md` §10 → P0 ✅、P1 ✅、P2 🟡（单件闭环；**AI 分析未接**）、**P3 ✅（查找闭环完成）**。
P3 里两处故意留给后面：**照片来源弹窗**（`UI.md` §2.5，要等 P6 的相机）与**排序说明**（`PRD.md` ⬜ FR-FND-09）。

**下一步**：P4 位置页（建/改/移/排序/删除门槛/位置照片，`UI.md` §4）——它是 `DELTA.md` 里唯一"参照有、我们还没有"的产品屏。

---

## 2026-10-09 · P3 照片墙（搜索 / 三层筛选 / 待归类 / 删除四档文案）

**触发**：用户"继续"。

**交付**：

| 文件 | 内容 |
|---|---|
| `ui/gallery/GalleryViewModel.kt` | 三段式搜索（SQL 取候选 → 谓词过滤 → 内存打分，仓库层一人做完）、三层筛选状态、`narrowing` 判据、⋮ 菜单 / 搬到 / 删除的动作，**刷新带 `refreshSeq` 守卫**（W6） |
| `ui/gallery/GalleryScreen.kt` | 顶栏（标题 + 「只看待归类」）、搜索框（逐字 label）、**三层筛选**（12 分类芯片 → 「更多筛选」→ 到期/季节/`#标签`，展开层限高）、**空态三句**、`GridCells.Adaptive(156dp)` 网格、单元（1:1 图 / 无图用名字占位 / 名字 + ⋮「操作」/ 位置行 null 时红字「待归类」/ 到期行 ≤30 天红字）、单元菜单、搬到框（默认「暂不归类，先进「待归类」」）、删除框（四档文案） |
| `ui/UiFormat.kt` | `deleteWarningOf` 四档 + `expireHint` 三句 + `expireLine`；`PendingDelete(itemId, warning)` 把 id 与那句话一起存 |
| `util/Dates.kt` | `expireWindowEnd`（唯一上界来源：`+1` 再 `-1`、`coerceAtLeast(0)`、`LocalDate` + 系统时区）、`daysUntil`、UTC↔本地零点 |
| 口径修正 | **`ItemDao.all()` 去掉 `ORDER BY`**（空查询时照片墙的次序就是插入序；编辑不挪位——`RULES.md` §10.1 明确不许想当然加 `updatedAt DESC`）；标签芯片补 `distinctBy { lowercase() }`；`photoDeletionOf` 两条路径都问、每类主人取 **max** 不取 sum |
| 外壳 | 「待归类」改成同一个屏幕的 `inboxMode`；开发期脚手架改由 `--es dev {selfcheck,design,seed}` 拉起，产品界面只在「设置」占位页留入口 |
| 开发期 | `DevSeedViewModel`/`DevSeedScreen`：一键生成 5 件**合成照片**的演示物品（不碰相册），可重复跑（幂等） |

**判绿双证**：**18 类 / 107 例**、`freshXml=18/18`、`failures=0 errors=0`、Spotless 通过。

**真机验证**（Xiaomi 13 / HyperOS；截图在 `artifacts/screenshots/P3/`）：5 件演示物品上墙（图、名字、位置行、到期行）；点「衣物」筛成 1 件（`深灰羽绒外套 / 家 / 衣柜`）；待归类只列"没位置或还是草稿"的件；删除框逐字为「照片和记录会一起删除，无法恢复。」；**重启后 5 件仍在**（库拉下来逐表点数：3/5/5/5）。

**四条新发现（`PLAN.md` §3.4 F18–F21）**：

1. **F18 一次"数据没落盘"的虚惊**：真机库里 `item` 0 行而界面有 5 件 → 查清是**开发期 seed 工具不幂等**（第二遍先删种子、再因"同级重名"抛错）。事务本身没问题：JVM 加了 `DurabilityTest`（磁盘库 + 关掉重开），真机 `pm clear` 后重跑一切正常。**教训**：开发期工具也要幂等；持久化必须用"关掉再打开"测。
2. **F19 待归类页只改了标题**：`inboxMode` 没带上 `onlyInbox` 谓词 → "待归类"把全库列了出来。已改 `setInboxMode(on)`（标题 + 谓词一起切），测试钉住。
3. **F20 刷新竞态**：VM 构造时那次刷新与用户刚点的筛选同时在飞 → 屏幕是没筛过的结果。加 `refreshSeq` 丢弃过期结果（正是 `DELTA.md` W6 那条）。
4. **F21 横屏问题**：只露图片、文字行被挤出、FAB 压图 → 记入 P6 自适应清单。

**阶段状态**：`PLAN.md` §10 → P0 ✅、P1 ✅、P2 🟡（单件闭环）、**P3 🟡**（照片墙已通；详情页与"点格子进详情"未做）。

---

## 2026-10-09 · P2 录入闭环（单件闭环已通）

**触发**：用户"按建议执行，没有需要拍板的地方就自动进行下去"。

**交付**：

| 文件 | 内容 |
|---|---|
| `domain/CaptureFlow.kt` | `capture(jpeg, onDraftSaved)`：**先落库**，回调里立刻给出真实 `itemId`（「稍后处理」的前提）；`replacePhoto` 走三步顺序。AI 分析留出接缝（P3 接，`analyzing` 恒 false） |
| `ui/capture/CaptureViewModel.kt` | 选图入口：照片一到就落库 |
| `ui/review/ReviewUiState.kt` | `ReviewSlot`（含 `isThrowawayShell`）+ 四本账（`notStored`/`leftBehind`/`alreadySaved`/`unsaved`）+ `canSave` |
| `ui/review/ReviewViewModel.kt` | 13 个字段编辑（都记 `touched`）、`save`/`keepForLater`/`discardCurrent`/`requestExit`/`cancelExit`/`exitWithoutSaving`/`exitKeepingEdits`/`closeWithoutAsking`、`consumeFinished` |
| `ui/review/ReviewScreen.kt` | 顶栏三颗（✕/稍后处理/保存这一件）、多件芯片排（`chipLabel` 带「● 」）、照片区（180dp / Fit / 「换一张」与「选择照片」两支）、AI 状态行（三句位置话 + 分析中那句）、**13 个字段按次序**、位置选择框、到期框（UTC→本地零点换算）、**退出框两支逐字文案** |
| `ui/inbox/InboxScreen.kt` + VM | 待归类列表与空态「没有待归类的物品，全都放好了。」（逐字） |
| `di/DataModule.kt` | Hilt 装配：DB / PhotoStore（`files/photos`）/ 两个仓库 / CaptureFlow |
| 外壳 | Nav3 增加 `ReviewKey(itemId)` 与 `InboxKey`；Home 变成 P2 入口（选一张照片 / 待归类 / 两个开发期脚手架） |

**判绿双证**：**14 类 / 85 例**、`freshXml=14/14`、`failures=0 errors=0`、Spotless 通过。

**真机自检 10 项全绿**（`artifacts/screenshots/P2/device-selfcheck-p2.png`，**用合成照片，不碰相册里的私人照片**）：

```
✅ 选图即落库：DRAFT + 照片行      ✅ 未保存的件出现在待归类
✅ 确认页保存：DRAFT → CONFIRMED   ✅ 确认但没选位置：仍在待归类（确认 ≠ 已归位）
✅ 选了位置再确认：离开待归类      ✅ 稍后处理：字段写了、状态还是 DRAFT、留在待归类
✅ 空壳回收：行与照片文件都清掉了  ✅（P1 四项：中文搜索/标签筛选/待归类谓词/引用计数）
```
另：选图器验证——点「选一张照片」拉起 `com.android.photopicker`，返回后回到本应用，**无崩溃**，且未选中任何私人照片。

**三条新发现（`PLAN.md` §3.4 F16/F17）**：

1. **F16「确认 ≠ 已归位」**：待归类的谓词是 `locationId IS NULL OR status = 'DRAFT'`，所以**确认了但没选位置**的物品仍在待归类——把它搬出去的动作是"选位置"，不是"保存"。这条是我在真机自检里判反了、被自检自己抓出来的（已改成两条断言）。
2. **F17 确认页的协程测试不能用虚拟时间的 `runTest`**：VM 在 `viewModelScope`（Main）而 Room 的挂起查询在别的线程完成，`runTest` 会提前结束；改成 `Unconfined` + 轮询等待。另有一条"用名字等待会匹配到旧槽位"的竞态。
3. 测试基础设施的这条规矩要沿用到后面所有 VM 测试（`TESTING.md` 的补充项）。

**阶段状态**：`PLAN.md` §10 → **P0 ✅、P1 ✅、P2 🟡**（多件录入、详情页/删除/搬移按切片留给 P2b/P3；AI 分析在 P3）。

---

## 2026-10-09 · P1 收尾：仓库层与领域层（P1 关单）

**触发**：用户"按建议执行，没有需要拍板的地方就自动进行下去"。

**交付（提交 `0ea72c7`）**：

| 文件 | 内容 |
|---|---|
| `data/repo/Models.kt` | `ItemDraft`/`Filters`/`ItemSummary`/`PhotoDeletion` + 三张词表（类别 12、季节 4、到期类型 4） |
| `data/repo/ItemRepository.kt` | `newDraft`（两行同生同死）、`newDraftSharingPhoto`（不写新文件）、`swapPhoto`（先写新→改行→回收旧）、`confirm`（名称必填 + `lastMovedAt` 三档 + 到期改了就重新武装）、`keepAsDraft`（位置不动）、`move`（空名不转 CONFIRMED）、`rankedSearch`、`summaries`、`rebuildSearchText`（只补算变化）、`photoDeletionOf`、`delete`（先删行再回收） |
| `data/repo/LocationRepository.kt` | 同级重排**按显示次序重编号**（不是交换）、`changeParent` 三道 require、`delete` 门槛 + `LocationNotEmptyException(childCount, itemCount)`、`setPhoto`（一位置一张） |
| `domain/LocationTree.kt` + `LocationTreePolicy.kt` | 全量入内存、孤儿当根、`seen` 断环、`selectablePaths`（**路径字符串 + 根不列**）、`descendantIds`（**含自己**） |
| `domain/SearchRanker.kt` | 五档打分：名字全等 100 / 前缀 80 / 包含 60 / 别名 40 / 派生列 20 |

**判绿双证**：**12 类 / 73 例**、`freshXml=12/12`、`failures=0 errors=0`、Spotless 通过。

**本轮踩到并已入档的四条（`PLAN.md` §3.4 F12–F15）**：

1. **F12 Room 3 的事务 API 改名**：`withTransaction` → **`withWriteTransaction`**；`try/catch` 里要显式 `<Unit>`。
2. **F13 KDoc 里写 `/**` 会让整个文件变成未闭合注释**（Kotlin 块注释可嵌套）——中文里紧跟斜杠的加粗要留空格。
3. **F14 `spotlessApply` 不能和编译放在同一次 Gradle 调用里**（并行执行 → 编译读到半截文件，报出文件末尾之外的行号）。
4. **F15 位置树语义要按参照逐条对齐**：`selectablePaths()` 是**路径字符串且根不列**、`descendantIds()` **含自己**——我第一版理解反了，3 条测试红，读参照 `LocationTree.kt` 后修正。

**阶段状态**：`PLAN.md` §10 → **P0 ✅、P1 ✅**（`applyAnalysis` 的 AI 字段合并按计划并入 P3）。下一步 **P2 录入闭环**。

---

## 2026-10-08 · P1 数据与照片层 + 参照实现装进真机（截图对比基线就位）

**触发**：用户拍板"开 P1"与"参照 APK 走复制源码到临时目录构建"。

### 1. P1 数据与照片层（提交 `bd3b315`）

| 交付 | 内容 |
|---|---|
| 四张表 | `location_node` / `item` / `item_photo` / `item_tag`，与参照**逐字段一致**（见下） |
| 三个 DAO | `LocationDao` / `ItemDao`（含 `searchLike` 的 **LIMIT 300**、`inbox`、`moveTo` 时间戳同源、`itemIdsWithTag` 大小写不敏感）/ `PhotoDao`（含 `refsTo`、`otherOwnersOnPath`） |
| 派生搜索列 | `SearchTextBuilder.FIELD_VERSION = 2`，拼接顺序照 `ARCHITECTURE.md` §4.4（**标签插在 aliases 与 description 之间**） |
| 照片三档 | `PhotoStore`：bounds 采样解码 → **EXIF 七种方向烤进像素** → 1600/q85 原图 + 256/q80 缩略图 + 1024/q80 分析图（不落盘）；失败自清理 |
| 引用计数回收 | `deleteUnreferencedPhotoFiles` 作为唯一规则；调用契约"先删自己的行再问"用测试钉住 |
| 有意偏差（`DELTA.md` W2） | **不写 `fallbackToDestructiveMigration`**——版本一升就必须交 `Migration` + 迁移测试 |
| 测试 | **8 类 / 32 例全绿**（判绿双证：freshXml 8/8、tests=32 failures=0 errors=0） |

**F11（硬证据）**：把本实现与参照各自导出的 `app/schemas/…/1.json` 逐字段比对——**4 张表、20/7/2/7 个字段的列名/类型/非空/主键/外键/索引全部相同**。这是"旧备份包能导进新实现、新备份包也能导回参照"的前提。**以后每次改 schema 都要重跑这次比对。**

**F10（选 3.x 的学费）**：Room 3 把类型转换注解改名成 `@ColumnTypeConverter` / `@ColumnTypeConverters`（`androidx.room3` 里根本没有 `TypeConverter`），且没有 `room3-ktx`；写错时的报错是**极其笼统**的 `[MissingType]: Element 'X' references a type that is not present`。已写进 `TECH_STACK.md` §8.10。

**真机自检（P1 出口判据"EXIF 真机正确"）**：新增开发期脚手架 `DataSelfCheckScreen`（用独立 `selfcheck.db` + `cacheDir`，**不碰用户数据**，跑完清理），在 Xiaomi 13 / HyperOS 上跑真 Android 运行时，7 项全绿：

```
✅ 中文搜索命中「外套」：1 条（真机 SQLite）
✅ 标签筛选命中（大小写不敏感）
✅ 归位后不在待归类里
✅ 引用计数：共用时 2 → 删一条后 1
✅ EXIF 方向已烤进像素：800×400 存出来是 400×800
✅ 缩略图长边 256 ≤ 256
✅ 分析图只在内存里：400×800，2 KB
```

截图：`artifacts/screenshots/P1/device-selfcheck-portrait.png`。

### 2. 参照实现装进真机（对比基线）

- 源码**只读**复制到 `.reference/wheremy`（gitignore）→ 副本内用 PATH 上的 Gradle 8.14.3 构建（参照无 wrapper）→ `BUILD SUCCESSFUL`，APK **18,960,302 字节** → 装成 `com.wheremy.app`，与本实现的 `com.wheremy.app.dsh` **同机共存**。
- 采到四张空态截图（`artifacts/screenshots/REF/`，每张配同名 `.xml` 原始 dump）：照片墙空态、待归类空态、设置页、位置页空态。逐屏文字见 `DELTA.md` §3。
- **原目录零改动**：`git status --porcelain` 空、`log -1` 仍是 `ce6d343`，并以时间戳递归扫过原目录（排除 `.git`）无任何新写入。
- 过程账：`artifacts/REF-build-report.md`；复用脚本：`artifacts/tools/{miui-install,capture,tap-text}.ps1`。
- 顺带一条坑：**`adb exec-out screencap -p > file` 在 PowerShell 里可能把 PNG 当文本损坏**，改用 `adb shell screencap -p /sdcard/x.png` + `adb pull` 更稳。

### 3. 阶段状态

`PLAN.md` §10：**P0 ✅ 完成**、**P1 🟡 进行中（仅差仓库层 `ItemRepository`/`LocationRepository`）**。

---

## 2026-10-08 · Phase 0 补测：真机验证通过（10/10 步完成）

**触发**：用户完成手机端设置后要求继续真机相关工作。

**结论：Phase 0 十步全部通过，P0 可以关单**（唯一遗留是 CI 还没在真 GitHub 上跑过一次，留待首次推送）。

**最大的一条：F9 的真相被查明，且与原先的判断完全不同。**
装包一直报 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`，看着像"USB 安装权限没开"。实际用 `uiautomator dump` 一读：**「USB安装」与「USB调试（安全设置）」两个开关本来就是 `checked=true`**。真因是 MIUI 会弹一个 **「USB安装提示 · 正在通过USB安装此应用，是否继续？」**，而「拒绝」带 **3 秒倒计时**——`adb install` 期间没有人在手机上点，于是自动拒绝并回那句"canceled by user"。
**破法**：后台起安装 → 约 1.8 s 后 dump 出「继续安装」按钮的实时 `bounds` → `input tap` 其中点 → `Success`。
（`pm install`、`install-create/write/commit` 三条路都被同一个弹窗拦，绕不过去。）已写成 `REBUILD.md` §7 **D7**，供真机验收清单复用。

**真机证据（Xiaomi 13 / HyperOS V816 / `1080×2400 @440dpi`）**：

| 项 | 结果 |
|---|---|
| 安装 | `Success`（`pm path com.wheremy.app.dsh` 可查到） |
| 启动 | 冷启动 `TotalTime 569 ms`（竖屏）/ `619 ms`（横屏），`Status: ok` |
| 存活 | `topResumedActivity` = 本应用；`pidof` 有进程；`logcat -b crash` **为空** |
| 导航 | 点「看设计系统」进入画廊；**返回键回到上一屏且进程不退**（Nav3 `onBack` 生效） |
| 截图 | 真机 4 张（Home/画廊 × 竖/横）+ Roborazzi golden 2 张，均落在 `artifacts/screenshots/P0/`（不进 git） |
| 附带发现 | 本实现**不锁竖屏**（参照实现锁竖屏 ⛔FR-PRM-04）：横屏能正常渲染，但布局尚未自适应 → 留给 P6 的自适应项 |

**同步更新的文档**：`PLAN.md`（§3.4 第 10 步改 ✅、F9 改写成查明真相的破法、§10 P0 置 ✅）、`DELTA.md`（§3 补环境快照与 P0 两行截图对比）、`REBUILD.md`（新增 §7 D 组 D7 真机坑）。

**下一步**：进 **P1（数据与照片层）**——按 `RULES.md` §2 建四张表、照片三档（原图/缩略图/派生）、引用计数回收、EXIF 烤平、派生搜索列；出口判据在 `PLAN.md` §5。

---

## 2026-10-08 · Phase 0 开工：工具链落地与实测

**触发**：用户拍板"立即执行 Phase 0"，并选定首版纳入 6 条 ⬜（`PLAN.md` §2.4 建议档）。

**动作**：在本工作区（**不碰参照仓库**）从零搭出可运行、可测、可回归的工程骨架，并按 `PLAN.md` §3.3 的 10 步逐条验证。首个提交 `4628a9b`。

**结论：现行代际组合可用，不需要回退阶梯的第 ②/③ 档。**

| 结果 | 内容 |
|---|---|
| ✅ 9 步通过 | `assembleDebug`（APK 12.9 MB）、6 例测试（JVM + Robolectric + Room3）、Spotless、Hilt+KSP、Nav3 往返、Room 3 验证门、Roborazzi 两张 golden、wrapper + CI 文件 |
| ⛔ 1 步阻塞 | 真机安装被 HyperOS 拦：`INSTALL_FAILED_USER_RESTRICTED` → **待用户开启「USB 调试（安全设置）/ 允许 USB 安装」**；在此之前真机项记 `⏳ 待验`，不算通过 |
| ⏳ 未执行 | CI 只在本地写好，**未在真 GitHub 上跑过** |

**实测发现 F1–F9（详见 `PLAN.md` §3.4）**，其中三条会改变后续开发：

1. **F1（改变技术基线）**：Compose 1.12.1 要求 `compileSdk ≥ 37`，参照实现的 36 不够 → 本实现定 **compileSdk 37 / targetSdk 36**。这是"比参照新一档"的第一条硬证据。
2. **F2（改变测试配置）**：Robolectric 4.17 在 JDK 17+ 必须加 `--add-opens`（`java.lang`/`java.util`/`java.io`/`jdk.internal.access`），否则所有 Robolectric 测试报 `Failed to interact with raw FileDescriptor internals`。
3. **F3（改变静态检查配置）**：ktlint 的 `function-naming` 会误报 Compose 的 PascalCase 函数；且 Spotless 在临时目录跑 ktlint，根 `.editorconfig` 传不进去 → 用 `editorConfigOverride` 放行。

其余：F4 本机 init 脚本（阿里云镜像）迫使 `RepositoriesMode.PREFER_SETTINGS`，同时把镜像显式写进 `settings.gradle.kts`；F5 AGP 9 需要 Gradle ≥ 9.1（本机 PATH 是 8.14.3）→ 必须先有 wrapper；F6 `androidx.room3` 没有 `room3-ktx`、catalog 别名不能含 `class`；F7 Compose 测试规则已换 `…junit4.v2.createComposeRule`；F8 Nav3 的 `entry` 不需要 import；F9 见上表阻塞项。

**同步更新的文档**：`PLAN.md`（§3.1 技术基线表两处按实测修正、新增 §3.4 实测结果、§10 阶段记录表 P0 置 🟡）、`DELTA.md`（§4 工程对比 11 行从"目标"改成"现状 + 证据"）、`README.md`（文档地图已含 PLAN/DELTA）。

**下一步**：① 用户开启真机安装权限后补第 10 步（顺带采集本实现首张真机截图）；② 进 P1（数据与照片层，`RULES.md` §2 四表 + 照片三档 + 引用计数）。

---

## 2026-10-08 · 第三轮：联网复核 + 开发计划与差异台账

**触发**：用户开通搜索（`DEEPSEEK_API_KEY`）后要求"用这个过程验证文档里的最佳实践"，并追问"能否进入开发阶段、先写代码还是先做 UI、后续总流程给建议"；同时明确三条边界：**参照仓库只读**、**在本工作区独立开发**、**最终与参照实现逐项对比**。

### 1. 技术选型复核（`TECH_STACK.md` §8.9）

- **工具实况**：`web_search` 间歇可用（前 3 次成功，之后连续 `fetch failed`）；`web_fetch` 仍被"非公网 IP"挡住；**主力是 `curl`**——`api.github.com` / `raw.githubusercontent.com` / Google Maven / Maven Central / services.gradle.org / plugins.gradle.org 全部 200。
- **确认**：Compose BOM `2026.09.00`、`navigation3-runtime` `1.2.0`（其后 `1.3.0-alpha02`）、activity `1.13.0`、lifecycle `2.11.0`、KSP `2.3.12`、Hilt `2.60.1`、ktlint 插件 `14.2.0`、Spotless `8.10.3`、Coil `3.6.3`、OkHttp `5.5.0`、WorkManager `2.12.0`、`nowinandroid` 仍在活跃维护。
- **修正**：Gradle current = **9.8.1**（原写 9.8.0）；AGP 稳定线 **9.4.1**；Kotlin 稳定线 **2.4.20**。
- **新增发现**：① **Room 有 3.x 线且已 stable**（`androidx.room3:room3-runtime` `3.0.3`，KMP 结构；2.x 线仍是 `2.8.5`）→ 新实现选哪条线做成 Phase 0 的验证门；② 解析 Compose BOM POM 得到映射：`material3 = 1.4.0`（**M3 Expressive 那一代**）、`material3-adaptive-navigation-suite = 1.4.0`、`ui/foundation/runtime = 1.12.1`；③ 官方新增 Nav3 样板仓库 `android/nav3-recipes`（1,391 star，2026-10-06 仍在推）；④ 官方 UI 样例可作对照源（`compose-samples` 23,498 star、`androidify`、`codelab-android-compose`）；⑤ FTS `trigram` 的 CJK 退化被第三方独立复现（OpenClaw 的 issue + 修复提交）→ 印证"中文不上 trigram"。
- **环境就绪度实测**：JDK Temurin 21.0.8；SDK 平台 `android-29/34/35/36`、build-tools `36.1.0`；真机 `9aa4b43e` 在线；六个依赖源可达。

### 2. 新增两份文档

| 文档 | 内容 | 为什么必须有 |
|---|---|---|
| **`PLAN.md`** | 就绪判定（六项全过 → **可以开工**）、技术起点（现行代际：AGP 9.4.1 / Gradle 9.8.1 wrapper / Kotlin 2.4.20 / KSP 2.3.12 / BOM 2026.09.00 / Nav3 1.2.0 / Hilt 2.60.1；Room 2/3 由 Phase 0 验证门决定）、**"先做可运行的 UI 骨架 + 设计系统，再按纵向切片推进"**的结论与反例、阶段 P0–P6 与出口判据、与参照实现的对比方法（双包并存 + 截图对照 + 四类对比）、工作纪律（**参照仓库绝不修改、也不在其目录执行写盘命令**）、阶段记录表 | 用户要的"能否开工 + 总流程建议"必须落在文档里，否则下一次又要重新讨论 |
| **`DELTA.md`** | 87 条 FR 的对比矩阵（**从 `PRD.md` 自动生成**，防止手抄漂移）+ W1–W11 细节清单（本实现逐条修掉）+ 界面截图对比表 + 工程对比表 + 防漂移规则 | 用户明确要求"最终要和参照实现对比"；差异必须有单一台账，且每格都要有证据 |

### 3. 结论口径

- **可以进入开发阶段**：六项就绪判据全部通过，唯一的前置是 Phase 0 的工具链 spike（含三级回退阶梯）。
- **先 UI 还是先代码**：都不是——先"可运行的 UI 骨架 + 设计系统"，随后纵向切片；理由与三个反例写在 `PLAN.md` §4。
- **与参照实现的关系**：文档集是共同基线（产品/规则/界面语义跨实现不变）；**代码独立编写**；as-built 类文档仍描述参照实现，本实现的现状记在 `DELTA.md`，收尾时再一次性回写 `PRD.md` 状态列（`PLAN.md` §6.4）。

---

## 2026-10-07 · 文档集审查与扩充（本轮）

**触发**：用户要求"先审查 `docs/` 下的文档，改掉错误、缺失与矛盾；不够就补新文档，形成一套完整、能支撑开发/测试/迭代/发布的文档，并覆盖项目经理、软件经理、架构师、开发、测试、UI 设计各角色"；
中途追加两条准则：**①技术架构与选择优先官方或成熟第三方的最佳实践，尽量不自己造轮子；②多从网络（GitHub 等技术网站）搜集，找出最佳组合。**

### 1. 核对既有五份 as-built 文档（`PRD.md` / `ARCHITECTURE.md` / `RULES.md` / `UI.md` / `REBUILD.md`）

**核对方式**：以实现仓库的代码基线 `cf73f5b` 为事实源（该基线与实现仓库当前 HEAD 的 `app/` 逐字节一致），逐条比对文档里的 `文件:行`、常量、计数、SQL 语义、顺序不变量与逐字文案；四路并行核对（ARCHITECTURE / RULES / UI / PRD+REBUILD），只读不改。

**核对结论（量化）**：

| 文档 | 逐字文案 | 行号引用 | 发现的实质错误 | 说明 |
|---|---|---|---|---|
| `UI.md` | 119 条全部命中（仅 1 处引文缺字符） | 186 条精确引用逐条比对 | 4 处（计数口径 2、测试结论 1、引文 1）+ 3 处内部矛盾 + 1 处枚举漏项 | 屏幕结构、字段次序、返回键优先级全部与源码一致 |
| `ARCHITECTURE.md` | 引文全部命中 | ≈250 条 | 6 处（1 处依赖方向、1 处计数、1 处"唯一 lowercase"过度声称、1 处订阅者、1 处列名、1 处参数名）+ 3 处跨文档引错 | 57 个文件行数、版本表、事务边界、顺序不变量逐条对得上 |
| `RULES.md` | 40+ 条逐字串全部一致 | 多处 | 8 处（1 处实质事实错误、3 处引文不逐字、4 处跨章引用）+ 11 处行号漂移 | 阈值、谓词、状态机、11 条失败文案、4 档删除话术全部正确；**W1–W11 在基线上仍然成立** |
| `PRD.md` / `REBUILD.md` | 引文全部命中 | ~100 处抽查 | 8 处（含 1 处状态标记错、2 处可达性/存在性声称错、1 处坑已失效）+ 4 处缺失 + 3 处矛盾 + 4 处行号 | **两套例数分法都对账通过**：`PRD.md` §7 按域 44+38+17+53+12+7+32+20+10+20+27+30+18+78+38+19+9+9 = 481；`REBUILD.md` §3 按里程碑 1+105+65+100+104+84+22 = 481，类数 1+9+5+4+10+5+5 = 39，逐类例数一致 |

**已修正的关键问题**（每条都在原文里改掉，不是只在日志里记一笔）：

1. **`RULES.md` §5.7 的实质事实错误**：原写"词表外的类别/季节……输入关键词仍然搜得到"，与源码（`category`/`seasons` 不进 `search_text`）和本文 §10.3、`PRD.md` FR-AI-11 三处对撞。**已改成"既筛不到、也搜不到"**，三处口径恢复自洽。
2. **`UI.md` §5.2 的测试结论已被证伪**：原写"`DetailScreenTest` 没有断'点删除之后回到了上一页'"，实际 `DetailScreenTest.kt:254` 就断言了 `backCount == 1`。**已改成"结果被钉住、没钉住的是 consume 与 onBack 的先后"**。
3. **两处界面计数错**（照抄会多摆一颗按钮，按文字断言的测试立刻红）：`UI.md` §3.6"正文三行"→"正文两颗"；§6.1"右边三颗"→"右边两颗"。
4. **取景屏接线口径**：`UI.md` §2.6"六个回调"→"八个参数（`gate` 是状态、其余七个是回调）"。
5. **状态恢复清单漏项**：`UI.md` §2.1"三格刻意不是 saveable"→"四格不是 saveable（三格刻意）"；§12 的"九格"改指"§2.1 八格 + §7 的 `pendingMode`"。
6. **`RULES.md` §12.3 标题少算一条**："三条拒 + 一条放行"→"**四条拒** + 一条放行"（与 `PRD.md` FR-BAK-06 一致）。
7. **引文不逐字三处**（提示词与解析器的依据）：`brand` 一句补回限定、`container_guess` 整句补全、回显 JSON 补回空格。
8. **跨文档/跨章节引错四处**：`RULES.md` 的"FR-FND-03"改指成功判据 G3；`spec §7`→`spec §6.6`；"spec §8 的 NFR-02"→`PRD.md` NFR-02；§16.N 的约定改指 `REBUILD.md`。
9. **`ARCHITECTURE.md` 的两处依赖事实**：`util/Dates.kt` 是"`domain/` + `ui/` 两层调用"（`data/` 零引用）并补上 `localMillisToUtcPicker`；"全库唯一的 `lowercase()`"改成"写入侧唯一、读侧另有 5 处"。
10. **`Models.kt` 类型数**："五个类型"→"四个类型"（与该节自己的表格一致）；DAO 的 SQL 列名 `search_text`→`searchText`；`LocationPickerDialog` 参数 `targets`→`options`；`changes` 流的"没有订阅者"限定为"生产代码里没有"。
11. **接口与文档的失修**：`RULES.md`/`UI.md`/`ARCHITECTURE.md` 里 20 余处行号按基线修正，并统一了两份文档对同一区间不一致的写法（`ReviewScreen.kt` 的 AI 状态行、`isNarrowing` 的定义区间等）。
12. **基线表述**：把"本机 `HEAD = cf73f5b`"改成"代码基线 `cf73f5b`（其后的提交只动 `docs/`）"——避免与实现仓库 HEAD 混淆。
13. **`PRD.md` 的状态标记错**：`FR-TRC-05`（"留痕里加耗时与 **HTTP 状态**"）记作 `⬜`，但 HTTP 状态**早在留痕里**（`AnalysisTrace.kt:16` 的 `status: Int?`，`ApiPhotoAnalyzerTest.kt:319` 断言 `[500, 200]`）→ 状态改 🟡，条目收窄为"加耗时"，并补进 §5.1。
14. **`PRD.md` 的可达性声称错**：`FR-PRM-05` 说"网格里 48dp 缩略图与芯片组没有 `contentDescription`"，而三处缩略图与网格图**都有**（`ItemRow.kt:44`、`GalleryScreen.kt:282`、`TreeScreen.kt:258`）→ 偏差收窄为"筛选芯片组 + 焦点次序"。
15. **`PRD.md` 的引用/编号/引文错**：`Entities.kt:11`（那是 `PhotoOwner` 枚举）→ 字段在 `:71`；`Models.kt:55 EXPIRE_KINDS` → `:55 CATEGORIES`/`:59 SEASONS`/`:61 EXPIRE_KINDS`；`FR-DB-01` 的"原话"补全为"Plan 1 期间允许**破坏性迁移（尚无真实数据）**，发布前换成真实 Migration"；节点菜单「操作」→「位置操作」（`TreeScreen.kt:279`，与照片墙那颗「操作」区分）；`SettingsViewModel.kt:279`→`:278`；`AppContainer.kt` 的 `ai-trace` 字面量在 `:72`。
16. **`PRD.md` §5 与 §3 的口径**：标题从"⬜ 一览"改成"⬜ 与 🟡 一览"并说明表里混着两类；补进漏收的 `FR-FND-08`；§8"其余四份文档"改写成按角色指路（完整地图指向 `README.md` §2）。
17. **`PRD.md` §7 的覆盖缺口**：`ItemRow` 一行原写"在 `UiFormatTest`/各屏里断"，实测 `app/src/test` 里 grep `ItemThumbRow` **零命中** → 照实标成覆盖缺口（`TESTING.md` §5 同步记录）。
18. **`REBUILD.md` 的四处失修**：`WheremyRoot.kt:76-93` 不是"九格全 saveable"（8 saveable + 3 刻意 `remember`）；`deliverPhoto` 是 `:123-145`；`UiFormat` 的 `failureText`/`deleteWarningOf` 区间；`ReviewViewModel` 三计数在 `:70/77/84`（不是 `:415-421`）。
19. **`REBUILD.md` 的失效坑**：§7 D5 / §3 M6 / §6 第 1 行都在讲 `FileProvider.getUriForFile` 在 Windows 必抛——但仓库里 grep `FileProvider`/`getUriForFile`/`file_paths` **零命中**（相机走应用内 CameraX + 文件路径，不产 content URI）→ 标成"历史坑，现在不适用"，并写清它何时会复活。
20. **`REBUILD.md` 的可达性声称错**：§4 第 5 条说「打开相机权限」"在真机上根本走不到"，而 `gate == NeedsPermission` 时它**会**渲染（`CameraScreen.kt:65-68`）→ 改成"两处各判一次权限、结论必须一致却没有保证"。
21. **`REBUILD.md` 的工具链事实**：`sdkmanager` 并不存在（SDK 里没有 `cmdline-tools`）→ §8 与 §7 B8 改成"`adb` 用绝对路径；没有 `sdkmanager`，别写进脚本"；"没有装 Android Studio"改成"构建全走命令行 Gradle"。
22. **`RULES.md` §12.3 的第五道拒**：补上读包阶段的版本拒（`manifest.schemaVersion > DB_SCHEMA_VERSION`，`BackupRepository.kt:139-141`），说明它不在 `validate()` 里——"旧包必须还能导入"是两处的合起来。

### 2. 补齐被引用的历史材料

`PRD.md`/`RULES.md`/`ARCHITECTURE.md`/`REBUILD.md` 全文大量引用 `docs/superpowers/` 下的 spec 与 plan（"spec §N"共几十处），但该目录在本工作区**缺失**，引用全部悬空。

**动作**：把这四份历史文件（2 份 spec + 2 份 plan）按字节复制进 `docs/superpowers/`，并在 `README.md` §8 说明"spec 是规范来源之一、plan 只是过程记录、两者不参与冲突裁决"。
**代价**：文档集体积增加约 615 KB；换来的是"spec §N"这类引用第一次变得可点、可查。

### 3. 新增角色与流程文档（本轮的主要产出）

原有五份文档回答的是"产品要什么、代码长什么样、规则怎么算、界面怎么摆、怎么重建"，**缺少"谁做、什么时候做、怎么走流程、怎么测、怎么发布、怎么选型"**。新增：

| 新增 | 角色 | 回答什么 |
|---|---|---|
| `README.md` | 全员 | 文档地图、角色导航、唯一事实源、冲突裁法、三条阅读路线、四种迭代入口 |
| `PROJECT.md` | 项目经理 / 软件经理 | 范围、RACI、里程碑（含新增 M7 发布就绪）、迭代节奏、DoR/DoD、度量、**风险登记 R1–R14**、假设与依赖 |
| `PROCESS.md` | 软件经理 / 全员 | **八条变更通道**（新增/修改/删除/补细节/重构/升级依赖/schema 与备份/发布）、文档同步矩阵、**质量门 G0–G5**、缺陷流程、提交与评审、例外与技术债 |
| `TESTING.md` | 测试 | 五层测试（纯 JVM/数据/系统件/Compose UI/真机）、每层测得到与测不到、本项目的测试写法、**变异验牙**、双向覆盖对账、判绿操作手册（含 PowerShell 版）、缺陷与真机验收规范、测试数据隐私 |
| `DEVELOPMENT.md` | 开发 | 环境与命令、代码地图、编码纪律（"不"清单）、依赖评审、调试、扩展点清单（加字段/加筛选/AI 字段/失败原因/加屏/通知/备份格式/后台任务）、排障、提交前自检 |
| `RELEASE.md` | 软件经理 / 发布 | 版本策略与兼容矩阵、⭐发布前必做、G0–G5 证据要求、迁移与兼容、构建与分发、回滚与事故处置、发布后跟踪、可执行清单 |
| `DESIGN_SYSTEM.md` | UI / 交互设计 | 设计原则、色彩语义、字号与排版、间距与尺寸 token、组件库、状态设计、图标与品牌、无障碍、交付物与"改视觉时不许跟着变"的对照表 |
| `TECH_STACK.md` | 架构师 | **技术选型准则（官方/成熟优先，自造要证据）**、现状对照（✅/🟡/🔒/⬜）、反模式清单、迁移队列 T1–T15、依赖评审流程、**§8 联网核实结果与引用来源** |
| `DECISIONS.md` | 架构师 / 全员 | ADR 流程与 24 条决策记录（D1–D14 + 10 条提炼）、被替代与已废止记录、复核触发条件、常见误读 |
| `GLOSSARY.md` | 全员 | 业务词 ↔ 代码符号 ↔ 库表列对照 |
| `templates/`（8 张） | 全员 | 功能提案、变更请求、ADR、迭代计划、测试计划、缺陷单、发布清单、真机验收单 |
| `CHANGELOG.md`（本文） | 全员 | 文档集的变更记录 |

### 4. 准则变更：技术选型（用户拍板）

**新准则**：官方推荐的最佳实践优先 → 成熟第三方 → 尽量不自己造轮子；自造必须留证据（"找过谁、为什么不用、代价是什么"）并给复核触发条件。
**落点**：`TECH_STACK.md` §1 的五条准则（P1–P5）、`PROCESS.md` §3.6 的依赖更新通道、§5 同步矩阵新增两行（加依赖 / 自造新机制）、`PROJECT.md` 风险 R13、`README.md` 的准则与导航。
**现状判定**：数据库（Room）、偏好（DataStore）、后台（WorkManager）、相机（CameraX）、序列化（kotlinx.serialization）、HTTP（OkHttp）、图片加载（Coil）、测试（JUnit/Robolectric/Compose UI test/mockwebserver）**已经就是官方或社区最佳实践**；真正自造的只有 **DI、导航、文案资源、搜索** 四处 + 若干无官方对应物的（备份包格式、照片管线、留痕、位置树），后者已逐条给出"为什么保留 + 复核触发条件"。
**迁移队列**：T1 文案资源化 → T2 Hilt → T3 Navigation 3 → ~~T4 FTS/trigram~~（**2026-10-07 联网核实后改为"默认不做"**）→ … → T9 静态检查 → T10 wrapper+CI → T11 截图回归 → T12 纯版本 bump → T13 AGP9/Gradle9/Kotlin2.4 → T14 性能发布配置 → T15 依赖更新自动化（各带触发条件、成本与前置），**不自动推翻任何既有 ADR**：被取代的条目要新写一条 ADR 并标明取代者状态。
**证据强度**：`TECH_STACK.md` §8 把结论分成四档并在**同一天完成了一轮真实联网核实**（工具被挡时改用 `curl` 直连包仓库元数据与 GitHub，版本号逐项实测、本机对 Room/OkHttp/Coil 做过二次复核）：高（联网核实）/ 高（实测）/ 高（本地实证）/**未验证（§8.6 单列，不许当结论引用）**。

#### 联网核实改动了什么（同日追加）

| 改动 | 内容 |
|---|---|
| **一条判定翻转** | 搜索**不上 FTS**：`trigram` 官方原文写明"少于 3 个 unicode 字符的子串在全文查询里不匹配任何行"，而中文主场景是 1–2 字词；`trigram` 还需 SQLite ≥ 3.34（minSdk 26 拿不到，要自带 `androidx.sqlite:sqlite-bundled`）；Room 只有 `@Fts3/@Fts4`。→ T4 从"可迁移"改为**默认不做**，`ADR-01` 的结论由"够用"升级为"对中文更合适" |
| **两条目标修正** | ① 导航迁移目标改为 **Navigation 3**（1.2.0 于 2026-09-23 标注 stable，官方示例已迁，避免二次迁移）② **CI 的前置是提交 Gradle Wrapper**（新增 T10），与本机"不生成 wrapper"的现状并存——已在 `REBUILD.md`（§3 M0、§7 A5、§8 第 1 条）、`PRD.md` NFR-06、`DEVELOPMENT.md`、`ARCHITECTURE.md` §2、`RELEASE.md` §6.1 各补一句"加 CI 时另说" |
| **七个新待办** | T9 静态检查（ktlint+Spotless，**不引 detekt**）、T10 wrapper+CI、T11 Roborazzi 截图回归、T12 纯版本 bump、T13 AGP9/Gradle9/Kotlin2.4（**必须同批**）、T14 R8/Profiles、T15 依赖更新自动化（Renovate **或** Dependabot，只上一个） |
| **版本对照入档** | `TECH_STACK.md` §8.3 逐项列出"本仓库 vs 上游最新 stable"（Room/DataStore/CameraX 已是最新 → 不动；WorkManager/Coil/OkHttp/serialization/coroutines/Robolectric/Compose BOM/lifecycle/activity/exifinterface 有落后 → T12） |
| **明确"不改"的清单** | 官方文档佐证了若干现状就是最佳实践：`java.time`（minSdk 26 原生，别加 desugaring）、`BitmapFactory` + `inSampleSize`（`androidx.graphics` **不是**压缩库，已解包 AAR 核实）、手写 DI（官方明确允许）、JVM + Robolectric（官方本地测试路径）、"fakes over mocks"（与手写替身方向一致） |
| **复核节奏** | §8.8：每季度或每次动 T9–T15 前，用三条现成 `curl` 重跑版本实测；结论变了要写 ADR，不许只改表格 |

### 5. 本轮没有做的事（明确写下，避免以为是漏了）

- **没有改一行代码**：本工作区只有文档，所有修正都落在文档上；代码侧的动作（如 T1–T8 迁移、⭐FR-DB-01/⭐FR-RLS-01）需要实现仓库里的独立迭代。
- **没有把"已知偏差"（`RULES.md` §15 W1–W11）当错误改掉**：它们是**记录在案的现状**，修它们要走变更通道（`PROCESS.md` §3.2），不是校对。
- **没有为新文档编造任何事实**：新增文档里的每个数字、行号、逐字串都以基线源码或既有文档为依据；发现既有文档互相矛盾时，先在文档集里判明并修掉，未判明的一律写进对应文档的"发现的不一致"一节。

### 6. 遗留与下一步

| # | 事项 | 去处 |
|---|---|---|
| 1 | 技术迁移（T1–T8）**不占用 FR 编号**：它们的权威队列在 `TECH_STACK.md` §4（各自带触发条件、成本、前置），`PRD.md` §5 不再收录一份副本——遵守"一份信息只有一个权威出处"（`README.md` §3.1）。要动用某一条时，按 `PROCESS.md` §3.5/§3.6 开一次迭代 | `TECH_STACK.md` §4 + `DECISIONS.md` §6 |
| 2 | 实现仓库的 `docs/` 与本工作区的文档集当前**同源但已分叉**（本工作区新增 11 份文档、补回 `superpowers/`、并修正了五份 as-built 文档的若干处）；合并方向与时机待用户决定 | 本文 + 用户 |
| 3 | **联网核实已做了一轮，但要定期重跑**：`web_search`（缺 key）与 `web_fetch`（多个域名被判 `non-public IP`）都不可用，改用 `curl` 直连 Google Maven / Maven Central / Plugin Portal / GitHub 拿到实测版本与官方口径，结果全部落在 `TECH_STACK.md` §8。**三条判断被更正**：① 搜索不上 FTS（`trigram` 对 <3 个 unicode 字符的查询"不匹配任何行"，中文 1–2 字词是主场景）② 导航迁移目标改为 **Navigation 3**（1.2.0 已 stable）③ CI 的前置是**提交 Gradle Wrapper**（与本机"不生成 wrapper"并存）。**新增待办** T9 静态检查、T10 wrapper+CI、T11 Roborazzi 截图回归、T12 纯版本 bump、T13 AGP9/Gradle9/Kotlin2.4 大版本、T14 R8/Profiles、T15 依赖更新自动化；**未验证项**（KSP 兼容矩阵、`Room.Fts5` 是否存在等）单列在 §8.6 | 复核节奏见 `TECH_STACK.md` §8.8：每季度或每次要动 T9–T15 之前，用 §8.3 的两个元数据入口重跑版本实测 |
