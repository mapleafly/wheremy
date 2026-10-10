package com.wheremy.app.ui.gallery

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.PhotoOwner
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.repo.ItemDraft
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.data.settings.testSettings
import com.wheremy.app.data.testJpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * `FR-OPR-06` 批量操作：多选后搬移 / 删除 / 改类别。
 *
 * 批量最怕的两件事，这里各钉一条：
 * 1. **口径与单件不一致**（批量搬移没更新 `lastMovedAt`、批量删除漏删照片文件）——
 *    所以实现是逐件复用单件那条路，测试直接核对库与文件；
 * 2. **删除确认的文案含糊**：单件那句说的是"这一件"，批量必须把"会跟着没的照片"与
 *    "还有别的记录在用、会留着的照片"分开数。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BatchOperationsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var store: PhotoStore
    private lateinit var items: ItemRepository
    private lateinit var locations: LocationRepository
    private var clock = 1_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = PhotoStore(File(tmp.root, "photos"))
        items = ItemRepository(db, store, now = { clock++ })
        locations = LocationRepository(db, store)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun gallery() = GalleryViewModel(items, locations, testSettings())

    private fun add(name: String, category: String? = null, locationId: String? = null): String = runBlocking {
        val id = items.newDraft(testJpeg())
        items.confirm(ItemDraft(id = id, name = name, category = category, locationId = locationId))
        id
    }

    private fun awaitState(vm: GalleryViewModel, what: String, predicate: (GalleryUiState) -> Boolean): GalleryUiState {
        repeat(200) {
            val s = vm.state.value
            if (predicate(s)) return s
            Thread.sleep(10)
        }
        throw AssertionError("等不到：$what，当前 = ${vm.state.value}")
    }

    // —— 仓库层 ——

    @Test
    fun `批量搬到同一处：只数真的动了的`() = runBlocking {
        val home = locations.add(parentId = null, name = "家", kind = "房间")
        val a = add("羽绒服")
        val b = add("围巾")
        val c = add("电钻", locationId = home)

        val moved = items.moveAll(listOf(a, b, c), home)

        assertEquals("c 本来就在家里，不该算进去", 2, moved)
        assertEquals(home, items.get(a)?.locationId)
        assertEquals(home, items.get(b)?.locationId)
        assertTrue("搬动时间要写上（与单件同口径）", (items.get(a)?.lastMovedAt ?: 0L) > 0L)
    }

    @Test
    fun `批量改类别：没变的不算，清空也支持`() = runBlocking {
        val a = add("羽绒服", category = "衣物")
        val b = add("围巾")
        val c = add("电钻", category = "工具")

        assertEquals("b 从 null 变衣物、c 从工具变衣物 = 2 件真变了", 2, items.setCategoryAll(listOf(a, b, c), "衣物"))
        assertEquals("衣物", items.get(a)?.category)
        assertEquals("衣物", items.get(b)?.category)
        assertEquals("衣物", items.get(c)?.category)

        assertEquals(3, items.setCategoryAll(listOf(a, b, c), null))
        assertNull(items.get(a)?.category)
    }

    @Test
    fun `批量删除：照片文件跟着没，共用的照片留着`() = runBlocking {
        val a = add("羽绒服")
        val b = add("围巾")
        val photoA = db.photoDao().forOwner(PhotoOwner.ITEM, a).first()
        // ⚠️ 库里存的是**相对 rootDir** 的路径，别拿它直接 new File
        val fileA = store.file(photoA.filePath)
        assertTrue("先确认文件真在", fileA.exists())

        assertEquals(2, items.deleteAll(listOf(a, b)))

        assertNull("记录没了", items.get(a))
        assertNull(items.get(b))
        assertFalse("没人再用的照片文件也删了", fileA.exists())
    }

    // —— VM 层 ——

    @Test
    fun `长按进入多选并选中那一张，再点卡片是切换`() {
        val a = add("羽绒服")
        val b = add("围巾")
        val vm = gallery()

        vm.startSelection(a)
        assertEquals(setOf(a), vm.state.value.selectedIds)

        vm.toggleSelection(b)
        assertEquals(setOf(a, b), vm.state.value.selectedIds)

        vm.toggleSelection(a)
        assertEquals(setOf(b), vm.state.value.selectedIds)

        vm.clearSelection()
        assertTrue("清空 = 退出多选态", vm.state.value.selectedIds.isEmpty())
    }

    @Test
    fun `批量删除的确认文案把两类照片分开说`() {
        val a = add("羽绒服")
        val b = add("围巾")
        val vm = gallery()
        vm.startSelection(a)
        vm.toggleSelection(b)

        vm.askDeleteSelection()
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
        // 三件共用同一张照片（导入/复制时会出现）：仓库自带的 newDraftSharingPhoto 就是干这个的。
        // ⚠️ 必须留一件"还在用"的——把共用者全删光，文件本来就该删，那样测不出"照片留着"。
        val b = runBlocking { items.newDraftSharingPhoto(a) }
        val c = runBlocking { items.newDraftSharingPhoto(a) }
        val sharedFile = store.file(runBlocking { db.photoDao().forOwner(PhotoOwner.ITEM, a).first().filePath })
        assertTrue("三件共用同一个文件", sharedFile.exists())

        val vm = gallery()
        vm.startSelection(a)
        vm.toggleSelection(b)
        vm.askDeleteSelection()

        val warning = awaitState(vm, "确认框立起来") { it.pendingBatchDelete != null }.pendingBatchDelete!!.warning
        assertTrue("要说清照片会留着：$warning", warning.contains("照片留着"))
        assertTrue("要说清有几件是这种：$warning", warning.contains("2 件"))
        assertTrue("不能误说照片会被删：$warning", !warning.contains("会跟着没"))

        // 真删：共用照片不能删（还有别的记录在用）
        vm.confirmDeleteSelection()
        awaitState(vm, "删完") { it.pendingBatchDelete == null && it.selectedIds.isEmpty() }
        assertTrue("还有 c 在用 → 共用照片文件必须留着", sharedFile.exists())
        assertNull("记录删掉了", runBlocking { items.get(a) })
        assertNull(runBlocking { items.get(b) })
        assertTrue("c 还在，而且它的照片行没被删", runBlocking { db.photoDao().forOwner(PhotoOwner.ITEM, c) }.isNotEmpty())
    }

    @Test
    fun `批量搬完之后给出件数并退出多选态`() {
        val home = runBlocking { locations.add(parentId = null, name = "家", kind = "房间") }
        val a = add("羽绒服")
        val b = add("围巾")
        val vm = gallery()
        vm.startSelection(a)
        vm.toggleSelection(b)

        vm.askMoveSelection()
        awaitState(vm, "位置清单出来") { it.moveSelectionOpen && it.locationOptions.isNotEmpty() }
        vm.confirmMoveSelection(home)

        val after = awaitState(vm, "搬完") { it.selectedIds.isEmpty() && it.notice != null }
        assertTrue("要说搬了几件、搬到哪：${after.notice}", after.notice!!.contains("2 件") && after.notice!!.contains("家"))
        assertEquals(home, runBlocking { items.get(a) }?.locationId)
    }

    @Test
    fun `批量改类别：结果句说清新类别，库也真的变了`() {
        val a = add("羽绒服")
        val b = add("围巾")
        val vm = gallery()
        vm.startSelection(a)
        vm.toggleSelection(b)

        vm.askCategorySelection()
        assertTrue(vm.state.value.categorySelectionOpen)
        vm.confirmCategorySelection("衣物")

        val after = awaitState(vm, "改完") { it.selectedIds.isEmpty() && it.notice != null }
        assertTrue("要说改成什么：${after.notice}", after.notice!!.contains("衣物") && after.notice!!.contains("2 件"))
        assertEquals("衣物", runBlocking { items.get(a) }?.category)
        assertEquals("衣物", runBlocking { items.get(b) }?.category)
    }
}
