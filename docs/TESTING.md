# Wheremy 测试策略与执行手册（TESTING）

**这份文档回答**：测什么、在哪一层测、怎么算绿、怎么证明测试真有牙、缺陷怎么管、真机怎么验收。
**它不回答**：规则本身（`RULES.md`）、流程门禁（`PROCESS.md` §6）、每条需求对应哪个测试类（`PRD.md` §7）。

**基线**：39 个结果类 / 481 例（另有 3 个不带 `@Test` 的 helper），全部跑在 JVM 上。
**读者**：测试人员、开发（自测）、以及任何要判断"这批改动能不能合"的人。

---

## 0. 三条原则

1. **判绿不认 `exit code`，认双证**：`BUILD SUCCESSFUL` **且** 本轮 XML 的 mtime 晚于本轮起跑时刻，计数自己数一遍（§6）。
   理由：这个项目上真出现过"exit 0 + 空输出 + 读的是上一轮陈旧 XML"的伪绿（`REBUILD.md` §1.1）。
2. **全绿不等于有牙**：每批改动都要做一次变异验牙（§4）。历史上一批 12 次变异里有 2 条真的无牙。
3. **能测的都测，测不到的写清楚谁覆盖**：只有"这一层 JVM 里打不开"的东西（CameraX 取景、系统选图器、真实通知弹窗、EXIF 方向、HyperOS 权限弹窗、启动器图标渲染）才允许交给真机（否决项 D7），
   并且要在代码注释里写明"由谁覆盖"（例：`CameraXSource.kt` 的 KDoc 写着这一层由真机自验覆盖）。

---

## 1. 分层与现状（39 类 / 481 例）

| 层 | 跑在哪 | 类 / 例 | 覆盖什么 |
|---|---|---|---|
| ① 纯 JVM | 无 Robolectric | 12 类 / 100 例 | 纯函数、解析、打分、状态计算 |
| ② Robolectric 数据层 | in-memory Room + 真文件 | 8 类 / 130 例 | 事务、外键、引用计数、照片落盘、备份 |
| ③ Robolectric 系统件 | Robolectric + 假替身 | 5 类 / 26 例 | 通知构造与回写、装配、图标资源、相机接缝 |
| ④ Compose 界面 | `createComposeRule` + Robolectric | 14 类 / 225 例 | 组合、按文字找节点、状态迁移、按钮可用性 |
| ⑤ 真机人工 | 人 + 真机 | 7 条 | 相机、选图器、通知、EXIF、权限、图标、字体缩放 |

**① 纯 JVM（12 类 / 100 例）**：`LocationTreeTest`(11)、`SearchRankerTest`(7)、`SuggestLocationUseCaseTest`(12)、
`AnalyzePhotoUseCaseTest`(3)、`CameraGateTest`(4)、`DatesTest`(4)、`SearchTextBuilderTest`(4)、
`AnalysisParserTest`(15)、`AnalysisTraceTest`(4)、`ApiPhotoAnalyzerTest`(31，用 `mockwebserver`)、
`UiFormatTest`(4)、`BuildSmokeTest`(1)。

> **11 类 AI 失败都有断言**（`REBUILD.md` §3 M2 的出口判据），但断言方式不统一：10 类写的是
> `assertEquals(AnalysisOutcome.Failed(FailureReason.X), …)`，**`SERVER` 那一条走的是留痕的 `outcome` 字符串**
> （`ApiPhotoAnalyzerTest.kt:318` 的 `listOf("SERVER", "success")`）。所以**按 `FailureReason.` 字面 grep 会数出 10 类**，
> 不要据此判"缺一类"。

**② Robolectric 数据层（8 类 / 130 例）**：`DatabaseSmokeTest`(2)、`ItemSearchDaoTest`(6)、`PhotoStoreTest`(10)、
`BackupRepositoryTest`(18)、`ItemRepositoryTest`(34)、`LocationRepositoryTest`(27)、`CaptureFlowTest`(17)、
`ReminderEngineTest`(16)。
> **不 mock 文件系统**（`ARCHITECTURE.md` §14）：照片回收与备份去重只有真文件才能证伪。
> 落文件的用例走 `ctx.filesDir` 下的测试子目录，`setUp` 里先 `deleteRecursively()`。

**③ Robolectric 系统件（5 类 / 26 例）**：`ReminderWorkerTest`(12)、`AppContainerTest`(5)、`LauncherIconTest`(3)、
`CameraWiringTest`(2)、`CameraAccessTest`(4)。

