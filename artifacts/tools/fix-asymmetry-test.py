# 修 BackupAsymmetryTest：DAO 方法名、缺 import、Kotlin 字符串里的 ASCII 引号。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\test\java\com\wheremy\app\data\backup\BackupAsymmetryTest.kt"
t = io.open(PATH, encoding="utf-8").read()

# 1) import
if "import androidx.room3.withWriteTransaction" not in t:
    t = t.replace(
        "import androidx.room3.Room",
        "import androidx.room3.Room\nimport androidx.room3.withWriteTransaction",
        1,
    )

# 2) wipeLocal：用 DAO 真有的方法
old = """    private fun wipeLocal() = runBlocking {
        db.withWriteTransaction {
            db.photoDao().all().forEach { db.photoDao().deleteById(it.id) }
            db.itemDao().all().forEach { db.itemDao().deleteById(it.id) }
        }
        store.rootDir.listFiles()?.forEach { it.deleteRecursively() }
        Unit
    }"""
new = """    private fun wipeLocal() = runBlocking {
        db.withWriteTransaction {
            db.photoDao().deleteAll()
            db.itemDao().all().forEach { db.itemDao().deleteById(it.id) }
        }
        store.rootDir.listFiles()?.forEach { it.deleteRecursively() }
        Unit
    }"""
assert old in t, "wipeLocal 没匹配"
t = t.replace(old, new, 1)

# 3) Kotlin 字符串里的 ASCII 引号 → 「」
t = t.replace(
    'assertNotNull("照片行也在（这就是"缩略图空白"的来源）", row)',
    'assertNotNull("照片行也在（这就是「缩略图空白」的来源）", row)',
)

io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("已修")
