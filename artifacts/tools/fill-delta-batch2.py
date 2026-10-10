# 归档第二、三批：FR-LOC / FR-FND / FR-OPR / FR-EXP / FR-BAK / FR-CFG / FR-TRC / FR-PRM。
# 只改映射里有的行；每行的证据必须是具名测试类或真机动作。
import re
import sys

PATH = r"D:\src\dsh\wheremy\docs\DELTA.md"

ROWS = {
    # —— 位置（FR-LOC）——
    "FR-LOC-01": ("✅", "同参照：下钻 + 面包屑，根层级不参与收纳",
                  "`LocationTreeTest` / `TreeViewModelTest`；真机下钻与返回"),
    "FR-LOC-02": ("✅", "同参照：新增/改名/移动/删除四件在同一个菜单里",
                  "`LocationRepositoryTest` / `TreeViewModelTest`；真机逐项走过"),
    "FR-LOC-03": ("✅", "同参照：类型是软引导——填什么都行，选单只给常见值",
                  "`LocationRepositoryTest`；真机新增位置时改类型"),
    "FR-LOC-04": ("✅", "同参照：只有空位置能删，弹窗说清「子位置和物品需要先搬走」",
                  "`LocationRepositoryTest`（删除门槛）；真机弹窗"),
    "FR-LOC-05": ("🟡", "与参照同形：有那句实话 + 节点菜单里的「移到根层级」，但**没有**一键把里面物品搬空的快捷入口",
                  "`TreeScreen` 的删除门槛弹窗；真机"),
    "FR-LOC-06": ("✅", "同参照：位置也能有照片（`PhotoOwner.LOCATION`）",
                  "`TreeViewModelTest` / `PhotoStoreTest`；真机位置页缩略图"),
    "FR-LOC-07": ("✅", "同参照：防环、防重名、防自己挂自己",
                  "`LocationTreeTest`（环与孤儿父子）+ `LocationRepositoryTest`"),
    "FR-LOC-08": ("⬜", "同参照未做：路径数到几百条时要把候选裁剪或分组",
                  "—"),
    # —— 查找（FR-FND）——
    "FR-FND-01": ("✅", "同参照：一个 `search_text` 覆盖名称/品牌/颜色/材质/别名/标签/描述/备注",
                  "`SearchTextBuilderTest`（`FIELD_VERSION = 2`）+ `SearchRankerTest`"),
    "FR-FND-02": ("✅", "同参照：结果就是照片网格（自适应列数，矮窗口横排）",
                  "`GalleryViewModelTest`；真机竖/横两张截图"),
    "FR-FND-03": ("✅", "同参照：类别/季节/标签/只有待归类/快到期-已过期",
                  "`ItemRepositoryTest` / `GalleryViewModelTest`；真机逐个芯片点过"),
    "FR-FND-04": ("✅", "同参照：待归类是 tab，不是另一张表（谓词 `locationId IS NULL OR status='DRAFT'`）",
                  "`GalleryViewModelTest`；真机；两条教训 F16（确认≠已归位）与 F19（只改标题不改谓词）"),
    "FR-FND-05": ("✅", "同参照：空态说清是「一件都还没有」还是「被筛空了」",
                  "`GalleryViewModelTest`；真机两种空态各看一次"),
    "FR-FND-06": ("✅", "同参照：点结果能看到完整路径，并能跳到那个位置",
                  "`DetailViewModelTest`；真机从搜索进详情再跳位置"),
    "FR-FND-07": ("🟡", "与参照同形：超过 300 条**静默截断**；「告知或分页」这一条**还没做**——它在用户批准的首版清单里，仍在待做队列（见本文 §4）",
                  "`ItemRepositoryTest`（`LIMIT 300` 与 `ORDER BY updatedAt DESC`）"),
    "FR-FND-08": ("⬜", "同参照未做：多标签/多类别组合筛选（现在是单值）",
                  "—"),
    "FR-FND-09": ("⬜", "同参照未做：「最近动过」排序（现在固定按更新时间的倒序）",
                  "—"),
    # —— 单项操作（FR-OPR）——
    "FR-OPR-01": ("✅", "同参照：详情页给完整路径、照片、全部字段",
                  "`DetailViewModelTest`；真机"),
    "FR-OPR-02": ("✅", "同参照：详情页「编辑」回到确认页，字段齐全",
                  "`ReviewViewModelTest`；真机（F31 修的就是这条路：页面开了却从不 `start`）"),
    "FR-OPR-03": ("✅", "同参照：详情页搬走与卡片菜单搬移是同一段逻辑",
                  "`ItemRepositoryTest`（`move` 与 `lastMovedAt` 口径）；真机两处各走一次"),
    "FR-OPR-04": ("✅", "同参照：按照片共用情况分四档说人话，二次确认",
                  "`UiFormatTest.deleteWarningOf`；真机删除确认"),
    "FR-OPR-05": ("✅", "同参照：手工搬动即视为确认，但有名字门槛（空名不给搬）",
                  "`ItemRepositoryTest`"),
    "FR-OPR-06": ("⬜", "同参照未做：多选后批量搬移/删除/改类别",
                  "—"),
    # —— 到期提醒（FR-EXP）——
    "FR-EXP-01": ("✅", "同参照：每件一个到期日 + 种类（保质期/证件/自定义）",
                  "`ItemRepositoryTest`；真机确认页设到期日"),
    "FR-EXP-02": ("✅", "同参照：每天一次（`1, DAYS` + `KEEP`），**一次只发一条聚合通知**",
                  "`ReminderRunnerTest`；真机收到通知"),
    "FR-EXP-03": ("✅", "同参照：0–365 天，含「当天」；改完立刻重排",
                  "`SettingsRepositoryTest` / `ReminderEngineTest`；真机改成 30 天、杀掉重开还在"),
    "FR-EXP-04": ("✅", "同参照：`expiringBy` 三分支把「已过期且说过了」排除掉，因此同一截止日最多说两次",
                  "`ReminderEngineTest`（三分支逐条）；真机调试按钮连按两次不回写"),
    "FR-EXP-05": ("✅", "同参照：点通知进的是**筛好的**照片墙，不是首页",
                  "`MainActivity` 读 `ReminderNotifier.EXTRA_ENTRY`；真机（F25：已运行时走 `onNewIntent`）"),
    "FR-EXP-06": ("✅", "同参照：权限没给就只跳系统页，不就地申请、不谎报已发",
                  "真机设置页那句话与按钮各走一次"),
    "FR-EXP-07": ("✅", "同参照：设置页 debug 区「立刻跑一次」；**写回是有意的**（与真实发送共用一段逻辑）",
                  "`ReminderRunnerTest`；真机连按两次核过"),
    "FR-EXP-08": ("✅", "同参照：卡片上的到期行 + 详情页的到期日",
                  "`UiFormatTest`；真机照片墙卡片"),
    "FR-EXP-09": ("⬜", "同参照未做：借出归还、很久没动过这类提醒",
                  "—"),
    # —— 备份（FR-BAK）——
    "FR-BAK-01": ("✅", "同参照：手动导出 zip（四张表一个事务里读，同一路径只写一次，读不到的图跳过）",
                  "`BackupRepositoryTest`；真机导出到文件并拉到电脑上看过条目"),
    "FR-BAK-02": ("✅", "同参照：合并（按主键跳过）与覆盖（先清空再重建，并删本机多余照片）",
                  "`BackupRepositoryTest`；真机两种模式各走一次（删一件 → 覆盖导入 → 那一件回来）"),
    "FR-BAK-03": ("🟡", "与参照同形：库是**一个事务**、失败整体回滚；照片文件层**刻意**不进事务（先在暂存目录，事务成功后才搬）",
                  "`BackupRepositoryTest`（四拒之后库里一条没动）"),
    "FR-BAK-04": ("✅", "同参照：导出通道的构造参数里**没有** `SettingsRepository`，key 物理上进不去",
                  "`DataModule.backupRepository` 的构造参数；真机包内条目只有 `manifest.json` / `data.json` / `photos/**`"),
    "FR-BAK-05": ("✅", "同参照：不像备份包的输入直接拒，且是**中文人话**（`BackupFormatException.message`）",
                  "`BackupRepositoryTest`"),
    "FR-BAK-06": ("✅", "同参照：四条拒 + 一条放行；孤儿/环让外键拒掉整个包（`parentsFirst`）",
                  "`BackupRepositoryTest`；真机（包里有对不上的引用时那句「备份里有对不上的引用，已取消导入，原来的数据没有动」）"),
    "FR-BAK-07": ("✅", "同参照：`schemaVersion` 是导入侧**唯一**的兼容闸门，只查上界不查下界",
                  "`BackupRepositoryTest`（更高版本直接拒，本机数据一条不动）"),
    "FR-BAK-08": ("⬜", "同参照未做：自动定时导出",
                  "—"),
    "FR-BAK-09": ("⬜", "同参照未做：备份加密",
                  "—"),
    # —— 设置（FR-CFG）——
    "FR-CFG-01": ("✅", "同参照：三节（到期提醒 / AI 分析照片 / 备份）+ 开发期工具",
                  "真机逐节走过"),
    "FR-CFG-02": ("✅", "同参照：AI 开关 + 接口地址 / 模型名 / key 三个字段",
                  "真机（设备上的偏好文件里读到 `base_url=https://api.deepseek.com`、`model=deepseek-flash`）"),
    "FR-CFG-03": ("✅", "同参照：key 只在 DataStore（`files/datastore/wheremy_settings.preferences_pb`），不进备份包、不进留痕、不进日志",
                  "真机；`BackupRepositoryTest`（包内无设置）"),
    "FR-CFG-04": ("✅", "同参照：提前天数八颗预设 + 0–365 手填",
                  "`SettingsRepositoryTest`；真机"),
    "FR-CFG-07": ("✅", "同参照：设置读不出来时按默认值显示，并且**说出**这不是存下来的值",
                  "`SettingsRepositoryTest`（读写失败路径）"),
    "FR-CFG-05": ("⛔", "同参照：**不提供**主题/配色设置项——跟随系统（含深色与动态色），只有 chrome 用动态色",
                  "`WheremyTheme`；真机深/浅截图与设计系统基线图"),
    "FR-CFG-06": ("⬜", "**未做**：类别/季节/位置类型的自定义管理。它在用户批准的首版清单里，本实现还没做（现在词表是代码里的常量）",
                  "—"),
    # —— 可观测（FR-TRC）——
    "FR-TRC-01": ("✅", "同参照：一次尝试一条，重试就是两条；只记提示词原文与回应原文",
                  "`AiLogicTest.留痕上限是十条`；真机 `files/ai-trace/…-NOTHING_FOUND.txt`（就是靠它判出 F37 是模型自己说的）"),
    "FR-TRC-02": ("✅", "同参照：痕里没有 key、没有那张 base64 的图（`record()` 只收提示词与回应两个字符串）",
                  "`AnalysisTrace.record` 的签名；真机留痕文件逐字看过"),
    "FR-TRC-03": ("✅", "比参照更硬：目录写死在 `files/ai-trace`（**不在 `files/photos` 下**），备份打包只走 photos，所以物理上出不了门",
                  "`AiLogicTest.留痕上限是十条`（断言目录名不以 photos 开头）；真机备份包条目里没有它"),
    "FR-TRC-04": ("⬜", "**未做**：累计调用次数/花费展示。它在用户批准的首版清单里，本实现还没做",
                  "—"),
    "FR-TRC-05": ("🟡", "与参照同形：痕里每条有 `at=<毫秒时间戳>` 与 `outcome`，**没有单次耗时**字段",
                  "`AnalysisTrace`（`at=` / `outcome=` / `prompt=` / `response=` 四行）；真机留痕文件"),
    # —— 权限与适配（FR-PRM）——
    "FR-PRM-01": ("✅", "比参照少一条也够：自己声明 `POST_NOTIFICATIONS` + `CAMERA` + `INTERNET`（AI 请求，F36），**无任何存储权限**（选图走系统选图器）",
                  "`aapt2 dump badging`（debug 与 release 两个 APK）；真机系统「应用信息 → 权限」"),
    "FR-PRM-02": ("✅", "比参照多做半步：进屏**自动申请一次** + 常驻「打开相机权限」出口，两件事都在",
                  "`CameraGateTest`；真机自动权限框 + 拒绝后的取景屏"),
    "FR-PRM-03": ("✅", "同参照：Android 13+ 通知权限被拒就降级，只引导去系统页",
                  "真机设置页与调试按钮"),
    "FR-PRM-04": ("✅", "**不锁竖屏**（参照是 ⛔ 锁竖屏）：窄窗口底栏 / 宽 ≥600dp 左侧导航轨 / 矮窗口（高 <480dp）照片墙横排格子",
                  "`WheremyRoot` 的 `BoxWithConstraints` 分级；真机竖/横两张截图（F21 治好的那条）"),
    "FR-PRM-05": ("🟡", "做了一半：格子图给物品名（空名给「未命名物品」）、详情照片给「物品照片」、位置缩略图给「<位置名>的位置照片」，触摸目标沿用 M3 的 48dp；**没做** `accessibility-check` 的 ATF 深扫与 TalkBack 走查",
                  "真机 `uiautomator dump`（`content-desc` 是物品名）；`CameraScreenScreenshotTest` 等 5 张基线图"),
    "FR-PRM-06": ("⬜", "同参照未做：多语言（现在中文硬编码在源码里，按 ADR-16 不抽 `strings.xml`）",
                  "—"),
}


def main():
    with open(PATH, encoding="utf-8") as f:
        lines = f.readlines()

    filled = 0
    seen = set()
    out = []
    for line in lines:
        m = re.match(r"^\|\s*`(FR-[A-Z]+-\d+)`\s*\|", line)
        if not m:
            out.append(line)
            continue
        fid = m.group(1)
        seen.add(fid)
        if fid not in ROWS or not line.rstrip().endswith("| | | |"):
            out.append(line)
            continue
        parts = [c.strip() for c in line.strip().strip("|").split("|")]
        impl, note, evidence = ROWS[fid]
        out.append("| `%s` | %s | %s | %s | %s | %s |\n" % (fid, parts[1], parts[2], impl, note, evidence))
        filled += 1

    missing = [fid for fid in ROWS if fid not in seen]
    with open(PATH, "w", encoding="utf-8", newline="") as f:
        f.writelines(out)
    print("batch2/3 filled: %d" % filled)
    print("mapping not found in doc: (%d) %s" % (len(missing), ", ".join(missing)))
    if missing:
        sys.exit(2)


main()
