# 发布记录 —— wheremy 0.1.0（2026-10-13）

> 用法来自 `RELEASE.md` §8：把 `templates/release-checklist.md` 复制一份填，**别把填好的放回 `templates/`**。
> 本文件就是那份**已填好的记录**；规矩与出处全在 `RELEASE.md`，这里只放证据与读数。
> 模板的硬规矩：**没做的格子必须写理由，不许留空**——下面凡是空着的都注明了"为什么空、什么时候填"。

## 0. 本次发布

| 项 | 内容 |
|---|---|
| `versionName` / `versionCode` | `0.1.0` / `1`（首个交付版，未发布过，不需要 +1） |
| commit | 本记录所在提交 **`70345b9`**；**归档的那个包由 `75e7c54` 构建**（依据：APK 内 `META-INF/version-control-info.textproto`；两者在 `app/src/main` 上零差异） |
| 发布物文件名 + SHA-256 | **`wheremy-0.1.0-release.apk`**，2,746,725 字节，`05d0ebd179b995570486c1d457f69cfbf8b0f21ff5b44ae2951bfdac1743703e` |
| 签名摘要 | DN `CN=Wheremy, OU=Personal, O=Wheremy, L=Beijing, ST=Beijing, C=CN`；证书 SHA-256 `8118b97d588e43c91a77540154fda366749e0111f1bafd2765ee7a47676b8369`；`apksigner verify` 无输出＝通过 |
| 归档位置 | **`artifacts/release/0.1.0/`**（APK + `SHA256SUMS.txt` + `README.md`）；**不进 git**，哈希与证据在 `RELEASE.md` §10.10 |
| 发布负责人 / 日期 | **待用户签**：软件经理＝用户本人（只有他持 keystore）；日期留空待填 |
| 给谁、怎么给（侧载渠道） | **待定**：目前只有用户自己一台机器在用；没有对外分发渠道，也没有上架计划 |

## 1. 环境快照

1. **装的是哪个包、装于何时**：`com.wheremy.app`（release 包，上面那个哈希），
   **2026-10-13 11:06 覆盖安装**（`adb install -r`，**数据保留**）。此前的**人工清单七条**验收在
   2026-10-12 15:38 那次**全新空白**安装上做的（`RELEASE.md` §10.6）。
2. **本机数据与权限**：10-12 走查开始时**全新空白**（用户此前把两个应用都删了），通知权限**未授过**（第 7 步里授的）；
   10-13 演练开始时数据为 **1 位置 / 3 物品 / 2 照片**（源自 10-12 走查 + 之后试存的两件）。

## 2. ⭐ 发布前必做（逐条签收）

| # | 项 | 完成 | 证据 / 理由 | 签收人 |
|---|---|---|---|---|
| 1 | ⭐FR-DB-01 真实 Migration 替换破坏性迁移 | ✅ 不适用 | 本次没动 schema：`DB_SCHEMA_VERSION = 1`，且**没有** `fallbackToDestructiveMigration`（`RELEASE.md` §10.2 第 4 项） | 架构师（脚本+测试）＝本人自验 |
| 2 | ⭐FR-RLS-01 release 签名（keystore **只记位置**） | ✅ | 用户自己的 keystore（`D:\keys\wheremy-release.jks`，别名 `wheremy-release`；**口令只在用户密码管理器里**）；`apksigner` 与 `signingReport` 两处互证同一证书 `8118b97d…` | 用户 |
| 3 | `isMinifyEnabled` 的处置 + 理由 | ✅ | **打开**（`isMinifyEnabled = true` + `isShrinkResources = true`），理由与 keep 规则见 `ARCHITECTURE.md` §… 与 `proguard-rules.pro`；R8 包真机冒烟过 | 架构师 |
| 4 | 权限复核（只有 `INTERNET` / `POST_NOTIFICATIONS` / `CAMERA`） | ✅ | 自己声明这三条，无存储权限；合并清单另有 WorkManager 四条（§2.1 已记） | 架构师 |
| 5 | `allowBackup="false"` 复核 | ✅ | 源码 + **release APK 清单**两处核过 | 架构师 |
| 6 | 备份 / 导入回归（`RULES.md` §12 全部判据） | ✅ | 单测覆盖 + 2026-10-13 真机往返（§10.8：合并导入 / 覆盖导入 / 逐字节对账） | 本人自验 + 用户真机 |
| 7 | 迁移演练：旧包 → 导入 → 对账 | ✅（首版口径） | **首版没有"上一发布版"**（`RELEASE.md` §3.7），做的是**同版本往返 + 旧包合并**，四条回执与逐字节对账见 §10.8；跨版本留给 0.2.0 | 用户 |

