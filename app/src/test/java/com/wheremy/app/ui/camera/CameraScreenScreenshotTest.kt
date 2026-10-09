package com.wheremy.app.ui.camera

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
 * 取景屏的截图基线（`PLAN.md` P6 的"截图回归覆盖两套主题与三种窗口宽度"第一批）。
 * 记录：`./gradlew :app:recordRoborazziDebug`；校验：`./gradlew :app:verifyRoborazziDebug`。
 *
 * 三张分别钉住三态里最要紧的两态 + 矮窗口（手机横屏）那一版：
 * 换布局时这几张会红，逼着人去看"是不是把快门或相册出路挤没了"。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(gate: CameraGate, missed: Boolean = false) {
        compose.setContent {
            WheremyTheme(darkTheme = false) {
                CameraScreen(
                    gate = gate,
                    busy = false,
                    missed = missed,
                    onRequestPermission = {},
                    onOpenSettings = {},
                    onUseAlbum = {},
                    onCancel = {},
                    onShot = {},
                    preview = {},
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun cameraNeedsPermissionPortrait() {
        show(CameraGate.NeedsPermission)
        compose.onRoot().captureRoboImage("src/test/screenshots/camera-needs-permission-portrait.png")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp")
    fun cameraReadyLandscape() {
        show(CameraGate.Ready)
        compose.onRoot().captureRoboImage("src/test/screenshots/camera-ready-landscape.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun cameraUnavailableWithMissedLine() {
        show(CameraGate.Unavailable, missed = true)
        compose.onRoot().captureRoboImage("src/test/screenshots/camera-unavailable-missed.png")
    }
}
