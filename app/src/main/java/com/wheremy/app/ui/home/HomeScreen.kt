package com.wheremy.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wheremy.app.ui.capture.CaptureViewModel
import com.wheremy.app.ui.readJpeg
import com.wheremy.app.ui.theme.WheremySpacing

/**
 * 开发期的入口屏（真正的照片墙是 P3）。
 * P2 的闭环从这里开始：**选一张照片 → 立刻落库 → 打开确认页**。
 */
@Composable
fun HomeScreen(
    greeting: String,
    onOpenDesignGallery: () -> Unit,
    onOpenDataSelfCheck: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenReview: (String) -> Unit,
    vm: CaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val captured by vm.captured.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) readJpeg(context, uri)?.let(vm::capture)
    }

    LaunchedEffect(captured) {
        captured?.let { itemId ->
            vm.consumeCaptured()
            onOpenReview(itemId)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(WheremySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WheremySpacing.md),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag("home_greeting"),
            )
            Text(
                text = "P2：选一张照片就落库，然后进确认页。产品屏（照片墙/位置页）从 P3 起按切片补齐。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            ) { Text("选一张照片") }
            OutlinedButton(onClick = onOpenInbox) { Text("待归类") }
            OutlinedButton(onClick = onOpenDesignGallery) { Text("看设计系统") }
            OutlinedButton(onClick = onOpenDataSelfCheck) { Text("数据层自检（P1）") }
        }
    }
}
