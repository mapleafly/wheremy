package com.wheremy.app.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.ItemSummary
import com.wheremy.app.data.repo.SortOrder
import com.wheremy.app.ui.UiFormat
import com.wheremy.app.ui.theme.WheremySpacing
import com.wheremy.app.util.daysUntil
import java.io.File

/**
 * 照片墙 / 待归类（`UI.md` §3）。**同一个屏幕两种模式**：待归类就是 `inboxMode = true`。
 *
 * 逐字契约：顶栏标题、搜索框 label、三句空态、位置行为空时写「待归类」并用 error 色、
 * 菜单标题是那件的名字、删除框那句按"照片还有谁在用"分档。改文案要连测试一起改。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    inboxMode: Boolean = false,
    /** 矮窗口（手机横屏）：格子换成横排——竖排的 1:1 图 + 三行字在矮窗口里放不下（F21） */
    compactHeight: Boolean = false,
    showExpiringSoonOnly: Boolean = false,
    onOpenItem: (String) -> Unit,
    vm: GalleryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    // 每次进入本页都重读：别的屏（详情/位置页）可能刚搬走、删掉或覆盖导入过
    // ⚠️ 真机上踩到过：覆盖导入之后回到照片墙，墙上还是导入前那几件（库已经变了，视图没跟上）
    LaunchedEffect(Unit) { vm.refresh() }

    // 待归类页：标题 + **收件箱谓词**一起切（只改标题会把全库列出来——第一版就是这么错的）
    LaunchedEffect(inboxMode) { vm.setInboxMode(inboxMode) }

    // 从到期通知点进来：直接筛"快到期/已过期"，并把上一屏的筛选清掉
    LaunchedEffect(showExpiringSoonOnly) { if (showExpiringSoonOnly) vm.showExpiringSoonOnly() }

    // 列表在菜单打开期间被刷新（例如删除后重读）→ 旧 id 直接丢掉菜单状态，
    // 否则会拿旧名字配新数据
    val menuItem = state.rows.firstOrNull { it.item.id == state.menuFor }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (inboxMode) "待归类" else "照片墙") },
                actions = {
                    if (!inboxMode) {
                        AssistChip(
                            onClick = vm::toggleOnlyInbox,
                            label = { Text(if (state.filters.onlyInbox) "只看待归类 · 开" else "只看待归类") },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                label = { Text("搜名称、颜色、材质、品牌、备注等文字") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WheremySpacing.lg),
            )

            FilterBar(state = state, expanded = expanded, onToggleExpanded = { expanded = !expanded }, vm = vm)

            // FR-FND-07：`LIMIT 300` 截断发生在打分之前，所以"正好 300 条"很可能还有更多。
            // 界面必须把这件事说出来——不然用户会以为搜到的就是全部（`RULES.md` §15 W4）。
            if (state.capped) {
                Text(
                    text = "只显示了最近更新的 ${ItemRepository.SEARCH_LIMIT} 条，可能还有更多：" +
                        "再加一个词，或者用筛选取个范围。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = WheremySpacing.lg)
                        .testTag("capped_line"),
                )
            }

            when {
                state.narrowing && state.rows.isEmpty() -> EmptyState("按当前的搜索和筛选没找到物品，去掉几个条件再看看。", vm)
                state.inboxMode && state.rows.isEmpty() -> EmptyState("没有待归类的物品，全都放好了。", vm)
                state.rows.isEmpty() && !state.loading -> EmptyState("还没有物品。点右下角的按钮选第一张照片。", vm)
                else -> LazyVerticalGrid(
                    // 矮窗口里横排格子要更宽才放得下（缩略图 + 文字列）
                    columns = GridCells.Adaptive(
                        minSize = if (compactHeight) 260.dp else WheremySpacing.gridMin,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = WheremySpacing.md,
                        end = WheremySpacing.md,
                        top = WheremySpacing.sm,
                        // 底部给 FAB 让位（外壳那一颗现在贴的是内容区的右下角）
                        bottom = 88.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.rows, key = { it.item.id }) { summary ->
                        GalleryCell(
                            summary = summary,
                            horizontal = compactHeight,
                            onOpen = { onOpenItem(summary.item.id) },
                            onMenu = { vm.openMenu(summary.item.id) },
                        )
                    }
                }
            }
        }
    }

    if (menuItem != null) {
        AlertDialog(
            onDismissRequest = vm::closeMenu,
            title = { Text(menuItem.item.name.ifBlank { "未命名物品" }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(WheremySpacing.xs)) {
                    TextButton(onClick = { vm.askMove(menuItem.item.id) }) { Text("搬到…") }
                    TextButton(onClick = { vm.askDelete(menuItem.item.id) }) { Text("删除") }
                }
            },
            confirmButton = { TextButton(onClick = vm::closeMenu) { Text("关闭") } },
        )
    }

    state.moveFor?.let { itemId ->
        AlertDialog(
            onDismissRequest = vm::cancelMove,
            title = { Text("搬到") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // 默认那一条：把位置清空，退回待归类
                    TextButton(onClick = { vm.move(itemId, null) }) {
                        Text("暂不归类，先进「待归类」")
                    }
                    state.locationOptions.forEach { option ->
                        TextButton(onClick = { vm.move(itemId, option.id) }) { Text(option.label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = vm::cancelMove) { Text("取消") } },
        )
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
}

@Composable
private fun EmptyState(text: String, vm: GalleryViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WheremySpacing.lg),
        verticalArrangement = Arrangement.spacedBy(WheremySpacing.md),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("gallery_empty"))
        val hasFilters = vm.state.value.let { it.query.isNotBlank() || it.filters.category != null }
        if (hasFilters) {
            TextButton(onClick = vm::clearFilters) { Text("清掉条件") }
        }
    }
}

