package com.wheremy.app.ui.camera

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * 取景门禁三态（`UI.md` §2.1 / §8）。
 * 三态各对应那一屏的一句不同的话，判错就会出现"明明没授权却给了快门"。
 */
@RunWith(RobolectricTestRunner::class)
class CameraGateTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Robolectric 默认什么都不声明：先装作"这台机器有相机"，三态其余两条才测得到
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, true)
    }

    @Test
    fun `没授权时是 NeedsPermission`() {
        shadowOf(app).denyPermissions(Manifest.permission.CAMERA)
        assertEquals(CameraGate.NeedsPermission, cameraGate(app))
    }

    @Test
    fun `授权之后是 Ready`() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        assertEquals(CameraGate.Ready, cameraGate(app))
    }

    @Test
    fun `没有相机硬件的机器是 Unavailable`() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, false)
        assertEquals(CameraGate.Unavailable, cameraGate(app))
    }

    @Test
    fun `有硬件但没授权，判定的是授权而不是硬件`() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, true)
        shadowOf(app).denyPermissions(Manifest.permission.CAMERA)
        assertEquals(CameraGate.NeedsPermission, cameraGate(app))
    }
}
