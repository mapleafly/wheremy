# Wheremy 文档总览（从这里开始）

**这份文档是入口**：它回答"我想做某件事，该读哪一份、改哪一份、找谁拍板"。
产品本身、代码结构、业务规则、界面细节都不在这里展开——每一条都指明去处的章节号。

- **代码基线**：commit `cf73f5b`（2026-10-06）—— 指**参照实现**（`D:\src\qoder\wheremy`，只读）。主源 57 个 Kotlin 文件 / 6881 行；测试 39 个结果类 / 481 例。
- **文档集基线**：本目录。**16 份正文文档** + 本入口（`README.md`）+ `CHANGELOG.md` + 1 套模板（`templates/`）+ 1 份历史材料存档（`superpowers/`）。
- **本工作区的开发**：从零实现，目标是不弱于且优于参照实现——计划见 `PLAN.md`，差异逐条记在 `DELTA.md`。
- **最高纪律（NFR-02）**：**界面上每一句话都必须真的成立**。判据在 `RULES.md` §16，操作步骤在 `REBUILD.md` §5，流程门禁在 `PROCESS.md` §6。
- **技术选型准则**：**官方推荐的最佳实践优先，其次成熟第三方，尽量不要自己造轮子**；自造要写 ADR 并给复核条件（`TECH_STACK.md` §1）。
- **行号会漂、符号名不漂**：所有 `文件:行` 都以 `cf73f5b` 为准；找不到行就按符号名搜（`RULES.md` §0、`ARCHITECTURE.md` 开头）。

---

## 0. 30 秒版本

**Wheremy 是什么**：把家里东西拍一张照，AI 替我记下它是什么、放在哪一层；日后我忘了自己有这东西时，也能搜出来、找得到、记得住自己有什么。（完整定位见 `PRD.md` §1）

| 维度 | 事实 | 出处 |
|---|---|---|
| 使用者 | 单人单机，无账号、无云同步、无多人共享 | `PRD.md` §1.2、⛔D8 |
| 平台 | Android 原生（Kotlin + Compose + Room + WorkManager），侧载 APK | `ARCHITECTURE.md` §1、§2 |
| 数据 | 全部在本地；手动导出 zip 是唯一的迁移手段 | `PRD.md` FR-BAK、`RULES.md` §12 |
| AI 的角色 | 加速器不是闸门：AI 慢、坏、没配 key 都不影响录入与查找 | `PRD.md` FR-AI-06、`RULES.md` §13 |
| 成功判据 | 5 条可测判据 G1–G5 | `PRD.md` §1.1 |
| 当前形态 | **可交付**（2026-10-12）：release 已用用户自己的 keystore 签名、R8 已开、真机人工清单七条全过（`RELEASE.md` §10.7）；schema 仍冻结、本实现未引入破坏性迁移 | `RELEASE.md` §3/§10.7、`PRD.md` ⭐FR-DB-01/⭐FR-RLS-01 |

---

## 1. 角色导航：我要做的事 → 先读哪几份

| 角色 | 我负责 | 必读（按顺序） | 参考 | 我会产出 / 更新 |
|---|---|---|---|---|
| **产品 / 项目经理** | 要做什么、先后顺序、验收口径 | `PRD.md` → `PROJECT.md` → `PROCESS.md` §2/§3 | `DECISIONS.md`（已否决项）、`TESTING.md` §8 | `PRD.md`（FR 状态与新增）、`PROJECT.md`（范围/里程碑/风险）、`templates/feature-proposal.md` |
| **软件经理 / 技术负责人** | 流程、门禁、排期、发布、本次开发节奏 | `PROJECT.md` → `PROCESS.md` → **`PLAN.md`** → `RELEASE.md` | `REBUILD.md` §1/§3、`TESTING.md` §5、`DELTA.md` | `PROCESS.md`、`PROJECT.md` §迭代节奏、`PLAN.md` §10 阶段表、`CHANGELOG.md`、`templates/iteration-plan.md` |
| **架构师** | 分层、接缝、技术决策、技术选型 | `ARCHITECTURE.md` → `TECH_STACK.md` → `DECISIONS.md` → `RULES.md` §1/§2/§12 | `REBUILD.md` §2（不可变项）、`REBUILD.md` §4（值得提前的分离） | `ARCHITECTURE.md`、`TECH_STACK.md`、`DECISIONS.md`（新 ADR）、`templates/adr.md` |
| **开发** | 写代码、写测试、自证 | **`PLAN.md`** → `DEVELOPMENT.md` → `ARCHITECTURE.md` → `RULES.md` → `UI.md` | `PRD.md` §7（守门测试索引）、`TESTING.md` §3/§4、`DELTA.md` | 代码与测试、`DELTA.md`（差异与证据）、`ARCHITECTURE.md`（结构变了才改）、`RULES.md`（规则变了才改） |
| **测试** | 测什么、怎么判绿、怎么验牙 | `TESTING.md` → `RULES.md` → `PRD.md` §7 | `REBUILD.md` §1/§6/§7、`UI.md` §14/§15 | 测试代码、`templates/test-plan.md`、`templates/defect-report.md`、真机验收单 |
| **UI / 交互设计** | 屏幕长什么样、怎么点、视觉规范 | `UI.md` → `DESIGN_SYSTEM.md` → `RULES.md` §16 | `ARCHITECTURE.md` §11（谁持有状态） | `UI.md`（信息架构与逐字文案）、`DESIGN_SYSTEM.md`（视觉与组件） |
| **发布 / 运维** | 出包、迁移、回滚 | `RELEASE.md` → `PROJECT.md` §风险 → `TESTING.md` §7 | `REBUILD.md` §8（环境事实） | `templates/release-checklist.md`、`CHANGELOG.md` |
| **新人（任何角色）** | 30 分钟知道这是什么 | `README.md` → `PRD.md` §1 | `GLOSSARY.md` 查词 | — |