## 3. 门禁 G0–G5 的证据

| 门 | 证据 | 通过 |
|---|---|---|
| **G0 构建** | `assembleDebug` / `assembleRelease` 均 `BUILD SUCCESSFUL`；`app-release.apk` 2,746,725 字节；本记录所在轮次三个产物 mtime 都是当轮的 | ✅ |
| **G1 单测全绿双证** | ① 命令输出 `BUILD SUCCESSFUL`；② **43 份 XML** 的 mtime 都晚于本轮起跑，自数 **`tests=268 failures=0 errors=0`** | ✅ |
| **G2 变异验牙** | 本批 **4 处改动逐个验牙，4/4 都红**（明细见下） | ✅ |
| **G3 覆盖对账** | 正向：`DELTA.md` §2 的 **87 行**矩阵每行都能落到真实测试类；反向：**43 类 / 268 例**；`check-prd-delta.py` 零不一致 | ✅ |
| **G4 文档同步** | 本轮改了：`CHANGELOG.md`、`RELEASE.md`（§7.1.1／§9 第 23–29 条／§10.6–§10.10）、`UI.md` §3.1、`PRD.md` FR-FND-04、`PROJECT.md` 发布行 | ✅ |
| **G5 真机人工清单签收** | 见第 4 节（用户本人，7/7） | ✅ |

**G2 明细（把生产代码改坏 → 期望那条红 → 实际红了哪条）**：

| # | 改坏了什么 | 期望并实际变红的用例 |
|---|---|---|
| M1 | `ReviewScreen.kt`：`contentDescription = "名称，必填"` → `"名称"`（丢掉"必填"） | `ReviewAccessibilityTest > 名称框的读屏文案带必填` ✅ |
| M2 | `SettingsScreen.kt`：自动导出行从"整行一个可切换项"还原成"`Switch` 独立 + 标签另起一行" | `SettingsAccessibilityTest > 自动导出的开关与标签是同一个无障碍节点` ✅ |
| M3 | `SettingsScreen.kt`：预设芯片 `selected = state.leadDays == days` → `selected = false` | `SettingsAccessibilityTest > 提前天数的预设芯片带选中状态` ✅ |
| M4 | `GalleryScreen.kt`：`selected = state.filters.onlyInbox` → `selected = false` | `GalleryAccessibilityTest > 芯片的标签与状态在同一个无障碍节点上` ✅ |

> 四处改坏之后**都已还原**，`git status app/src/main` 零差异，随后完整 CI 再跑一遍仍全绿。

## 4. 真机人工清单签收（`REBUILD.md` §6 七条）

**2026-10-12 由用户本人逐条走完，7/7 通过**；逐条结果与**用户原话**（含首走时那条键盘缺陷的原话）在 `RELEASE.md` §10.6。
这里不重复贴，只记结论与签收人：

| # | 要验的东西 | 结果 | 签收人 |
|---|---|---|---|
| 1 | 取景屏预览真的亮了、快门跟手 | ✅ | 用户 |
| 2 | 系统选图器行为 | ✅ | 用户 |
| 3 | 通知到底弹没弹、点进去落到哪 | ✅（修掉 F48 键盘缺陷之后） | 用户 |
| 4 | EXIF 方向 | ✅ | 用户 |
| 5 | HyperOS 权限弹窗与静默驳回 | ✅ | 用户 |
| 6 | 桌面图标在启动器里的渲染 | ✅ | 用户 |
| 7 | 长名字 / 多标签在字体缩放下的排版 | ✅ | 用户 |

**无障碍专项**（与上面七条并列的另一件事）：**TalkBack 焦点次序走查 2026-10-13 通过**（两轮；第一轮 4 处问题都已修），见 §10.7。

## 5. 迁移演练与备份自查

- [x] 用**上一版导出**的包：合并导入 → 对账三个数（位置 1 / 物品 3 / 照片 2）
      —— 🗣「合并导入完成 · 位置 0 个 · 物品 0 件 · 照片 0 张」（旧包内容本机都有，**按主键全部跳过**）
