package com.wheremy.app.ui.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 取景要用的一点点 CameraX（`UI.md` §8）：**这一屏不认识 `PreviewView`**，
 * 预览面由外壳填进 `preview` 槽，`PreviewView` 与 `ImageCapture` 都活在这个会话里。
 */
class CameraSession(private val context: Context, private val lifecycleOwner: LifecycleOwner) {

    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private var provider: ProcessCameraProvider? = null
    private var capture: ImageCapture? = null

    /** 预览面。整场会话只有一个，重复 `AndroidView` 重组不会重建它。 */
    val previewView: PreviewView = PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    /** 绑上生命周期；失败就返回 false（门禁该判成"没有可用的相机"）。 */
    suspend fun start(): Boolean = runCatching {
        val future = ProcessCameraProvider.getInstance(context)
        val cameraProvider = suspendCancellableCoroutine { continuation ->
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { continuation.resume(it) }
                        .onFailure { continuation.resumeWithException(it) }
                },
                mainExecutor,
            )
        }
        provider = cameraProvider
        val imageCapture = ImageCapture.Builder()
            // 取景屏只用来"拍到就行"：交给 PhotoStore 去缩放与派生缩略图
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        capture = imageCapture
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        true
    }.getOrDefault(false)

    fun stop() {
        runCatching { provider?.unbindAll() }
        provider = null
        capture = null
    }

    /** 按下快门：拿到就是 JPEG 字节（读不出就是 null，界面照"没拍到"说）。 */
    suspend fun shootJpeg(): ByteArray? {
        val imageCapture = capture ?: return null
        return runCatching {
            suspendCancellableCoroutine { continuation ->
                imageCapture.takePicture(
                    mainExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bytes = image.use { proxy ->
                                proxy.planes.firstOrNull()?.buffer?.let { buffer ->
                                    ByteArray(buffer.remaining()).also { buffer.get(it) }
                                }
                            }
                            if (bytes == null || bytes.isEmpty()) {
                                continuation.resume(null)
                            } else {
                                continuation.resume(bytes)
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            continuation.resume(null)
                        }
                    },
                )
            }
        }.getOrNull()
    }
}

/** 预览槽：把会话里的 `PreviewView` 交给 Compose。 */
@Composable
fun CameraPreview(session: CameraSession, modifier: Modifier = Modifier) {
    AndroidView(factory = { session.previewView }, modifier = modifier)
}

/** 记住一个会话，并在离开组合时解开相机（不然别的应用拿不到相机）。 */
@Composable
fun rememberCameraSession(): CameraSession {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val session = remember(context, lifecycleOwner) { CameraSession(context.applicationContext, lifecycleOwner) }
    DisposableEffect(session) { onDispose { session.stop() } }
    return session
}