**一人多角是常态**：这个项目的实际规模是一到两个人，所以上表是"职责清单"而不是"分工必须照做"。但**每一份产出必须落在文档里**——尤其是决策与例外，见 `PROCESS.md` §2。

---

## 2. 文档地图：每份回答什么、不回答什么

| 文档 | 回答 | 不回答 | 状态 |
|---|---|---|---|
| `README.md`（本文） | 读哪份、改哪份、谁说了算、怎么迭代 | 任何具体需求或规则 | 导航 |
| `PRD.md` | **要什么**：目标态需求清单，每条带 `✅/🟡/⬜/⛔` 状态、触发条件与代价 | 规则怎么算（→`RULES.md`）、屏幕怎么摆（→`UI.md`） | 目标态 + 状态标记 |
| `PROJECT.md` | **谁、什么时候、多大风险**：角色 RACI、里程碑排期、迭代节奏、DoR/DoD、风险登记、度量 | 单条需求的验收（→`PRD.md`） | 管理 |
| `PROCESS.md` | **一次改动怎么走**：八条变更通道、文档同步矩阵、质量门禁、提交与评审、缺陷与例外 | 具体测试怎么写（→`TESTING.md`） | 流程（权威） |
| `ARCHITECTURE.md` | **代码长什么样**：分层、每个类负责什么、谁调用谁、跨层顺序不变量 | 要做什么（→`PRD.md`）、规则怎么算（→`RULES.md`） | as-built |
| `TECH_STACK.md` | **技术选型凭什么**：官方/成熟方案优先的准则、现状对照（✅/🟡/🔒/⬜）、反模式清单、迁移队列 T1–T9、依赖评审流程，以及"最佳组合"的结论与**待联网核实清单**（§8） | 代码现在长什么样（→`ARCHITECTURE.md`）、逐条决策的完整 ADR（→`DECISIONS.md`） | 准则（权威） |
| `RULES.md` | **规则怎么算**：谓词、阈值、状态机、逐字文案、已知偏差 W1–W11、可检验判据 §16 | 屏幕布局（→`UI.md`） | as-built（权威） |
| `UI.md` | **屏幕上有什么、什么次序、怎么点**、信息架构与逐字文案清单 | 视觉风格（→`DESIGN_SYSTEM.md`） | as-built（权威） |
| `DESIGN_SYSTEM.md` | **视觉与组件规范**：色彩语义、字号、间距、组件用法、状态设计、无障碍 | 信息架构（不许改，见 `UI.md` §1/§12） | 规范（可演进） |
| `DEVELOPMENT.md` | **怎么把环境搭起来、怎么写、怎么加东西**：工具链、命令、编码纪律、扩展点清单 | 从零重建的顺序（→`REBUILD.md`） | 操作手册 |
| `TESTING.md` | **怎么测**：分层、判绿双证、变异验牙、覆盖对账、缺陷分级、真机清单规范 | 每条规则的值（→`RULES.md`） | 流程 + 手册 |
| `RELEASE.md` | **怎么发布**：版本策略、发布前必做、签名、迁移、回滚、发布后跟踪 | 日常开发（→`DEVELOPMENT.md`） | 流程 + 清单 || `RELEASE.md` | **怎么发布**：版本策略、发布前必做、签名、迁移、回滚、发布后跟踪 | 日常开发（→`DEVELOPMENT.md`） | 流程 + 清单 |
| `KEYSTORE.md` | **怎么生成与保管 release 密钥**：一条条命令、三份备份、`keystore.properties` 的坑、三条验证命令、装机两个坑、换密钥不可逆 | 发布门禁与回滚（→`RELEASE.md`） | 操作手册（⭐FR-RLS-01） |
| `DECISIONS.md` | **为什么这么定**：ADR 流程 + 25 条决策记录（D1–D14 + 10 条提炼 + 技术选型准则 ADR-25）+ 推翻条件与复核触发 | 需求状态（→`PRD.md` §6 是索引） | 决策记录（权威） |
| `GLOSSARY.md` | 业务词 ↔ 代码符号 ↔ 库表列 的对照 | 定义本身（→`RULES.md`） | 查阅 |
| `REBUILD.md` | **从零重建的顺序与验收**、硬性不可变项、已踩过的坑 | 日常迭代（→`PROCESS.md`） | as-built 路线图（描述参照实现） |
| `PLAN.md` | **本工作区这次开发怎么走**：就绪判定、技术起点、先 UI 还是先代码、阶段 P0–P6 与出口判据、与参照实现的对比方法 | 单条需求（→`PRD.md`）、门禁细节（→`PROCESS.md`）、选型依据（→`TECH_STACK.md`） | **执行计划（权威）** |
| `DELTA.md` | **本实现 ↔ 参照实现的逐条差异**：87 条 FR 矩阵、W1–W11 细节清单、界面截图对比、工程对比 | 规则与界面细节（→`RULES.md`/`UI.md`） | **差异台账** |
| `CHANGELOG.md` | 本文档集的变更记录（谁在哪一轮改了什么） | 软件版本记录（→`RELEASE.md` §8） | 日志 |
| `templates/` | 8 张可照抄的表单：提案、变更、ADR、迭代、测试、缺陷、发布、验收 | — | 模板 |
| `superpowers/` | 历史材料：原始 spec 与两份 plan | — | **只读存档** |