/**
 * 筛选区三层（`UI.md` §3.3）：12 颗分类芯片 → 一颗「更多筛选」→ 展开后
 * 「快到期/已过期」+ 季节 + `#标签`。
 * 展开那层**必须限高**：标签是全库累积的，芯片云不限高会把下面的网格压成一条缝。
 */
@Composable
private fun FilterBar(state: GalleryUiState, expanded: Boolean, onToggleExpanded: () -> Unit, vm: GalleryViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WheremySpacing.lg, vertical = WheremySpacing.sm),
        verticalArrangement = Arrangement.spacedBy(WheremySpacing.xs),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm),
        ) {
            state.categories.forEach { category ->
                FilterChip(
                    selected = state.filters.category == category,
                    onClick = { vm.toggleCategory(category) },
                    label = { Text(category) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm)) {
            FilterChip(
                selected = expanded,
                onClick = onToggleExpanded,
                label = { Text(state.moreFiltersLabel) },
                modifier = Modifier.testTag("more_filters"),
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(WheremySpacing.xs),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm)) {
                    // 到期条件排在季节、标签之前：它是从通知点进来时唯一开着的那一个
                    FilterChip(
                        selected = state.filters.expireUntil != null,
                        onClick = vm::toggleExpiring,
                        label = { Text("快到期/已过期") },
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm)) {
                    state.seasons.forEach { season ->
                        FilterChip(
                            selected = state.filters.season == season,
                            onClick = { vm.toggleSeason(season) },
                            label = { Text(season) },
                        )
                    }
                }
                // 排序（FR-FND-09）：只改顺序不改集合；再点当前那颗 = 回默认
                FlowRow(horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm)) {
                    SortOrder.entries.forEach { order ->
                        FilterChip(
                            selected = state.filters.sort == order,
                            onClick = { vm.setSort(order) },
                            label = { Text("排序：${order.label}") },
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm)) {
                    state.tags.forEach { tag ->
                        FilterChip(
                            selected = state.filters.tag == tag,
                            onClick = { vm.toggleTag(tag) },
                            label = { Text("#$tag") },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单元（`UI.md` §3.5）：图 1:1 → 名字 + ⋮ → 位置行 → 到期行。点图或名字进详情页。
 *
 * `horizontal = true` 是**矮窗口**（手机横屏）的形态：竖排的 1:1 图 + 三行字在矮窗口里会被裁掉，
 * 文字行根本看不见（F21）；横排把缩略图放左边、三行字放右边，同样高度里信息齐得多。
 */
@Composable
private fun GalleryCell(summary: ItemSummary, horizontal: Boolean = false, onOpen: () -> Unit, onMenu: () -> Unit) {
    val thumb: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier = modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onOpen),
        ) {
            if (summary.thumbPath != null) {
                AsyncImage(
                    model = File(summary.thumbPath),
                    // 无障碍：读屏要能说出这是哪一件；名字为空时不能给一个空描述
                    contentDescription = summary.item.name.ifBlank { "未命名物品" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // 没照片时拿名称占位：照片墙不会是一片空白格子
                Text(
                    text = summary.item.name.take(8).ifBlank { "未命名" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }

    val details: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = summary.item.name.ifBlank { "未命名物品" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen),
            )
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.MoreVert, contentDescription = "操作")
            }
        }

        // 位置为空时写「待归类」并用 error 色：留空等于说"这件没有位置是正常状态"
        val noLocation = summary.locationPath == null
        Text(
            text = summary.locationPath ?: "待归类",
            style = MaterialTheme.typography.bodySmall,
            color = if (noLocation) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        summary.item.expiresAt?.let { expiresAt ->
            val days = daysUntil(expiresAt)
            Text(
                text = UiFormat.expireHint(days),
                style = MaterialTheme.typography.bodySmall,
                color = if (days <= 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (horizontal) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm),
        ) {
            thumb(Modifier.size(WheremySpacing.thumb * 2))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(WheremySpacing.xs),
            ) { details() }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(WheremySpacing.xs)) {
            thumb(Modifier.fillMaxWidth().aspectRatio(1f))
            details()
        }
    }
}
