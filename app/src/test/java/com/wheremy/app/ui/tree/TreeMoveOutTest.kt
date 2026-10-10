package com.wheremy.app.ui.tree

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
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
 * `FR-LOC-05`：删位置时**就地**把里面的东西搬走。
 *
 * 这条要钉三件事：
 * 1. 非空位置一打开删除弹窗就能看到"还剩什么"（不是按了删除才知道）；
 * 2. 「把物品搬到…」之后**自动重试删除**——搬走的唯一目的就是删掉它，让用户再点一次是多余的；
 * 3. 子位置**不能搬进自己的子树**（否则树上多出一个环）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TreeMoveOutTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var locations: LocationRepository
    private lateinit var items: ItemRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val store = PhotoStore(File(tmp.root, "photos"))
        locations = LocationRepository(db, store)
        items = ItemRepository(db, store)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun tree() = TreeViewModel(locations, items, testSettings())

    private fun addItem(name: String, locationId: String?): String = runBlocking {
        val id = items.newDraft(testJpeg())
        items.confirm(ItemDraft(id = id, name = name, locationId = locationId))
        id
    }

    private fun awaitState(vm: TreeViewModel, what: String, predicate: (TreeUiState) -> Boolean): TreeUiState {
        repeat(200) {
            val state = vm.state.value
            if (predicate(state)) return state
            Thread.sleep(10)
        }
        throw AssertionError("等不到：$what，当前 = ${vm.state.value}")
    }

    @Test
    fun `非空位置一打开删除弹窗就能看到还剩什么`() = runBlocking {
        val home = locations.add(parentId = null, name = "家", kind = "房间")
        val drawer = locations.add(parentId = home, name = "抽屉", kind = "抽屉")
        addItem("电钻", drawer)
        addItem("胶带", drawer)

        val vm = tree()
        vm.open(home)
        awaitState(vm, "根层读出来") { it.currentParentId == home && !it.loading }

        vm.askDelete(drawer)
        val state = awaitState(vm, "删除弹窗里数出还剩什么") { it.pendingDeleteId == drawer && it.pendingDeleteItemCount == 2 }
        assertEquals("两件物品", 2, state.pendingDeleteItemCount)
        assertEquals("没有子位置", 0, state.pendingDeleteChildCount)

        // 直接按删除：数据层挡住，弹窗**不关**，数字还在（这样"搬走"那两颗按钮就在眼前）
        vm.confirmDelete()
        val blocked = awaitState(vm, "被挡住之后弹窗仍在") { it.pendingDeleteId == drawer && it.notice != null }
        assertEquals(2, blocked.pendingDeleteItemCount)
        assertEquals("位置没被删掉", "抽屉", locations.byId(drawer)?.name)
    }

    @Test
    fun `把物品搬走之后自动删掉这个位置`() = runBlocking {
        val home = locations.add(parentId = null, name = "家", kind = "房间")
        val drawer = locations.add(parentId = home, name = "抽屉", kind = "抽屉")
        val drill = addItem("电钻", drawer)
        val tape = addItem("胶带", drawer)

        val vm = tree()
        vm.open(home)
        awaitState(vm, "根层读出来") { it.currentParentId == home && !it.loading }
        vm.askDelete(drawer)
        awaitState(vm, "删除弹窗打开") { it.pendingDeleteId == drawer && it.pendingDeleteItemCount == 2 }

        vm.startMoveOut(MoveOutKind.ITEMS)
        awaitState(vm, "目标清单出来") { it.moveOutFrom == drawer && it.moveOptions.isNotEmpty() }
        vm.pickMoveOutTarget(home)

        // 等 refresh 也落地：pendingDeleteId 先翻，行列表随后才更新
        val after = awaitState(vm, "搬完并自动删掉") { it.pendingDeleteId == null && it.rows.isEmpty() }
        assertNull("位置删掉了", locations.byId(drawer))
        assertEquals("物品搬到家里了", home, items.get(drill)?.locationId)
        assertEquals(home, items.get(tape)?.locationId)
        assertTrue("要说出搬了几件", after.notice!!.contains("2 件物品"))
        assertEquals("根层现在只剩物品、没有抽屉", 0, after.rows.size)
    }

    @Test
    fun `子位置也要搬走才删得掉，两段都搬完才自动删`() = runBlocking {
        val home = locations.add(parentId = null, name = "家", kind = "房间")
        val drawer = locations.add(parentId = home, name = "抽屉", kind = "抽屉")
        val box = locations.add(parentId = drawer, name = "格子", kind = "收纳盒")
        addItem("电钻", drawer)

        val vm = tree()
        vm.open(home)
        awaitState(vm, "根层读出来") { it.currentParentId == home && !it.loading }
        vm.askDelete(drawer)
        val opened = awaitState(vm, "两个数字都对") { it.pendingDeleteId == drawer && it.pendingDeleteChildCount == 1 }
        assertEquals(1, opened.pendingDeleteItemCount)

        // 只搬物品：子位置还在 → 仍然删不掉，弹窗还在
        vm.startMoveOut(MoveOutKind.ITEMS)
        awaitState(vm, "物品目标清单") { it.moveOutFrom == drawer }
        vm.pickMoveOutTarget(home)
        val stillBlocked = awaitState(vm, "物品数归零、子位置还挡着") {
            it.pendingDeleteId == drawer && it.pendingDeleteItemCount == 0 && it.pendingDeleteChildCount == 1
        }
        assertEquals("物品搬走了", 0, stillBlocked.pendingDeleteItemCount)
        assertEquals("位置还在", "抽屉", locations.byId(drawer)?.name)

        // 再搬子位置 → 自动删掉
        vm.startMoveOut(MoveOutKind.CHILDREN)
        awaitState(vm, "子位置目标清单") { it.moveOutFrom == drawer && it.moveOutKind == MoveOutKind.CHILDREN }
        vm.pickMoveOutTarget(home)
        awaitState(vm, "这下删掉了") { it.pendingDeleteId == null }
        assertNull(locations.byId(drawer))
        assertEquals("格子挂到家里了", home, locations.byId(box)?.parentId)
    }

    @Test
    fun `子位置不能搬进自己的子树`() = runBlocking {
        val home = locations.add(parentId = null, name = "家", kind = "房间")
        val drawer = locations.add(parentId = home, name = "抽屉", kind = "抽屉")
        locations.add(parentId = drawer, name = "格子", kind = "收纳盒")
        locations.add(parentId = null, name = "工作间", kind = "房间")

        val vm = tree()
        vm.open(home)
        awaitState(vm, "根层读出来") { it.currentParentId == home && !it.loading }
        vm.askDelete(drawer)
        awaitState(vm, "删除弹窗打开") { it.pendingDeleteId == drawer }

        vm.startMoveOut(MoveOutKind.CHILDREN)
        val state = awaitState(vm, "目标清单出来") { it.moveOutFrom == drawer && it.moveOptions.isNotEmpty() }
        val labels = state.moveOptions.map { it.label }
        assertTrue("自己不能当目标：$labels", labels.none { it.endsWith("抽屉") })
        assertTrue("自己的子树也不能当目标：$labels", labels.none { it.contains("格子") })
        assertTrue("别的位置可以：$labels", labels.any { it.contains("工作间") })
    }
}
