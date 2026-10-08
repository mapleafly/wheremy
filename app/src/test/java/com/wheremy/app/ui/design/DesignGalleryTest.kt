package com.wheremy.app.ui.design

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.wheremy.app.ui.theme.WheremyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 设计系统画廊的界面测试：证明 Robolectric + Compose 链路通了（PLAN.md §3.3）。 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class DesignGalleryTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theGalleryShowsEverySection() {
        compose.setContent {
            WheremyTheme { DesignGalleryScreen(onBack = {}) }
        }

        compose.onNodeWithText("设计系统画廊").assertIsDisplayed()

        // 画廊是可滚动的：每个小节先滚过去，再断言"看得见"
        listOf("颜色语义", "字号阶梯", "间距 token", "组件状态", "无障碍要求").forEach { section ->
            compose.onNodeWithText(section).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun theSearchFieldAcceptsInput() {
        compose.setContent {
            WheremyTheme { DesignGalleryScreen(onBack = {}) }
        }

        compose.onNodeWithTag("gallery_search").performTextInput("外套")
        compose.onNodeWithText("外套").assertIsDisplayed()
    }
}
