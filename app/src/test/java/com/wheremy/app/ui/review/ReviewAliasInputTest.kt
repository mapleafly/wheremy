package com.wheremy.app.ui.review

import android.content.Context
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.ai.AnalysisTrace
import com.wheremy.app.data.ai.ApiPhotoAnalyzer
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.data.settings.testSettings
import com.wheremy.app.data.testJpeg
import com.wheremy.app.domain.CaptureFlow
import com.wheremy.app.ui.capture.CaptureViewModel
import com.wheremy.app.ui.theme.WheremyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 别名输入框（`RULES.md` §15 W9 / `UI.md` §6.5）。
 *
 * 这一格曾经每按一键就把原文拆成列表再用「、」拼回去，于是 `、` `，` `,` 与结尾空格
 * 都会在**同一帧**被吃掉——用户按了没反应；句号不是分隔符，所以它能留下。
 * 这条测试按真人的顺序逐个键敲进去，断言输入框里留下的就是敲进去的那串原文。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ReviewAliasInputTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var vm: ReviewViewModel
    private lateinit var items: ItemRepository
    private lateinit var flow: CaptureFlow

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val store = PhotoStore(File(tmp.root, "photos"))
        items = ItemRepository(db, store)
        val locations = LocationRepository(db, store)
        flow = CaptureFlow(items)
        vm = ReviewViewModel(
            items,
            locations,
            flow,
            // 不连网：这一条只测输入框（AI 那条路会立刻落到"没配置"）
            ApiPhotoAnalyzer({ null }, AnalysisTrace(File(tmp.root, "ai-trace"))),
            testSettings(),
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun render(): ReviewViewModel {
        val itemId = runBlocking { items.newDraft(testJpeg()) }
        compose.setContent {
            WheremyTheme(darkTheme = false) {
                ReviewScreen(
                    itemId = itemId,
                    onDone = {},
                    captureVm = CaptureViewModel(flow),
                    onReplacePhoto = {},
                    vm = vm,
                )
            }
        }
        compose.waitUntil(timeoutMillis = 20_000) { vm.state.value.current != null }
        return vm
    }

    /** 按真人的节奏逐键输入（每一段都走 IME，不是一次 setText 到底）。 */
    private fun type(text: String) {
        compose.onNodeWithTag("field_aliases").performScrollTo().performTextInput(text)
    }

    @Test
    fun `顿号 逗号 空格 都能留在输入框里`() {
        val vm = render()

        listOf("围巾", "、", "帽子", "，", "手套", ",", "小米 11。").forEach(::type)

        val expected = "围巾、帽子，手套,小米 11。"
        compose.onNodeWithTag("field_aliases").assertTextContains(expected)
        assertEquals("原文就是原文，一个字符都不许被吃掉", expected, vm.state.value.current!!.aliasesText)
    }

    @Test
    fun `落库用的列表仍然按三个分隔符拆开`() {
        val vm = render()

        listOf("围巾", "、", "帽子", "，", "手套", ",", "小米 11。").forEach(::type)

        assertEquals(
            listOf("围巾", "帽子", "手套", "小米 11。"),
            vm.state.value.current!!.draft.aliases,
        )
    }

    /**
     * 换行不是分隔符，也不许留在原文里。
     *
     * 实测：Compose 的 `singleLine = true` 只改渲染，粘进来的 `\n` 仍会进 `onValueChange`。
     * 所以 `AliasText.oneLine` 把它折成空格——比丢字符更像"单行框里粘了一段两行文本"。
     */
    @Test
    fun `带换行的粘贴落成一行，不会成为一个含换行的别名`() {
        val vm = render()

        compose.onNodeWithTag("field_aliases").performScrollTo().performTextInput("围巾\n帽子")

        val slot = vm.state.value.current!!
        assertEquals("换行折成空格", "围巾 帽子", slot.aliasesText)
        assertEquals("仍然是一个别名，不是两个", listOf("围巾 帽子"), slot.draft.aliases)
    }
}
