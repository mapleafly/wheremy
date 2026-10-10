package com.wheremy.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 开发期入口的门控（**F38**）。
 *
 * 为什么要有这一条：release 包里 `--es dev seed` 曾经**仍然可达**——路由注册与 extra 解析
 * 都没按 `BuildConfig.DEBUG` 门控。那些脚手架能写库、能改设置，不该出现在交付出去的包里。
 *
 * 单测跑在 debug 变体上，`BuildConfig.DEBUG` 恒为 true，所以这里**显式传** `debugBuild`，
 * 把 release 那一支也钉住。
 */
class DevRouteGateTest {

    @Test
    fun `debug 包里五个开发期入口都认`() {
        assertEquals(DataSelfCheckKey, devRouteFor("selfcheck", debugBuild = true))
        assertEquals(DesignGalleryKey, devRouteFor("design", debugBuild = true))
        assertEquals(DevSeedKey, devRouteFor("seed", debugBuild = true))
        assertEquals(BackupSmokeKey, devRouteFor("backupcheck", debugBuild = true))
        assertEquals(WordListSmokeKey, devRouteFor("words", debugBuild = true))
    }

    @Test
    fun `release 包里一个都不认`() {
        listOf("selfcheck", "design", "seed", "backupcheck", "words", null, "", "unknown").forEach { dev ->
            assertNull(
                "release 里 --es dev $dev 必须当成没有这个 extra",
                devRouteFor(dev, debugBuild = false),
            )
        }
    }

    @Test
    fun `名字不对就当没给（debug 里也不乱跳）`() {
        assertNull(devRouteFor(null, debugBuild = true))
        assertNull(devRouteFor("", debugBuild = true))
        assertNull(devRouteFor("seed2", debugBuild = true))
    }
}
