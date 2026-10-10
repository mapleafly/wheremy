# 修上一版被 PowerShell 反引号吃掉而写坏的测试（改用脚本文件，避免 PS 转义）。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\test\java\com\wheremy\app\ui\gallery\GalleryViewModelTest.kt"
t = io.open(PATH, encoding="utf-8").read()

# 先把上一版插进去的坏块整段删掉（从它的 @Test 到那个不完整的函数体结束）
bad = re.search(r"\n    @Test\n    fun 组合筛选：连点两个类别.*?\n    \}\n", t, re.S)
if bad:
    t = t[: bad.start()] + "\n" + t[bad.end() :]
    print("已删掉坏块")
else:
    print("没找到坏块（可能名字不同）")

good = '''
    @Test
    fun `组合筛选：连点两个类别，两个都留着；再点一次只去掉那一个`() {
        val vm = gallery()

        vm.toggleCategory("衣物")
        vm.toggleCategory("数码")
        assertEquals("两个都选中", setOf("衣物", "数码"), vm.state.value.filters.categories)

        vm.toggleCategory("衣物")
        assertEquals("再点衣物只去掉衣物", setOf("数码"), vm.state.value.filters.categories)

        vm.toggleSeason("冬")
        vm.toggleSeason("秋")
        vm.toggleTag("冬装")
        vm.toggleTag("自检种子")
        val f = vm.state.value.filters
        assertEquals(setOf("冬", "秋"), f.seasons)
        assertEquals(setOf("冬装", "自检种子"), f.tags)
    }
'''
idx = t.rstrip().rfind("}")
t = t[:idx] + good + t[idx:]
io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("已写入正确的测试")