**④ Compose 界面（14 类 / 225 例）**：`ReviewViewModelTest`(48)、`ReviewScreenTest`(30)、`SettingsViewModelTest`(22)、
`WheremyRootTest`(20)、`TreeViewModelTest`(19)、`SettingsScreenTest`(16)、`GalleryScreenTest`(15)、
`GalleryViewModelTest`(12)、`TreeScreenTest`(11)、`DetailScreenTest`(10)、`CameraScreenTest`(9)、
`DetailViewModelTest`(8)、`LocationPickerDialogTest`(4)、`SnackbarMessageTest`(1)。
屏幕尺寸由 `@Config(qualifiers = "w411dp-h891dp")` 固定（`WheremyRootTest.kt:80`），规则用
`createAndroidComposeRule<ComponentActivity>()`。

**⑤ 真机人工（7 条）**：清单在 `REBUILD.md` §6；写法规矩见 §8。

**三个 helper（不带 `@Test`，不计入类数）**：
`TestExecutors.kt` 的 `InlineExecutor`（让 Room 的查询与事务在当前线程跑完，消掉与 eager 测试调度器的竞态）、
`TestSettings.kt`（把 DataStore 等到"就是要的那份值并安静下来"——Robolectric 下 DataStore 文件按名字跨测试共享，这是踩过的坑）、
`FakeTrace.kt`（只回答 `count()`，留痕落盘由 `AnalysisTraceTest` 自己盯）。

---

## 2. 每层能测什么、测不到什么

| 层 | 测得到 | 测不到 | 替代证据 |
|---|---|---|---|
| ① 纯 JVM | 谓词、阈值、打分、提示词解析、失败分类、日期换算 | 任何 Android API | — |
| ② 数据层 | 事务原子性、外键拒收、引用计数、文件真的写/删了、备份包读写、幂等 | 磁盘满、真实 IO 报错、进程被杀 | 代码里显式的 `runCatching` 兜底 + 真机演练 |
| ③ 系统件 | 通知内容与回写时机、PendingIntent 参数、图标资源形状、装配与目录 | 系统是否真的弹出、权限弹窗行为 | 真机自验（§8 第 3、5 条） |
| ④ Compose 界面 | 按**逐字文案**找节点、状态迁移、按钮 `enabled`、对话框分支、snackbar 消费 | 真实字体缩放、真机排版、手势细节、动画 | 真机自验（§8 第 7 条） |
| 契约（mockwebserver） | HTTP 状态码映射、重试、超时、请求体与请求头、留痕内容 | 真实供应商的怪异回包 | AI 留痕（原文可查），`RULES.md` §13 |
| ⑤ 真机 | 相机、选图器、通知弹出与跳转、EXIF、权限、图标、字体缩放 | 不可复现、不可回归 | 签收单 + 下次发布的清单复跑 |

**形状类断言的分寸**（`REBUILD.md` §7 A4）：`AlertDialog` 的超长正文会自己折行、按钮会另起一行，
110 字和 1400 字都挤不坏——**拿"按钮还在不在"去钉弹框形状是变异不出红的假绿**。
只有"文字与控件同处一个 `Row`"的地方才值得钉（例：`ReviewScreen.kt:135`、`:429-430` 的 `weight(1f)`）。

---

## 3. 写一个测试：本项目的既有风格

**位置**：`app/src/test/java/com/wheremy/app/...`，包路径与主源镜像。

**命名**：类名 `XxxTest`；方法名是**一句英文陈述句**，描述行为而不是方法名，例：
`nothingPickedYetOffersToPickAndRefusesToSave`、`thePhotoIsOnScreenWhileTheAnalysisIsStillFlying`、
`onceTheItemIsOnDiskTheSameButtonBecomesSwapPhoto`、`deleteAsksOnceThenLeavesThePage`、
`editingAStoredItemThenLeavingDoesNotCallItADraft`。

**类级 KDoc**：用中文写"这个类守的是什么"，并写清取舍。真实例子（`ReviewScreenTest.kt` 头部）：
> 确认页上那几条"以前只能在手机上点着看"的：照片还没落库时按钮怎么说、"换一张"是不是真能把回调打出去……
> **文案就是要验收的东西，所以按原文比对，不抽常量。**

**结构**：`@Before fun setUp()` 建 in-memory Room 与临时照片目录；`@Test fun xxx() = runBlocking { … }`（数据层）
或 `@Test fun xxx() { … }`（界面层）。**断言消息写中文**，因为它将来会被人读：
`assertEquals("原图与缩略图两个文件都该在", 2, photoFileCount())`。

