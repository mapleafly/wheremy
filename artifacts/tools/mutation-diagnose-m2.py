# 单独诊断 M2：把开关行还原成旧写法，看设置页那 4 条测试里到底红哪条
import io
import re
import subprocess
import sys

sys.stdout.reconfigure(errors="replace")
ROOT = r"D:\src\dsh\wheremy"
SETTINGS = ROOT + r"\app\src\main\java\com\wheremy\app\ui\settings\SettingsScreen.kt"

OLD_ROW = '''                    Row(
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

NEW_ROW = '''                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Switch(
                            checked = state.autoExportEnabled,
                            onCheckedChange = vm::setAutoExportEnabled,
                        )
                        Text("每天自动导出一份", modifier = Modifier.weight(1f))
                    }'''

original = io.open(SETTINGS, encoding="utf-8").read()
assert OLD_ROW in original, "找不到锚点"
io.open(SETTINGS, "w", encoding="utf-8", newline="").write(original.replace(OLD_ROW, NEW_ROW, 1))
try:
    p = subprocess.run(
        [ROOT + r"\gradlew.bat", ":app:testDebugUnitTest", "--tests", "*SettingsAccessibilityTest*", "--console=plain"],
        cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=900,
    )
    out = (p.stdout or "") + (p.stderr or "")
    print("== 变异后：设置页无障碍测试的完整结果 ==")
    for line in out.splitlines():
        s = line.strip()
        if "SettingsAccessibilityTest" in s or "tests completed" in s:
            print("  " + s[:150])
    # 找出失败用例的原因
    for m in re.finditer(r"(\S+SettingsAccessibilityTest\S*) > (.+?) FAILED", out):
        print("  失败：", m.group(0)[:150])
finally:
    io.open(SETTINGS, "w", encoding="utf-8", newline="").write(original)
    print("  已还原")
