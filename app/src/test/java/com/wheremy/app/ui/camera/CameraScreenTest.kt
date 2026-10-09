package com.wheremy.app.ui.camera

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * 取景屏三态与四条铁律（`UI.md` §8）。
 *
 * 这一屏的错误代价很高：`NeedsPermission` 时给了快门 = 用户按下去什么都没发生；
 * 少了「改用相册选」 = 权限被永久驳回的人没有出路。
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(
        gate: CameraGate,
        busy: Boolean = false,
        missed: Boolean = false,
        onShot: () -> Int = { 0 },
        onUseAlbum: () -> Int = { 0 },
        onOpenSettings: () -> Int = { 0 },
        onCancel: () -> Int = { 0 },
        onRequestPermission: () -> Int = { 0 },
    ) {
        rule.setContent {
            CameraScreen(
                gate = gate,
                busy = busy,
                missed = missed,
                onRequestPermission = { onRequestPermission() },
                onOpenSettings = { onOpenSettings() },
                onUseAlbum = { onUseAlbum() },
                onCancel = { onCancel() },
                onShot = { onShot() },
                preview = {},
            )
        }
    }

    @Test
    fun `没有相机时说这台机器没有可用的相机`() {
        show(CameraGate.Unavailable)

        rule.onNodeWithText("这台机器没有可用的相机").assertIsDisplayed()
        rule.onNodeWithText("拍这张").assertDoesNotExist()
        // 常驻的那两颗
        rule.onNodeWithText("改用相册选").assertIsDisplayed()
        rule.onNodeWithText("取消").assertIsDisplayed()
    }

    @Test
    fun `没授权时给授权入口与相册出路`() {
        var settings = 0
        var album = 0
        show(CameraGate.NeedsPermission, onOpenSettings = { settings++ }, onUseAlbum = { album++ })

        rule.onNodeWithText("取景要用相机，先给它授权").assertIsDisplayed()
        rule.onNodeWithText("打开相机权限").performClick()
        rule.onNodeWithText("改用相册选").performClick()

        assertEquals(1, settings)
        assertEquals(1, album)
        rule.onNodeWithText("拍这张").assertDoesNotExist()
    }

    @Test
    fun `Ready 才给快门`() {
        var shots = 0
        show(CameraGate.Ready, onShot = { shots++ })

        rule.onNodeWithText("拍这张").assertIsDisplayed().performClick()
        assertEquals(1, shots)
        // 有预览与快门时，授权入口就不该再挡着
        rule.onNodeWithText("打开相机权限").assertDoesNotExist()
    }

    @Test
    fun `忙的时候快门按不动`() {
        show(CameraGate.Ready, busy = true)
        rule.onNodeWithText("拍这张").assertIsNotEnabled()
    }

    @Test
    fun `不忙的时候快门按得动`() {
        show(CameraGate.Ready, busy = false)
        rule.onNodeWithText("拍这张").assertIsEnabled()
    }

    @Test
    fun `没拍到之前没有那一行`() {
        show(CameraGate.Ready, missed = false)
        rule.onNodeWithText("没拍到，再按一次，或者改用相册").assertDoesNotExist()
    }

    @Test
    fun `没拍到才给那一行`() {
        show(CameraGate.Ready, missed = true)
        rule.onNodeWithTag("camera_missed").assertIsDisplayed()
    }
}
