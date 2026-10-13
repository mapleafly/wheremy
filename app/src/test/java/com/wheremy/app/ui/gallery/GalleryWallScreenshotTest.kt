package com.wheremy.app.ui.gallery

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
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
 * 照片墙的截图基线（2026-10-13 新增）。
 *
 * 为什么补这一条：**最常用的一屏原先没有基线**——只有相机三张 + 设计系统两张。
 * 这次把「只看待归类」从 `AssistChip` 换成 `FilterChip`（选中态有填充样式），正好需要一张图钉住外观。
 * 两种状态各一张：关着 / 开着。
 *
 * ⚠️ 两条容易踩的坑（都踩过）：
 * 1. **截图前必须等"筛选后的查询"落地**：`toggleOnlyInbox()` 会触发一次异步刷新，
 *    若在它跑完前结束测试，`@After` 关库时会炸成 `IllegalStateException: Database is closed`。
 *    所以数据里特意放了一件**不在待归类**的物品：打开筛选后行数从 3 掉到 2，
 *    等到 2 就证明那次查询真的跑完了（比"睡一会儿"可靠）。
 * 2. 内存库必须在 `@After` 里关（不关会污染下一条测试，见 `PLAN.md` F50）。
 *
 * 记录：`./gradlew :app:recordRoborazziDebug`；校验：`./gradlew :app:verifyRoborazziDebug`。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class GalleryWallScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        if (::db.isInitialized) db.close()
    }

    @Test
    fun galleryWallBaseline() {
        renderWall(onlyInbox = false) {
            compose.onRoot().captureRoboImage("src/test/screenshots/gallery-wall.png")
        }
    }

    @Test
    fun galleryWallOnlyInboxBaseline() {
        renderWall(onlyInbox = true) {
            compose.onRoot().captureRoboImage("src/test/screenshots/gallery-wall-only-inbox.png")
        }
    }

    /** 建库、塞三件（两件在待归类、一件在「家」里），挂上照片墙；[onlyInbox] 为真时先打开筛选。 */
    private fun renderWall(onlyInbox: Boolean, block: (GalleryViewModel) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val store = PhotoStore(java.io.File(tmp.root, "photos"))
        val items = ItemRepository(db, store)
        val locations = LocationRepository(db, store)

        runBlocking {
            val home = locations.add(parentId = null, name = "家", kind = null)
            val a = items.newDraft(testJpeg())
            items.confirm(ItemDraft(id = a, name = "电钻", locationId = null))
            val b = items.newDraft(testJpeg(width = 600, height = 600))
            items.confirm(ItemDraft(id = b, name = "羽绒外套", locationId = null, category = "衣物"))
            val c = items.newDraft(testJpeg(width = 500, height = 500))
            items.confirm(ItemDraft(id = c, name = "螺丝刀", locationId = home))
        }

        val vm = GalleryViewModel(items, locations, testSettings())

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

        // 先等初始加载（三件都在）
        compose.waitUntil(timeoutMillis = 20_000) { vm.state.value.rows.size == 3 }

        if (onlyInbox) {
            // ⚠️ 必须**在**初始加载之后切：渲染前切会被初始加载覆盖掉（筛选又变回 false，踩过）
            vm.toggleOnlyInbox()
            // 等"筛选生效 **且** 查询落地"：行数掉到 2 说明那次异步查询已经跑完，
            // 截图之后不会再有刷新在跑（否则 @After 关库会炸 Database is closed）
            compose.waitUntil(timeoutMillis = 20_000) {
                vm.state.value.filters.onlyInbox && vm.state.value.rows.size == 2
            }
        }

        block(vm)
    }
}