---

## 3. 唯一事实源与冲突裁法

### 3.1 一份信息只有一个权威出处

| 信息 | 唯一权威 | 谁可以改 |
|---|---|---|
| 要做什么、优先级、`✅/🟡/⬜/⛔` 状态 | `PRD.md` | 产品（⛔ 的推翻见 `DECISIONS.md` §2） |
| 规则怎么算（谓词、阈值、状态机） | `RULES.md` | 开发 + 测试同时改 |
| 界面文案逐字串 | **源码**（`RULES.md`/`UI.md` 是抄本） | 见 `REBUILD.md` §5 的五步门 |
| 屏幕与字段次序、返回键优先级 | `UI.md` | UI + 测试 |
| 视觉、组件、间距、状态样式 | `DESIGN_SYSTEM.md` | UI |
| 类与方法、调用顺序、跨层不变量 | `ARCHITECTURE.md` | 架构师 + 开发 |
| 技术选型准则、用什么库、自造与否 | `TECH_STACK.md` | 架构师（自造要写 ADR） |
| 流程、门禁、文档同步要求 | `PROCESS.md` | 软件经理 |
| 里程碑与风险 | `PROJECT.md` | 软件经理 |
| 技术决策与否决理由 | `DECISIONS.md` | 架构师（推翻要写新 ADR） |
| 重建顺序与不可变项 | `REBUILD.md` | 架构师 |

### 3.2 冲突裁法

1. **先判定，再改**：文档与源码不一致时，**不允许**用一句"以源码为准"收场。必须判定哪一边错了，并把错的那一边改掉（`PROCESS.md` §5 的文档同步矩阵）。
2. **同层冲突**：逐字文案 → `RULES.md` 优先；屏幕结构与字段次序 → `UI.md` 优先；"这条该不该存在" → `PRD.md` 优先；层与类的边界 → `ARCHITECTURE.md` 优先；顺序与验收 → `REBUILD.md` 优先；**流程本身** → `PROCESS.md` 优先。
3. **口径冲突**：`PRD.md` 写目标态，其余写 as-built。所以"PRD 说有、代码没有"不是矛盾，是 `⬜/🟡` 状态；反过来"代码有、PRD 没写"才是缺陷（补一条 FR）。
4. **裁决要留痕**：任何一次冲突的判决写进 `CHANGELOG.md`（一行：谁、哪两份、判了谁错、改了什么）。

