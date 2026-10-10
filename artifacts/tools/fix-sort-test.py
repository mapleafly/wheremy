# 修 SortOrderTest 里那条"相关度"用例：`searchLike` 是单向包含，得让两条都命中同一个查询词。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\test\java\com\wheremy\app\data\repo\SortOrderTest.kt"
t = io.open(PATH, encoding="utf-8").read()

old = '''        add("电钻")
        add("钻头")
        // 名字完全命中的那条分更高（SearchRanker 的 100/80/60…），排序开关不该影响它
        val ranked = names("电钻", Filters(sort = SortOrder.DEFAULT))
        assertEquals(listOf("电钻", "钻头"), ranked)'''
new = '''        // 两条都要命中同一个查询词（`searchLike` 是单向包含：`%电钻%` 不会命中"钻头"），
        // 名字**完全**等于查询词的那条分更高（SearchRanker 的 100/80/60…）
        add("电钻配件")
        add("电钻")
        val ranked = names("电钻", Filters(sort = SortOrder.DEFAULT))
        assertEquals("相关度高的在前，排序开关不该影响它", listOf("电钻", "电钻配件"), ranked)'''

if old in t:
    t = t.replace(old, new, 1)
    io.open(PATH, "w", encoding="utf-8", newline="").write(t)
    print("已改")
else:
    print("没匹配到")
