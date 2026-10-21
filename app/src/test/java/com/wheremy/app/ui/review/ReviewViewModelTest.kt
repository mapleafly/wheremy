package com.wheremy.app.ui.review

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.ai.AnalysisTrace
import com.wheremy.app.data.ai.ApiPhotoAnalyzer
import com.wheremy.app.data.db.ItemStatus
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.data.settings.testSettings
import com.wheremy.app.data.testJpeg
import com.wheremy.app.domain.CaptureFlow
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
 * 确认页状态机（`RULES.md` §6）。
 * 这一支钉的是"退出时会不会丢东西、说哪句话、删什么"——数错了就是丢用户的数据。
 *
 * ⚠️ 测试基础设施：VM 的协程跑在 `viewModelScope`（Main），而 Room 的挂起查询在它自己的线程池上完成。
 * 所以这里**不用虚拟时间的 `runTest`**（它会在 Room 回来之前就结束），改成真实等待：
 * Main 设成 `Dispatchers.Unconfined` + [awaitState] 轮询。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReviewViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var store: PhotoStore
    private lateinit var items: ItemRepository
    private lateinit var locations: LocationRepository
    private lateinit var flow: CaptureFlow
    private lateinit var vm: ReviewViewModel

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
        flow = CaptureFlow(items)
        // 测试里**不连网**：配置给 null → AI 那条路走 NO_CONFIG，表单照旧能手填
        vm = ReviewViewModel(
            items,
            locations,
            flow,
            ApiPhotoAnalyzer({ null }, AnalysisTrace(File(tmp.root, "ai-trace"))),
            // 词表用内存版设置（FR-CFG-06）：测试里就是预设
            testSettings(),
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** 等 VM 的异步动作落地（Room 的查询在别的线程上完成）。 */
    private fun awaitState(description: String, predicate: (ReviewUiState) -> Boolean): ReviewUiState {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val state = vm.state.value
            if (predicate(state)) return state
            Thread.sleep(20)
        }
        throw AssertionError("等不到：$description，当前状态 = ${vm.state.value}")
    }

    private fun newDraftItem(): String = runBlocking { flow.capture(testJpeg()) {}.itemId }

    @Test
    fun `start 把刚落库的那件读成槽位`() {
        val itemId = newDraftItem()

        vm.start(itemId)

        val slot = awaitState("槽位加载出来") { it.current != null }.current!!
        assertEquals(itemId, slot.itemId)
        assertEquals("新草稿没名字", "", slot.draft.name)
        assertTrue("缩略图要给绝对路径", slot.thumbPath!!.endsWith(".jpg"))
        assertNull(slot.locationPath)
    }

    @Test
    fun `单件保存后关页且库里是已确认`() {
        val itemId = newDraftItem()
        vm.start(itemId)
        awaitState("槽位就绪") { it.current != null }
        vm.setName("电钻")

        vm.save()

        awaitState("关页") { it.finished }
        assertEquals(ItemStatus.CONFIRMED, runBlocking { items.get(itemId) }!!.status)
    }

    @Test
    fun `稍后处理写字段但留在待归类`() {
        val itemId = newDraftItem()
        vm.start(itemId)
        awaitState("槽位就绪") { it.current != null }
        vm.setName("护照")

        vm.keepForLater()

        awaitState("关页") { it.finished }
        val stored = runBlocking { items.get(itemId) }!!
        assertEquals("状态还是草稿", ItemStatus.DRAFT, stored.status)
        assertEquals("护照", stored.name)
        assertTrue(runBlocking { items.inbox() }.any { it.id == itemId })
    }

    @Test
    fun `什么都没动就退出：不问，也不留下空壳`() {
        val itemId = newDraftItem()
        val photoPath = runBlocking { items.photosOf(itemId).single().filePath }
        vm.start(itemId)
        awaitState("槽位就绪") { it.current != null }

        vm.requestExit()

        val state = awaitState("关页") { it.finished }
        assertFalse("不该弹框", state.confirmExit)
        assertNull("空壳要连行一起摘掉", runBlocking { items.get(itemId) })
        assertFalse("照片文件按引用计数一起回收", store.file(photoPath).exists())
    }

    @Test
    fun `打过字就一定要问`() {
        val itemId = newDraftItem()
        vm.start(itemId)
        awaitState("槽位就绪") { it.current != null }
        vm.setQuantity(2) // 只改数量也算"他动过"

        vm.requestExit()

        val state = vm.state.value
        assertTrue(state.confirmExit)
        assertFalse("问过之前不能关页", state.finished)
        assertEquals("有落库未保存的件：支二的第一句要说", 1, state.leftBehind)
    }

    @Test
    fun `保存改动并留下就是稍后处理`() {
        val itemId = newDraftItem()
        vm.start(itemId)
        awaitState("槽位就绪") { it.current != null }
        vm.setName("电钻")

        vm.requestExit()
        vm.exitKeepingEdits()

        awaitState("关页") { it.finished }
        val stored = runBlocking { items.get(itemId) }!!
        assertEquals("字段要写进去", "电钻", stored.name)
        assertEquals("但状态还是草稿", ItemStatus.DRAFT, stored.status)
    }

    @Test
    fun `不保存直接离开不写字段也不删已落库的件`() {
        val itemId = newDraftItem()
        vm.start(itemId)
        awaitState("槽位就绪") { it.current != null }
        vm.setName("别写进去")

        vm.requestExit()
        vm.exitWithoutSaving()

        awaitState("关页") { it.finished }
        val stored = runBlocking { items.get(itemId) }!!
        assertEquals("名字不该被写进去", "", stored.name)
        assertEquals(ItemStatus.DRAFT, stored.status)
    }

    @Test
    fun `已经确认过的件离开不动它`() {
        val itemId = newDraftItem()
        vm.start(itemId)
        awaitState("槽位就绪") { it.current != null }
        vm.setName("电钻")
        vm.save()
        awaitState("第一次关页") { it.finished }
        vm.consumeFinished()

        vm.start(itemId)
        // 等"重载后"的槽位：`saved` 回到 false 才是判据。**别用名字等**——
        // 保存前那份槽位的名字也是「电钻」，会提前匹配上（第一版就是这么红的）
        awaitState("重新进来槽位就绪") { it.current?.saved == false && it.current?.draft?.name == "电钻" }

        vm.requestExit()

        val state = awaitState("直接关页") { it.finished }
        assertFalse("库里那件是存好的，没什么可丢，不必问", state.confirmExit)
        assertEquals(ItemStatus.CONFIRMED, runBlocking { items.get(itemId) }!!.status)
    }

    @Test
    fun `换一张照片不抹掉还没保存的手填字段`() {
        val itemId = newDraftItem()
        vm.start(itemId)
        awaitState("槽位就绪") { it.current != null }
        vm.setName("电钻")
        vm.setAliasesText("钻、电钻 ")
        val oldThumb = vm.state.value.current!!.thumbPath

        vm.replacePhoto(testJpeg(width = 500, height = 500))

        // 等缩略图真的换掉；别用 aliasesText 等——它换图之前就是那个值
        awaitState("缩略图换掉") { it.current?.thumbPath != null && it.current?.thumbPath != oldThumb }
        val slot = vm.state.value.current!!
        assertEquals("手填的名字要留着", "电钻", slot.draft.name)
        assertEquals("别名原文要留着", "钻、电钻 ", slot.aliasesText)
        assertEquals("落库列表仍按分隔符拆", listOf("钻", "电钻"), slot.draft.aliases)
    }

    @Test
    fun `不要这件只对没落库的有效且不能摘最后一件`() {
        val itemId = newDraftItem()
        vm.start(itemId)
        awaitState("槽位就绪") { it.current != null }

        vm.discardCurrent()

        assertEquals("最后一件不许摘", 1, vm.state.value.slots.size)
        assertNull("已落库的那件也不许走这条路", vm.state.value.notice)
    }
}
