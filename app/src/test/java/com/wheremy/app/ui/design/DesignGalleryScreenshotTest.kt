package com.wheremy.app.ui.design

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.wheremy.app.ui.theme.WheremyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 截图回归的第一条基线（PLAN.md §3.3 第 8 步）。
 * 记录：`./gradlew :app:recordRoborazziDebug`；校验：`./gradlew :app:verifyRoborazziDebug`。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class DesignGalleryScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun designGalleryLightBaseline() {
        compose.setContent {
            WheremyTheme(darkTheme = false) { DesignGalleryScreen(onBack = {}) }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/design-gallery-light.png")
    }

    @Test
    fun designGalleryDarkBaseline() {
        compose.setContent {
            WheremyTheme(darkTheme = true) { DesignGalleryScreen(onBack = {}) }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/design-gallery-dark.png")
    }
}
