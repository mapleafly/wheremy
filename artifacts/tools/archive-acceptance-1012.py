# 记录 2026-10-12 真机人工清单 7/7 签收（RELEASE §10.7），并把一批「过期状态」对齐事实。
import io
import sys

sys.stdout.reconfigure(errors="replace")
D = r"D:\src\dsh\wheremy\docs"
ok = 0
fail = []


def edit(path, old, new, label):
    global ok
    t = io.open(path, encoding="utf-8").read()
    if old not in t:
        fail.append(label)
        return
    t = t.replace(old, new, 1)
    io.open(path, "w", encoding="utf-8", newline="").write(t)
    ok += 1


# ============ A. RELEASE.md：新增 §10.7 签收记录 ============
REL = D + r"\RELEASE.md"
sec107 = """
### 10.7 真机人工清单复走 —— 2026-10-12（用户本人，**7/7 通过**）

> 这一节是**这一轮**的签收，判据同样是机器/用户产生的证据。交付给用户的那份是
> `templates/device-acceptance.md`（七条，写在最前面、不出现内部编号）。

**环境快照**（模板要求的两行）：

1. 手机上装的是 **Wheremy release 包**：`com.wheremy.app`、versionName `0.1.0` / versionCode `1`、
   用**用户自己的 keystore** 签名（`apksigner` 的 SHA-256 `B070DD26…`）、**2.62 MB**；
   装的时间 **2026-10-12 15:38**（重装，故走查开始时是全新空白）；
2. 数据状态：走查开始前 **全新空白**（用户此前把两个包都删了）；通知权限：走查前 **未授过**（第 7 步里授的）。

**逐条结果**：

| # | 步骤 | 结果 | 用户回的原话（逐字） |
|---|---|---|---|
| 1 | 打开看一眼 | ✅ 通过 | —— |
| 2 | 拍一张或选一张 | ✅ 通过 | —— |
| 3 | 存进去一件 | ✅ **通过**（本轮修掉 F48 之后） | 首走时卡住：「焦点进入「名称」输入框后，不能调出键盘。填写不进去任何文字，所以也不能保存（因为名称为空）。」 |
| 4 | 再找回来 | ✅ 通过 | —— |
| 5 | 建一个位置 | ✅ 通过 | —— |
| 6 | 导出 | ✅ 通过 | 🗣「导出完成 · 位置1个 · 物品1件 · 照片1张 · 273KB」 |
| 7 | 通知这条路 | ✅ 通过 | —— |
| 8 | 回我三件事 | ✅ 已回 | 🗣「每一步都一样完成，导出回执：导出完成 · 位置1个 · 物品1件 · 照片1张 · 273KB，没有任何其他弹窗」 |

**第 6 步回执自洽性核对**：位置 **1** 个（第 5 步建的）、物品 **1** 件（第 3 步存的）、照片 **1** 张
（第 2 步拍的）——三个数字与走查动作一一对应，**自洽**。

**第 8 步的关键答复**：「**没有任何其他弹窗**」。模板把"有没有出现账号/登录/密码/验证类弹窗"
列为必答项，因为那意味着应用要了不该要的东西；答"没有"= 这条通过。

**⚠️ 本轮**没有**覆盖到的**：**AI 预填**。重装后 API key 丢失（设置页显示「还缺 API key」），
所以第 2 步走的是「没配 key」的分支：照片能拍进来、字段留空手填。AI 预填的真机验证在更早轮次做过
（`PLAN.md` §10 的 P2 行：AI 已接、真机连模型验证通过）。要本轮也覆盖，需在设置页重填 key 后再走一次第 2 步。

**判据对应**：`RELEASE.md` §9 第 21 条（真机人工清单七条逐条签收）→ ✅；第 22 条（TalkBack 焦点次序走查）
**仍未做**（那是无障碍专项，与这七条是两件事）。

"""
edit(REL, "### 10.6 结论", sec107.lstrip("\n") + "### 10.6 结论", "RELEASE §10.7")