- [x] 覆盖导入 → 对账三个数：🗣「覆盖导入完成 · 位置 1 个 · 物品 3 件 · 照片 2 张」，
      与动手前那次导出的回执**一字不差**；`data.json` 与四张照片**逐字节一致**（只有 manifest 的 `exportedAt` 不同）
- [x] 反面演练：包内 `manifest.json` 的 `schemaVersion` 改成 `2` → 🗣「合并导入失败：这个备份来自更新的版本（schema 2 > 1），先升级应用再导入」，
      被拒后 `data.json` 哈希与导入前相同 = **一条没动**
- [x] 包里没有 key、没有任何设置项（只看条目名）：包内只有 4 个条目
      （`manifest.json` / `data.json` / `photos/originals/<照片>.jpg` / `photos/thumbs/<照片>.jpg`）
- **动手前那次「导出到文件」的回执（三个数）**：🗣「导出完成 · 位置 1 个 · 物品 3 件 · 照片 2 张 · 468 kB」

### 5.1 加密包的演练（2026-10-14 补，`FR-BAK-09` 落地后）

四条全过，逐步回执见 `RELEASE.md` **§10.8.1**：加密导出（包里搜不到物品名）→ 错口令「口令不对」且对话框留着 →
对口令导入成功 → **明文 v1 包仍然不问口令就能导入**（`ADR-27` 的兼容承诺）。
顺带记下两个真机操作坑：`adb shell input text` 会被输入法拼成中文（用数字口令）；`adb push` 的文件在选择器里看不见（用应用自己导出）。

## 6. 发布后 24 小时观察项

**整节未做**——因为**还没正式发布**（本记录是"发布前"状态；发布动作由用户决定）。条目本身已定下来
（`templates/release-checklist.md` §6），发布后按它逐条打勾：

- [ ] 能打开、不崩
- [ ] 录一条物品（拍照或选图 → 保存）
- [ ] 搜一次、筛一次
- [ ] 导出一次，回执里的数字对得上
- [ ] 到期提醒当天有没有响（`POST_NOTIFICATIONS` 已授）
- [ ] 用户回的原话里有没有异常 → 有就开 `templates/defect-report.md` 并定级

## 7. 回滚决定与签收

| 项 | 内容 |
|---|---|
| 可回滚到什么（哪个包、放在哪） | `artifacts/release/0.1.0/wheremy-0.1.0-release.apk`（2,746,725 字节 / `05d0ebd1…`）——**就是验收通过的那一版** |
| 回滚前必须先做什么 | **让用户先导出**，并别改名、别移动那个 zip（`RELEASE.md` §7.1.1 与 §7.2 的 P0 第一步） |
| 谁持有 keystore（只写人，**不写口令**） | 用户本人（位置 `D:\keys\wheremy-release.jks`，别名 `wheremy-release`；口令只在密码管理器里） |
| 回滚触发条件 | 出现 P0（数据损失/不可逆）或 P1（主线不可用/界面说的不成立）且判定是包的问题（`RELEASE.md` §7.2） |
| 是否回滚 / 决定人 / 时间 | **尚未发生**（发布后 24 小时内按需决定；决定人＝用户） |

- [x] 发布记录已归档（APK + 哈希 + keystore 位置）
- [x] 仓库根 `CHANGELOG.md` 已写入本次版本
- [x] `docs/CHANGELOG.md` 已记本次发布相关改动

## 附：本次**不做**的东西（免得以后有人以为漏了）

| 项 | 状态 | 触发条件（来自 `PLAN.md` §7） |
|---|---|---|
| `FR-SRC-08` 多图 | ⬜ 等决定 | 用户拍板要不要；牵涉库结构（一物多图） |
| `FR-BAK-09` 备份加密 | ⬜ 等决定 | 用户拍板；须同时定"忘了口令怎么办" |
| `FR-EXP-09` / `FR-PRM-06` / `FR-LOC-08` / `FR-AI-09` / `FR-AI-10` | ⬜ 事件驱动 | 各自的触发条件见 `PLAN.md` §7 与 `PROJECT.md` |
| Baseline Profile | ⬜ 有触发条件 | 冷启动中位数连续两轮 > 阈值时做（`RELEASE.md` §10.4 记了当前 218 ms） |
