package com.wheremy.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.wheremy.app.reminder.ReminderNotifier
import com.wheremy.app.ui.camera.CameraGate
import com.wheremy.app.ui.camera.CameraPreview
import com.wheremy.app.ui.camera.CameraScreen
import com.wheremy.app.ui.camera.cameraGate
import com.wheremy.app.ui.camera.rememberCameraSession
import com.wheremy.app.ui.capture.CaptureViewModel
import com.wheremy.app.ui.debug.DataSelfCheckScreen
import com.wheremy.app.ui.debug.DevSeedScreen
import com.wheremy.app.ui.design.DesignGalleryScreen
import com.wheremy.app.ui.detail.DetailScreen
import com.wheremy.app.ui.gallery.GalleryScreen
import com.wheremy.app.ui.review.ReviewScreen
import com.wheremy.app.ui.settings.SettingsScreen
import com.wheremy.app.ui.tree.TreeScreen
import com.wheremy.app.ui.tree.TreeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * 照片的两个来源（`UI.md` §2.5）。`NONE` 表示没有在等照片。
 */
private enum class PhotoTarget { NONE, ITEM, LOCATION }

/**
 * 外壳（`UI.md` §2）：照片墙是起点，「待归类」是同一个屏幕的 `inboxMode`。
 *
 * 这里还管三件横跨多屏的事：**照片来源弹窗**、**取景屏叠层**、以及 `deliverPhoto` 这个唯一收口。
 */
