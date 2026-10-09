package com.wheremy.app.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.wheremy.app.ui.theme.WheremySpacing

/**
 * 取景屏（`UI.md` §8）。它是**盖在别的屏上面的叠层**，所以自己不透明。
 *
 * 四条不许动的：
 * 1. 背景不透明（`NeedsPermission`/`Unavailable` 两态没有预览面垫着）；
 * 2. 快门那颗**只在 `Ready` 时给**，且 `enabled = !busy`；
 * 3. 「改用相册选」**常驻**——权限被永久驳回时这是唯一出路；
 * 4. 「打开相机权限」那颗也**一直都在**（会不会被驳回不写死）。
 *
 * `missed` 是本地状态：不写库、不进 VM。
 */
@Composable
fun CameraScreen(
    gate: CameraGate,
    busy: Boolean,
    missed: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onUseAlbum: () -> Unit,
    onCancel: () -> Unit,
    onShot: () -> Unit,
    preview: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .testTag("camera_screen"),
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (gate) {
                CameraGate.Unavailable -> Text(
                    text = "这台机器没有可用的相机",
                    color = Color.White,
                    modifier = Modifier.testTag("camera_unavailable"),
                )

                CameraGate.NeedsPermission -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(WheremySpacing.md),
                ) {
                    Text("取景要用相机，先给它授权", color = Color.White)
                    // 进门时的运行时申请由外壳在 `LaunchedEffect(gate)` 里发起；这颗**一直都在**，
                    // 被驳回之后它就是唯一的路（`UI.md` §8 第 4 条）。
                    OutlinedButton(onClick = onOpenSettings) { Text("打开相机权限") }
                }

                CameraGate.Ready -> preview()
            }
        }

        if (missed) {
            Text(
                text = "没拍到，再按一次，或者改用相册",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(horizontal = WheremySpacing.lg)
                    .testTag("camera_missed"),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WheremySpacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("取消") }
            if (gate == CameraGate.Ready) {
                OutlinedButton(onClick = onShot, enabled = !busy) { Text("拍这张") }
            }
            // 常驻：权限被永久驳回时这是唯一出路
            TextButton(onClick = onUseAlbum) { Text("改用相册选") }
        }
    }
}
