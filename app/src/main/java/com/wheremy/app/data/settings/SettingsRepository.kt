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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** DataStore 文件名（`ARCHITECTURE.md` §6.4）。 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "wheremy_settings")

/**
 * 四套词表（`FR-CFG-06`）。**空 = 用代码里的预设**，所以"从没改过"和"改回默认"是同一件事。
 *
 * 🔒 改词表**不动已有数据**：`category` / `seasons` 从来不做白名单校验（`RULES.md` §5.7），
 * 用户把某一项删掉之后，已经用了这个值的物品仍然在详情页原样显示——只是芯片行上没它了。
 */
data class WordLists(
    val categories: List<String>,
    val seasons: List<String>,
    val expireKinds: List<String>,
    val locationKinds: List<String>,
) {
    /** 按名字取那一套——界面遍历枚举时要用，免得写四个 `when` 分支。 */
    fun of(kind: WordListKind): List<String> = when (kind) {
        WordListKind.CATEGORIES -> categories
        WordListKind.SEASONS -> seasons
        WordListKind.EXPIRE_KINDS -> expireKinds
        WordListKind.LOCATION_KINDS -> locationKinds
    }

    companion object {
        /** 📐 每个词表最多这些项：再多芯片行就没法用了，也不符合"个人自己的分类法"这个场景。 */
        const val MAX_ITEMS = 24
    }
}

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
    /** 四套词表（`FR-CFG-06`）；每一项为空列表时由调用方回落到代码里的预设。 */
    val categories: List<String> = emptyList(),
    val seasons: List<String> = emptyList(),
    val expireKinds: List<String> = emptyList(),
    val locationKinds: List<String> = emptyList(),
    /** 定时自动导出（`FR-BAK-08`）：开关 + 用户选的那棵 SAF 目录树 + 上一次的结果。 */
    val autoExportEnabled: Boolean = false,
    val autoExportUri: String = "",
    val autoExportLastAt: Long = 0L,
    val autoExportLastResult: String = "",
) {
    /** 四样齐了才算能用：开关、key、地址、模型。 */
    val hasUsableKey: Boolean
        get() = visionEnabled && apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()

    /** 真正生效的四套词表：存过的用存的，没存过的用预设。 */
    fun wordLists(
        presetCategories: List<String>,
        presetSeasons: List<String>,
        presetExpireKinds: List<String>,
        presetLocationKinds: List<String>,
    ): WordLists = WordLists(
        categories = categories.ifEmpty { presetCategories },
        seasons = seasons.ifEmpty { presetSeasons },
        expireKinds = expireKinds.ifEmpty { presetExpireKinds },
        locationKinds = locationKinds.ifEmpty { presetLocationKinds },
    )
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
            // 缺省 **0**（= 从没补算过），**不能**取当前的 FIELD_VERSION：
            // 取当前值的话，键不存在时闸门永远相等（`x != x` 为假），
            // 于是"加了搜索字段"这件事对老库**完全不生效**——2026-10-10 真机上就是这么漏掉类别与季节的。
            // 缺省 0 的代价只是首次启动多跑一次 rebuildSearchText（它只写真的变了的行）。
            searchTextVersion = prefs[KEY_SEARCH_TEXT_VERSION] ?: 0,
            aiCallCount = prefs[KEY_AI_CALLS] ?: 0,
            aiTokens = prefs[KEY_AI_TOKENS] ?: 0L,
            categories = splitList(prefs[KEY_CATEGORIES]),
            seasons = splitList(prefs[KEY_SEASONS]),
            expireKinds = splitList(prefs[KEY_EXPIRE_KINDS]),
            locationKinds = splitList(prefs[KEY_LOCATION_KINDS]),
            autoExportEnabled = prefs[KEY_AUTO_EXPORT_ENABLED] ?: false,
            autoExportUri = prefs[KEY_AUTO_EXPORT_URI].orEmpty(),
            autoExportLastAt = prefs[KEY_AUTO_EXPORT_LAST_AT] ?: 0L,
            autoExportLastResult = prefs[KEY_AUTO_EXPORT_LAST_RESULT].orEmpty(),
        )
    }

    suspend fun current(): Settings = changes.first()

    /**
     * 四套词表的**实际生效值**：存过的用存的，没存过的用代码里的预设（`FR-CFG-06`）。
     *
     * 预设由调用方传进来，是为了不让数据层反过来依赖 `ui` 层要用的那份常量表。
     */
    suspend fun wordLists(
        presetCategories: List<String>,
        presetSeasons: List<String>,
        presetExpireKinds: List<String>,
        presetLocationKinds: List<String>,
    ): WordLists = current().wordLists(presetCategories, presetSeasons, presetExpireKinds, presetLocationKinds)

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

    /**
     * 改一套词表（`FR-CFG-06`）。写入侧就清洗：去空白、丢空项、去重、封顶 [WordLists.MAX_ITEMS]。
     *
     * 存成**多行字符串**而不是逗号分隔：用户自己的叫法里很可能就有逗号（"螺丝、钉子"这种写法反而常见），
     * 用一个不会出现在词里的分隔符（换行）最省事。
     */
    suspend fun setWordList(kind: WordListKind, values: List<String>) = store.edit { prefs ->
        val clean = normalize(values)
        if (clean.isEmpty()) {
            // 清空 = 回到预设（而不是"一项都没有"）
            prefs.remove(kind.key)
        } else {
            prefs[kind.key] = clean.joinToString("\n")
        }
    }.let { }

    /** 「恢复默认」：四套词表一起清掉，等于回到代码里的预设。 */
    suspend fun resetWordLists() = store.edit { prefs ->
        WordListKind.entries.forEach { prefs.remove(it.key) }
    }.let { }

    private fun normalize(values: List<String>): List<String> = values
        .map { it.replace('\n', ' ').replace('\r', ' ').trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(WordLists.MAX_ITEMS)

    private fun splitList(raw: String?): List<String> =
        raw?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    // —— 定时自动导出（`FR-BAK-08`）——

    suspend fun setAutoExportEnabled(enabled: Boolean) = store.edit { it[KEY_AUTO_EXPORT_ENABLED] = enabled }.let { }

    /**
     * 记下用户选的那棵 SAF 目录树。传 null/空串 = 取消（下次自动导出会跳过并说明原因）。
     *
     * ⚠️ 这里只存 URI 字符串；**持久化授权**（`takePersistableUriPermission`）由界面层在拿到结果时立刻做，
     * 否则重启后这个 URI 就打不开了。
     */
    suspend fun setAutoExportUri(uri: String?) = store.edit { prefs ->
        val value = uri?.trim().orEmpty()
        if (value.isEmpty()) prefs.remove(KEY_AUTO_EXPORT_URI) else prefs[KEY_AUTO_EXPORT_URI] = value
    }.let { }

    /** 记下这一次的结果（成功或失败都说人话），设置页照原样显示。 */
    suspend fun setAutoExportResult(at: Long, result: String) = store.edit {
        it[KEY_AUTO_EXPORT_LAST_AT] = at
        it[KEY_AUTO_EXPORT_LAST_RESULT] = result
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
        val KEY_CATEGORIES = stringPreferencesKey("word_categories")
        val KEY_SEASONS = stringPreferencesKey("word_seasons")
        val KEY_EXPIRE_KINDS = stringPreferencesKey("word_expire_kinds")
        val KEY_LOCATION_KINDS = stringPreferencesKey("word_location_kinds")
        val KEY_AUTO_EXPORT_ENABLED = booleanPreferencesKey("auto_export_enabled")
        val KEY_AUTO_EXPORT_URI = stringPreferencesKey("auto_export_uri")
        val KEY_AUTO_EXPORT_LAST_AT = longPreferencesKey("auto_export_last_at")
        val KEY_AUTO_EXPORT_LAST_RESULT = stringPreferencesKey("auto_export_last_result")
    }
}

/** 四套词表的名字（`FR-CFG-06`）——界面与仓储用同一个枚举，别在两处各写一遍字符串。 */
enum class WordListKind(val label: String, val key: androidx.datastore.preferences.core.Preferences.Key<String>) {
    CATEGORIES("物品类别", SettingsRepository.KEY_CATEGORIES),
    SEASONS("季节", SettingsRepository.KEY_SEASONS),
    EXPIRE_KINDS("到期类型", SettingsRepository.KEY_EXPIRE_KINDS),
    LOCATION_KINDS("位置类型", SettingsRepository.KEY_LOCATION_KINDS),
}
