package com.wheremy.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// —— 颜色种子：沿用参照实现的设计基线（青绿 + 琥珀），DESIGN_SYSTEM.md §3 ——
val Teal40 = Color(0xFF00695C)
val Teal80 = Color(0xFF7FD6C5)
val Amber40 = Color(0xFF9A6A00)
val Amber80 = Color(0xFFFFD37A)

private val LightScheme = lightColorScheme(
    primary = Teal40,
    secondary = Amber40,
    tertiary = Teal40,
)

private val DarkScheme = darkColorScheme(
    primary = Teal80,
    secondary = Amber80,
    tertiary = Teal80,
)

/**
 * 间距与尺寸 token（DESIGN_SYSTEM.md §5）。
 * **界面里不许再出现字面 dp**——要新值先加到这里，再在文档里记一行。
 */
object WheremySpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp

    val touchTarget: Dp = 48.dp
    val thumb: Dp = 48.dp
    val photoBox: Dp = 180.dp
    val detailPhoto: Dp = 200.dp
    val gridMin: Dp = 156.dp

    val cornerSmall: Dp = 8.dp
    val cornerMedium: Dp = 10.dp
    val cornerLarge: Dp = 12.dp
}

@Composable
fun WheremyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
