package com.wheremy.app.ui.tree

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wheremy.app.data.repo.ItemSummary
import com.wheremy.app.ui.theme.WheremySpacing
import java.io.File

/**
 * 位置页（`UI.md` §4）。
 *
 * 三条不许动的：**面包屑就是标题**（所以退一层只有「上一层」与系统返回两条路）；
 * **没有照片时只给选图那一步**（一张空位上不该出现"移除照片"）；
 * **边界那两行不给方向按钮**（点了什么都没发生，比少一个按钮更糟）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreeScreen(
    /** 位置照片走外壳那颗来源弹窗（拍照 / 相册） */
    onAskForLocationPhoto: () -> Unit,
    vm: TreeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // 每次进入本页（含从详情页返回）都刷新一遍
    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            vm.consumeNotice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.breadcrumb.isEmpty()) {
                            "全部位置"
                        } else {
                            state.breadcrumb.joinToString(" / ")
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (state.breadcrumb.isNotEmpty()) {
                        TextButton(onClick = vm::up) { Text("上一层") }
                    }
                    TextButton(onClick = vm::startCreate) { Text("新建") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(WheremySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WheremySpacing.sm),
        ) {
            // 1 位置照片区：只在非根层级
            if (!state.isRoot) {
                item {
                    LocationPhotoArea(
                        photoPath = state.locationPhotoPath,
                        onPick = onAskForLocationPhoto,
                        onClear = vm::clearPhoto,
                    )
                }
            }

            // 2 空态两句
            if (!state.loading && state.isEmpty) {
                item {
                    Text(
                        text = state.emptyText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("tree_empty"),
                    )
                }
            }

            // 3 子位置行
            items(state.rows, key = { it.node.id }) { row ->
                LocationRow(
                    row = row,
                    onOpen = { vm.open(row.node.id) },
                    onMenu = { vm.openMenu(row.node.id) },
                )
            }

            // 4 「这里的物品」+ 缩略行（非根层级且当前层有物品）
            if (!state.isRoot && state.itemsHere.isNotEmpty()) {
                item {
                    Text("这里的物品", style = MaterialTheme.typography.titleMedium)
                }
                items(state.itemsHere, key = { "item-${it.item.id}" }) { summary ->
                    ItemThumbRow(summary)
                }
            }

            // 5 常驻 80dp 空白
            item { Box(Modifier.height(80.dp)) }
        }
    }

    // —— 节点菜单：边界那两行不给方向按钮 ——
    val menuRow = state.rows.firstOrNull { it.node.id == state.menuFor }
    if (menuRow != null) {
        val index = state.rows.indexOfFirst { it.node.id == menuRow.node.id }
        AlertDialog(
            onDismissRequest = vm::closeMenu,
            title = { Text(menuRow.node.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(WheremySpacing.xs)) {
                    if (index > 0) {
                        TextButton(onClick = { vm.shift(menuRow.node.id, up = true) }) { Text("上移") }
                    }
                    if (index >= 0 && index < state.rows.lastIndex) {
                        TextButton(onClick = { vm.shift(menuRow.node.id, up = false) }) { Text("下移") }
                    }
                    TextButton(onClick = { vm.startEdit(menuRow.node.id) }) { Text("改名 / 设置类型") }
                    TextButton(onClick = { vm.askMove(menuRow.node.id) }) { Text("移动到…") }
                    TextButton(onClick = { vm.askDelete(menuRow.node.id) }) { Text("删除") }
                }
            },
            confirmButton = { TextButton(onClick = vm::closeMenu) { Text("关闭") } },
        )
    }

    state.moveFor?.let { id ->
        AlertDialog(
            onDismissRequest = vm::cancelMove,
            title = { Text("移动到") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { vm.move(id, null) }) { Text("移到根层级") }
                    state.moveOptions.forEach { option ->
                        TextButton(onClick = { vm.move(id, option.id) }) { Text(option.label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = vm::cancelMove) { Text("取消") } },
        )
    }

    state.pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text("删除「${state.pendingDeleteName}」？") },
            // 门槛在数据层，这句话只是提前说清规则
            text = { Text("只有空位置可以删除：子位置和物品需要先搬走。") },
            confirmButton = { TextButton(onClick = vm::confirmDelete) { Text("删除") } },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text("取消") } },
        )
    }

    if (state.editOpen) {
        EditLocationDialog(
            initial = state.editId?.let { id -> state.rows.firstOrNull { it.node.id == id }?.node },
            kinds = state.kinds,
            onSave = vm::saveEdit,
            onDismiss = vm::cancelEdit,
        )
    }
}

/** 位置照片区：**没有照片时只给选图那一步**。 */
@Composable
private fun LocationPhotoArea(photoPath: String?, onPick: () -> Unit, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(WheremySpacing.sm)) {
        if (photoPath != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(MaterialTheme.shapes.medium),
            ) {
                AsyncImage(
                    model = File(photoPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm)) {
                OutlinedButton(onClick = onPick) { Text("换一张") }
                OutlinedButton(onClick = onClear) { Text("移除照片") }
            }
        } else {
            OutlinedButton(onClick = onPick) { Text("给这个位置选一张照片") }
        }
    }
}

/** 节点行（`UI.md` §4.3）：缩略图（**只有真记过照片才画**）→ 名字 + 计数 → 「▸」→ ⋮。 */
@Composable
private fun LocationRow(row: LocationRowUi, onOpen: () -> Unit, onMenu: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        horizontalArrangement = Arrangement.spacedBy(WheremySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.thumbPath != null) {
            AsyncImage(
                model = File(row.thumbPath),
                // 无障碍：读屏说得出是哪个位置的图
                contentDescription = "${row.node.name}的位置照片",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(row.node.name, style = MaterialTheme.typography.titleMedium)
            val direct = "${row.directCount} 件"
            // 只有子树数与直属数不同才追加括号，否则会出现「3 件（含子位置 3 件）」
            val counts = if (row.subtreeCount != row.directCount) {
                "$direct（含子位置 ${row.subtreeCount} 件）"
            } else {
                direct
            }
            Text(
                text = listOfNotNull(row.node.kind, counts).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.hasChildren) Text("▸", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onMenu) {
            Icon(Icons.Filled.MoreVert, contentDescription = "位置操作")
        }
    }
}

@Composable
private fun ItemThumbRow(summary: ItemSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WheremySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (summary.thumbPath != null) {
            AsyncImage(
                model = File(summary.thumbPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(WheremySpacing.thumb)
                    .clip(MaterialTheme.shapes.small),
            )
        }
        Text(
            text = summary.item.name.ifBlank { "未命名物品" },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** 新建 / 编辑位置框（`UI.md` §4.7）：名称 → 类型（可选，也可自己填）。 */
@Composable
private fun EditLocationDialog(
    initial: com.wheremy.app.domain.LocationNode?,
    kinds: List<String>,
    onSave: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var kind by remember { mutableStateOf(initial?.kind.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建位置" else "编辑位置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(WheremySpacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("location_name"),
                )
                OutlinedTextField(
                    value = kind,
                    onValueChange = { kind = it },
                    // 类型是自由标签不是枚举，这四个字不能省
                    label = { Text("类型（可选，也可自己填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    kinds.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = if (kind == option) "" else option },
                            label = { Text(option) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            // 空名字就是按不动（参照实现两处做法不同，这里取"禁用"那一种）
            TextButton(
                onClick = { onSave(name.trim(), kind.trim().ifBlank { null }) },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