# ============ B. PROJECT.md ============
PROJ = D + r"\PROJECT.md"
edit(PROJ,
     "| ⭐FR-DB-01 | 用真实 Room Migration 替换 `fallbackToDestructiveMigration(true)` | 现在改任何 `@Entity` 字段 = **静默清空整机数据**（`RULES.md` §15 W2） | 未做 |",
     "| ⭐FR-DB-01 | 用真实 Room Migration 替换 `fallbackToDestructiveMigration(true)` | 现在改任何 `@Entity` 字段 = **静默清空整机数据**（`RULES.md` §15 W2） | **本实现没有这个债**：从第一天就没引入 `fallbackToDestructiveMigration`（`WheremyDatabase.kt:15` 注释写明），版本一升没交 Migration 会当场抛错而不是清库。真升 schema 时按 `REBUILD.md` §2 走（写 Migration + 测试） |",
     "PROJECT FR-DB-01")
edit(PROJ,
     "| ⭐FR-RLS-01 | release 签名包（现 `isMinifyEnabled = false` 且无 signingConfig） | 无法稳定侧载给别人用、无法升级安装 | 未做 |",
     "| ⭐FR-RLS-01 | release 签名包 | 无法稳定侧载给别人用、无法升级安装 | ✅ **2026-10-10 完成（用户自己做）**：用户生成 keystore 并在仓库外放好 `keystore.properties`；`signingReport` 是 `Config: release`，真机装的是正式签名包。另：R8 早已打开（`isMinifyEnabled = true` + `isShrinkResources = true`，`app/build.gradle.kts:76-77`），旧的「未混淆」说法已过期 |",
     "PROJECT FR-RLS-01")
edit(PROJ,
     "| R1 | **改 schema 静默清空整机数据**（`fallbackToDestructiveMigration(true)`） | 中 | 极大（数据不可逆） | 任何一次想改 `@Entity` 的冲动 | 验收期冻结字段（`REBUILD.md` §2 第 1 条）；解禁 = ⭐FR-DB-01 落地；改前必须先导出 |",
     "| R1 | **改 schema 的升级路径**（本实现没有破坏性迁移，风险已降级） | 低 | 大（升错会抛错/丢数据） | 任何一次想改 `@Entity` 的冲动 | 本实现没引入 `fallbackToDestructiveMigration`，所以「静默清空」这条风险不存在；要改 schema 仍按 `REBUILD.md` §2 走：写 Migration + migration 测试 + 改前先导出 |",
     "PROJECT R1")
edit(PROJ,
     "| R2 | 无 release 签名 / 未混淆 | 高（一旦要分发） | 中 | 要把包给别人用时 | ⭐FR-RLS-01；`RELEASE.md` §3 |",
     "| R2 | ~~无 release 签名 / 未混淆~~ **已消解（2026-10-10）** | ~~高~~ 低 | 中 | —— | ⭐FR-RLS-01 已完成（用户 keystore）；R8 与资源收缩已开；`RELEASE.md` §3 |",
     "PROJECT R2")
edit(PROJ,
     "| 功能 | M0–M6 全部落地；`PRD.md` §3 共 **87 条 FR**：`✅` 65、`🟡` 7（AI-11 / LOC-05 / FND-07 / BAK-03 / PRM-02 / PRM-05 / TRC-05）、`⬜` 14、`⛔` 1（CFG-05）；`PRD.md` §5 待做队列 **21 条**（17 条 `⬜` + 4 条 `🟡`，另含 ⭐FR-DB-01、⭐FR-RLS-01 两条发布前必做） |",
     "| 功能 | M0–M6 全部落地；`PRD.md` §3 共 **87 条 FR**：`✅` **79**、`🟡` **0**、`⬜` **7**（SRC-08 / BAK-09 / EXP-09 / PRM-06 / LOC-08 / AI-09 / AI-10，都是「大」且带触发条件的）、`⛔` 1（CFG-05）——**2026-10-12 口径**（对账脚本 `artifacts/tools/check-prd-delta.py` 零不一致） |",
     "PROJECT 功能行")
