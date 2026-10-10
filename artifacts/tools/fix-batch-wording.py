# 批量删除文案：kept 那一侧数的是**记录件数**，不是文件张数（一条文件可能被两件共用）——用「件」。
import io
import sys

sys.stdout.reconfigure(errors="replace")
FMT = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app\ui\UiFormat.kt"
TEST = r"D:\src\dsh\wheremy\app\src\test\java\com\wheremy\app\ui\gallery\BatchOperationsTest.kt"

f = io.open(FMT, encoding="utf-8").read()
old = '''        photosToDelete > 0 && photosKept > 0 ->
            "删除这 $count 件：$photosToDelete 张照片会跟着没；另有 $photosKept 张还有别的记录在用，照片留着。"'''
new = '''        photosToDelete > 0 && photosKept > 0 ->
            "删除这 $count 件：$photosToDelete 张照片会跟着没；另有 $photosKept 件的照片还有别的记录在用，照片留着。"'''
assert old in f, "没找到混合分支"
f = f.replace(old, new, 1)
# 文档注释里把"张/件"的口径写清楚
f = f.replace(
    "     * 单件那句（[deleteWarningOf]）说的是\"这一件\"；批量必须把**两类照片分开数**：\n"
    "     * 会跟着没的、还有别的记录在用所以会留着的——含糊说\"照片可能被删\"等于没说。",
    "     * 单件那句（[deleteWarningOf]）说的是\"这一件\"；批量必须把**两类照片分开数**：\n"
    "     * 会跟着没的、还有别的记录在用所以会留着的——含糊说\"照片可能被删\"等于没说。\n"
    "     *\n"
    "     * 数口径：`photosToDelete` 是**文件张数**（一条文件没别人用才算它会没），\n"
    "     * `photosKept` 是**记录件数**（同一条文件可能被两件共用），所以后者说\"件\"不说\"张\"。",
)
io.open(FMT, "w", encoding="utf-8", newline="").write(f)
print("UiFormat 文案已改")

t = io.open(TEST, encoding="utf-8").read()
old_assert = '''        assertTrue("要说清照片会留着：$warning", warning.contains("照片留着"))
        assertTrue("也要说清有几张：$warning", warning.contains("2 张"))'''
new_assert = '''        assertTrue("要说清照片会留着：$warning", warning.contains("照片留着"))
        assertTrue("要说清有几件是这种：$warning", warning.contains("2 件"))
        assertTrue("不能误说照片会被删：$warning", !warning.contains("会跟着没"))'''
assert old_assert in t, "没找到断言"
t = t.replace(old_assert, new_assert, 1)
io.open(TEST, "w", encoding="utf-8", newline="").write(t)
print("测试断言已改")
