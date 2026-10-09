package com.wheremy.app.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wheremy.app.ui.theme.WheremySpacing

/** 开发期演示数据的入口（`adb shell am start … --es dev seed`）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevSeedScreen(onDone: () -> Unit, vm: DevSeedViewModel = hiltViewModel()) {
    val status by vm.status.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.seed() }

    Scaffold(topBar = { TopAppBar(title = { Text("演示数据（开发期）") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(WheremySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WheremySpacing.md),
        ) {
            Text(status, style = MaterialTheme.typography.bodyLarge)
            Text(
                "演示数据都是合成照片，不碰相册；随时可以再跑一次（会先清掉上一次的种子）。",
                style = MaterialTheme.typography.bodySmall,
            )
            if (done) {
                OutlinedButton(onClick = onDone) { Text("去照片墙看看") }
            } else {
                TextButton(onClick = onDone) { Text("返回") }
            }
        }
    }
}
