# 测试计划 / 报告（Test Plan & Report）

> 用法：一批改动一份。动手前填 1–3 节，跑完填 4–6 节。**跑完不填证据等于没跑。**
> 判绿的可操作定义在 `REBUILD.md` §1.1；分层做法与变异纪律见 `TESTING.md`。

## 0. 基本信息

| 项 | 内容 |
|---|---|
| 被测变更（FR / CR / 缺陷 id） | |
| 代码基线 commit / 执行人 / 日期 | |
| 本轮起跑时刻 | （后面判 XML 新旧要用它） |

## 1. 被测变更与影响面

- 改了什么（一句话）：
- 落在哪几层：

**可能被碰到的 🔒 规则**（勾了的必须有用例守着）：

- [ ] `RULES.md` §1 五个谓词（待归类 / 快到期 / 没保存 / 空壳 / 能保存）
- [ ] `RULES.md` §2 状态机、§3 先落库三条硬要求、§4 整批重排、§5 字段合并谁赢
- [ ] `RULES.md` §6 退出确认判定树与逐字文案
- [ ] `RULES.md` §9 删除与照片回收、§10 搜索三段与打分
- [ ] `RULES.md` §11 到期提醒（排班 / 最多两次 / 四变体 / 发不出去就不回写）
- [ ] `RULES.md` §12 备份（导出 / 固定顺序 / 四拒一放行 / MERGE 与 OVERWRITE）
- [ ] `RULES.md` §16 NFR-02 的可检验判据
- [ ] `UI.md` 信息架构、次序、逐字串
- [ ] 其它：

## 2. 分层清单

> 能落在低层就别放高层（`PRD.md` NFR-05）。只有"这一层 JVM 里打不开"的才允许交给真机自验。

| 层 | 本轮要加 / 改什么 | 类与例数 |
|---|---|---|
| 纯 JVM（domain，不依赖 Android） | | |
| Robolectric 数据（Room in-memory / Repository） | | |
| Robolectric 系统（WorkManager、权限、文件、SAF） | | |
| Compose UI（Robolectric + compose test） | | |
| mockwebserver 契约（AI 请求与失败分支） | | |
| 真机人工（只能真机看的） | | |

## 3. 变异验牙计划

> 全绿只证明"测试跑过了"。逐条写"改哪一行、期望哪条红"，跑完把实际结果填回来。

| # | 改坏哪一行（`文件:行`） | 怎么改坏 | 期望哪条红 | 实际 |
|---|---|---|---|---|
| 1 | | 反 boolean / 改阈值 / 删一次写 / 换文案 | | |
| 2 | | | | |

**没有牙的断言**：补 or 删（二选一，不许留着）：

## 4. 判绿证据（双证，缺一不可）

```bash
gradle :app:testDebugUnitTest        # 只许前台跑，见 REBUILD.md §7 A1
# ① 本轮 XML 都晚于起跑时刻（期望 39 份）
find app/build/test-results/testDebugUnitTest -name '*.xml' -newermt '<起跑时间>' | wc -l
# ② 自己数，不认 exit code
grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
  app/build/test-results/testDebugUnitTest/*.xml \
  | awk -F'"' '{t+=$2; f+=$6; e+=$8} END {print "tests="t, "failures="f, "errors="e}'
```

| 证据 | 值 |
|---|---|
| `BUILD SUCCESSFUL` | 是 / 否 |
| 本轮 XML 份数 / 期望 | ____ / 39 |
| `tests` / `failures` / `errors` | ____ / ____ / ____ |
| 结论 | 绿 / **NO-VERDICT**（XML 不新鲜一律算这个） |

> 两个假绿的来源：后台跑 gradle 会 exit 0 + 空输出；脚本不给项目根当 `cwd` 会 3–4 秒 exit 1 且不写 XML（`REBUILD.md` §7 A1/A2）。

## 5. 覆盖对账（双向）

| 方向 | 做法 | 结果 |
|---|---|---|
| 正向 | `PRD.md` §7 每行 → 类真的存在、`@Test` 数与表一致 | |
| 反向 | `grep -rl "@Test" app/src/test \| wc -l` = 39；`grep -c` 求和 = 481 | |

- 例数变化说明（从 481 变到多少、多 / 少在哪）；`ItemRow` 这种"没有自己的类"的行落到谁家：

## 6. 结论与遗留

- 结论：
- 本轮新增的已知偏差（同步进 `RULES.md` §15 / `UI.md` §14）：
- 遗留项（每条带触发条件）+ 下一批要补的测试：
