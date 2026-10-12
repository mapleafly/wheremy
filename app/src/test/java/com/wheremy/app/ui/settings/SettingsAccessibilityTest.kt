package com.wheremy.app.ui.settings

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.backup.AutoExporter
import com.wheremy.app.data.backup.BackupRepository
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.settings.testSettings
import com.wheremy.app.domain.ReminderEngine
import com.wheremy.app.reminder.ReminderRunner
import com.wheremy.app.ui.theme.WheremyTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * 设置页开关的**无障碍语义**（`FR-PRM-05`；2026-10-12 真机走查发现，`PLAN.md` F50）。
 *
 * 走查反馈：「开关和标签是**分离**的两个焦点，而且有时标签在前、有时在后，
 * 读到开关时不知道它是哪个开关」。官方做法是把整行做成**一个**可切换项
 * （`Modifier.toggleable(role = Role.Switch)` + `Switch(onCheckedChange = null)`）。
 *
 * ⚠️ 为什么用 Compose 测试断言、而不是 `adb shell uiautomator dump`：
 * 后者看的是**原始视图树**，而读屏（TalkBack）读的是**合并后的语义树**——两者不是一棵树。
 * `onNode(hasText(标签) and isToggleable())` 命中的恰好是读屏会念的那**一个**节点，
 * 所以这条断言等价于"读屏能不能把标签和开关一起念出来"，而且能进 CI 防回归。
 */
@RunWith(RobolectricTestRunner::class)
class SettingsAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `自动导出的开关与标签是同一个无障碍节点`() {
        renderSettings()

        // 一个节点同时"有这句标签"且"是可切换项" → 读屏会把它们一起念出来。
        // 加 hasClickAction：证明整行可点（点标签也能切换），不是只能戳那颗小开关。
        compose.onNode(hasText(AUTO_EXPORT_LABEL) and isToggleable())
            .assert(hasClickAction())
            .assertIsOff()
    }

    @Test
    fun `AI 预填的开关与标签是同一个无障碍节点`() {
        renderSettings()

        compose.onNode(hasText(VISION_LABEL) and isToggleable())
            .assert(hasClickAction())
    }

    @Test
    fun `标签在语义树里只出现一次——不再有一个只会念开或关的独立焦点`() {
        renderSettings()

        compose.onAllNodes(hasText(AUTO_EXPORT_LABEL)).assertCountEquals(1)
        compose.onAllNodes(hasText(VISION_LABEL)).assertCountEquals(1)
    }

    /**
     * 提前天数的预设（「当天 / 1 天 / 3 天 / 7 天」）是 `FilterChip(selected = …)`，
     * 材料组件自带选中语义 → 读屏会念「已选中」。默认提前 3 天，所以「3 天」应是选中态。
     */
    @Test
    fun `提前天数的预设芯片带选中状态`() {
        renderSettings()

        compose.onNode(hasText("3 天") and isSelectable()).assertIsSelected()
    }

    /** 建好依赖、把设置页挂上去。 */
    private fun renderSettings() {
        val store = PhotoStore(File(tmp.root, "photos"))
        val settings = testSettings()
        val backup = BackupRepository(db, store, "0.1.0-test", File(store.rootDir, ".incoming"))
        val vm = SettingsViewModel(
            settings = settings,
            runner = ReminderRunner(settings, ReminderEngine(db.itemDao())),
            backup = backup,
            // 自动导出的"存哪儿"给 null：这一屏的测试只看语义，不碰系统文件选择器
            exporter = AutoExporter(backup, { null }, settings),
        )
        compose.setContent {
            WheremyTheme(darkTheme = false) {
                SettingsScreen(
                    onOpenDesignGallery = {},
                    onOpenDataSelfCheck = {},
                    onOpenDevSeed = {},
                    vm = vm,
                )
            }
        }
    }

    private companion object {
        const val AUTO_EXPORT_LABEL = "每天自动导出一份"
        const val VISION_LABEL = "选完照片让 AI 先把名称、类别、季节填一遍，你再改"
    }
}
