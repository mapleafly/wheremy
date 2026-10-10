package com.wheremy.app.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.checkRoboAccessibility
import com.wheremy.app.ui.camera.CameraGate
import com.wheremy.app.ui.camera.CameraScreen
import com.wheremy.app.ui.design.DesignGalleryScreen
import com.wheremy.app.ui.theme.WheremyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 无障碍深扫（`FR-PRM-05` 剩的那一半）。
 *
 * 用 Android **ATF**（Accessibility Test Framework）那套检查规则跑在 JVM 上——
 * 和 Roborazzi 的 `roborazzi-accessibility-check` 拼起来，**不需要设备、进得了 CI**。
 *
 * 分两档用：
 * - [scanOnly]：只记录不判红（用来摸清现状、写进文档）；
 * - [mustPass]：**判红**，进 CI 当门禁——新增的可访问性问题会被挡在提交前。
 *
 * ⚠️ 检查的是"控件本身"：触摸目标尺寸、有没有可读的名字、对比度、重复的可点区域等。
 * 屏幕上的**逐字文案**与焦点次序不在这套规则里，那两条靠 `UI.md` 的约定与人工走查。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class AccessibilityScanTest {

    @get:Rule
    val compose = createComposeRule()

    private fun options(level: RoborazziATFAccessibilityChecker.CheckLevel) = RoborazziATFAccessibilityCheckOptions(
        checker = RoborazziATFAccessibilityChecker(),
        failureLevel = level,
    )

    /** 只记录（LogOnly）：把 ATF 认为有问题的地方打出来，不判红。 */
    private fun scanOnly() =
        compose.onRoot().checkRoboAccessibility(options(RoborazziATFAccessibilityChecker.CheckLevel.LogOnly))

    /** 判红：这一档进 CI。 */
    private fun mustPass() =
        compose.onRoot().checkRoboAccessibility(options(RoborazziATFAccessibilityChecker.CheckLevel.Error))

    @Test
    fun `取景屏三态都扫一遍`() {
        compose.setContent {
            WheremyTheme(darkTheme = false) {
                CameraScreen(
                    gate = CameraGate.NeedsPermission,
                    busy = false,
                    missed = false,
                    onRequestPermission = {},
                    onOpenSettings = {},
                    onUseAlbum = {},
                    onCancel = {},
                    onShot = {},
                    preview = {},
                )
            }
        }
        mustPass()
    }

    @Test
    fun `设计系统那一屏扫一遍`() {
        compose.setContent { WheremyTheme(darkTheme = false) { DesignGalleryScreen(onBack = {}) } }
        scanOnly()
    }
}
