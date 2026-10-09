package com.wheremy.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheremy.app.data.settings.SettingsRepository
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
    val notifyProblem: String? = null,
    val notice: String? = null,
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
}

/** 设置页（`UI.md` §7）。三节：到期提醒 / AI 分析照片 / 备份（备份在 P5 后半）。 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val runner: ReminderRunner,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.changes.collect { saved ->
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
                    )
                }
            }
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
}
