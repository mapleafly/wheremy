package com.wheremy.app.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wheremy.app.data.repo.CATEGORIES
import com.wheremy.app.data.repo.EXPIRE_KINDS
import com.wheremy.app.data.repo.SEASONS
import com.wheremy.app.ui.UiFormat
import com.wheremy.app.ui.capture.CaptureViewModel
import com.wheremy.app.ui.theme.WheremySpacing
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    /** 要打开/编辑的那一件；外壳把它一路带进来（`UI.md` §2.7 的三分派之一） */
    itemId: String?,
    onDone: () -> Unit,
    /** 外壳那一格的显影槽：录入页开着时换一张走它（同一个 itemId，填过的字段都留着） */
    captureVm: CaptureViewModel,
    /** 「换一张」也走外壳那颗来源弹窗（拍照 / 相册），与 FAB 同一个收口 */
    onReplacePhoto: () -> Unit,
    vm: ReviewViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val options by vm.locationOptions.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var locationDialog by remember { mutableStateOf(false) }
    var expireDialog by remember { mutableStateOf(false) }
    var addTagText by remember { mutableStateOf("") }

    // ⚠️ 少了这一步就是"顶栏画出来了、正文一片空白"：照片刚落库、itemId 已经在手上，
    // 但槽位要靠 start(itemId) 读出来（真机上踩到过）
    LaunchedEffect(itemId) { itemId?.let(vm::start) }

    LaunchedEffect(captureVm) {
        captureVm.replacements.collect { vm.replacePhoto(it) }
    }

    LaunchedEffect(Unit) { vm.loadLocationOptions() }

    // 关页只有一格：先 consumeFinished 再 onDone（UI.md §6.7）
    LaunchedEffect(state.finished) {
        if (state.finished) {
            vm.consumeFinished()
            onDone()
        }
    }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            vm.consumeNotice()
        }
    }

    val cur = state.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("确认物品信息") },
                navigationIcon = {
                    IconButton(onClick = vm::requestExit) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                },
                actions = {
                    TextButton(
                        onClick = vm::keepForLater,
                        enabled = cur?.itemId != null,
                    ) { Text("稍后处理") }
                    TextButton(
                        onClick = vm::save,
                        enabled = state.canSave,
                    ) { Text(if (state.slots.size > 1) "保存这一件" else "保存") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (cur == null) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(WheremySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WheremySpacing.md),
        ) {
            if (state.slots.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm)) {
                    state.slots.forEachIndexed { index, slot ->
                        FilterChip(
                            selected = index == state.selected,
                            onClick = { /* 切件在 P2b 接多件录入时接上 */ },
                            label = { Text(slot.chipLabel(index)) },
                        )
                    }
                }
                if (cur.itemId == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "这一件还没保存",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = vm::discardCurrent) { Text("不要这件") }
                    }
                }
            }

            PhotoArea(
                thumbPath = cur.thumbPath,
                hasStoredItem = state.slots.any { it.itemId != null },
                onPick = onReplacePhoto,
            )

            AiStatusLine(state = state, onRetry = vm::retryAnalysis)

            OutlinedTextField(
                value = cur.draft.name,
                onValueChange = vm::setName,
                label = { Text("名称 *") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("field_name"),
            )

            ChipRow("分类") {
                CATEGORIES.forEach { category ->
                    FilterChip(
                        selected = cur.draft.category == category,
                        onClick = { vm.setCategory(if (cur.draft.category == category) null else category) },
                        label = { Text(category) },
                    )
                }
            }

            ChipRow("季节") {
                SEASONS.forEach { season ->
                    FilterChip(
                        selected = season in cur.draft.seasons,
                        onClick = { vm.toggleSeason(season) },
                        label = { Text(season) },
                    )
                }
            }

            OutlinedTextField(
                value = cur.draft.color.orEmpty(),
                onValueChange = vm::setColor,
                label = { Text("颜色") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = cur.draft.material.orEmpty(),
                onValueChange = vm::setMaterial,
                label = { Text("材质") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = cur.draft.brand.orEmpty(),
                onValueChange = vm::setBrand,
                label = { Text("品牌") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = cur.draft.aliases.joinToString("、"),
                onValueChange = { raw ->
                    vm.setAliases(
                        raw.split('、', '，', ',')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() },
                    )
                },
                label = { Text("别名（顿号分隔，方便日后搜到）") },
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(WheremySpacing.xs)) {
                Text("标签（点一下删除）", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    cur.draft.tags.forEach { tag ->
                        AssistChip(onClick = { vm.toggleTag(tag) }, label = { Text(tag) })
                    }
                }
                AddTagField(
                    text = addTagText,
                    onTextChange = { addTagText = it },
                    onAdd = {
                        if (addTagText.isNotBlank()) {
                            vm.toggleTag(addTagText)
                            addTagText = ""
                        }
                    },
                )
            }

            OutlinedTextField(
                value = cur.draft.description.orEmpty(),
                onValueChange = vm::setDescription,
                label = { Text("描述") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = cur.draft.notes.orEmpty(),
                onValueChange = vm::setNotes,
                label = { Text("备注（只有你自己看得到的）") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("数量", modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.setQuantity(cur.draft.quantity - 1) }) { Text("−") }
                Text("${cur.draft.quantity}", modifier = Modifier.testTag("quantity"))
                TextButton(onClick = { vm.setQuantity(cur.draft.quantity + 1) }) { Text("＋") }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("放在哪里", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = cur.locationPath ?: "未选择",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedButton(onClick = { locationDialog = true }) { Text("选择") }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("到期提醒", style = MaterialTheme.typography.labelLarge)
                    Text(text = cur.expireLabel(), style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = { expireDialog = true }) { Text("设置") }
            }

            HorizontalDivider()
        }
    }

    if (locationDialog) {
        LocationPickerDialog(
            title = "选择位置",
            options = options,
            onPick = {
                vm.setLocation(it)
                locationDialog = false
            },
            onDismiss = { locationDialog = false },
        )
    }

    if (expireDialog) {
        ExpireDialog(
            initial = cur?.draft?.expiresAt,
            initialKind = cur?.draft?.expireKind,
            onConfirm = { millis, kind ->
                vm.setExpire(millis, kind)
                expireDialog = false
            },
            onClear = {
                vm.setExpire(null, null)
                expireDialog = false
            },
            onDismiss = { expireDialog = false },
        )
    }

    if (state.confirmExit) {
        ExitDialog(state = state, vm = vm)
    }
}

/** 芯片文字：名字空 → 「第 N 件」；没走完的前面加「● 」。 */
private fun ReviewSlot.chipLabel(index: Int): String {
    val label = draft.name.ifBlank { "第 ${index + 1} 件" }
    return if (saved) label else "● $label"
}

private fun ReviewSlot.expireLabel(): String {
    val expiresAt = draft.expiresAt ?: return "未设置"
    // 整句由 UiFormat 生成：照片墙、详情页、这里三处用的是同一句
    return com.wheremy.app.ui.UiFormat.expireLine(draft.expireKind, expiresAt)
}

@Composable
private fun PhotoArea(thumbPath: String?, hasStoredItem: Boolean, onPick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(WheremySpacing.sm)) {
        if (thumbPath != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheremySpacing.photoBox)
                    .clip(MaterialTheme.shapes.medium),
            ) {
                coil3.compose.AsyncImage(
                    model = File(thumbPath),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        OutlinedButton(onClick = onPick) {
            Text(if (hasStoredItem) "换一张" else "选择照片")
        }
    }
}

/**
 * AI 状态行（`UI.md` §6.4）：分析中一句话 + 进度条；失败给那一条文案 + 「重新分析」；
 * 都没事时位置话**三句互斥**（文案由 `UiFormat` 一处生成）。
 *
 * 🔒 那一句必须带 `Modifier.weight(1f)`：不给权重会在 Row 里先按整行宽度被量走，
 * 「重新分析」剩 0 宽竖成一列——真机上的形状 bug（批次 AI）。
 */
@Composable
private fun AiStatusLine(state: ReviewUiState, onRetry: () -> Unit) {
    val failureText = UiFormat.failureText(state.failure)
    when {
        state.analyzing -> {
            Column(verticalArrangement = Arrangement.spacedBy(WheremySpacing.xs)) {
                Text("AI 正在看照片，可以先自己填")
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        failureText != null -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = failureText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                // 照片没落库时没有可重跑的对象，按下去不会有任何反应
                if (state.canRetry) {
                    TextButton(onClick = onRetry) { Text("重新分析") }
                }
            }
        }

        state.guess != null -> {
            // 三句话互斥（`UiFormat.locationGuessLine`）：光说"AI 猜的位置：橱柜"会被读成
            // "它给了建议而界面没用"——用户就是这么报的 bug（批次 Z）
            val outcome = state.guessOutcome
            if (outcome != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = UiFormat.locationGuessLine(state.guess, outcome),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.canRetry) {
                        TextButton(onClick = onRetry) { Text("重新分析") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(WheremySpacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

@Composable
private fun AddTagField(text: String, onTextChange: (String) -> Unit, onAdd: () -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text("加标签") },
        singleLine = true,
        trailingIcon = { TextButton(onClick = onAdd) { Text("＋") } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onAdd() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LocationPickerDialog(
    title: String,
    options: List<LocationOption>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(WheremySpacing.xs),
            ) {
                if (options.isEmpty()) {
                    Text("还没有位置。先去「位置」页建一个。")
                } else {
                    options.forEach { option ->
                        TextButton(onClick = { onPick(option.id) }) { Text(option.path) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpireDialog(
    initial: Long?,
    initialKind: String?,
    onConfirm: (Long, String?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(initialKind) }
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    // UTC 零点毫秒换回本地零点：不换算会差一天（Dates.kt 的注释）
                    val millis = picker.selectedDateMillis?.let { utcMillisToLocalStartOfDay(it) }
                    if (millis != null) onConfirm(millis, kind)
                },
                enabled = picker.selectedDateMillis != null,
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onClear) { Text("清除") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(WheremySpacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm)) {
                EXPIRE_KINDS.forEach { option ->
                    FilterChip(
                        selected = kind == option,
                        onClick = { kind = option },
                        label = { Text(option) },
                    )
                }
            }
            DatePicker(state = picker)
        }
    }
}

/** 退出确认框：两支（`UI.md` §6.6 / `RULES.md` §6.2、§6.3）。 */
@Composable
private fun ExitDialog(state: ReviewUiState, vm: ReviewViewModel) {
    val branchOne = state.notStored > 0
    AlertDialog(
        onDismissRequest = vm::cancelExit,
        title = {
            Text(
                when {
                    branchOne -> "还有物品没保存，确定离开？"
                    state.leftBehind > 0 -> "还没保存，确定离开？"
                    else -> "改动还没保存，确定离开？"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(WheremySpacing.xs)) {
                if (branchOne) {
                    Text("还有 ${state.notStored} 件没保存，离开后它们不会留下。")
                    if (state.leftBehind > 0) {
                        Text("另外 ${state.leftBehind} 件已经在库里，会以草稿留在「待归类」。")
                    }
                } else {
                    if (state.leftBehind > 0) {
                        Text("照片和 AI 填的信息已经在库里了，状态还是草稿，会出现在「待归类」。")
                    }
                    if (state.alreadySaved > 0) {
                        Text("库里那 ${state.alreadySaved} 件是已经存好的，离开不会改动它们。")
                    }
                    Text("但你手打的这些改动还没写进去，不保存就会丢。")
                }
            }
        },
        confirmButton = {
            // 支一故意不给「保存改动并留下」：那颗只写当前一件，在这一支里兑现不了
            if (branchOne) {
                TextButton(onClick = vm::cancelExit) { Text("继续编辑") }
            } else {
                TextButton(onClick = vm::exitKeepingEdits) { Text("保存改动并留下") }
            }
        },
        dismissButton = {
            TextButton(onClick = vm::exitWithoutSaving) { Text("不保存，直接离开") }
        },
    )
}

/** UTC 零点毫秒 → 本地零点毫秒（唯一实现在 `util/Dates.kt`）。 */
private fun utcMillisToLocalStartOfDay(utcMillis: Long): Long =
    com.wheremy.app.util.utcMillisToLocalStartOfDay(utcMillis)
