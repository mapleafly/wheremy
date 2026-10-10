# 共用照片的场景要留一个"还在用"的记录：三件共用一条文件，只删其中两件。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\test\java\com\wheremy\app\ui\gallery\BatchOperationsTest.kt"
t = io.open(PATH, encoding="utf-8").read()

old = '''        val a = add("羽绒服")
        // 共用同一张照片（导入/复制时会出现）：仓库自带的 newDraftSharingPhoto 就是干这个的
        val b = runBlocking { items.newDraftSharingPhoto(a) }
        val sharedFile = store.file(runBlocking { db.photoDao().forOwner(PhotoOwner.ITEM, a).first().filePath })
        assertTrue("两张共用同一个文件", sharedFile.exists())'''
new = '''        val a = add("羽绒服")
        // 三件共用同一张照片（导入/复制时会出现）：仓库自带的 newDraftSharingPhoto 就是干这个的。
        // ⚠️ 必须留一件"还在用"的——把共用者全删光，文件本来就该删，那样测不出"照片留着"。
        val b = runBlocking { items.newDraftSharingPhoto(a) }
        val c = runBlocking { items.newDraftSharingPhoto(a) }
        val sharedFile = store.file(runBlocking { db.photoDao().forOwner(PhotoOwner.ITEM, a).first().filePath })
        assertTrue("三件共用同一个文件", sharedFile.exists())'''
assert old in t, "没找到共用照片那段"
t = t.replace(old, new, 1)

old2 = '''        awaitState(vm, "删完") { it.pendingBatchDelete == null && it.selectedIds.isEmpty() }
        assertTrue("共用照片文件要留着", sharedFile.exists())
        assertNull("记录删掉了", runBlocking { items.get(a) })'''
new2 = '''        awaitState(vm, "删完") { it.pendingBatchDelete == null && it.selectedIds.isEmpty() }
        assertTrue("还有 c 在用 → 共用照片文件必须留着", sharedFile.exists())
        assertNull("记录删掉了", runBlocking { items.get(a) })
        assertNull(runBlocking { items.get(b) })
        assertTrue("c 还在，而且它的照片行没被删", runBlocking { db.photoDao().forOwner(PhotoOwner.ITEM, c) }.isNotEmpty())'''
assert old2 in t, "没找到删完后的断言"
t = t.replace(old2, new2, 1)

io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("已改成三件共用的场景")