**三条硬要求**：

1. **文案按原文比对**：界面测试按逐字串找节点（`onNodeWithText("保存这一件")`），**不许把文案抽成常量**。
2. **必须断言 `enabled`**：这个项目里"按钮该不该能按"本身是规则（`RULES.md` §16.4、§1.5）。
3. **禁止假绿手法**：为了变绿而放松断言、放宽时间约束、吞掉异常，都算造假证据（`PROCESS.md` §9.3）。

**几个实测过的坑**（写之前先看，能省一轮排查）：

| 坑 | 绕法 |
|---|---|
| Room 的事务沿用调用方线程，`runBlocking` 在 Robolectric 主线程上 → 主线程检查变成假异常 | `allowMainThreadQueries()` + 注释写清原因（`ItemRepositoryTest` 就是这么写的） |
| DataStore 文件按名字跨测试共享，上一轮的写在 `tearDown` 之后才落地，污染下一个用例 | `TestSettings` 的"等到安静"：目标值连续维持住一段**时间**（不是连续读到 N 次，那会在磁盘被占时把预算花光） |
| ViewModel 单测里 Room 的 IO 线程与测试调度器抢时序 | `InlineExecutor` |
| 界面用例里的 VM 是"按了就写、不等回话"的 | 同上，用 `TestSettings` 声明前提 |

---

## 4. 变异验牙（怎么证明测试有牙）

**做法**：把这一批改动里的那一行**故意改坏**，期望**恰好对应的那条**测试变红。红在别处=断言写宽了；哪都不红=无牙。

**六个常用变异手法**（够覆盖这个项目的规则类型）：

| 手法 | 例子 |
|---|---|
| 阈值 ±1 | `days <= 30` → `<= 31`；`leadDays.coerceAtLeast(0)` 去掉 |
| 反 boolean / 换分支 | `if (a.tags.isNotEmpty())` → `isEmpty()`；`!saved` → `saved` |
| 改文案一个字 | 「稍后处理」→「稍后办」；`「待归类」` 的括号去掉 |
| 删一次写 | 删掉 `markExpireNotified`；删掉 `deleteUnreferencedPhotoFiles` |
| 换顺序 | `finished = true` 挪到删除之前；`analysis?.cancel()` 挪到写入之后 |
| 去掉守卫 | `takeIf { draft.locationId == null }` 改成无条件覆盖 |

**记录格式**（写进迭代收口段，是 G2 的证据）：

```
变异点 | 期望红 | 实际红 | 结论
ReviewScreen.kt:135 的 weight(1f) 删掉 | ReviewScreenTest 的"长句不挤按钮" | 无 | 无牙 → 已补断言 X
```

**已知的两类无牙**（`REBUILD.md` §1.3、§7 A4）：用无权重断言钉 `AlertDialog` 形状；只断言"屏幕上有这句话"而不断言它属于**哪一支**。

**跑崩了怎么办**：变异中断会遗留 `GradleWorkerMain` 攥住 `app/build/test-results/testDebugUnitTest/binary/output.bin`，
下一次报 `Unable to delete directory` —— **这是文件锁不是测试红**，按命令行区分 DAEMON/WORKER 后 `gradle --stop`（`REBUILD.md` §7 A3）。

---

## 5. 覆盖对账（双向）

跑完一轮之后做一次双向对账，两个方向都可能骗人。

**正向**：`PRD.md` §7 表里每一行 → 那个测试类真的存在、`@Test` 数与表里一致。
**反向**：

```bash
grep -rl "@Test" app/src/test | wc -l          # 期望 39
grep -rho "@Test" app/src/test | wc -l         # 期望 481
```

**分层分解**（§1 的表就是这次对账的产物）：100（纯 JVM）+ 130（数据层）+ 26（系统件）+ 225（界面）= 481。

**口径**（`REBUILD.md` §1.2）：
- **类名存在不等于有人守**：`PRD.md` §7 里有 `ItemRow` 这种"没有自己的类"的一行，对账时它落到别家的例数里。
- **数字会随迭代长大**：改口径时要同步四处——`PRD.md` §7、`REBUILD.md` §1.2/§3、`ARCHITECTURE.md` §14、本文 §1。

---

## 6. 判绿操作手册（G1 双证）

