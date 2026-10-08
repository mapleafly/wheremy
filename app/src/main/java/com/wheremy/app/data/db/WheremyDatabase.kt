package com.wheremy.app.data.db

import android.content.Context
import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase

/** 备份 manifest 也写这个号；导入时据此判断"这个包是不是来自更新的版本"。 */
const val DB_SCHEMA_VERSION = 1

/**
 * 数据库入口（`ARCHITECTURE.md` §4.1）。
 *
 * ⚠️ **与参照实现的一处有意偏差（`DELTA.md` 的 W2）**：这里**没有** `fallbackToDestructiveMigration`。
 * 参照实现允许破坏性迁移（注释原话"Plan 1 期间允许……发布前换成真实 Migration"），
 * 本实现不把这笔债记在账上：版本号一升，就必须同时交一个 `Migration`，
 * 并由迁移测试拿真实 schema JSON 验一遍（出口判据在 `TESTING.md` §5）。
 */
@Database(
    entities = [
        LocationNodeEntity::class,
        ItemEntity::class,
        ItemPhotoEntity::class,
        ItemTagEntity::class,
    ],
    version = DB_SCHEMA_VERSION,
    exportSchema = true,
)
@ColumnTypeConverters(ItemStatusConverter::class)
abstract class WheremyDatabase : RoomDatabase() {

    abstract fun locationDao(): LocationDao

    abstract fun itemDao(): ItemDao

    abstract fun photoDao(): PhotoDao

    companion object {
        const val NAME = "wheremy.db"

        fun build(context: Context): WheremyDatabase =
            Room.databaseBuilder(context, WheremyDatabase::class.java, NAME).build()
    }
}
