# 给 F50 那一节补上"真 CI 又抓到一次"的记录（本地绿、真 CI 红）
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\docs\CHANGELOG.md"
t = io.open(PATH, encoding="utf-8").read()

anchor = "测试从 **42 类 / 261 例** 长到 **43 类 / 265 例**，本地 CI 四道门全绿。"
add = anchor + """

**⚠️ 这批在真 CI 上又红了一次（本地四道门全绿）**：报

```
ReviewAccessibilityTest > 名称框的读屏文案带必填 FAILED
    kotlinx.coroutines.test.UncaughtExceptionsBeforeTest
```

根因是重构这条测试时把原来的 `db.close()` 弄丢了：内存库不关，它后台协程的未捕获异常会留到
**下一条测试**才炸，报成 `UncaughtExceptionsBeforeTest`。本地单跑这一类（顺序不同）不暴露，
**真 CI 跑整套才炸**——又一次印证"本地绿 ≠ 真 CI 绿"。改法：db 提升为测试类字段，在 `@After` 里关闭。
（这也说明"真 CI 必须跑"不是形式主义。）"""
if anchor in t:
    t = t.replace(anchor, add, 1)
    io.open(PATH, "w", encoding="utf-8", newline="").write(t)
    print("CHANGELOG 已补记真 CI 那一次")
else:
    print("!! 没找到锚点")
