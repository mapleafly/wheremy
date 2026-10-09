package com.wheremy.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.wheremy.app.ui.capture.CaptureViewModel
import com.wheremy.app.ui.debug.DataSelfCheckScreen
import com.wheremy.app.ui.debug.DevSeedScreen
import com.wheremy.app.ui.design.DesignGalleryScreen
import com.wheremy.app.ui.detail.DetailScreen
import com.wheremy.app.ui.gallery.GalleryScreen
import com.wheremy.app.ui.review.ReviewScreen
import com.wheremy.app.ui.theme.WheremySpacing
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
fun WheremyRoot(devRoute: String? = null, captureVm: CaptureViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val start: NavKey = when (devRoute) {
        "selfcheck" -> DataSelfCheckKey
        "design" -> DesignGalleryKey
        "seed" -> DevSeedKey
        else -> GalleryKey
    }
    val backStack = remember { mutableStateListOf(start) }
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

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<GalleryKey> {
                GalleryScreen(
                    inboxMode = false,
                    onOpenItem = { itemId -> backStack.add(DetailKey(itemId)) },
                    onPickPhoto = launchPicker,
                    onOpenInbox = { switchTo(InboxKey) },
                    onOpenTree = { switchTo(TreeKey) },
                    onOpenSettings = { switchTo(SettingsKey) },
                )
            }
            entry<InboxKey> {
                GalleryScreen(
                    inboxMode = true,
                    onOpenItem = { itemId -> backStack.add(DetailKey(itemId)) },
                    onPickPhoto = launchPicker,
                    onOpenGallery = { switchTo(GalleryKey) },
                    onOpenTree = { switchTo(TreeKey) },
                    onOpenSettings = { switchTo(SettingsKey) },
                )
            }
            entry<TreeKey> {
                TreeScreen(
                    onOpenGallery = { switchTo(GalleryKey) },
                    onOpenInbox = { switchTo(InboxKey) },
                    onOpenSettings = { switchTo(SettingsKey) },
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
                SettingsPlaceholderScreen(
                    onBack = { backStack.removeLastOrNull() },
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

/** 设置页占位（真正的三节在 P5：到期提醒 / AI 分析照片 / 备份）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPlaceholderScreen(
    onBack: () -> Unit,
    onOpenDesignGallery: () -> Unit,
    onOpenDataSelfCheck: () -> Unit,
    onOpenDevSeed: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(WheremySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WheremySpacing.md),
        ) {
            Text(
                text = "设置页的三节（到期提醒 / AI 分析照片 / 备份）在 P5 实现。",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onOpenDesignGallery) { Text("看设计系统（开发期）") }
            OutlinedButton(onClick = onOpenDataSelfCheck) { Text("数据层自检（开发期）") }
            OutlinedButton(onClick = onOpenDevSeed) { Text("生成演示数据（开发期）") }
        }
    }
}
