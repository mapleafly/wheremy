# FR-OPR-06 第三步：界面（长按进多选、选中标记、底部工具条、三个对话框、结果提示）。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app\ui\gallery\GalleryScreen.kt"
t = io.open(PATH, encoding="utf-8").read()

# —— 1) 卡片签名 + 选中标记 + 长按 ——
old_sig = "@Composable\nprivate fun GalleryCell(summary: ItemSummary, horizontal: Boolean = false, onOpen: () -> Unit, onMenu: () -> Unit) {"
new_sig = '''@Composable
private fun GalleryCell(
    summary: ItemSummary,
    horizontal: Boolean = false,
    /** 多选态（`FR-OPR-06`）：在选的时候点卡片 = 选中/取消，而不是进详情页。 */
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onOpen: () -> Unit,
    onToggle: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onMenu: () -> Unit,
) {'''
assert old_sig in t, "没找到 GalleryCell 签名"
t = t.replace(old_sig, new_sig, 1)

old_thumb = '''        Box(
            modifier = modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onOpen),
        ) {'''
new_thumb = '''        Box(
            modifier = modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                // 长按 = 进入多选并选上这一张（Android 的通用手势，不用额外的"选择"按钮）
                .combinedClickable(
                    onClick = { if (selectionMode) onToggle() else onOpen() },
                    onLongClick = onLongPress,
                )
                // 选中的视觉：外框 + 右上角勾，两个都给——只给颜色的话色弱用户看不出来
                .then(
                    if (selected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                    } else {
                        Modifier
                    },
                ),
        ) {'''
assert old_thumb in t, "没找到缩略图 Box"
t = t.replace(old_thumb, new_thumb, 1)

# 勾标记：塞在缩略图 Box 里（图片之后）
old_after_image = '''            } else {
                // 没照片时拿名称占位：照片墙不会是一片空白格子
                Text(
                    text = summary.item.name.take(8).ifBlank { "未命名" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }'''
new_after_image = '''            } else {
                // 没照片时拿名称占位：照片墙不会是一片空白格子
                Text(
                    text = summary.item.name.take(8).ifBlank { "未命名" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(WheremySpacing.xs)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
    }'''
assert old_after_image in t, "没找到缩略图内容收尾"
t = t.replace(old_after_image, new_after_image, 1)

# —— 2) 调用点 ——
old_call = '''                        GalleryCell(
                            summary = summary,
                            horizontal = compactHeight,
                            onOpen = { onOpenItem(summary.item.id) },
                            onMenu = { vm.openMenu(summary.item.id) },
                        )'''
new_call = '''                        GalleryCell(
                            summary = summary,
                            horizontal = compactHeight,
                            selectionMode = state.selectedIds.isNotEmpty(),
                            selected = summary.item.id in state.selectedIds,
                            onOpen = { onOpenItem(summary.item.id) },
                            onToggle = { vm.toggleSelection(summary.item.id) },
                            onLongPress = { vm.startSelection(summary.item.id) },
                            onMenu = { vm.openMenu(summary.item.id) },
                        )'''
assert old_call in t, "没找到 GalleryCell 调用点"
t = t.replace(old_call, new_call, 1)

# —— 3) 多选工具条：网格之后、Column 之内（贴底） ——
old_grid_end = '''                    items(state.rows, key = { it.item.id }) { summary ->'''
assert old_grid_end in t

# 在 when 块之后、Column 结束之前插入工具条：找 `            }\n        }\n    }\n\n    if (menuItem != null) {`
anchor_bar = '''        }
    }

    if (menuItem != null) {'''
bar = '''        }

        // —— 多选工具条（`FR-OPR-06`）：贴底，选中数量 + 三件事 + 取消 ——
        if (state.selectedIds.isNotEmpty()) {
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = WheremySpacing.lg,
                        vertical = WheremySpacing.sm,
                    ),
                    verticalArrangement = Arrangement.spacedBy(WheremySpacing.xs),
                ) {
                    Text("已选 ${state.selectedIds.size} 件", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm)) {
                        OutlinedButton(onClick = vm::askMoveSelection) { Text("搬到…") }
                        OutlinedButton(onClick = vm::askCategorySelection) { Text("改类别") }
                        OutlinedButton(onClick = vm::askDeleteSelection) { Text("删除") }
                        TextButton(onClick = vm::clearSelection) { Text("取消") }
                    }
                }
            }
        }
    }

    // 结果一句话（搬了几件/删了几件）：说清数量，不然用户不知道刚才那一下做了什么
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            vm.consumeNotice()
        }
    }

    if (menuItem != null) {'''