---

## 4. 三条阅读路线

**① 产品速览（30 分钟）**：`PRD.md` §1 → §2 概念模型 → §1.1 成功判据 → `README.md` §0。
读完能回答：这个 app 给谁、解决什么、不做什么。

**② 上手改代码（半天）**：`DEVELOPMENT.md` §2–§5 → `ARCHITECTURE.md` §1/§11/§13 → `RULES.md` §1/§2 → `UI.md` §1 → 跑一次 `gradle :app:testDebugUnitTest` 并按 `TESTING.md` §6 判绿。
读完能回答：环境怎么搭、改动要落在哪一层、跑绿怎么算数。

**③ 接手发布（2 小时）**：`RELEASE.md` §3–§6 → `PROJECT.md` §6 风险 → `REBUILD.md` §2/§8 → `TESTING.md` §7。
读完能回答：发布前必做哪几件、签名与迁移怎么处理、错了怎么退。

---

## 5. 迭代入口：四种常见改动，各自走哪条路

实际开发中新增/修改/删除/细化功能是常态。**每一条都有既定通道**，不要在通道外动手。

| 我想做的事 | 走哪条通道 | 关键动作 | 模板 |
|---|---|---|---|
| **新增一个功能**（新 FR） | `PROCESS.md` §3.1 | 先拿 FR id 与状态 `⬜`；写触发条件与代价；设计 → 规则 → UI → 实现 → 测试 → 更新索引 | `templates/feature-proposal.md` |
| **修改一条已有行为** | `PROCESS.md` §3.2 | 先改 `RULES.md` 的规则与逐字文案 → 改断言它的测试（先红）→ 再改代码；同步 `UI.md` | `templates/change-request.md` |
| **删除一个功能** | `PROCESS.md` §3.3 | 判定"删的是什么"：入口/规则/数据；数据迁移与备份兼容必须单独评估；FR 状态改成 `⛔` 并写理由 | `templates/change-request.md` |
| **补充细节 / 调整文案与次序** | `PROCESS.md` §3.4 | 走 `REBUILD.md` §5 的五步门；文案改动必须连测试一起改 | `templates/change-request.md` |
| **重构（行为不变）** | `PROCESS.md` §3.5 | 先用测试把现状钉死 → 再改结构 → 变异验证；不许顺手改行为 | — |
| **升级依赖 / 工具链** | `PROCESS.md` §3.6 | 一条依赖一次提交；跑全量回归 + 真机冒烟；按 `TECH_STACK.md` §5 的五项体检答辩 | — |
| **推进一个开发阶段**（P0–P6） | `PLAN.md` §5/§7 | 按阶段出口判据走 G0–G5；每阶段结束更新 `PLAN.md` §10 与 `DELTA.md` | `templates/iteration-plan.md` |
| **迁移技术栈**（换库 / 换掉自造件） | `TECH_STACK.md` §4 迁移队列 + `PROCESS.md` §3.5 | 先用测试把现状钉死 → 再迁 → 变异验证；高风险迁移（导航、DI）要新写 ADR | `templates/adr.md` |
| **改 schema / 备份格式** | `PROCESS.md` §3.7 | **高风险通道**：先看 `REBUILD.md` §2 的不可变项与 `RELEASE.md` §5 | `templates/adr.md` |
| **准备发布** | `PROCESS.md` §3.8 | 逐条签收 `RELEASE.md` §9 的可执行清单（门禁与证据要求见 §4） | `templates/release-checklist.md` |

---

## 6. 文档维护纪律

1. **改代码不改文档 = 没做完**。`PROCESS.md` §5 的同步矩阵规定了"改这一处必须动哪几份"；出口判据 `PROCESS.md` §6 G4。
2. **文档改动也走评审**：逐字文案、规则、门禁三类改动必须先改文档再改代码（文档先行），其余可以代码与文档同一次提交。
3. **不许写进文档**：API key 或任何厂商的 key 字面值、`local.properties` 内容、真机导出的 `data.json`、含隐私的截图与 dump、任何账号密码（`REBUILD.md` §9）。
4. **状态标记只认四种**：`✅ 已落地` / `🟡 部分落地` / `⬜ 待做`（必须带触发条件与代价）/ `⛔ 已否决`（必须带理由，推翻要写 ADR）。
5. **已知偏差不许悄悄消失**：`RULES.md` §15（W1–W11）与 `UI.md` §14 是"照实记录、未必正确"的清单；修掉一条就在这里划掉一条并说明，**不许改成"已修复"却不留改动记录**。
6. **每个 `⬜` 都要能被触发**：没有触发条件与代价的待办等于"以后再说"，`PROCESS.md` §6 G4 会把它挡回来。

