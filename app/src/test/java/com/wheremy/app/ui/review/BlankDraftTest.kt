package com.wheremy.app.ui.review

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.ai.AnalysisTrace
import com.wheremy.app.data.ai.ApiPhotoAnalyzer
import com.wheremy.app.data.db.PhotoOwner
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.repo.Filters
import com.wheremy.app.data.repo.ItemDraft
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.data.settings.testSettings
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
import java.io.File

/**
 * `FR-SRC-09` 无照片的手填条目。
 *
 * 这条的关键不是"能建出来"，而是**后面每一步都不需要为它另开一条路**：
 * 确认入库、检索、删除、照片引用计数、AI 那条线——都得在"没有照片"时表现正常。
 * 尤其是 AI：手动填的条目**不是**"照片丢了"，不能弹那句"找不到这张照片了"。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BlankDraftTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var store: PhotoStore
    private lateinit var items: ItemRepository
    private lateinit var locations: LocationRepository
    private lateinit var flow: CaptureFlow

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
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `手填开出来的草稿没有照片行，也没有待分析的照片`() = runBlocking {
        val id = flow.startBlank()

        assertEquals("名字是空的，等用户填", "", items.get(id)?.name)
        assertNull("没有缩略图", items.thumbPathOf(id))
        assertNull("没有原图", items.photoBytesOf(id))
        assertTrue("库里一行照片都没有", db.photoDao().forOwner(PhotoOwner.ITEM, id).isEmpty())
        assertNull("也没有待分析的照片（不该去跑 AI）", items.takePendingAnalysis())
    }

    @Test
    fun `手填的条目照样能确认入库、能被搜到`() = runBlocking {
        val id = flow.startBlank()
        items.confirm(ItemDraft(id = id, name = "身份证", category = "文档", notes = "抽屉第二格"))

        assertEquals("身份证", items.get(id)?.name)
        assertEquals(
            "按名字能搜到（与有照片的条目同一条检索路）",
            listOf(id),
            items.search("身份证", Filters()).rows.map { it.id },
        )
        val summary = items.summaries(listOf(items.get(id)!!)).first { it.item.id == id }
        assertNull("墙上那一格没有缩略图（界面拿名字占位）", summary.thumbPath)
    }

    @Test
    fun `删手填的条目：文案说的是记录会被删，不牵扯照片`() = runBlocking {
        val id = flow.startBlank()
        items.confirm(ItemDraft(id = id, name = "身份证"))

        val deletion = items.photoDeletionOf(id)
        assertFalse("它本来就没有照片", deletion.hasPhoto)
        assertTrue(
            "文案不该提照片共用那两档",
            com.wheremy.app.ui.UiFormat.deleteWarningOf(deletion).contains("记录会被删除"),
        )

        items.delete(id)
        assertNull(items.get(id))
    }

    @Test
    fun `确认页打开手填条目：不跑分析，也不说照片丢了`() {
        val id = runBlocking {
            val newId = flow.startBlank()
            items.confirm(ItemDraft(id = newId, name = "身份证"))
            newId
        }
        val vm = ReviewViewModel(
            items,
            locations,
            flow,
            ApiPhotoAnalyzer({ null }, AnalysisTrace(File(tmp.root, "ai-trace"))),
            testSettings(),
        )

        vm.start(id)
        repeat(100) {
            if (vm.state.value.current != null && !vm.state.value.analyzing) return@repeat
            Thread.sleep(10)
        }

        val state = vm.state.value
        assertEquals("读出来了", id, state.current?.itemId)
        assertFalse("没有照片 → 不分析", state.analyzing)
        assertNull("没有失败", state.failure)
        assertNull("**不能**说「找不到这张照片了」——那会让人以为出了问题", state.notice)
        assertNull("也没有位置建议", state.guess)
    }

    @Test
    fun `有照片的那条路一个字都没改：照样会跑一次分析`() {
        val id = runBlocking {
            val newId = items.newDraft(com.wheremy.app.data.testJpeg())
            newId
        }
        val vm = ReviewViewModel(
            items,
            locations,
            flow,
            ApiPhotoAnalyzer({ null }, AnalysisTrace(File(tmp.root, "ai-trace"))),
            testSettings(),
        )

        vm.start(id)
        repeat(100) {
            if (vm.state.value.current != null && !vm.state.value.analyzing) return@repeat
            Thread.sleep(10)
        }

        // 分析器在测试里返回 null（不联网），所以收场是"分析失败"那一条文案——
        // 这里只钉"**它确实去分析了**"（有照片就该走 AI 那条线）
        val state = vm.state.value
        assertTrue("有照片 → 会跑分析（失败也是跑过了）", state.failure != null || state.guess != null || state.analyzing)
    }
}
