package com.wheremy.app.di

import android.content.Context
import com.wheremy.app.BuildConfig
import com.wheremy.app.data.backup.BackupRepository
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.repo.LocationRepository
import com.wheremy.app.data.settings.SettingsRepository
import com.wheremy.app.data.settings.settingsDataStore
import com.wheremy.app.domain.CaptureFlow
import com.wheremy.app.domain.ReminderEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * 数据层的装配（`ARCHITECTURE.md` §6）。
 * 照片根目录与参照实现一致：`files/photos`（备份导入靠 `originals/`、`thumbs/` 两个目录名认照片）。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): WheremyDatabase = WheremyDatabase.build(context)

    @Provides
    @Singleton
    fun photoStore(@ApplicationContext context: Context): PhotoStore = PhotoStore(File(context.filesDir, "photos"))

    @Provides
    @Singleton
    fun itemRepository(db: WheremyDatabase, photoStore: PhotoStore): ItemRepository = ItemRepository(db, photoStore)

    @Provides
    @Singleton
    fun locationRepository(db: WheremyDatabase, photoStore: PhotoStore): LocationRepository =
        LocationRepository(db, photoStore)

    @Provides
    @Singleton
    fun captureFlow(items: ItemRepository): CaptureFlow = CaptureFlow(items)

    @Provides
    @Singleton
    fun settingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context.settingsDataStore)

    @Provides
    @Singleton
    fun reminderEngine(db: WheremyDatabase): ReminderEngine = ReminderEngine(db.itemDao())

    /**
     * 备份（`RULES.md` §12）。构造参数里**刻意没有** `SettingsRepository`：
     * 导出通道物理上碰不到 key 与任何设置。
     */
    @Provides
    @Singleton
    fun backupRepository(
        db: WheremyDatabase,
        photoStore: PhotoStore,
        @ApplicationContext context: Context,
    ): BackupRepository = BackupRepository(
        db = db,
        photoStore = photoStore,
        appVersion = BuildConfig.VERSION_NAME,
        // 解包暂存目录：刻意在 photos/ 下面（它的两个正式目录之外），覆盖导入的清理不会误删它
        stagingDir = File(photoStore.rootDir, ".incoming"),
    )
}
