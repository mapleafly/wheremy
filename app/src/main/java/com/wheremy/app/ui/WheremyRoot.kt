package com.wheremy.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.wheremy.app.ui.debug.DataSelfCheckScreen
import com.wheremy.app.ui.design.DesignGalleryScreen
import com.wheremy.app.ui.home.HomeScreen
import kotlinx.serialization.Serializable

/** 目的地键（Nav3 要求 @Serializable）。P0/P1 只有三屏，P2 起逐步补齐产品屏。 */
@Serializable
data object HomeKey : NavKey

@Serializable
data object DesignGalleryKey : NavKey

/** 开发期脚手架：P1 的数据层自检（P2 起被真正的照片墙取代）。 */
@Serializable
data object DataSelfCheckKey : NavKey

@Composable
fun WheremyRoot(greeting: String) {
    val backStack = remember { mutableStateListOf<NavKey>(HomeKey) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<HomeKey> {
                HomeScreen(
                    greeting = greeting,
                    onOpenDesignGallery = { backStack.add(DesignGalleryKey) },
                    onOpenDataSelfCheck = { backStack.add(DataSelfCheckKey) },
                )
            }
            entry<DesignGalleryKey> {
                DesignGalleryScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<DataSelfCheckKey> {
                DataSelfCheckScreen(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
