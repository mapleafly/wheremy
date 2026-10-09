package com.wheremy.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheremy.app.data.backup.BackupFormatException
import com.wheremy.app.data.backup.BackupRepository
import com.wheremy.app.data.backup.BackupSummary
import com.wheremy.app.data.backup.ImportMode
import com.wheremy.app.data.backup.formatBytes
import com.wheremy.app.data.repo.CATEGORIES
import com.wheremy.app.data.repo.EXPIRE_KINDS
import com.wheremy.app.data.repo.LOCATION_KINDS
import com.wheremy.app.data.repo.SEASONS
import com.wheremy.app.data.settings.SettingsRepository
import com.wheremy.app.data.settings.WordListKind
import com.wheremy.app.data.settings.WordLists
import com.wheremy.app.reminder.ReminderNotifier
import com.wheremy.app.reminder.ReminderRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 八颗预设（`UI.md` §7.1）：上限放到 365 之后 ±1 按不到那儿去，所以「一年」这类得是现成的按钮。 */
val LEAD_DAY_PRESETS: List<Pair<String, Int>> = listOf(
    "当天" to 0,
    "1 天" to 1,
    "3 天" to 3,
    "7 天" to 7,
    "30 天" to 30,
    "90 天" to 90,
    "半年" to 180,
    "一年" to 365,
)

data class SettingsUiState(
    val loading: Boolean = true,
    val leadDays: Int = SettingsRepository.DEFAULT_LEAD_DAYS,
    val visionEnabled: Boolean = SettingsRepository.DEFAULT_VISION_ENABLED,
    /** 表单里的三个值（**按了「保存 AI 配置」才落盘**） */
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    /** 已落盘的模型名：三态说明里「已就绪：走 $savedModel」用的是它 */
    val savedModel: String = "",
    val showKey: Boolean = false,
    val debugResult: String? = null,
    /** FR-TRC-04：累计调用次数（含重试）与服务端报的 tokens 合计；费用不折算，看用户自己的账单。 */
    val aiCalls: Int = 0,
    val aiTokens: Long = 0L,
    val notifyProblem: String? = null,
    val notice: String? = null,
    /** 备份：忙的时候**忽略新手势**（`RULES.md` §16） */
    val busy: Boolean = false,
    /** 备份那一次的结果那句话（五种收场之一） */
    val backupResult: String? = null,
    /**
     * 四套词表的**编辑草稿**（`FR-CFG-06`）：按了各自那颗「保存」才落盘。
     * 空串 = 还没动过，界面会填上当前生效的那份。
     */
    val wordDrafts: Map<WordListKind, String> = emptyMap(),
    /** 当前生效的四套词表（存过的或预设）。 */
    val wordLists: WordLists? = null,
) {
    /** 设置页那一行（🗣）：0 天不许说"提前 0 天"。 */
    val leadDaysLine: String
        get() = if (leadDays == 0) "当天到期提醒" else "提前 $leadDays 天提醒"

    /** 三态说明里的那句（加粗显示）。 */
    val visionStateLine: String
        get() = when {
            !visionEnabled -> "开关关着，选完照片直接进手填。"
            apiKey.isBlank() || baseUrl.isBlank() || model.isBlank() -> "还缺 API key：填好保存之前，选照片都会走手填。"
            else -> "已就绪：走 $savedModel。"
        }

    /**
     * FR-TRC-04：累计用量那行。
     *
     * 🔒 **只报次数与 tokens，不折算成钱**：各家的价格表会变，本机算出来的金额迟早是错的，
     * 说错钱比不说更糟。花钱多少让用户看自己的账单。
     */
    val aiUsageLine: String
        get() = if (aiCalls == 0) {
            "还没调用过 AI。"
        } else {
            "累计调用 $aiCalls 次（含重试）· 服务端报的 tokens 合计 $aiTokens · 费用按你的账单为准（本机不折算）"
        }
}