edit(PROJ,
     "| 测试 | 39 类 / 481 例全绿；判绿方式见 `PROCESS.md` §6 G1 |",
     "| 测试 | **42 类 / 261 例全绿**（2026-10-12：本地 CI 四道门 + GitHub CI 真跑全绿）；判绿方式见 `PROCESS.md` §6 G1 |",
     "PROJECT 测试行")
edit(PROJ,
     "| 发布 | **未就绪**：⭐FR-DB-01、⭐FR-RLS-01 未做；`RELEASE.md` §3 是就绪清单 |",
     "| 发布 | **本轮已具备交付条件**：⭐FR-RLS-01 已完成（用户 keystore）、**真机人工清单七条 2026-10-12 全部通过**（`RELEASE.md` §10.7）；未做的只剩**无障碍专项**（TalkBack 焦点次序走查，第 22 条）与那 7 条带触发条件的 `⬜`。`RELEASE.md` §3 是就绪清单 |",
     "PROJECT 发布行")
edit(PROJ,
     "| 下一步（建议顺序） | ① **执行 `PLAN.md` §8 的三步**（Phase 0 spike → 设计系统落地 → 采集对比基线）；② 之后按 P1–P6 推进，每阶段走 G0–G5 更新 `PLAN.md` §10 与 `DELTA.md`；③ 发布前回到 ⭐FR-DB-01 / ⭐FR-RLS-01 与 `RELEASE.md` §3 清单 |",
     "| 下一步（建议顺序） | ① **无障碍专项**：用户按 `templates/talkback-walkthrough.md` 走一遍（7 屏，听焦点次序）；② 若要验 **AI 预填**：设置页重填 API key 后重走验收单第 2 步；③ 之后按 `PRD.md` §5 的触发条件决定那 7 条 `⬜` 要不要开（`FR-SRC-08` 多图与 `FR-BAK-09` 加密需要用户先拍板） |",
     "PROJECT 下一步行")

# ============ C. README.md ============
edit(D + r"\README.md",
     "| 当前形态 | 验收期：schema 冻结、无 release 签名、破坏性迁移未换 | `REBUILD.md` §2、`PRD.md` ⭐FR-DB-01/⭐FR-RLS-01 |",
     "| 当前形态 | **可交付**（2026-10-12）：release 已用用户自己的 keystore 签名、R8 已开、真机人工清单七条全过（`RELEASE.md` §10.7）；schema 仍冻结、本实现未引入破坏性迁移 | `RELEASE.md` §3/§10.7、`PRD.md` ⭐FR-DB-01/⭐FR-RLS-01 |",
     "README 当前形态")

# ============ D. DELTA.md ============
edit(D + r"\DELTA.md",
     "| ⭐FR-RLS-01 release 签名包 | ⬜ 未做 | ⬜ | 目标 P6 出一份可侧载的签名包（keystore 不进 git） | |",
     "| ⭐FR-RLS-01 release 签名包 | ✅ **已完成（2026-10-10，用户自己做）** | ⬜ | 用户生成 keystore、仓库外放 `keystore.properties`；`signingReport` 为 `Config: release`，真机装的是正式签名包（`KEYSTORE.md`） | |",
     "DELTA FR-RLS-01")

# ============ E. PRD.md ============
edit(D + r"\PRD.md",
     "| ⭐ FR-RLS-01 | release 签名包（现在用调试签名） | 侧载给别人用时（**用户已拍板\"后续再做\"**） | 小-中：要生成/保管 keystore，且**不进 git** |",
     "| ⭐ FR-RLS-01 | release 签名包 | ~~侧载给别人用时~~ **已完成（2026-10-10，用户自己做）** | 已还清：用户生成并保管 keystore（仓库外 `keystore.properties`，**不进 git**），release 包已正式签名；做法见 `KEYSTORE.md` |",
     "PRD FR-RLS-01")

print("成功替换 %d 处" % ok)
if fail:
    print("未匹配：")
    for f in fail:
        print("  - " + f)
