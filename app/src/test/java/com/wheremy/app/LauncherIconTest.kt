package com.wheremy.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/**
 * 桌面图标的两条不变项（`DESIGN_SYSTEM.md` §267-268、`REBUILD.md` §6 第 6 条）。
 *
 * 1. **安全区**：前景矢量里每一个坐标到圆心 (54,54) 的距离 ≤ 33——画布 108×108，
 *    但任何蒙版下都保得住的只有正中间直径 66 的那个圆。三次贝塞尔落在控制点的凸包内、
 *    而圆是凸集，所以"控制点都在圆内"直接推出"描出来的曲线也在圆内"；
 *    为了让每个数字都是坐标，前景**只用 M / L / Z**。
 * 2. **背景色就是主题里的 Teal40**：图标与界面不同色，用户对不上号。
 *
 * 真机上还验过一次"抽屉里能认出它"（`artifacts/screenshots/P6/device-launcher-drawer.png`）。
 */
class LauncherIconTest {

    private val moduleDir = File("").absoluteFile

    private fun read(path: String): String {
        val file = File(moduleDir, path)
        assertTrue("找不到 $path（测试的工作目录应当是模块根）", file.isFile)
        return file.readText()
    }

    @Test
    fun `前景的每个坐标都在安全圆里`() {
        val xml = read("src/main/res/drawable/ic_launcher_foreground.xml")
        val paths = Regex("""android:pathData="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toList()
        assertTrue("前景里应当有图形", paths.isNotEmpty())

        val center = 54.0
        val safeRadius = 33.0
        var points = 0
        paths.forEach { data ->
            // 只用 M / L / Z：把数字每两个当一对坐标
            val numbers = Regex("""-?\d+(?:\.\d+)?""").findAll(data).map { it.value.toDouble() }.toList()
            assertEquals("这条路径的数字个数应当是偶数（每两个一组坐标）：$data", 0, numbers.size % 2)
            numbers.chunked(2).forEach { (x, y) ->
                val distance = hypot(x - center, y - center)
                assertTrue(
                    "坐标 ($x, $y) 到圆心 $distance > $safeRadius，蒙版下会被切掉",
                    distance <= safeRadius,
                )
                points++
            }
        }
        assertTrue("至少要检查到几个点", points >= 8)
    }

    @Test
    fun `背景色就是主题里的 Teal40`() {
        val background = read("src/main/res/values/ic_launcher_background.xml")
        assertTrue("背景色应当是 #00695C（Teal40）", background.contains("#00695C"))

        // 主题里那个值也在同一处写着；两边不一致时这里会红
        val theme = read("src/main/java/com/wheremy/app/ui/theme/Theme.kt")
        assertTrue("主题里应当有 Teal40 = 0xFF00695C", theme.contains("0xFF00695C"))
    }

    @Test
    fun `清单指向自适应图标`() {
        val manifest = read("src/main/AndroidManifest.xml")
        assertTrue("清单必须给出 android:icon", manifest.contains("""android:icon="@mipmap/ic_launcher""""))
        assertTrue(
            "v26 限定下应当有一份 adaptive-icon",
            File(moduleDir, "src/main/res/mipmap-anydpi-v26/ic_launcher.xml").isFile,
        )
        val adaptive = read("src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
        assertTrue("背景槽要有", adaptive.contains("<background"))
        assertTrue("前景槽要有", adaptive.contains("<foreground"))
    }
}
