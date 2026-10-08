package com.wheremy.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wheremy.app.ui.theme.WheremySpacing

/**
 * DesignGallery：设计系统的"活样本"。
 *
 * 它不是一个用户功能，而是**开发期的验收面**：颜色语义、字号、间距、组件状态都在这里
 * 一次性看齐（PLAN.md §5 P0）。改设计系统就要改这里，并让截图回归立刻反映出来。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignGalleryScreen(onBack: () -> Unit) {
    var chipOn by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设计系统画廊") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(WheremySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WheremySpacing.lg),
        ) {
            Section("颜色语义") {
                Row(horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm)) {
                    Swatch("primary", MaterialTheme.colorScheme.primary)
                    Swatch("secondary", MaterialTheme.colorScheme.secondary)
                    Swatch("error", MaterialTheme.colorScheme.error)
                    Swatch("surfaceVariant", MaterialTheme.colorScheme.surfaceVariant)
                }
                Text(
                    text = "error 只表示「需要你处理」：位置为空、快到期、失败。换色等于改语义。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section("字号阶梯") {
                Text("titleLarge · 页面主标题", style = MaterialTheme.typography.titleLarge)
                Text("titleMedium · 卡片主标题", style = MaterialTheme.typography.titleMedium)
                Text("bodyMedium · 正文与字段", style = MaterialTheme.typography.bodyMedium)
                Text("bodySmall · 次要说明", style = MaterialTheme.typography.bodySmall)
                Text("labelLarge · 按钮与芯片", style = MaterialTheme.typography.labelLarge)
            }

            Section("间距 token") {
                val tokens = listOf(
                    "xs" to WheremySpacing.xs,
                    "sm" to WheremySpacing.sm,
                    "md" to WheremySpacing.md,
                    "lg" to WheremySpacing.lg,
                    "xl" to WheremySpacing.xl,
                )
                Column(verticalArrangement = Arrangement.spacedBy(WheremySpacing.xs)) {
                    tokens.forEach { (name, value) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(value)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Text(
                                text = "  $name = ${value.value.toInt()}dp",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Text(
                    text = "触控目标 ${WheremySpacing.touchTarget.value.toInt()}dp · " +
                        "缩略图 ${WheremySpacing.thumb.value.toInt()}dp · " +
                        "网格最小 ${WheremySpacing.gridMin.value.toInt()}dp",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section("组件状态") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = chipOn,
                        onClick = { chipOn = !chipOn },
                        label = { Text("已选") },
                    )
                    FilterChip(
                        selected = !chipOn,
                        onClick = { chipOn = !chipOn },
                        label = { Text("未选") },
                    )
                    AssistChip(onClick = {}, label = { Text("辅助") })
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索框（singleLine）") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gallery_search"),
                )
                Card {
                    Column(Modifier.padding(WheremySpacing.md)) {
                        Text("卡片单元", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "1:1 图片 + 名称 + 位置 + 到期行（P3 实现）。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Section("无障碍要求") {
                Text(
                    text = "每个可点元素要么有文字、要么有 contentDescription；触控目标不小于 48dp；" +
                        "字缩放 200% 不许破版。这三条进 P0 的截图与走查清单。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(WheremySpacing.sm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("section_$title"),
        )
        content()
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(WheremySpacing.touchTarget)
                .clip(RoundedCornerShape(WheremySpacing.cornerSmall))
                .background(color),
        )
        Text(text = name, style = MaterialTheme.typography.bodySmall)
    }
}
