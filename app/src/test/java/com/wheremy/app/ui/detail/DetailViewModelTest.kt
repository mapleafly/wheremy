package com.wheremy.app.ui.detail

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
import org.junit.Assert.assertNotNull
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
 * 详情页状态机（`UI.md` §5）。
 * 三态必须互斥、删完**不 reload**、搬到要重读——这三条错了分别是"对着不存在的记录按编辑"、
 * "删了还显示"、"搬完位置行还是旧的"。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailViewModelTest {

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

    private fun detail(): DetailViewModel = DetailViewModel(items, locations)

    private fun awaitState(
        vm: DetailViewModel,
        description: String,
        predicate: (DetailUiState) -> Boolean,
    ): DetailUiState {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val state = vm.state.value
            if (predicate(state)) return state
            Thread.sleep(20)
        }
        throw AssertionError("等不到：$description，当前 = ${vm.state.value}")
    }

    private fun addItem(name: String = "电钻", locationId: String? = null): String = runBlocking {
        val id = items.newDraft(testJpeg())
        items.confirm(ItemDraft(id = id, name = name, locationId = locationId, tags = listOf("工具")))
        id
    }

    @Test
    fun `读到一条就给出摘要与照片绝对路径`() {
        val id = addItem()
        val vm = detail()

        vm.load(id)

        val state = awaitState(vm, "读出来") { it.summary != null }
        assertFalse(state.loading)
        assertFalse(state.missing)
        assertFalse(state.failed)
        assertEquals("电钻", state.summary!!.item.name)
        assertEquals(listOf("工具"), state.summary!!.tags)
        assertEquals(1, state.photoPaths.size)
        assertTrue(
            "给界面的是绝对路径",
            state.photoPaths.single().startsWith("/") || state.photoPaths.single().contains("photos"),
        )
    }

    @Test
    fun `记录不存在是 missing 而不是 failed`() {
        val vm = detail()

        vm.load("不存在的-id")

        val state = awaitState(vm, "读到 missing") { !it.loading }
        assertTrue(state.missing)
        assertFalse("'没有这条记录'与'读不出来'是两件事", state.failed)
        assertNull("顶栏三颗要因此不出现", state.summary)
    }

    @Test
    fun `删完只置 deleted 不 reload`() {
        val id = addItem()
        val vm = detail()
        vm.load(id)
        awaitState(vm, "先读出来") { it.summary != null }

        vm.askDelete()
        val pending = awaitState(vm, "问到那句话") { it.pendingDelete != null }.pendingDelete!!
        assertEquals(id, pending.itemId)
        assertEquals("照片和记录会一起删除，无法恢复。", pending.warning)

        vm.confirmDelete()

        val state = awaitState(vm, "置 deleted") { it.deleted }
        assertNull("库里已经没有这一行，没有可 load 的东西", runBlocking { items.get(id) })
        assertNotNull("但状态里还留着旧摘要，退屏交给界面", state.summary)
    }

    @Test
    fun `搬到之后要重读位置行`() {
        val home = runBlocking { locations.add(parentId = null, name = "家", kind = "房间") }
        val box = runBlocking { locations.add(parentId = home, name = "收纳盒", kind = "收纳盒") }
        val id = addItem(locationId = null)
        val vm = detail()
        vm.load(id)
        val before = awaitState(vm, "先读出来") { it.summary != null }
        assertNull("还没归位", before.summary!!.locationPath)

        vm.move(box)

        val after = awaitState(vm, "位置行刷新") { it.summary?.locationPath != null }
        assertEquals("家 / 收纳盒", after.summary!!.locationPath)
        assertEquals(box, runBlocking { items.get(id) }!!.locationId)
    }

    @Test
    fun `搬到暂不归类会写回待归类`() {
        val home = runBlocking { locations.add(parentId = null, name = "家", kind = "房间") }
        val id = addItem(locationId = home)
        val vm = detail()
        vm.load(id)
        awaitState(vm, "先读出来") { it.summary?.locationPath != null }

        vm.move(null)

        awaitState(vm, "位置行变回待归类") { it.summary?.locationPath == null }
        assertNull(runBlocking { items.get(id) }!!.locationId)
    }

    @Test
    fun `搬到对话框给候选并默认有一条暂不归类`() {
        runBlocking { locations.add(parentId = null, name = "家", kind = "房间") }
        val id = addItem()
        val vm = detail()
        vm.load(id)
        awaitState(vm, "先读出来") { it.summary != null }

        vm.askMove()

        val state = awaitState(vm, "候选读出来") { it.moving && it.moveOptions.isNotEmpty() }
        assertTrue(state.moveOptions.any { it.label == "家" })
    }
}
