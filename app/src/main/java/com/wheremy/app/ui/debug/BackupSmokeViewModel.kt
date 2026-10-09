package com.wheremy.app.ui.debug

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheremy.app.data.backup.BackupRepository
import com.wheremy.app.data.backup.ImportMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * 备份的**端到端冒烟**（开发期）：导出一次 → 立刻合并导入一次，把两边的数说清楚。
 *
 * 为什么要有它：R8 会把"只被反射用到的"东西裁掉，而备份这条路上有两处纯反射
 * （kotlinx.serialization 生成的 `$$serializer`、Room 3 的实体构造函数）。
 * 这两处在单元测试里永远绿（测试不跑 R8），只有**装一个 R8 出来的包**按一次才知道。
 *
 * 用法：`adb shell am start -n <包名>/com.wheremy.app.MainActivity --es dev backupcheck`
 * （包名用 `.r8smoke` 那个变体，别用正式包名）。
 */
@HiltViewModel
class BackupSmokeViewModel @Inject constructor(private val backup: BackupRepository) : ViewModel() {

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private fun say(line: String) = _lines.update { it + line }

    fun run() {
        viewModelScope.launch {
            val out = ByteArrayOutputStream()
            runCatching { withContext(Dispatchers.IO) { backup.exportTo(out) } }
                .onSuccess { summary ->
                    say("导出：位置 ${summary.locations} · 物品 ${summary.items} · 照片 ${summary.photos} · ${summary.bytes} 字节")
                }
                .onFailure { t ->
                    say("导出失败：${t::class.simpleName}: ${t.message}")
                    return@launch
                }

            val bytes = out.toByteArray()
            runCatching {
                withContext(Dispatchers.IO) {
                    backup.importFrom(ByteArrayInputStream(bytes), ImportMode.MERGE)
                }
            }.onSuccess { imported ->
                say("合并导入：位置 ${imported.locations} · 物品 ${imported.items} · 照片 ${imported.photos}（本机已有的会按主键跳过）")
                say("结论：R8 之后序列化与反序列化都活着。")
            }.onFailure { t ->
                say("导入失败：${t::class.simpleName}: ${t.message}")
            }
        }
    }

    private fun MutableStateFlow<List<String>>.update(block: (List<String>) -> List<String>) {
        value = block(value)
    }

    /** 留个口子给界面显示"当前用的是哪个包"，免得在手机上分不清装的是哪一个。 */
    fun packageName(context: Context): String = context.packageName
}
