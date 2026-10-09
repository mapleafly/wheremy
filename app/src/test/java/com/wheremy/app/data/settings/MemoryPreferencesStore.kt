package com.wheremy.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 测试用的**内存版** DataStore（**F23**：Windows 上真 DataStore 的"原子改名"会失败，
 * 那是测试环境的坑，不是产品 bug）。文件形态的持久化由真机验证兜。
 *
 * 放在测试源码里给多个测试类共用：设置仓储现在被确认页、照片墙、位置页三处注入，
 * 每个测试都自己写一遍内存替身只会把同一段代码抄三遍。
 */
class MemoryPreferencesStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

/** 一个能直接用的设置仓储（测试里不需要关心它怎么存）。 */
fun testSettings(): SettingsRepository = SettingsRepository(MemoryPreferencesStore())
