package com.wheremy.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wheremy.app.data.backup.ImportMode
import com.wheremy.app.data.settings.SettingsRepository
import com.wheremy.app.ui.theme.WheremySpacing
import java.time.LocalDate

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
    var confirmOverwrite by remember { mutableStateOf(false) }

    // ⚠️ `pendingMode` 在回调的**第一行**就被清空：一次选择只兑现一次导入，重来必须重新点按钮。
    // 导入的 MIME 用通配（**不按 zip 收窄**）：选中的任何文件都交给读包阶段去判。
    var pendingMode by remember { mutableStateOf<ImportMode?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val mode = pendingMode
        pendingMode = null
        if (uri != null && mode != null) {
            vm.importBackup(mode) { context.contentResolver.openInputStream(uri) }
        }
    }
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            vm.exportBackup { context.contentResolver.openOutputStream(uri) }
        }
    }

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
                    // FR-TRC-04：累计用量（只报次数与 tokens，不折算成钱——价格表会变，说错钱比不说更糟）
                    Text(
                        text = state.aiUsageLine,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("ai_usage_line"),
                    )
                    Text(
                        "key 只存在这台手机的本地设置里：不进备份包，也不跟着任何云端同步走。换手机请先在旧机上把它清空，再重装。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // —— 7.3 备份 ——
            Text("备份", style = MaterialTheme.typography.titleMedium)
            Card {
                Column(
                    modifier = Modifier.padding(WheremySpacing.md),
                    verticalArrangement = Arrangement.spacedBy(WheremySpacing.sm),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm)) {
                        OutlinedButton(
                            onClick = { exporter.launch("wheremy-backup-${LocalDate.now()}.zip") },
                            enabled = !state.busy,
                        ) { Text("导出到文件") }
                        OutlinedButton(
                            onClick = {
                                pendingMode = ImportMode.MERGE
                                importer.launch(arrayOf("*/*"))
                            },
                            enabled = !state.busy,
                        ) { Text("合并导入") }
                        OutlinedButton(
                            // 「覆盖导入」不直接执行：先把确认框立起来
                            onClick = { confirmOverwrite = true },
                            enabled = !state.busy,
                        ) { Text("覆盖导入") }
                    }
                    if (state.busy) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Text("正在读写备份包…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    state.backupResult?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("settings_backup_result"),
                        )
                    }
                    Text("导出是一个 zip 包：位置树、物品、照片。存到你自己选的位置。")
                    Text("备份文件里不含 API key，也不含任何设置。", fontWeight = FontWeight.Bold)
                    Text("「合并导入」按主键跳过本机已经有的；「覆盖导入」先把本机清空，再按备份包重建。")
                    Text("应用已被系统设置为不做云备份（allowBackup=false），导出是你唯一的迁移手段。")
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

    if (confirmOverwrite) {
        AlertDialog(
            onDismissRequest = { confirmOverwrite = false },
            title = { Text("覆盖导入会清空本机数据") },
            text = {
                // 这句承重：它把"只保证库、照片文件层刻意不回滚"说全了，不许缩成"只替换数据"
                Text("先把现在的物品、位置和照片全部删掉，再按备份包重建。中途断开不会留下半个库，但清空之后就只能靠这个包了。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmOverwrite = false
                        pendingMode = ImportMode.OVERWRITE
                        importer.launch(arrayOf("*/*"))
                    },
                ) { Text("确定覆盖导入") }
            },
            dismissButton = { TextButton(onClick = { confirmOverwrite = false }) { Text("取消") } },
        )
    }
}
