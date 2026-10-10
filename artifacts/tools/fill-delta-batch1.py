# 把 DELTA.md 的"本实现"列按批次填上（第一批：FR-SRC / FR-AI / FR-REV）。
# 只改映射里有的行，其余行原样不动；每行必须有具名测试或真机动作。
import re
import sys

PATH = r"D:\src\dsh\wheremy\docs\DELTA.md"

ROWS = {
    "FR-SRC-01": ("✅", "同参照；走系统选图器（`PickVisualMedia`），不需要任何存储权限",
                  "真机 2026-10-09：相册选图 → 回到录入页并显示新图（`device-picked-photo-review.png`）；`ReviewViewModelTest`"),
    "FR-SRC-02": ("✅", "同参照；CameraX 1.5.3，取景屏三态",
                  "真机快门 → 确认页，竖拍照片方向正确（`device-shot-portrait-review.png`）；`CameraGateTest` / `CameraScreenTest`"),
    "FR-SRC-03": ("✅", "逐字同参照",
                  "`CameraScreenTest`；真机两个入口各走一次（FAB 与位置照片）"),
    "FR-SRC-04": ("✅", "同参照：换图只换图，字段一个字不动",
                  "`ReviewViewModelTest`；真机「换一张」→ 相册 → 回录入页"),
    "FR-SRC-05": ("✅", "同参照：`uses-feature required=false`，取景屏说「这台机器没有可用的相机」，其余功能照用",
                  "`CameraGateTest`（用 `shadowOf(packageManager).setSystemFeature` 模拟两种机器）"),
    "FR-SRC-06": ("✅", "同参照：一张照片多件靠芯片排切换",
                  "`ReviewViewModelTest`（多件槽位与「不要这件」）；真机多件路径随 P3 一并验"),
    "FR-SRC-07": ("✅", "同参照",
                  "`ReviewViewModelTest`"),
    "FR-SRC-08": ("⬜", "同参照未做：多角度要靠多拍几张，本实现没做关联",
                  "—"),
    "FR-SRC-09": ("⬜", "同参照未做：目前录入一定从一张照片开始",
                  "—"),
    "FR-AI-01": ("✅", "本轮接上真模型；字段回填只写 AI 给得出的，位置只在空着时填",
                 "真机 2026-10-09 拍真照片 → 名称「按摩球」/颜色「紫色」/材质「橡胶」（`device-ai-analysis.png`）；`AiLogicTest` 18 例"),
    "FR-AI-02": ("✅", "分析中不挡表单；回填**不标记** `touched`，所以「你还有改动没保存」不会被 AI 触发",
                 "`ReviewViewModelTest`；`ReviewUiState.canSave` 与状态行的 `Modifier.weight(1f)`"),
    "FR-AI-03": ("✅", "同参照，且**绝不虚构**：对不上就给 null",
                 "`AiLogicTest.都对不上就给 null，绝不虚构` / `只给末级词时取更浅的那个`"),
    "FR-AI-04": ("✅", "同参照：带清单那一版**整句替换** `container_guess` 问法；空清单时整段不出现、问法退回通用",
                 "`AiLogicTest.提示词带位置清单时用替换问法`"),
    "FR-AI-05": ("✅", "同参照：按钮只在照片**已落库**时给（没落库时按下去不会有任何反应）；照片文件不在时说明下一步",
                 "`ReviewUiState.canRetry`；真机「重新分析」；`ReviewViewModel.retryAnalysis`"),
    "FR-AI-06": ("✅", "同参照：11 类失败每条都带着下一步，没有一条只说「失败了」",
                 "`AiLogicTest.失败文案十一条逐字且都带下一步` / `超时文案里不写秒数`"),
    "FR-AI-07": ("✅", "同参照：`NO_CONFIG` 一句话，字段留空，直接手填",
                 "`AiLogicTest`（NO_CONFIG 文案）；`ReviewViewModelTest` 用「配置返回 null」的假分析器走全程"),
    "FR-AI-08": ("✅", "同参照：设置里两栏（接口地址 / 模型名），换厂商不动代码",
                 "真机当前配置 `https://api.deepseek.com` + `deepseek-flash`；`SettingsRepositoryTest`"),
    "FR-AI-09": ("⬜", "同参照未做：数量与到期日仍要手填",
                 "—"),
    "FR-AI-10": ("⬜", "同参照未做：只能一件一件重跑",
                 "—"),
    "FR-AI-11": ("🟡", "与参照同形：`category`/`seasons` 不做白名单校验，词表外的值照单入库、详情页看得见、筛选与搜索都够不着",
                 "`AiLogicTest.标量与数组的清洗不对称是现状`（相邻契约）；差值清单见 `DELTA.md` §4"),
    "FR-REV-01": ("✅", "同参照：进这一屏第一眼就有图（照片落在录入那一刻，不等保存）",
                  "`ReviewViewModelTest`；真机 `device-picked-photo-review.png`"),
    "FR-REV-02": ("✅", "次序同参照：名称* → 分类 → 季节 → 颜色 → 材质 → 品牌 → 别名 → 标签（`UI.md` §6.5）",
                  "`ReviewUiStateTest`；真机确认页逐项核对"),
    "FR-REV-03": ("✅", "同参照：名字必填（唯一带星号），保存转 `CONFIRMED`",
                  "`ItemRepositoryTest` / `ReviewViewModelTest`"),
    "FR-REV-04": ("✅", "同参照：退回 `DRAFT`，位置**不动**（待归类的谓词本来就是 `OR`）",
                  "`ItemRepositoryTest`；真机「稍后处理」→ 待归类里还在（F16）"),
    "FR-REV-05": ("✅", "同参照的四本账互不合并：`notStored` / `leftBehind` / `alreadySaved` / `unsaved`",
                  "`ReviewUiStateTest`（四本账与退出框两支）"),
    "FR-REV-06": ("✅", "同参照：空壳（没名、没动过、没保存）退出时回收",
                  "`ReviewViewModelTest`；真机退出后待归类里不留空壳"),
    "FR-REV-07": ("✅", "同参照，且比参照多一条证据：**磁盘库关库再打开**之后数据还在",
                  "`DurabilityTest`（真·磁盘库 + 关库重开）；教训见 F18：别把「看起来没落库」当成事务坏了"),
    "FR-REV-08": ("✅", "同参照：多件时芯片上有圆点、跨件退出要确认",
                  "`ReviewUiStateTest` / `ReviewViewModelTest`"),
}


def cells(line):
    return [c.strip() for c in line.strip().strip("|").split("|")]


def main():
    with open(PATH, encoding="utf-8") as f:
        lines = f.readlines()

    filled = 0
    missed = []
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
        parts = cells(line)
        impl, note, evidence = ROWS[fid]
        new = "| `%s` | %s | %s | %s | %s | %s |\n" % (
            fid, parts[1], parts[2], impl, note, evidence)
        out.append(new)
        filled += 1

    for fid in ROWS:
        if fid not in seen:
            missed.append(fid)

    with open(PATH, "w", encoding="utf-8", newline="") as f:
        f.writelines(out)

    print("已填行数：%d" % filled)
    print("映射里没在文档中找到的：(%d) %s" % (len(missed), ", ".join(missed)))
    if missed:
        sys.exit(2)


main()
