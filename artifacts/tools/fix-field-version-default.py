# 修 F45：searchTextVersion 的缺省值不能是"当前 FIELD_VERSION"——那样键不存在时闸门永远相等，
# 字段版本升了也不会补算（真机上发现：加了类别/季节后老数据的 searchText 仍是旧的）。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app\data\settings\SettingsRepository.kt"
t = io.open(PATH, encoding="utf-8").read()

old = "            searchTextVersion = prefs[KEY_SEARCH_TEXT_VERSION] ?: SearchTextBuilder.FIELD_VERSION,"
new = """            // 缺省 **0**（= 从没补算过），**不能**取当前的 FIELD_VERSION：
            // 取当前值的话，键不存在时闸门永远相等（`x != x` 为假），
            // 于是"加了搜索字段"这件事对老库**完全不生效**——2026-10-10 真机上就是这么漏掉类别与季节的。
            // 缺省 0 的代价只是首次启动多跑一次 rebuildSearchText（它只写真的变了的行）。
            searchTextVersion = prefs[KEY_SEARCH_TEXT_VERSION] ?: 0,"""
assert old in t, "没找到那行"
t = t.replace(old, new, 1)
io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("已修 searchTextVersion 缺省值")
