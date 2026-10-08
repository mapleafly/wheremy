package com.wheremy.app.data.db

import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase

/**
 * Phase 0 的最小 schema：只用来验证 **Room 3（`androidx.room3`）** 这条线能不能用（PLAN.md §3.3 第 7 步）。
 * 真正的四张表（`item` / `location_node` / `item_photo` / `item_tag`）在 P1 按 `RULES.md` §2 建模。
 */
@Entity(tableName = "item")
data class ItemEntity(@PrimaryKey val id: String, val name: String, val locationId: String?)

@Dao
interface ItemDao {
    @Insert
    suspend fun insert(item: ItemEntity)

    @Query("SELECT * FROM item ORDER BY name")
    suspend fun all(): List<ItemEntity>

    @Query("SELECT COUNT(*) FROM item")
    suspend fun count(): Int
}

@Database(entities = [ItemEntity::class], version = 1, exportSchema = true)
abstract class WheremyDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
}
