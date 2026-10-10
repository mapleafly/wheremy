# 把"搜索字段版本默认值"这条测试改成钉住正确行为（缺省 0，而不是当前 FIELD_VERSION）。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\test\java\com\wheremy\app\data\settings\SettingsRepositoryTest.kt"
t = io.open(PATH, encoding="utf-8").read()

old = '        assertEquals("搜索字段版本默认跟上代码", SearchTextBuilder.FIELD_VERSION, settings.searchTextVersion)'
new = '''        assertEquals(
            "缺省是 0（从没补算过）——取当前的 FIELD_VERSION 会让字段版本升了也不补算（F45）",
            0,
            settings.searchTextVersion,
        )'''
assert old in t, "没找到那条断言"
t = t.replace(old, new, 1)

# 追加一条：升了版本必须真的触发补算（用"缺省 0 + 当前版本"这个不等式表达）
add = '''
    @Test
    fun `字段版本升了必须能触发补算：缺省值不能等于当前版本`() {
        val settings = runBlocking { repo().current() }

        assertTrue(
            "缺省值必须与当前 FIELD_VERSION 不等，否则闸门永远为假、老库永远不补算（F45）",
            settings.searchTextVersion != SearchTextBuilder.FIELD_VERSION,
        )

        // 补算过之后记住版本，下次启动就不再重算
        runBlocking { repo().setSearchTextVersion(SearchTextBuilder.FIELD_VERSION) }
        assertEquals(
            "记过之后就相等了（不再重复补算）",
            SearchTextBuilder.FIELD_VERSION,
            runBlocking { repo().current() }.searchTextVersion,
        )
    }
'''
idx = t.rstrip().rfind("}")
t = t[:idx] + add + t[idx:]
io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("测试已更新")