---

## 7. 目录结构

```
docs/
├── README.md             ← 本文：入口、导航、冲突裁法、维护纪律
├── PRD.md                产品需求（目标态 + 状态标记）
├── PROJECT.md            项目章程：角色、里程碑、节奏、风险、DoR/DoD
├── PROCESS.md            开发流程与变更管理（八通道 + 同步矩阵 + 门禁）
├── ARCHITECTURE.md       技术架构（as-built）
├── TECH_STACK.md         技术选型准则与依赖策略（官方优先、迁移队列）
├── RULES.md              行为规则与逐字文案（as-built，权威）
├── UI.md                 界面信息架构与交互（as-built，权威）
├── DESIGN_SYSTEM.md      视觉与组件规范
├── DEVELOPMENT.md        开发环境、编码纪律、扩展点
├── TESTING.md            测试策略、判绿、验牙、缺陷
├── RELEASE.md            发布、迁移、回滚
├── DECISIONS.md          架构决策记录（ADR）与推翻流程
├── GLOSSARY.md           术语对照表
├── REBUILD.md            从零重建路线、不可变项、踩过的坑（描述参照实现）
├── PLAN.md               本次开发计划：就绪判定、技术起点、P0–P6、对比方法
├── DELTA.md              本实现 ↔ 参照实现 差异台账（87 条 FR + 细节 + 界面 + 工程）
├── CHANGELOG.md          本文档集变更记录
├── templates/            8 张可照抄的表单
└── superpowers/          历史材料（spec 规范 + plan 过程记录，只读）
```

---

## 8. 历史材料：`docs/superpowers/`

| 路径 | 是什么 | 怎么用 |
|---|---|---|
| `superpowers/specs/2026-10-02-wheremy-design.md` | 原设计文档（as-built 文档里的"spec §N"都指它） | **规范来源之一**：与 as-built 的偏离全部列在 `ARCHITECTURE.md` §15，别按 spec 直接改代码 |
| `superpowers/specs/2026-10-04-wheremy-multi-item-capture-design.md` | 多件录入设计 | 同上；`RULES.md` §4/§5.6 是它的落地版本 |
| `superpowers/plans/2026-10-02-wheremy-core-loop.md` | 最初的实现计划（过程记录，含推翻过的方案） | **只读历史**：不为其中的计划背书；里程碑与顺序以 `REBUILD.md` §3 为准 |
| `superpowers/plans/2026-10-02-wheremy-backup-reminders.md` | 备份与提醒的实现计划 | 同上 |

三份历史文件**不参与冲突裁决**（`README.md` §3.2）：它们记录"当时怎么想"，不定义"现在怎么做"。

---

## 9. 本文档集的变更记录

详见 `CHANGELOG.md`。最近一次：

| 日期 | 变更 |
|---|---|
| 2026-10-07 | 文档集审查与扩充：核对五份 as-built 文档与基线源码；补齐角色文档（`PROJECT.md`/`PROCESS.md`/`TESTING.md`/`DEVELOPMENT.md`/`RELEASE.md`/`DESIGN_SYSTEM.md`/`GLOSSARY.md`/`DECISIONS.md`）；新增技术选型准则（`TECH_STACK.md`，用户拍板"官方/成熟方案优先，尽量不自己造轮子"）；补回被引用的 `superpowers/` 材料；建立模板目录与本文的导航与裁法 |
| 2026-10-08 | ① 联网复核技术选型：`TECH_STACK.md` §8.9（确认既有结论 + 新增发现 **Room 3.0.3**、Gradle 9.8.1、Compose BOM→M3 **1.4.0**、官方 Nav3 样板仓库）；② 新增 **`PLAN.md`**（就绪判定 → 可开工；技术起点取现行代际；"先做可运行的 UI 骨架 + 设计系统，再按纵向切片推进"；P0–P6 阶段与出口判据）；③ 新增 **`DELTA.md`**（从 `PRD.md` 自动生成的 87 条 FR 对比矩阵 + W1–W11 细节清单 + 界面/工程对比） |
