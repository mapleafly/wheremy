package com.wheremy.app.ui.gallery

import android.content.Context
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.checkRoboAccessibility
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.repo.ItemDraft
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.data.settings.testSettings
import com.wheremy.app.data.testJpeg
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

/**
 * 照片墙的无障碍**判红**扫描（`FR-PRM-05`）。
 *
 * 为什么单给照片墙开一条：它是用得最多的一屏，也是唯一"格子密、可点区域多"的一屏——
 * 触摸目标尺寸与可读名字这两类问题最容易在这里冒出来。取景屏那条在 `AccessibilityScanTest`。
 *
 * 后面那条「开关类控件」是 2026-10-12 的全应用审计（`PLAN.md` F50 的延伸）：
 * 用户报设置页开关与标签分家之后，把所有**可切换类**控件都按读屏那棵树核了一遍。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class GalleryAccessibilityTest {

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
        // 内存库必须关：不关的话它的后台协程会把未捕获异常留到下一条测试里
        // （报成 UncaughtExceptionsBeforeTest，真 CI 抓到过）
        if (::db.isInitialized) db.close()
    }

    private lateinit var db: WheremyDatabase

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `照片墙有物品时也过 ATF 的 Error 档`() {
        renderWall()

        compose.onRoot().checkRoboAccessibility(
            RoborazziATFAccessibilityCheckOptions(
                checker = RoborazziATFAccessibilityChecker(),
                failureLevel = RoborazziATFAccessibilityChecker.CheckLevel.Error,
            ),
        )
    }

    /**
     * 芯片类控件的标签与状态都在**同一个**节点上（读屏那棵树）。
     *
     * 2026-10-13 起「只看待归类」也换成了 `FilterChip`：它是**开关行为**，材料组件自带 `selected` 语义，
     * 读屏念「只看待归类，已选中」——不用再靠改文案（原来是 `只看待归类 · 开`）来表达状态。
     */
    @Test
    fun `芯片的标签与状态在同一个无障碍节点上`() {
        renderWall()

        // 只看待归类：可选中 → 点一下变「已选中」，而且**文案不变**
        compose.onNode(hasText("只看待归类") and isSelectable())
            .assertIsNotSelected()
            .performClick()
        compose.onNode(hasText("只看待归类") and isSelectable())
            .assertIsSelected()

        // 分类芯片：同一个模式（未选中起步）
        compose.onNode(hasText("衣物") and isSelectable()).assertIsNotSelected()
    }

    /** 建库、塞一件物品、把照片墙挂上去。 */
    private fun renderWall() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val store = PhotoStore(java.io.File(tmp.root, "photos"))
        val items = ItemRepository(db, store)

        runBlocking {
            val id = items.newDraft(testJpeg())
            items.confirm(ItemDraft(id = id, name = "电钻", locationId = null))
        }

        val vm = GalleryViewModel(items, LocationRepository(db, store), testSettings())
        compose.setContent {
            WheremyTheme(darkTheme = false) {
                GalleryScreen(
                    inboxMode = false,
                    compactHeight = false,
                    showExpiringSoonOnly = false,
                    onOpenItem = {},
                    vm = vm,
                )
            }
        }
        compose.waitUntil(timeoutMillis = 20_000) { vm.state.value.rows.isNotEmpty() }
    }
}