assert anchor_bar in t, "没找到插入工具条的位置"
t = t.replace(anchor_bar, bar, 1)

# —— 4) 三个对话框：挂在单件搬移对话框之后（文件尾部的对话框区） ——
# 找文件里最后一个对话框块：`state.moveFor?.let {` 的结束——直接用"批量删除确认"插在 `}` 之前不方便，
# 改为插在 `if (menuItem != null) {` 之前（与工具条同区），保证在 Scaffold 之后。
dialogs = '''    // 批量搬到（FR-OPR-06）：与单件那条路同一份位置清单
    if (state.moveSelectionOpen) {
        AlertDialog(
            onDismissRequest = vm::cancelMoveSelection,
            title = { Text("把 ${state.selectedIds.size} 件搬到") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { vm.confirmMoveSelection(null) }) { Text("暂不归类，先进「待归类」") }
                    state.locationOptions.forEach { option ->
                        TextButton(onClick = { vm.confirmMoveSelection(option.id) }) { Text(option.label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = vm::cancelMoveSelection) { Text("取消") } },
        )
    }

    // 批量改类别（FR-OPR-06）：清单读生效词表，和确认页那颗芯片同源
    if (state.categorySelectionOpen) {
        AlertDialog(
            onDismissRequest = vm::cancelCategorySelection,
            title = { Text("把 ${state.selectedIds.size} 件的类别改成") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    state.categories.forEach { category ->
                        TextButton(onClick = { vm.confirmCategorySelection(category) }) { Text(category) }
                    }
                    TextButton(onClick = { vm.confirmCategorySelection(null) }) { Text("清空类别") }
                }
            },
            confirmButton = { TextButton(onClick = vm::cancelCategorySelection) { Text("取消") } },
        )
    }

    // 批量删除确认（FR-OPR-06）：那句话在 VM 里按"两类照片分开数"算好，这里只显示
    state.pendingBatchDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = vm::cancelDeleteSelection,
            title = { Text("删除这 ${pending.itemIds.size} 件？") },
            text = { Text(pending.warning) },
            confirmButton = { TextButton(onClick = vm::confirmDeleteSelection) { Text("删除") } },
            dismissButton = { TextButton(onClick = vm::cancelDeleteSelection) { Text("取消") } },
        )
    }

    if (menuItem != null) {'''
t = t.replace("    if (menuItem != null) {", dialogs, 1)

# —— 5) snackbar host + import ——
old_scaffold = '''    Scaffold(
        topBar = {'''
new_scaffold = '''    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {'''
assert old_scaffold in t, "没找到 Scaffold"
t = t.replace(old_scaffold, new_scaffold, 1)

imports = [
    ("import androidx.compose.foundation.combinedClickable", "import androidx.compose.foundation.clickable"),
    ("import androidx.compose.foundation.border", "import androidx.compose.foundation.background"),
    ("import androidx.compose.foundation.shape.CircleShape", "import androidx.compose.foundation.shape.RoundedCornerShape"),
    ("import androidx.compose.material.icons.Icons", "import androidx.compose.material3.AlertDialog"),
    ("import androidx.compose.material.icons.filled.CheckCircle", "import androidx.compose.material.icons.Icons"),
    ("import androidx.compose.material3.Icon", "import androidx.compose.material3.IconButton"),
    ("import androidx.compose.material3.SnackbarHost", "import androidx.compose.material3.Surface"),
    ("import androidx.compose.material3.SnackbarHostState", "import androidx.compose.material3.SnackbarHost"),
    ("import androidx.compose.runtime.remember", "import androidx.compose.runtime.remember"),
]
for imp, after in imports:
    if imp not in t:
        if after in t:
            t = t.replace(after, imp + "\n" + after, 1)
        else:
            print("  (import 锚点缺失：%s)" % after)

io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("界面已加多选态、工具条、三个对话框与结果提示")
