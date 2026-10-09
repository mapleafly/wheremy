package com.wheremy.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wheremy.app.data.repo.ItemSummary
import com.wheremy.app.ui.UiFormat
import com.wheremy.app.ui.theme.WheremySpacing
import com.wheremy.app.util.daysUntil
import java.io.File

/**
 * 详情页（`UI.md` §5）。
 *
 * 两条不许动的：
 * 1. 顶栏三颗（编辑/搬到/删除）**全挂在 `summary != null`** 上，读取中/记录没了/读抛了都只有「返回」；
 * 2. 名称**不挂顶栏**（顶栏要容下四颗），它是正文第一行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(itemId: String, onBack: () -> Unit, onEdit: (String) -> Unit, vm: DetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(itemId) { vm.load(itemId) }

    // 删掉之后是这一屏自己退场：先 consumeDeleted() 再 onBack()
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            vm.consumeDeleted()
            onBack()
        }
    }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            vm.consumeNotice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("物品详情", maxLines = 1) },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    // 三颗全挂在 summary != null 上
                    if (state.summary != null) {
                        TextButton(onClick = { onEdit(itemId) }) { Text("编辑") }
                        TextButton(onClick = vm::askMove) { Text("搬到") }
                        TextButton(onClick = vm::askDelete) { Text("删除") }
                    }
                },
            )
        },
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
            when {
                state.loading -> Text("读取中…", modifier = Modifier.testTag("detail_loading"))
                state.missing -> Text("没有这条记录。", modifier = Modifier.testTag("detail_missing"))
                state.summary == null -> Text(
                    // 第三态兜住"读的时候抛了"，所以这句话要含糊到不会说谎
                    "这条记录读不出来，返回上一页再试一次。",
                    modifier = Modifier.testTag("detail_failed"),
                )
                else -> DetailBody(summary = state.summary!!, photoPaths = state.photoPaths)
            }
        }
    }

    state.pendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text("删除这件物品？") },
            text = { Text(pending.warning) },
            confirmButton = { TextButton(onClick = vm::confirmDelete) { Text("删除") } },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text("取消") } },
        )
    }

    if (state.moving) {
        AlertDialog(
            onDismissRequest = vm::cancelMove,
            title = { Text("搬到") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { vm.move(null) }) { Text("暂不归类，先进「待归类」") }
                    state.moveOptions.forEach { option ->
                        TextButton(onClick = { vm.move(option.id) }) { Text(option.label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = vm::cancelMove) { Text("取消") } },
        )
    }
}

@Composable
private fun DetailBody(summary: ItemSummary, photoPaths: List<String>) {
    // 1 名称
    Text(
        text = summary.item.name.ifBlank { "未命名物品" },
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.testTag("detail_name"),
    )

    // 2 照片横滚（200dp 宽 1:1、Crop）；没有照片就整块不给
    if (photoPaths.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm)) {
            items(photoPaths) { path ->
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.medium),
                ) {
                    AsyncImage(
                        model = File(path),
                        // 这里是一排照片（不是什么有名字的东西），给一句能听懂的说明
                        contentDescription = "物品照片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    // 3 位置卡：左边「位置」+ 面包屑（null 时写「待归类（还没放好）」并加粗 + error 色），右边 #tag 排
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WheremySpacing.md),
            horizontalArrangement = Arrangement.spacedBy(WheremySpacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("位置", style = MaterialTheme.typography.labelLarge)
                val noLocation = summary.locationPath == null
                Text(
                    text = summary.locationPath ?: "待归类（还没放好）",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (noLocation) FontWeight.Bold else FontWeight.Normal,
                    color = if (noLocation) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (summary.tags.isNotEmpty()) {
                Text(
                    text = summary.tags.joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // 4 字段卡：十行，次序不许变
    val item = summary.item
    Card {
        Column(modifier = Modifier.padding(WheremySpacing.md)) {
            FieldRow("分类", item.category)
            FieldRow("季节", item.seasons?.replace(",", "、"))
            // 颜色在材质前面：与 SearchTextBuilder 的次序一致
            FieldRow("颜色", item.color)
            FieldRow("材质", item.material)
            FieldRow("品牌", item.brand)
            FieldRow("别名", item.aliases)
            // 数量仅当 n≠1
            if (item.quantity != 1) FieldRow("数量", "x${item.quantity}")
            item.expiresAt?.let { expiresAt ->
                val kindSuffix = item.expireKind?.let { " · $it" }.orEmpty()
                FieldRow(
                    "到期",
                    "${UiFormat.formatDate(expiresAt)}（${UiFormat.expireHint(daysUntil(expiresAt))}）$kindSuffix",
                )
            }
            FieldRow("描述", item.description)
            FieldRow("备注", item.notes)
        }
    }

    // 5 更新于
    Text(
        text = "更新于 ${UiFormat.formatDate(item.updatedAt)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    HorizontalDivider()
}

/** 值为 `null` 或空白时**整行不给**：不许出现"品牌：（空）"。 */
@Composable
private fun FieldRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = WheremySpacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(64.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
