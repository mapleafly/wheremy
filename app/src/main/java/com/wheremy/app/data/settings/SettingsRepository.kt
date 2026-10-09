package com.wheremy.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wheremy.app.data.db.SearchTextBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** DataStore 文件名（`ARCHITECTURE.md` §6.4）。 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "wheremy_settings")

data class Settings(
    val visionEnabled: Boolean,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val reminderLeadDays: Int,
    val searchTextVersion: Int,
    /** 累计调用次数（含重试）——留痕只留最近 10 条，所以计数得单独存一份（FR-TRC-04）。 */
    val aiCallCount: Int = 0,
    /** 服务端报的 tokens 合计；接口不报就不涨。 */
    val aiTokens: Long = 0L,
) {
    /** 四样齐了才算能用：开关、key、地址、模型。 */
    val hasUsableKey: Boolean
        get() = visionEnabled && apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()
}

/** AI 分析要的那三样（不可用时给 null）。 */
data class VisionConfig(val baseUrl: String, val model: String, val apiKey: String)

/**
 * 偏好设置（`ARCHITECTURE.md` §6.4）。
 *
 * ⚠️ **key 只在本地**：不进备份包、不进留痕、不进日志（`RULES.md` §12、§13.3）。
 */
class SettingsRepository(private val store: DataStore<Preferences>) {

    val changes: Flow<Settings> = store.data.map { prefs ->
        Settings(
            visionEnabled = prefs[KEY_VISION_ENABLED] ?: DEFAULT_VISION_ENABLED,
            baseUrl = prefs[KEY_BASE_URL] ?: DEFAULT_BASE_URL,
            model = prefs[KEY_MODEL] ?: DEFAULT_MODEL,
            apiKey = prefs[KEY_API_KEY].orEmpty(),
            reminderLeadDays = (prefs[KEY_LEAD_DAYS] ?: DEFAULT_LEAD_DAYS).coerceIn(LEAD_DAYS_MIN, LEAD_DAYS_MAX),
            searchTextVersion = prefs[KEY_SEARCH_TEXT_VERSION] ?: SearchTextBuilder.FIELD_VERSION,
            aiCallCount = prefs[KEY_AI_CALLS] ?: 0,
            aiTokens = prefs[KEY_AI_TOKENS] ?: 0L,
        )
    }

    suspend fun current(): Settings = changes.first()

    suspend fun currentConfig(): VisionConfig? {
        val settings = current()
        return if (settings.hasUsableKey) {
            VisionConfig(settings.baseUrl, settings.model, settings.apiKey)
        } else {
            null
        }
    }

    suspend fun setVisionEnabled(enabled: Boolean) = store.edit { it[KEY_VISION_ENABLED] = enabled }.let { }

    suspend fun setBaseUrl(value: String) = store.edit { it[KEY_BASE_URL] = value.trim() }.let { }

    suspend fun setModel(value: String) = store.edit { it[KEY_MODEL] = value.trim() }.let { }

    suspend fun setApiKey(value: String) = store.edit { it[KEY_API_KEY] = value.trim() }.let { }

    /** 上下界在写入侧就卡住：脏值不许进库。 */
    suspend fun setReminderLeadDays(days: Int) = store.edit {
        it[KEY_LEAD_DAYS] = days.coerceIn(LEAD_DAYS_MIN, LEAD_DAYS_MAX)
    }.let { }

    suspend fun setSearchTextVersion(version: Int) = store.edit { it[KEY_SEARCH_TEXT_VERSION] = version }.let { }

    /**
     * 记一次调用（FR-TRC-04）。**每尝试一次算一次**——重试也是真金白银，不能只算成功的。
     *
     * 只数次数与 tokens，**不折算成钱**：各家的价格表会变，本机算出来的金额迟早是错的，
     * 界面上就照实说"花费看你的账单"。
     */
    suspend fun recordAiCall(tokens: Int) = store.edit {
        it[KEY_AI_CALLS] = (it[KEY_AI_CALLS] ?: 0) + 1
        if (tokens > 0) it[KEY_AI_TOKENS] = (it[KEY_AI_TOKENS] ?: 0L) + tokens
    }.let { }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-flash"
        const val DEFAULT_LEAD_DAYS = 3
        const val DEFAULT_VISION_ENABLED = true
        const val LEAD_DAYS_MIN = 0
        const val LEAD_DAYS_MAX = 365

        val KEY_VISION_ENABLED = booleanPreferencesKey("vision_enabled")
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_MODEL = stringPreferencesKey("model")
        val KEY_API_KEY = stringPreferencesKey("api_key")
        val KEY_LEAD_DAYS = intPreferencesKey("reminder_lead_days")
        val KEY_SEARCH_TEXT_VERSION = intPreferencesKey("search_text_version")
        val KEY_AI_CALLS = intPreferencesKey("ai_call_count")
        val KEY_AI_TOKENS = longPreferencesKey("ai_token_total")
    }
}
