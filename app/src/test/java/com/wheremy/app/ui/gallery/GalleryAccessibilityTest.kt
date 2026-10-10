package com.wheremy.app.ui.gallery

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
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
import kotlinx.coroutines.runBlocking
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
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class GalleryAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `照片墙有物品时也过 ATF 的 Error 档`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
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

        compose.onRoot().checkRoboAccessibility(
            RoborazziATFAccessibilityCheckOptions(
                checker = RoborazziATFAccessibilityChecker(),
                failureLevel = RoborazziATFAccessibilityChecker.CheckLevel.Error,
            ),
        )

        db.close()
    }
}