/** 设置页（`UI.md` §7）。三节：到期提醒 / AI 分析照片 / 备份。 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val runner: ReminderRunner,
    private val backup: BackupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.changes.collect { saved ->
                val effective = saved.wordLists(CATEGORIES, SEASONS, EXPIRE_KINDS, LOCATION_KINDS)
                _state.update { current ->
                    current.copy(
                        loading = false,
                        leadDays = saved.reminderLeadDays,
                        visionEnabled = saved.visionEnabled,
                        savedModel = saved.model,
                        // 表单只在第一次填（之后是用户自己的编辑）
                        baseUrl = current.baseUrl.ifBlank { saved.baseUrl },
                        model = current.model.ifBlank { saved.model },
                        apiKey = current.apiKey.ifBlank { saved.apiKey },
                        aiCalls = saved.aiCallCount,
                        aiTokens = saved.aiTokens,
                        wordLists = effective,
                        // 草稿同样只在没动过的时候填
                        wordDrafts = WordListKind.entries.associateWith { kind ->
                            current.wordDrafts[kind].orEmpty().ifBlank { effective.of(kind).joinToString("、") }
                        },
                    )
                }
            }
        }
    }

    // —— 词表（FR-CFG-06）——

    fun setWordDraft(kind: WordListKind, text: String) {
        _state.update { it.copy(wordDrafts = it.wordDrafts + (kind to text)) }
    }

    /**
     * 保存一套词表。分隔符放宽：顿号、逗号、空格、换行都算——用户不会关心我们用哪个当分隔符。
     *
     * 清空 = 回到预设（`SettingsRepository.setWordList` 里就是这个语义），所以"恢复默认"和
     * "把框里删干净再保存"是同一件事，不用教两遍。
     */
    fun saveWordList(kind: WordListKind) {
        val text = state.value.wordDrafts[kind].orEmpty()
        val values = text.split('、', ',', '，', ' ', '\n', '\r', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        viewModelScope.launch {
            settings.setWordList(kind, values)
            _state.update { it.copy(notice = "「${kind.label}」已保存（${values.size} 项）。") }
        }
    }

    fun resetWordLists() {
        viewModelScope.launch {
            settings.resetWordLists()
            // 草稿也清掉，界面会重新填上预设
            _state.update { it.copy(wordDrafts = emptyMap(), notice = "四套词表都回到默认了。") }
        }
    }

    // —— 到期提醒 ——

    fun stepLeadDays(delta: Int) {
        viewModelScope.launch { settings.setReminderLeadDays(state.value.leadDays + delta) }
    }

    fun setLeadDays(days: Int) {
        viewModelScope.launch { settings.setReminderLeadDays(days) }
    }

    /** 那颗调试按钮：**不是只读操作**，按下去照样回写 `expireNotifiedAt`（`RULES.md` §11.4 ⚠️）。 */
    fun runDebugCheck(context: Context) {
        viewModelScope.launch {
            val sentence = runCatching { runner.runOnce(context, force = true) }
                .getOrElse { "检查失败：${it.message}" }
            _state.update { it.copy(debugResult = sentence, notifyProblem = ReminderNotifier.notifyProblem(context)) }
        }
    }

    /** 「回到前台就自己变」：系统设置里手动开关通知不会走应用内的授权回调。 */
    fun refreshNotifyProblem(context: Context) {
        _state.update { it.copy(notifyProblem = ReminderNotifier.notifyProblem(context)) }
    }

    // —— AI 配置 ——

    fun setVisionEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setVisionEnabled(enabled) }
    }

    fun setBaseUrl(value: String) = _state.update { it.copy(baseUrl = value) }

    fun setModel(value: String) = _state.update { it.copy(model = value) }

    fun setApiKey(value: String) = _state.update { it.copy(apiKey = value) }

    fun toggleShowKey() = _state.update { it.copy(showKey = !it.showKey) }

    fun saveAiConfig() {
        viewModelScope.launch {
            settings.setBaseUrl(state.value.baseUrl)
            settings.setModel(state.value.model)
            settings.setApiKey(state.value.apiKey)
            _state.update { it.copy(notice = "AI 配置已保存。") }
        }
    }

    /** 清空 key：换手机前的动作（设置页那句隐私话里说了这件事）。 */
    fun clearApiKey() {
        viewModelScope.launch {
            settings.setApiKey("")
            _state.update { it.copy(apiKey = "", notice = "API key 已清空。") }
        }
    }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    // —— 备份（`UI.md` §7.3；`RULES.md` §12、§16）——

    /**
     * 三颗按钮共用的收场，**五种**：忙 → 这次手势直接忽略；`BackupFormatException` → 贴异常自己的中文；
     * 其他异常 → 贴该操作的 `fallback`（英文 IOException 直接上屏没有意义）；
     * `null` → 连文件都没打开；成功 → 报条数，且**只有 `bytes > 0` 才**追加字节数。
     */
    private suspend fun operate(label: String, fallback: String, action: suspend () -> BackupSummary?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, backupResult = null) }
        val sentence = try {
            val summary = action()
            if (summary == null) {
                "${label}失败：打不开那个文件，换个位置再试"
            } else {
                buildString {
                    append("${label}完成 · 位置 ${summary.locations} 个 · 物品 ${summary.items} 件 · 照片 ${summary.photos} 张")
                    if (summary.bytes > 0) append(" · ${formatBytes(summary.bytes)}")
                }
            }
        } catch (e: BackupFormatException) {
            "${label}失败：${e.message}"
        } catch (e: Throwable) {
            "${label}失败：$fallback"
        }
        _state.update { it.copy(busy = false, backupResult = sentence) }
    }

    /** @param open 由界面层打开 SAF 流；返回 null 表示"连文件都没打开"（既不是异常也不算成功）。 */
    fun exportBackup(open: () -> java.io.OutputStream?) {
        viewModelScope.launch {
            operate(label = "导出", fallback = FALLBACK_EXPORT) {
                open()?.use { stream -> backup.exportTo(stream) }
            }
        }
    }

    fun importBackup(mode: ImportMode, open: () -> java.io.InputStream?) {
        val label = if (mode == ImportMode.MERGE) "合并导入" else "覆盖导入"
        viewModelScope.launch {
            operate(label = label, fallback = FALLBACK_IMPORT) {
                open()?.use { stream -> backup.importFrom(stream, mode) }
            }
        }
    }

    fun consumeBackupResult() = _state.update { it.copy(backupResult = null) }

    companion object {
        /**
         * 非 `BackupFormatException` 的兜底：给的是**下一步该做什么**。
         * 📌 这两句是本实现自己写的：文档只钉住了这个位置要给"该操作的下一步提示"，没有给逐字串。
         */
        const val FALLBACK_EXPORT = "换个有写入权限的位置再试一次（比如「下载」目录）"
        const val FALLBACK_IMPORT = "确认选的是 Wheremy 导出的 zip，然后再选一次"
    }
}
