package com.wheremy.app.ui.review

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.checkRoboAccessibility
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 确认页的无障碍**判红**扫描（`FR-PRM-05`）。
 *
 * 这一屏是表单最密的一屏：一排排芯片、若干输入框、右上角两颗文字按钮。
 * 触摸目标尺寸这类问题最容易在这里冒出来，所以它也进 ATF 的 Error 档。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class ReviewAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUpDispatcher() {
        // VM 的协程跑在 viewModelScope（Dispatchers.Main）上；设成 Unconfined 后立即执行，
        // 不再看主 Looper 的脸色——CI runner 慢的时候后者会让 waitUntil 超时（这条 flake 就是真 CI 抓到的）
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `确认页过 ATF 的 Error 档`() {
        renderReview()

        compose.onRoot().checkRoboAccessibility(
            RoborazziATFAccessibilityCheckOptions(
                checker = RoborazziATFAccessibilityChecker(),
                failureLevel = RoborazziATFAccessibilityChecker.CheckLevel.Error,
            ),
        )
    }

    /**
     * 名称框的读屏文案要带「必填」。
     *
     * 为什么单写一条：视觉上那一格是「名称 *」，**星号读屏读不出来**，
     * 于是读屏用户不知道这一格必填（2026-10-12 真机走查发现，`PLAN.md` F50）。
     * 现在靠 `contentDescription = "名称，必填"` 让读屏念出来，视觉不变。
     *
     * 断言用 `hasContentDescription` + `hasSetTextAction`：前者证明"念得对"，
     * 后者证明这仍然是个能打字的输入框（别把语义加坏了）。
     */
    @Test
    fun `名称框的读屏文案带必填`() {
        renderReview()

        compose.onNodeWithTag("field_name")
            .assert(hasContentDescription("名称，必填"))
            .assert(hasSetTextAction())
    }

    /** 建好依赖、把确认页挂上去，返回它的 VM（`state.current` 不为空 = 页已就绪）。 */
    private fun renderReview(): ReviewViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val store = PhotoStore(File(tmp.root, "photos"))
        val items = ItemRepository(db, store)
        val locations = LocationRepository(db, store)
        val flow = CaptureFlow(items)
        val settings = testSettings()

        val itemId = runBlocking { items.newDraft(testJpeg()) }
        val vm = ReviewViewModel(
            items,
            locations,
            flow,
            // 分析器配置给 null：这一屏的测试只扫界面、不发请求
            ApiPhotoAnalyzer({ null }, AnalysisTrace(File(tmp.root, "ai-trace"))),
            settings,
        )

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
}
