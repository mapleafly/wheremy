# 老测试钉的是"字段版本"这个闸门本身：加了类别与季节就该是 3。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\test\java\com\wheremy\app\data\db\SearchTextBuilderTest.kt"
t = io.open(PATH, encoding="utf-8").read()

old = """    @Test
    fun `字段版本是 2`() {
        // 1 → 2 是加了颜色与材质；改了拼接字段就要加一，否则已录物品不会补算
        assertEquals(2, SearchTextBuilder.FIELD_VERSION)
    }"""
new = """    @Test
    fun `字段版本是 3`() {
        // 这条守的是"闸门"本身，不是某个具体数字：
        // 1 → 2 是加了颜色与材质；2 → 3 是加了类别与季节（FR-AI-11：模型可能回词表外的值，也要能搜到）。
        // 改了拼接字段就**必须**加一，否则启动时不会补算已有物品，老数据搜不到新字段。
        assertEquals(3, SearchTextBuilder.FIELD_VERSION)
    }"""
assert old in t, "没找到版本测试"
t = t.replace(old, new, 1)
io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("已更新版本测试到 3")
