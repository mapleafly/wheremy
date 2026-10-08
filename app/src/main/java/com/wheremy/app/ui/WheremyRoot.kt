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
import com.wheremy.app.ui.inbox.InboxScreen
import com.wheremy.app.ui.review.ReviewScreen
import kotlinx.serialization.Serializable

/** 目的地键（Nav3 要求 @Serializable）。 */
@Serializable
data object HomeKey : NavKey

/** 确认页带参数：要确认的那一件的 id。 */
@Serializable
data class ReviewKey(val itemId: String) : NavKey

@Serializable
data object InboxKey : NavKey

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
                    onOpenInbox = { backStack.add(InboxKey) },
                    onOpenReview = { itemId -> backStack.add(ReviewKey(itemId)) },
                )
            }
            entry<ReviewKey> {
                // 这一屏自己不碰导航栈：只把"办完了"写进 finished，怎么落地由外壳决定
                ReviewScreen(onDone = { backStack.removeLastOrNull() })
            }
            entry<InboxKey> {
                InboxScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenItem = { itemId -> backStack.add(ReviewKey(itemId)) },
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