```bash
# 1) 前台跑（后台 shell 没有 gradle，会 exit 0 + 空输出）
gradle :app:testDebugUnitTest --rerun

# 2) 双证之一：本轮有多少份 XML 是新的（期望 39）
find app/build/test-results/testDebugUnitTest -name '*.xml' -newermt '<本轮起跑时间>' | wc -l

# 3) 双证之二：自己数 tests / failures / errors
grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
  app/build/test-results/testDebugUnitTest/*.xml \
  | awk -F'"' '{t+=$2; f+=$6; e+=$8} END {print "tests="t, "failures="f, "errors="e}'
```

**等价的 PowerShell 写法**（同一台机器上不用 Git Bash 时）：

```powershell
$start = Get-Date
gradle :app:testDebugUnitTest --rerun
$files = Get-ChildItem app\build\test-results\testDebugUnitTest\*.xml
$fresh = ($files | Where-Object { $_.LastWriteTime -gt $start }).Count
$t = 0; $f = 0; $e = 0
foreach ($file in $files) {
    $s = ([xml](Get-Content $file.FullName -Raw)).testsuite
    $t += [int]$s.tests; $f += [int]$s.failures; $e += [int]$s.errors
}
"freshXml=$fresh tools=$($files.Count) tests=$t failures=$f errors=$e"
```

**判绿**：`fresh == 本轮类数` **且** `tests == 本轮例数` 且 `failures == 0` 且 `errors == 0`。少一条都不算绿。
> 数字随批次增长：**当前 = 42 类 / 261 例**（2026-10-10）。判据是「fresh 等于本轮 XML 份数」，不是某个固定数字。

> 上面那段 PowerShell 的计数部分在本机对上一轮的 39 份 XML 实跑过，输出 `files=39 tests=481 failures=0 errors=0`——它同时也是"`PRD.md` §7 的 39 类 / 481 例"这条基线的第三次独立复核。

**三个伪绿/伪红陷阱**（都真实发生过，`REBUILD.md` §1.1、§7 A 组）：

| 现象 | 真相 | 处置 |
|---|---|---|
| 命令 exit 0、输出为空 | 后台 shell 里没有 gradle（version-fox 的 PATH 不注入后台任务） | 改前台跑；**判绿自己数 XML + 看 mtime** |
| `Directory '…' does not contain a Gradle build`、3–4 秒退出 | 脚本没给项目根当 cwd | `cwd` 指向项目根；判决前先断"本轮 XML 比起跑新"，不新鲜就是 **NO-VERDICT**，不是绿 |
| `Unable to delete directory …/binary/output.bin` | 变异跑崩遗留的 worker 文件锁 | `gradle --stop` 后重跑 |

**跑灰区**：改动了测试基础设施（helper、调度器、时间）之后，必须跑**全量**而不是单类——这类改动最会伤到别的类。

---

## 7. 缺陷与回归

分级、流程、模板见 `PROCESS.md` §7 与 `templates/defect-report.md`；测试侧记三条：

1. **先写会红的测试**（P0/P1 强制）：红是"我找到的是真问题"的证据，也是修复的验收条件。
2. **回归范围**：受影响类 + 全量（全量只有几百毫秒到几十秒的开销，没有省的理由）。
3. **缺陷的三种归宿**要分清楚：代码错 → 改代码；**文案不成立** → 走通道四（`PROCESS.md` §3.4，先写会红的测试再改文案）；
   文档错 → 直接改文档；"记录不改"的现状 → 记入 `RULES.md` §15 并注明已知未修。

**测试能发现的三类"不是 bug 的 bug"**（对账时别误报）：
`changeParent` 返回 false 是"本来就在这层"、`shift` 返回 false 是"到边界"、`import` 的 `action` 返回 null 是"连文件都没打开"——
它们都是**三态语义**，判据在 `RULES.md` §16.9。

---

## 8. 真机验收规范

**只有这七件必须真机**（`REBUILD.md` §6）：取景屏预览与快门手感、系统选图器行为、通知是否真弹出与点击落点、
EXIF 方向、HyperOS 权限弹窗与静默驳回、桌面图标渲染、长名字/多标签在真实字体缩放下的排版。

**写清单的三条硬规矩**（这条本身是一次返工换来的）：

1. 逐步写成"**哪个界面 → 点哪颗（用屏幕上逐字的按钮文字）→ 目的 → 成功的样子 → 失败时回我哪句原话**"；
2. **不许出现内部编号**（"跑 B 组三条""验 #6/7/9/10"这类都不行）；
3. 依赖界面状态的步骤要带**前置条件**，并在最前面写两行**环境快照**：装的是哪个包、装于何时（`install -r` 成功 ≠ 新代码生效）、
   本机数据是否为空、那条权限是否还没授过。

