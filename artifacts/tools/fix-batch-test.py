# 修 BatchOperationsTest 的两处：ownerType 用 "item"；批量删除文案的期望改成"独占照片"那句，
# 并补一条"共用照片"的混合用例（那才是两类分开数的价值所在）。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\test\java\com\wheremy\app\ui\gallery\BatchOperationsTest.kt"
t = io.open(PATH, encoding="utf-8").read()

# 1) ownerType
t = t.replace(
    'val photoA = items.get(a)!!.let { db.photoDao().forOwner("ITEM", a).first() }',
    'val photoA = db.photoDao().forOwner(PhotoOwner.ITEM, a).first()',
    1,
)
t = t.replace(
    "import com.wheremy.app.data.db.WheremyDatabase",
    "import com.wheremy.app.data.db.ItemPhotoEntity\nimport com.wheremy.app.data.db.PhotoOwner\nimport com.wheremy.app.data.db.WheremyDatabase",
    1,
)

# 2) 独占照片时那句文案 + 新增混合用例
old = '''        vm.askDeleteSelection()
        val pending = awaitState(vm, "确认框立起来") { it.pendingBatchDelete != null }.pendingBatchDelete!!
        assertEquals(2, pending.itemIds.size)
        assertTrue("两张都有自己的照片 → 说清会一起没：${pending.warning}", pending.warning.contains("2 张照片会跟着没"))

        vm.cancelDeleteSelection()
        assertNull(vm.state.value.pendingBatchDelete)'''
new = '''        vm.askDeleteSelection()
        val pending = awaitState(vm, "确认框立起来") { it.pendingBatchDelete != null }.pendingBatchDelete!!
        assertEquals(2, pending.itemIds.size)
        assertTrue(
            "两张的照片都只有自己用 → 明说会一起没：${pending.warning}",
            pending.warning.contains("照片和记录会一起删除"),
        )

        vm.cancelDeleteSelection()
        assertNull(vm.state.value.pendingBatchDelete)
    }

    @Test
    fun `批量删除：有共用照片时，两类分开数`() {
        val a = add("羽绒服")
        val b = add("围巾")
        // 造一张"两件共用"的照片：同一个文件路径挂两条 item_photo（导入/手工整理后会出现这种）
        val shared = File(store.rootDir, "shared.jpg").apply { writeBytes(testJpeg()) }
        val thumb = File(store.rootDir, "shared-thumb.jpg").apply { writeBytes(testJpeg()) }
        listOf(a, b).forEachIndexed { index, itemId ->
            runBlocking {
                db.photoDao().upsert(
                    ItemPhotoEntity(
                        id = "shared-$index",
                        ownerType = PhotoOwner.ITEM,
                        ownerId = itemId,
                        filePath = shared.absolutePath,
                        thumbPath = thumb.absolutePath,
                        sortOrder = 0,
                        createdAt = 1L,
                    ),
                )
            }
        }

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
        assertTrue("共用照片文件要留着", shared.exists())
        assertNull("记录删掉了", runBlocking { items.get(a) })'''
assert old in t, "没找到要改的断言块"
t = t.replace(old, new, 1)

io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("已修测试并补混合用例")
