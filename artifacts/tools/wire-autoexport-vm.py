# SettingsViewModel：自动导出的状态、开关、选目录、立刻导出。
import io
import re
import sys

sys.stdout.reconfigure(errors="replace")
PATH = r"D:\src\dsh\wheremy\app\src\main\java\com\wheremy\app\ui\settings\SettingsViewModel.kt"
t = io.open(PATH, encoding="utf-8").read()

# 1) 状态字段
anchor = "    /** 备份那一次的结果那句话（五种收场之一） */\n    val backupResult: String? = null,"
fields = '''    /** 备份那一次的结果那句话（五种收场之一） */
    val backupResult: String? = null,
    /** 自动导出（`FR-BAK-08`）：开关、落点、上次结果。 */
    val autoExportEnabled: Boolean = false,
    val autoExportUri: String = "",
    val autoExportLastAt: Long = 0L,
    val autoExportLastResult: String = "",
    val autoExportBusy: Boolean = false,'''
assert anchor in t, "没找到 backupResult 字段"
t = t.replace(anchor, fields, 1)

# 2) init 里回填
anchor2 = "                        aiCalls = saved.aiCallCount,\n                        aiTokens = saved.aiTokens,"
assert anchor2 in t, "没找到 aiCalls 回填"
t = t.replace(
    anchor2,
    anchor2
    + "\n                        autoExportEnabled = saved.autoExportEnabled,"
    + "\n                        autoExportUri = saved.autoExportUri,"
    + "\n                        autoExportLastAt = saved.autoExportLastAt,"
    + "\n                        autoExportLastResult = saved.autoExportLastResult,",
    1,
)

# 3) 派生文案 + 三个动作（挂在 consumeBackupResult 之后）
anchor3 = "    fun consumeBackupResult() = _state.update { it.copy(backupResult = null) }"
assert anchor3 in t, "没找到 consumeBackupResult"
extra = anchor3 + '''

    // —— 自动导出（FR-BAK-08）——

    /** 保存位置那行：没选时说清"要先选一个"，选了就把 URI 的尾巴露出来给人认。 */
    val autoExportUriLine: String
        get() = if (autoExportUri.isBlank()) {
            "保存位置：还没选。"
        } else {
            "保存位置：" + autoExportUri.substringAfterLast('%').takeLast(60)
        }

    /** 上次结果那行：**成功失败都说人话**（自动导出失败而用户不知道 = 没有备份）。 */
    val autoExportLine: String
        get() = when {
            autoExportLastAt == 0L -> "还没自动导出过。"
            else -> "上次（" + java.time.Instant.ofEpochMilli(autoExportLastAt)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("M月d日 HH:mm")) + "）：" + autoExportLastResult
        }

    fun setAutoExportEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setAutoExportEnabled(enabled)
            if (enabled && autoExportUri.isBlank()) {
                _state.update { it.copy(notice = "先点「选择保存位置」挑一个文件夹，不然每天到点也没地方写。") }
            }
        }
    }

    fun setAutoExportUri(uri: String) {
        viewModelScope.launch { settings.setAutoExportUri(uri) }
    }

    /** 选目录时如果持久化授权失败，也要说出来——不持久化的话重启后就写不进去了。 */
    fun notePersistFailed(message: String?) {
        _state.update { it.copy(notice = "目录授权没能持久化（" + (message ?: "系统拒绝") + "），重启后可能要重新选一次。") }
    }

    /** 「立刻导出一份」：与定时那一份走**同一段**逻辑（`AutoExporter.runOnce`）。 */
    fun exportNow() {
        viewModelScope.launch {
            _state.update { it.copy(autoExportBusy = true) }
            val message = runCatching { exporter.runOnce() }.getOrElse { it.message ?: "导出失败" }
            _state.update { it.copy(autoExportBusy = false, notice = message) }
        }
    }'''
t = t.replace(anchor3, extra, 1)

# 4) 构造参数注入 AutoExporter
anchor4 = "    private val backup: BackupRepository,"
assert anchor4 in t, "没找到 backup 构造参数"
t = t.replace(anchor4, anchor4 + "\n    private val exporter: com.wheremy.app.data.backup.AutoExporter,", 1)

io.open(PATH, "w", encoding="utf-8", newline="").write(t)
print("SettingsViewModel 已更新")
