# 工具条真机布局修正：4 颗按钮在 411dp 宽屏上放不下，且右下角的 FAB 会盖住「取消」。
# 改法：按钮行用 FlowRow（放不下就换行）+ 给 FAB 留出尾部空间。
import io
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app\ui\gallery\GalleryScreen.kt"
t = io.open(PATH, encoding="utf-8").read()

old_pad = """                    modifier = Modifier.padding(
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
new_pad = """                    // 尾部给 FAB 留位：真机上「取消」曾被右下角那颗 + 盖住（F43）
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
assert old_pad in t, "没找到工具条内容"
t = t.replace(old_pad, new_pad, 1)
io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("工具条布局已修（FlowRow + 给 FAB 留位）")
