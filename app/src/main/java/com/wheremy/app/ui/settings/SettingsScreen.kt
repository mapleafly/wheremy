package com.wheremy.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wheremy.app.data.settings.SettingsRepository
import com.wheremy.app.ui.theme.WheremySpacing

/**
 * 设置页（`UI.md` §7）。这一版两节：**到期提醒** + **AI 分析照片**（备份在 P5 后半）。
 *
 * 🔒 key 永不出现在备份包、留痕与日志里——这一节不许因为"方便"而加任何云端同步。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenDesignGallery: () -> Unit,
    onOpenDataSelfCheck: () -> Unit,
    onOpenDevSeed: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // 「回到前台就自己变」：系统设置里手动开关通知不走应用内的授权回调
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshNotifyProblem(context) }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            vm.consumeNotice()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(WheremySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WheremySpacing.md),
        ) {
            // —— 7.1 到期提醒 ——
            Text("到期提醒", style = MaterialTheme.typography.titleMedium)
            Card {
                Column(
                    modifier = Modifier.padding(WheremySpacing.md),
                    verticalArrangement = Arrangement.spacedBy(WheremySpacing.sm),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm),
                    ) {
                        OutlinedButton(
                            onClick = { vm.stepLeadDays(-1) },
                            enabled = state.leadDays > SettingsRepository.LEAD_DAYS_MIN,
                        ) { Text("−") }
                        Text(state.leadDaysLine, modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = { vm.stepLeadDays(+1) },
                            enabled = state.leadDays < SettingsRepository.LEAD_DAYS_MAX,
                        ) { Text("＋") }
                    }
                    LEAD_DAY_PRESETS.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm)) {
                            row.forEach { (label, days) ->
                                FilterChip(
                                    selected = state.leadDays == days,
                                    onClick = { vm.setLeadDays(days) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                    Text(
                        "到期提醒每天检查一次，几件物品也只发一条聚合通知。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = { vm.runDebugCheck(context) },
                        modifier = Modifier.testTag("settings_debug_check"),
                    ) { Text("调试：立刻检查一次到期提醒") }
                    state.debugResult?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("settings_debug_result"),
                        )
                    }
                    state.notifyProblem?.let { problem ->
                        Text(
                            problem,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        // 🔒 不就地申请权限（HyperOS 会静默驳回），两种理由共用这一颗按钮跳系统页
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            },
                        ) { Text("去系统设置打开") }
                    }
                }
            }

            // —— 7.2 AI 分析照片 ——
            Text("AI 分析照片", style = MaterialTheme.typography.titleMedium)
            Card {
                Column(
                    modifier = Modifier.padding(WheremySpacing.md),
                    verticalArrangement = Arrangement.spacedBy(WheremySpacing.sm),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "选完照片让 AI 先把名称、类别、季节填一遍，你再改",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(checked = state.visionEnabled, onCheckedChange = vm::setVisionEnabled)
                    }
                    Text(
                        state.visionStateLine,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("settings_vision_state"),
                    )
                    OutlinedTextField(
                        value = state.baseUrl,
                        onValueChange = vm::setBaseUrl,
                        label = { Text("接口地址 base_url") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.model,
                        onValueChange = vm::setModel,
                        label = { Text("模型名 model") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.apiKey,
                        onValueChange = vm::setApiKey,
                        label = { Text("API key") },
                        singleLine = true,
                        visualTransformation = if (state.showKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(onClick = vm::toggleShowKey) {
                                Text(if (state.showKey) "隐藏" else "显示")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm)) {
                        OutlinedButton(onClick = vm::saveAiConfig) { Text("保存 AI 配置") }
                        OutlinedButton(onClick = vm::clearApiKey) { Text("清空 key") }
                    }
                    Text("填完按保存才生效。", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "key 只存在这台手机的本地设置里：不进备份包，也不跟着任何云端同步走。换手机请先在旧机上把它清空，再重装。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // —— 开发期入口（产品正式版里会随 debug 工具一起摘掉） ——
            Card {
                Column(
                    modifier = Modifier.padding(WheremySpacing.md),
                    verticalArrangement = Arrangement.spacedBy(WheremySpacing.sm),
                ) {
                    Text("开发期工具", style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(onClick = onOpenDesignGallery) { Text("看设计系统（开发期）") }
                    OutlinedButton(onClick = onOpenDataSelfCheck) { Text("数据层自检（开发期）") }
                    OutlinedButton(onClick = onOpenDevSeed) { Text("生成演示数据（开发期）") }
                }
            }
        }
    }
}