@Composable
fun WheremyRoot(
    devRoute: String? = null,
    /** 通知点进来的落点：`expiring` → 筛过"快到期/已过期"的照片墙 */
    entry: String? = null,
    captureVm: CaptureViewModel = hiltViewModel(),
    // 位置照片要从外壳这条路送进去，所以位置页的 VM 提到这一层
    treeVm: TreeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val start: NavKey = when (devRoute) {
        "selfcheck" -> DataSelfCheckKey
        "design" -> DesignGalleryKey
        "seed" -> DevSeedKey
        else -> GalleryKey
    }
    val backStack = remember { mutableStateListOf(start) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // —— 取景屏 / 来源弹窗的状态（`UI.md` §2.5、§2.6）——
    var askingFor by remember { mutableStateOf(PhotoTarget.NONE) }
    var viewfinderOpen by remember { mutableStateOf(false) }
    var photoTarget by remember { mutableStateOf(PhotoTarget.NONE) }
    var missed by remember { mutableStateOf(false) }
    var shotBusy by remember { mutableStateOf(false) }
    var gate by remember { mutableStateOf(cameraGate(context)) }

    // 系统设置里手动开关相机权限不会走应用内的授权回调 → 回到前台重读一次
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { gate = cameraGate(context) }

    val session = rememberCameraSession()
    LaunchedEffect(viewfinderOpen, gate) {
        if (viewfinderOpen && gate == CameraGate.Ready) {
            if (!session.start()) gate = CameraGate.Unavailable
        } else {
            session.stop()
        }
    }

    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        gate = cameraGate(context)
    }

    // 进门先问一次相机权限（`UI.md` §8）；被驳回之后屏上那颗「打开相机权限」跳系统页
    LaunchedEffect(viewfinderOpen) {
        if (viewfinderOpen && cameraGate(context) == CameraGate.NeedsPermission) {
            askCamera.launch(Manifest.permission.CAMERA)
        }
    }

    // 从通知点进来（可能应用已经在别的屏上）：先回照片墙，再由它把"快到期/已过期"筛上
    LaunchedEffect(entry) {
        if (entry == ReminderNotifier.ENTRY_EXPIRING) {
            backStack.clear()
            backStack.add(GalleryKey)
        }
    }
    val captured by captureVm.captured.collectAsStateWithLifecycle()
    val captureError by captureVm.error.collectAsStateWithLifecycle()

    /**
     * `deliverPhoto` 是**唯一收口**（`UI.md` §2.4）：相册与相机两条来源回到这里，没有第二条路。
     */
    fun deliverPhoto(bytes: ByteArray?, target: PhotoTarget) {
        // 归零发生在最前面，于是 photoTarget 的读法保持字面意思
        photoTarget = PhotoTarget.NONE
        if (bytes == null || bytes.isEmpty()) {
            scope.launch { snackbar.showSnackbar("读不到这张照片，换一张试试") }
            return
        }
        when {
            target == PhotoTarget.LOCATION -> treeVm.setPhoto(bytes)
            // 录入页开着时换一张：同一个 itemId，填过的字段都留着
            backStack.lastOrNull() is ReviewKey -> captureVm.replaceWith(bytes)
            else -> captureVm.capture(bytes)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        // uri == null 是用户取消：只清那一格，不教训一句"换一张试试"
        if (uri != null) {
            // 选到的可能是几 MB 原图，读字节放 IO
            val target = photoTarget
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { readJpeg(context, uri) }
                deliverPhoto(bytes, target)
            }
        } else {
            photoTarget = PhotoTarget.NONE
        }
    }

    fun startAlbum(target: PhotoTarget) {
        photoTarget = target
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun openViewfinder(target: PhotoTarget) {
        photoTarget = target
        missed = false
        gate = cameraGate(context)
        viewfinderOpen = true
    }

    fun closeViewfinder() {
        viewfinderOpen = false
        photoTarget = PhotoTarget.NONE
        missed = false
    }

    // 底栏那颗 FAB = 全应用唯一的新增入口：先问来源
    val askForPhotoSource = { askingFor = PhotoTarget.ITEM }

    LaunchedEffect(captured) {
        captured?.let { itemId ->
            captureVm.consumeCaptured()
            backStack.add(ReviewKey(itemId))
        }
    }
    LaunchedEffect(captureError) {
        captureError?.let {
            captureVm.consumeError()
            snackbar.showSnackbar(it)
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

    Box(modifier = Modifier.fillMaxSize()) {
        // 返回键优先级（`UI.md` §2.3）：取景屏最先接。
        // ⚠️ 不能只靠 NavDisplay 的 onBack：它只在栈深 > 1 时才拦，而取景屏叠在栈底那一屏上——
        // 真机上按返回直接把应用退了（还露出了底下的应用）。这里自己接一层。
        BackHandler(enabled = viewfinderOpen) { closeViewfinder() }

        NavDisplay(
            backStack = backStack,
            // 返回键优先级（`UI.md` §2.3）：取景屏最先接，其余交给栈
            onBack = {
                if (viewfinderOpen) {
                    closeViewfinder()
                } else {
                    backStack.removeLastOrNull()
                }
            },
            entryProvider = entryProvider {
                entry<GalleryKey> {
                    GalleryScreen(
                        inboxMode = false,
                        showExpiringSoonOnly = entry == ReminderNotifier.ENTRY_EXPIRING,
                        onOpenItem = { itemId -> backStack.add(DetailKey(itemId)) },
                        onPickPhoto = askForPhotoSource,
                        onOpenInbox = { switchTab(InboxKey) },
                        onOpenTree = { switchTab(TreeKey) },
                        onOpenSettings = { switchTab(SettingsKey) },
                    )
                }
                entry<InboxKey> {
                    GalleryScreen(
                        inboxMode = true,
                        onOpenItem = { itemId -> backStack.add(DetailKey(itemId)) },
                        onPickPhoto = askForPhotoSource,
                        onOpenGallery = { switchTab(GalleryKey) },
                        onOpenTree = { switchTab(TreeKey) },
                        onOpenSettings = { switchTab(SettingsKey) },
                    )
                }
                entry<TreeKey> {
                    TreeScreen(
                        // 位置照片走同一颗来源弹窗（`PhotoTarget.LOCATION`）
                        onAskForLocationPhoto = { askingFor = PhotoTarget.LOCATION },
                        onOpenGallery = { switchTab(GalleryKey) },
                        onOpenInbox = { switchTab(InboxKey) },
                        onOpenSettings = { switchTab(SettingsKey) },
                        vm = treeVm,
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
                    ReviewScreen(
                        itemId = it.itemId,
                        onDone = { backStack.removeLastOrNull() },
                        captureVm = captureVm,
                        onReplacePhoto = askForPhotoSource,
                    )
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

        // ⚠️ 这里**不能**再放一个 Scaffold：它自带不透明底色，会把下面的 NavDisplay 整个遮住（真机白屏踩过）
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))

        // 取景屏叠在别的屏之上（`UI.md` §1.1）
        if (viewfinderOpen) {
            CameraScreen(
                gate = gate,
                busy = shotBusy,
                missed = missed,
                onRequestPermission = { askCamera.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                onUseAlbum = {
                    // 沿用这一张的目标
                    val target = photoTarget
                    closeViewfinder()
                    startAlbum(target)
                },
                onCancel = { closeViewfinder() },
                onShot = {
                    shotBusy = true
                    scope.launch {
                        val bytes = session.shootJpeg()
                        shotBusy = false
                        if (bytes == null || bytes.isEmpty()) {
                            missed = true
                        } else {
                            // 不复用 closeViewfinder：先关掉取景屏再送照片，否则读不出这张给谁
                            val target = photoTarget
                            closeViewfinder()
                            deliverPhoto(bytes, target)
                        }
                    }
                },
                preview = { CameraPreview(session, modifier = Modifier.fillMaxSize()) },
            )
        }

        // 照片来源弹窗（`UI.md` §2.5）：两个入口共用这一颗
        if (askingFor != PhotoTarget.NONE) {
            val target = askingFor
            AlertDialog(
                onDismissRequest = { askingFor = PhotoTarget.NONE },
                title = { Text("这张照片从哪来？") },
                text = { Text("拍照就在 Wheremy 里取景，按下快门才拍；它只要相机权限，不进相册。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            askingFor = PhotoTarget.NONE
                            openViewfinder(target)
                        },
                    ) { Text("拍照") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            askingFor = PhotoTarget.NONE
                            startAlbum(target)
                        },
                    ) { Text("从相册选") }
                },
            )
        }
    }
}
