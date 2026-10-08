package com.wheremy.app.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wheremy.app.data.repo.ItemSummary
import com.wheremy.app.ui.theme.WheremySpacing
import java.io.File

/**
 * 待归类（`UI.md` §3.4 第 2 句是这一屏的空态，逐字）。
 * 这一屏的完整形态（筛选区、单元菜单、搬到/删除框）属于 P3，这里先给列表与空态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(onBack: () -> Unit, onOpenItem: (String) -> Unit, vm: InboxViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("待归类") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (rows.isEmpty() && !loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(WheremySpacing.lg),
            ) {
                Text(
                    text = "没有待归类的物品，全都放好了。",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag("inbox_empty"),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(WheremySpacing.lg),
                verticalArrangement = Arrangement.spacedBy(WheremySpacing.md),
            ) {
                items(rows, key = { it.item.id }) { summary ->
                    InboxRow(summary = summary, onClick = { onOpenItem(summary.item.id) })
                }
            }
        }
    }
}

@Composable
private fun InboxRow(summary: ItemSummary, onClick: () -> Unit) {
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
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                // 空壳会以「未命名物品」出现在这里（RULES.md §1.4 记的是拍板过的现状）
                text = summary.item.name.ifBlank { "未命名物品" },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = summary.locationPath ?: "还没放好",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
