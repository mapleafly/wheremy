# 补上两处没对上的缩进（来源弹窗第三选项 + 照片占位）。
import io
import sys

sys.stdout.reconfigure(errors="replace")
SRC = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app"

# 1) 来源弹窗
p = SRC + r"\ui\WheremyRoot.kt"
t = io.open(p, encoding="utf-8").read()
old = '''                title = { Text("这张照片从哪来？") },
                text = { Text("拍照就在 Wheremy 里取景，按下快门才拍；它只要相机权限，不进相册。") },'''
new = '''                title = { Text("这张照片从哪来？") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(WheremySpacing.xs)) {
                        Text("拍照就在 Wheremy 里取景，按下快门才拍；它只要相机权限，不进相册。")
                        // 「不拍照，手动填」（FR-SRC-09）：只在**新建**时给——
                        // 换照片的时候这条没有意义（那条记录本来就有照片）
                        if (backStack.lastOrNull() !is ReviewKey) {
                            TextButton(
                                onClick = {
                                    askingFor = PhotoTarget.NONE
                                    captureVm.startBlank()
                                },
                            ) { Text("不拍照，手动填") }
                        }
                    }
                },'''
if old in t:
    t = t.replace(old, new, 1)
    io.open(p, "w", encoding="utf-8", newline="").write(t)
    print("来源弹窗已加第三个选项")
else:
    print("!! 来源弹窗没匹配")

# 2) 照片占位
p = SRC + r"\ui\review\ReviewScreen.kt"
t = io.open(p, encoding="utf-8").read()
old = '''        }
        OutlinedButton(onClick = onPick) {
            Text(if (hasStoredItem) "换一张" else "选择照片")
        }'''
new = '''        } else {
            // 没照片是**正常形态**（FR-SRC-09），不是缺东西：说清"这条就是手填的"，再给补照片的路
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheremySpacing.photoBox)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("没有照片（手动填的条目）", style = MaterialTheme.typography.bodyMedium)
            }
        }
        OutlinedButton(onClick = onPick) {
            Text(
                when {
                    thumbPath == null -> "加一张照片"
                    hasStoredItem -> "换一张"
                    else -> "选择照片"
                },
            )
        }'''
if old in t:
    t = t.replace(old, new, 1)
    io.open(p, "w", encoding="utf-8", newline="").write(t)
    print("照片占位已加")
else:
    print("!! 照片占位没匹配")
