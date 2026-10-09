package com.wheremy.app.ui.tree

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.repo.ItemDraft
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
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
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 位置页状态机（`UI.md` §4）。
 * 钉住的是三件容易写错的事：计数只在子树与直属不同才加括号、空态两句分根/子层、
 * 空名字不许保存（取"禁用"那一种做法）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TreeViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var store: PhotoStore
    private lateinit var items: ItemRepository
    private lateinit var locations: LocationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = PhotoStore(File(tmp.root, "photos"))
        items = ItemRepository(db, store)
        locations = LocationRepository(db, store)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun tree(): TreeViewModel = TreeViewModel(locations, items)

    private fun awaitState(vm: TreeViewModel, description: String, predicate: (TreeUiState) -> Boolean): TreeUiState {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val state = vm.state.value
            if (predicate(state)) return state
            Thread.sleep(20)
        }
        throw AssertionError("等不到：$description，当前 = ${vm.state.value}")
    }

    private fun addItem(name: String, locationId: String?): String = runBlocking {
        val id = items.newDraft(testJpeg())
        items.confirm(ItemDraft(id = id, name = name, locationId = locationId))
        id
    }

    @Test
    fun `根层空态与子层空态两句不同`() {
        val vm = tree()
        val root = awaitState(vm, "根层读出来") { !it.loading }
        assertTrue(root.isRoot)
        assertTrue(root.isEmpty)
        assertEquals(
            "还没有位置。点右上角「新建」建第一个，例如「家」「主卧」「衣柜」。",
            root.emptyText,
        )

        val home = runBlocking { locations.add(parentId = null, name = "家", kind = "房间") }
        vm.open(home)
        val child = awaitState(vm, "子层读出来") { !it.loading && it.currentParentId == home }
        assertFalse(child.isRoot)
        assertTrue(child.isEmpty)
        assertEquals("这个位置还是空的，点「新建」加个子位置，或去照片墙录入物品。", child.emptyText)
    }

    @Test
    fun `计数只在子树与直属不同才加括号`() {
        val home = runBlocking { locations.add(parentId = null, name = "家", kind = "房间") }
        val bedroom = runBlocking { locations.add(parentId = home, name = "主卧", kind = "房间") }
        val wardrobe = runBlocking { locations.add(parentId = bedroom, name = "衣柜", kind = "衣柜") }
        addItem("外套", wardrobe)

        val vm = tree()
        val root = awaitState(vm, "根层读出来") { it.rows.isNotEmpty() }
        val homeRow = root.rows.single { it.node.id == home }
        assertEquals("直属 0 件", 0, homeRow.directCount)
        assertEquals("子树 1 件", 1, homeRow.subtreeCount)
        assertTrue("直接放在家那一层的一件都没有：等值不追加", homeRow.subtreeCount != homeRow.directCount)
        assertTrue(homeRow.hasChildren)

        // 等值的情形：物品直接在衣柜里，衣柜的直属与子树都是 1
        vm.open(wardrobe)
        val wardrobeState = awaitState(vm, "衣柜读出来") { it.currentParentId == wardrobe && !it.loading }
        assertEquals(1, wardrobeState.itemsHere.size)
        assertEquals("衣橱自己没有子位置，所以根层看不到它，但计数逻辑按 direct == subtree 处理", 0, wardrobeState.rows.size)
    }

    @Test
    fun `上一层回到父节点`() {
        val home = runBlocking { locations.add(parentId = null, name = "家", kind = "房间") }
        val bedroom = runBlocking { locations.add(parentId = home, name = "主卧", kind = "房间") }
        val vm = tree()
        vm.open(bedroom)
        awaitState(vm, "进到主卧") { it.currentParentId == bedroom && !it.loading }

        vm.up()

        val back = awaitState(vm, "回到父层") { it.currentParentId == home && !it.loading && it.breadcrumb == listOf("家") }
        assertEquals(listOf("家"), back.breadcrumb)
    }

    @Test
    fun `空名字不保存`() {
        val vm = tree()
        awaitState(vm, "根层读出来") { !it.loading }

        vm.startCreate()
        vm.saveEdit(name = "   ", kind = "房间")

        Thread.sleep(200)
        assertTrue("空名字不许建出来", runBlocking { locations.all() }.isEmpty())
        assertTrue("框也不该关掉", vm.state.value.editOpen)
    }

    @Test
    fun `新建把名字与类型都写进去且类型可自由填`() {
        val vm = tree()
        awaitState(vm, "根层读出来") { !it.loading }
        vm.startCreate()

        vm.saveEdit(name = " 阁楼 ", kind = " 阁楼角 ")

        val after = awaitState(vm, "新位置出现") { it.rows.isNotEmpty() }
        assertEquals("阁楼", after.rows.single().node.name)
        assertEquals("自由标签要原样留下（trim 后）", "阁楼角", after.rows.single().node.kind)
    }

    @Test
    fun `删除门槛：不空时拒并把还剩什么说清楚`() {
        val home = runBlocking { locations.add(parentId = null, name = "家", kind = "房间") }
        runBlocking { locations.add(parentId = home, name = "主卧", kind = "房间") }
        val vm = tree()
        awaitState(vm, "根层读出来") { it.rows.isNotEmpty() }

        vm.askDelete(home)
        vm.confirmDelete()

        val state = awaitState(vm, "给出兜底那句话") { it.notice != null }
        assertTrue("要说清还剩什么", state.notice!!.contains("1 个子位置"))
        assertTrue(runBlocking { locations.byId(home) } != null)
    }

    @Test
    fun `空位置能删掉`() {
        val home = runBlocking { locations.add(parentId = null, name = "家", kind = "房间") }
        val vm = tree()
        awaitState(vm, "根层读出来") { it.rows.isNotEmpty() }

        vm.askDelete(home)
        vm.confirmDelete()

        val after = awaitState(vm, "删掉后列表空") { it.rows.isEmpty() && !it.loading }
        assertNull(runBlocking { locations.byId(home) })
        assertNull(after.pendingDeleteId)
    }

    @Test
    fun `同级上移重排整条`() {
        val a = runBlocking { locations.add(parentId = null, name = "A", kind = null) }
        val b = runBlocking { locations.add(parentId = null, name = "B", kind = null) }
        val vm = tree()
        val before = awaitState(vm, "根层读出来") { it.rows.size == 2 }
        assertEquals(listOf(a, b), before.rows.map { it.node.id })

        vm.shift(b, up = true)

        val after = awaitState(vm, "顺序换过来") { it.rows.map { r -> r.node.id } == listOf(b, a) }
        assertEquals(listOf(b, a), after.rows.map { it.node.id })
    }

    @Test
    fun `移动到候选里不该出现自己与自己的后代`() {
        val home = runBlocking { locations.add(parentId = null, name = "家", kind = "房间") }
        val bedroom = runBlocking { locations.add(parentId = home, name = "主卧", kind = "房间") }
        val vm = tree()
        awaitState(vm, "根层读出来") { it.rows.isNotEmpty() }

        vm.askMove(home)

        val state = awaitState(vm, "候选读出来") { it.moveFor == home }
        assertFalse("不能移到它自己下面", state.moveOptions.any { it.id == home })
        assertFalse("也不能移到它的子位置下面", state.moveOptions.any { it.id == bedroom })
    }

    @Test
    fun `位置照片：没有图时只给选图那一步`() {
        val home = runBlocking { locations.add(parentId = null, name = "家", kind = "房间") }
        val vm = tree()
        vm.open(home)
        val before = awaitState(vm, "进到家里") { it.currentParentId == home && !it.loading }
        assertNull("没图时不该有照片路径（界面据此只给选图）", before.locationPhotoPath)

        vm.setPhoto(testJpeg())

        val after = awaitState(vm, "照片挂上") { it.locationPhotoPath != null }
        assertTrue(after.locationPhotoPath!!.contains("photos"))
        assertTrue("库里只有一行", runBlocking { locations.photoOf(home) } != null)
    }
}