**签收方式**：由**人**在真机上回"看到了什么"，不是代理自报（`PROCESS.md` §6 G5）。模板：`templates/device-acceptance.md`。

> 系统弹窗（账号、登录、授权）出现时：**只按返回键取消，绝不输入任何账号密码**，并把"出现过"回报。

**真机操作的数据红线**（历史事故的判据，`REBUILD.md` §7 C16/C17/C18）：
任何会改动机上数据的动作先问用户；按任何「删除」之前，先用**可信读数**从库里算出"这一格是谁"，
断言目标 id 是自己建的那一条；报"丢了什么"之前，先把"现在还剩什么"读一遍。

---

## 9. 测试数据与隐私

| 规矩 | 理由 / 出处 |
|---|---|
| **测试不许依赖真 key**：M2 的出口判据是"不填 key 也必须全绿" | `REBUILD.md` §3 M2；`RULES.md` §13 |
| 联网行为用 `mockwebserver` 覆盖（11 类失败） | `ARCHITECTURE.md` §14 |
| 造数据用 **seed 备份包 + 应用内导入**，不靠打字 | 真机输入法会把 `adb shell input text` 的字母吞进拼音候选（`REBUILD.md` §7 D2） |
| 取库要 **db + `-wal` 两份**，整目录取到**全新**目录，`exec-out` 原始字节，先 `PRAGMA integrity_check` | `REBUILD.md` §7 B1–B4 |
| 测试与验收产生的截图/dump/临时库：**清理清单从 `ls` 生成，不从记忆生成**；删完再 `ls` 一次作为凭据 | `REBUILD.md` §7 E3 |
| 绝不提交：真实 key、`data.json`、截图与 dump、`local.properties` | `REBUILD.md` §9 |
| 测试照片用**自己复制**的文件造"两件共用一张图"，不动用户真照片 | `REBUILD.md` §7 E2 |

---

## 10. 新增功能时的测试清单

每加一条 FR，测试侧至少做五件事：

1. **一条具名测试**：命名按 §3，能说清"守的是哪条 FR"；
2. **文案断言**：新出现的每句话都要有能到达它的分支（`RULES.md` §16.3），并做一次变异；
3. **更新两处索引**：`PRD.md` §7 的需求 ↔ 守门测试表、`RULES.md` §18 的规则 ↔ 测试对应表；
4. **补分层**：纯逻辑落进 ①，数据/事务落进 ②，界面落进 ④；只有 JVM 打不开的部分才登记进 ⑤；
5. **跑一次双向对账**（§5）：类数与例数变了，就同步四处口径。

**"改了什么 → 至少要写什么测试"的速查**：

| 改动 | 至少覆盖 |
|---|---|
| 新增/修改一条谓词或阈值 | 边界值（恰好等于、恰好越过）+ 变异一次 |
| 新增一句界面文案 | 逐字断言 + 分支可达性 + 形状（与按钮同排时） |
| 新增一次写操作 | 成功与失败两条路；失败后**不该留下什么**也要断言 |
| 改顺序（先写后删之类） | 中途失败的状态断言（例：事务失败后照片文件被清掉） |
| 改 AI 请求/解析 | `mockwebserver` 契约用例 + 留痕内容不含 key 的那条断言 |
| 改备份读写 | 真文件 + `validate` 四条 + "缺文件放行、空路径拒" |
| 改 schema | migration 测试（从旧版本库升上来，数据逐行还在）+ 真机演练 |

---

## 11. 交付物

| 产物 | 何时 | 模板 |
|---|---|---|
| 测试计划 / 报告 | 每轮迭代 | `templates/test-plan.md` |
| 变异清单 | 每批改动（G2 证据） | 写在迭代计划的收口段 |
| 判绿证据 | 每批改动（G1 证据） | 命令 + 计数 + fresh 数 |
| 覆盖对账结果 | 每批改动（G3 证据） | 正反两次命令的输出 |
| 缺陷单 | 发现问题时 | `templates/defect-report.md` |
| 真机验收单 | 交付/发布前（G5） | `templates/device-acceptance.md` |
| TalkBack 走查单 | 交付/发布前（G5，无障碍专项） | `templates/talkback-walkthrough.md` |
