# G2 变异验牙：把本批新增的断言逐个"改坏生产代码"，确认对应的那条真的会红。
# 规矩见 REBUILD.md §1.3：全绿只证明"测试跑过了"，不证明"测试有牙"。
#
# 两个坑（都踩过，别再踩）：
# 1. **读写都要 newline=""**：不然 Python 会把 CRLF 读成 \n 再写回 LF，
#    整文件变成"已修改"（diff 全是换行），把变异验牙变成一次假的代码改动。
#    验完务必 `git status app/src/main` 确认零差异——这才是"还原干净"的硬证据。
# 2. **expected 要填测试的准确名字**，别凭记忆写：第一次跑 M2 时我把用例名记成
#    「每天自动导出的开关与标签是同一个无障碍节点」，而真名是「自动导出的开关与标签…」，
#    于是脚本判成"没红"——其实红的就是那条。填错名字会把"有牙"误判成"没牙"。
import io
import re
import subprocess
import sys

sys.stdout.reconfigure(errors="replace")
ROOT = r"D:\src\dsh\wheremy"

SETTINGS = r"app\src\main\java\com\wheremy\app\ui\settings\SettingsScreen.kt"


def read_text(path):
    # newline="" ⇒ 原样保留 CRLF，见上面坑 1
    return io.open(path, encoding="utf-8", newline="").read()


def write_text(path, text):
    io.open(path, "w", encoding="utf-8", newline="").write(text)

OLD_SWITCH_ROW = '''                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            // 同上：整行一个焦点，念「每天自动导出一份 + 开关 + 开/关」。
                            // 顺手把版式统一成「标签在左、开关在右」——原来这颗开关在文字左边，两处不一致。
                            .toggleable(
                                value = state.autoExportEnabled,
                                role = Role.Switch,
                                onValueChange = vm::setAutoExportEnabled,
                            ),
                    ) {
                        Text("每天自动导出一份", modifier = Modifier.weight(1f))
                        Switch(checked = state.autoExportEnabled, onCheckedChange = null)
                    }'''

NEW_SWITCH_ROW = '''                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Switch(
                            checked = state.autoExportEnabled,
                            onCheckedChange = vm::setAutoExportEnabled,
                        )
                        Text("每天自动导出一份", modifier = Modifier.weight(1f))
                    }'''

CASES = [
    dict(
        label="M1 名称框的必填文案",
        path=r"app\src\main\java\com\wheremy\app\ui\review\ReviewScreen.kt",
        old='.semantics { contentDescription = "名称，必填" }',
        new='.semantics { contentDescription = "名称" }',
        pattern="*ReviewAccessibilityTest*",
        expect="名称框的读屏文案带必填",
    ),
    dict(
        label="M2 开关整行一个焦点",
        path=SETTINGS,
        old=OLD_SWITCH_ROW,
        new=NEW_SWITCH_ROW,
        pattern="*SettingsAccessibilityTest*",
        expect="自动导出的开关与标签是同一个无障碍节点",
    ),
    dict(
        label="M3 提前天数预设的选中态",
        path=SETTINGS,
        old="selected = state.leadDays == days,",
        new="selected = false,",
        pattern="*SettingsAccessibilityTest*",
        expect="提前天数的预设芯片带选中状态",
    ),
    dict(
        label="M4 只看待归类的选中态",
        path=r"app\src\main\java\com\wheremy\app\ui\gallery\GalleryScreen.kt",
        old="selected = state.filters.onlyInbox,",
        new="selected = false,",
        pattern="*GalleryAccessibilityTest*",
        expect="芯片的标签与状态在同一个无障碍节点上",
    ),
]

# 可选：命令行给几个关键词就只跑那几条（例：python mutation-check-batch.py M2）
if len(sys.argv) > 1:
    CASES = [c for c in CASES if any(a in c["label"] for a in sys.argv[1:])]

print("== G2 变异验牙（%d 处）==" % len(CASES))
results = []
for c in CASES:
    full = ROOT + "\\" + c["path"]
    original = read_text(full)
    # 匹配前把换行统一成 \n（脚本里的多行块写的是 \n，而文件是 CRLF）——见坑 1
    norm = original.replace("\r\n", "\n")
    if c["old"] not in norm:
        print("  !! %s：找不到要改的那段，跳过" % c["label"])
        results.append((c["label"], "找不到锚点", "-"))
        continue
    write_text(full, norm.replace(c["old"], c["new"], 1).replace("\n", "\r\n"))
    try:
        p = subprocess.run(
            [ROOT + r"\gradlew.bat", ":app:testDebugUnitTest", "--tests", c["pattern"], "--console=plain"],
            cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=900,
        )
        out = (p.stdout or "") + (p.stderr or "")
        failed_lines = [l.strip() for l in out.splitlines() if "FAILED" in l]
        hit = any(c["expect"] in l for l in failed_lines)
        n_tests = re.search(r"(\d+) tests completed(?:, (\d+) failed)?", out)
        detail = ""
        if n_tests:
            detail = "%s 例，失败 %s" % (n_tests.group(1), n_tests.group(2) or "0")
        ok = "红了 ✓" if hit else "没红 ✗"
        print("  %-22s %s  （%s）" % (c["label"], ok, detail))
        if hit:
            print("      红的那条：%s" % [l for l in failed_lines if c["expect"] in l][0][:110])
        results.append((c["label"], ok, detail))
    finally:
        write_text(full, original)
        print("      已还原 %s" % c["path"])

print()
print("== 汇总 ==")
for label, ok, detail in results:
    print("  %-22s %-8s %s" % (label, ok, detail))
