# 工具条布局修正（按文件里真实的 24 空格缩进）；并取消真机上的删除对话框。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app\ui\gallery\GalleryScreen.kt"
t = io.open(PATH, encoding="utf-8").read()

old = """                    Column(
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
                    }"""
new = """                    Column(
                        // 尾部给 FAB 留位：真机上「取消」被右下角那颗 + 盖住过（F43）
                        modifier = Modifier.padding(
                            start = WheremySpacing.lg,
                            end = 88.dp,
                            top = WheremySpacing.sm,
                            bottom = WheremySpacing.sm,
                        ),
                        verticalArrangement = Arrangement.spacedBy(WheremySpacing.xs),
                    ) {
                        Text("已选 ${state.selectedIds.size} 件", style = MaterialTheme.typography.titleSmall)
                        // FlowRow 而不是 Row：四颗按钮在窄屏上放不下，Row 会把「取消」挤出屏幕
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(WheremySpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(WheremySpacing.xs),
                        ) {
                            OutlinedButton(onClick = vm::askMoveSelection) { Text("搬到…") }
                            OutlinedButton(onClick = vm::askCategorySelection) { Text("改类别") }
                            OutlinedButton(onClick = vm::askDeleteSelection) { Text("删除") }
                            TextButton(onClick = vm::clearSelection) { Text("取消") }
                        }
                    }"""
if old in t:
    t = t.replace(old, new, 1)
    io.open(PATH, "w", encoding="utf-8", newline="").write(t)
    print("工具条布局已修")
else:
    print("!! 没匹配到（缩进可能又变了）")
