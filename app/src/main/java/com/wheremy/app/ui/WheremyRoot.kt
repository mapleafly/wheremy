package com.wheremy.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.wheremy.app.reminder.ReminderNotifier
import com.wheremy.app.ui.capture.CaptureViewModel
import com.wheremy.app.ui.debug.DataSelfCheckScreen
import com.wheremy.app.ui.debug.DevSeedScreen
import com.wheremy.app.ui.design.DesignGalleryScreen
import com.wheremy.app.ui.detail.DetailScreen
import com.wheremy.app.ui.gallery.GalleryScreen
import com.wheremy.app.ui.review.ReviewScreen
import com.wheremy.app.ui.settings.SettingsScreen
import com.wheremy.app.ui.tree.TreeScreen
import kotlinx.serialization.Serializable

/** 目的地键（Nav3 要求 @Serializable）。 */
@Serializable
data object GalleryKey : NavKey

@Serializable
data object InboxKey : NavKey

@Serializable
data class ReviewKey(val itemId: String) : NavKey

@Serializable
data class DetailKey(val itemId: String) : NavKey

@Serializable
data object SettingsKey : NavKey

/** 位置页（`UI.md` §4）：树在内存里建，面包屑就是标题。 */
@Serializable
data object TreeKey : NavKey

/** 开发期：生成/清空演示数据（合成照片，不碰相册）。 */
@Serializable
data object DevSeedKey : NavKey

@Serializable
data object DesignGalleryKey : NavKey

@Serializable
data object DataSelfCheckKey : NavKey

/**
 * 外壳（`UI.md` §2）：照片墙是起点，「待归类」是同一个屏幕的 `inboxMode`。
 * 开发期的两个脚手架（设计系统、数据层自检）挂在「设置」占位页下面，不污染产品界面；
 * 也可以用 `adb shell am start -n …/.MainActivity --es dev selfcheck` 直接拉起来。
 */
@Composable
fun WheremyRoot(
    devRoute: String? = null,
    /** 通知点进来的落点：`expiring` → 筛过"快到期/已过期"的照片墙 */
    entry: String? = null,
    captureVm: CaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val start: NavKey = when (devRoute) {
        "selfcheck" -> DataSelfCheckKey
        "design" -> DesignGalleryKey
        "seed" -> DevSeedKey
        else -> GalleryKey
    }
    val backStack = remember { mutableStateListOf(start) }

    // 从通知点进来（可能应用已经在别的屏上）：先回照片墙，再由它把"快到期/已过期"筛上
    LaunchedEffect(entry) {
        if (entry == ReminderNotifier.ENTRY_EXPIRING) {
            backStack.clear()
            backStack.add(GalleryKey)
        }
    }
    val captured by captureVm.captured.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) readJpeg(context, uri)?.let(captureVm::capture)
    }
    val launchPicker = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        Unit
    }

    LaunchedEffect(captured) {
        captured?.let { itemId ->
            captureVm.consumeCaptured()
            backStack.add(ReviewKey(itemId))
        }
    }

    fun switchTo(key: NavKey) {
        backStack.clear()
        backStack.add(key)
    }

    /**
     * 底栏页签之间的切换：**照片墙永远留在栈底**。
     *
     * ⚠️ 用 `clear()+add()` 会把照片墙也清掉，于是"在设置页按返回"直接退出应用（真机上踩到过）。
     * 正确形状是：把当前页签换掉，照片墙还在下面——返回键于是自然回到照片墙。
     */
    fun switchTab(key: NavKey) {
        while (backStack.size > 1) backStack.removeLastOrNull()
        if (key != GalleryKey && backStack.lastOrNull() != key) backStack.add(key)
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<GalleryKey> {
                GalleryScreen(
                    inboxMode = false,
                    showExpiringSoonOnly = entry == ReminderNotifier.ENTRY_EXPIRING,
                    onOpenItem = { itemId -> backStack.add(DetailKey(itemId)) },
                    onPickPhoto = launchPicker,
                    onOpenInbox = { switchTab(InboxKey) },
                    onOpenTree = { switchTab(TreeKey) },
                    onOpenSettings = { switchTab(SettingsKey) },
                )
            }
            entry<InboxKey> {
                GalleryScreen(
                    inboxMode = true,
                    onOpenItem = { itemId -> backStack.add(DetailKey(itemId)) },
                    onPickPhoto = launchPicker,
                    onOpenGallery = { switchTab(GalleryKey) },
                    onOpenTree = { switchTab(TreeKey) },
                    onOpenSettings = { switchTab(SettingsKey) },
                )
            }
            entry<TreeKey> {
                TreeScreen(
                    onOpenGallery = { switchTab(GalleryKey) },
                    onOpenInbox = { switchTab(InboxKey) },
                    onOpenSettings = { switchTab(SettingsKey) },
                )
            }
            entry<DetailKey> {
                // 格子上点一下进的是详情页；详情页自己没有可编辑字段，「编辑」才进确认页
                DetailScreen(
                    itemId = it.itemId,
                    onBack = { backStack.removeLastOrNull() },
                    onEdit = { itemId -> backStack.add(ReviewKey(itemId)) },
                )
            }
            entry<ReviewKey> {
                // 这一屏自己不碰导航栈：只把"办完了"写进 finished，怎么落地由外壳决定
                ReviewScreen(onDone = { backStack.removeLastOrNull() })
            }
            entry<SettingsKey> {
                SettingsScreen(
                    onOpenDesignGallery = { backStack.add(DesignGalleryKey) },
                    onOpenDataSelfCheck = { backStack.add(DataSelfCheckKey) },
                    onOpenDevSeed = { backStack.add(DevSeedKey) },
                )
            }
            entry<DesignGalleryKey> {
                DesignGalleryScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<DataSelfCheckKey> {
                DataSelfCheckScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<DevSeedKey> {
                DevSeedScreen(onDone = { switchTo(GalleryKey) })
            }
        },
    )
}
