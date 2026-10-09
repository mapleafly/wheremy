package com.wheremy.app.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * 取景屏的门禁（`UI.md` §2.1 / §8）。
 *
 * 三态决定那一屏给什么：`Unavailable` 说"这台机器没有可用的相机"、
 * `NeedsPermission` 给「取景要用相机，先给它授权」+「打开相机权限」、`Ready` 才给预览与快门。
 */
enum class CameraGate { Unavailable, NeedsPermission, Ready }

/**
 * 现算一次门禁。
 *
 * ⚠️ 系统设置里手动开关相机权限**不会**走应用内的授权回调，所以除了 `LaunchedEffect(gate)`，
 * 外壳还要在 `ON_RESUME` 时重读一次（`UI.md` §7.1 记的那两个独立观察者，不是一个）。
 */
fun cameraGate(context: Context): CameraGate = when {
    !context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) -> CameraGate.Unavailable
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ->
        CameraGate.NeedsPermission
    else -> CameraGate.Ready
}
