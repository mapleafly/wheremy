package com.wheremy.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.wheremy.app.data.db.SearchTextBuilder
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.settings.SettingsRepository
import com.wheremy.app.reminder.ReminderNotifier
import com.wheremy.app.reminder.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Hilt 的根（TECH_STACK.md §3.1：DI 用官方推荐的 Hilt）。 */
@HiltAndroidApp
class WheremyApp :
    Application(),
    Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var items: ItemRepository

    private val startup = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** WorkManager 交给 Hilt 造 Worker（默认初始化器在清单里被摘掉，见 `AndroidManifest.xml`）。 */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        ReminderNotifier.ensureChannel(this)
        ReminderScheduler.schedule(this)
        repairSearchTextIfNeeded()
    }

    /**
     * 搜索字段版本变了就补算一遍（`RULES.md` §1.4 / §10.3）。
     * 只在版本不等时做，且记回设置——不然每次冷启动都要全表重算。
     */
    private fun repairSearchTextIfNeeded() {
        startup.launch {
            runCatching {
                if (settings.current().searchTextVersion != SearchTextBuilder.FIELD_VERSION) {
                    items.rebuildSearchText()
                    settings.setSearchTextVersion(SearchTextBuilder.FIELD_VERSION)
                }
            }
        }
    }
}
