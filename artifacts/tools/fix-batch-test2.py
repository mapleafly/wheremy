# 修两处测试造数据：
# 1) filePath 是相对 rootDir 的，存在性要用 store.file(...)；
# 2) 共用照片用仓库自带的 newDraftSharingPhoto（不是手工再插一行）。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\test\java\com\wheremy\app\ui\gallery\BatchOperationsTest.kt"
t = io.open(PATH, encoding="utf-8").read()

# —— 1) 独占照片那条：路径解析 + 断言 ——
old1 = '''        val photoA = db.photoDao().forOwner(PhotoOwner.ITEM, a).first()
        val pathA = photoA.filePath
        assertTrue("先确认文件真在", File(pathA).exists())

        assertEquals(2, items.deleteAll(listOf(a, b)))

        assertNull("记录没了", items.get(a))
        assertNull(items.get(b))
        assertFalse("没人再用的照片文件也删了", File(pathA).exists())'''
new1 = '''        val photoA = db.photoDao().forOwner(PhotoOwner.ITEM, a).first()
        // ⚠️ 库里存的是**相对 rootDir** 的路径，别拿它直接 new File
        val fileA = store.file(photoA.filePath)
        assertTrue("先确认文件真在", fileA.exists())

        assertEquals(2, items.deleteAll(listOf(a, b)))

        assertNull("记录没了", items.get(a))
        assertNull(items.get(b))
        assertFalse("没人再用的照片文件也删了", fileA.exists())'''
assert old1 in t, "没找到独占照片那段"
t = t.replace(old1, new1, 1)

# —— 2) 混合那条：改用 newDraftSharingPhoto ——
start = t.index("    fun `批量删除：有共用照片时，两类分开数`() {")
end = t.index("    @Test", start)
mixed = '''    fun `批量删除：有共用照片时，两类分开数`() {
        val a = add("羽绒服")
        // 共用同一张照片（导入/复制时会出现）：仓库自带的 newDraftSharingPhoto 就是干这个的
        val b = runBlocking { items.newDraftSharingPhoto(a) }
        val sharedFile = store.file(runBlocking { db.photoDao().forOwner(PhotoOwner.ITEM, a).first().filePath })
        assertTrue("两张共用同一个文件", sharedFile.exists())

        val vm = gallery()
        vm.startSelection(a)
        vm.toggleSelection(b)
        vm.askDeleteSelection()

        val warning = awaitState(vm, "确认框立起来") { it.pendingBatchDelete != null }.pendingBatchDelete!!.warning
        assertTrue("要说清照片会留着：$warning", warning.contains("照片留着"))
        assertTrue("也要说清有几张：$warning", warning.contains("2 张"))

        // 真删：共用照片不能删（还有别的记录在用）
        vm.confirmDeleteSelection()
        awaitState(vm, "删完") { it.pendingBatchDelete == null && it.selectedIds.isEmpty() }
        assertTrue("共用照片文件要留着", sharedFile.exists())
        assertNull("记录删掉了", runBlocking { items.get(a) })
    }

'''
t = t[:start] + mixed + t[end:]

# 不再需要手工插行 → 去掉 ItemPhotoEntity 的 import（保留 PhotoOwner）
if "ItemPhotoEntity" not in t.split("import com.wheremy.app.data.db.ItemPhotoEntity")[-1]:
    t = t.replace("import com.wheremy.app.data.db.ItemPhotoEntity\n", "", 1)

io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("已修两处测试造数据")
