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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wheremy.app.ui.theme.WheremySpacing

/**
 * 备份冒烟页（开发期）：进门自动跑一次导出 + 合并导入，把结果逐行写出来。
 * 用途见 [BackupSmokeViewModel]：**验证 R8 之后反射那条路还活着**。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSmokeScreen(onBack: () -> Unit, vm: BackupSmokeViewModel = hiltViewModel()) {
    val lines by vm.lines.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.run() }

    Scaffold(topBar = { TopAppBar(title = { Text("备份冒烟（开发期）") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(WheremySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WheremySpacing.sm),
        ) {
            Text("包名：${vm.packageName(context)}", style = MaterialTheme.typography.bodySmall)
            lines.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("backup_smoke_line"),
                )
            }
            OutlinedButton(onClick = onBack) { Text("返回") }
        }
    }
}
