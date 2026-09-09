package com.what2eat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.what2eat.core.database.entity.FoodCategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 餐饮分类 DAO。
 */
@Dao
interface FoodCategoryDao {

    @Query("SELECT * FROM food_category ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<FoodCategoryEntity>>

    @Query("SELECT * FROM food_category WHERE enabled = 1 ORDER BY sortOrder ASC, createdAt ASC")
    fun observeEnabled(): Flow<List<FoodCategoryEntity>>

    @Query("SELECT * FROM food_category WHERE parentId IS NULL ORDER BY sortOrder ASC")
    fun observeRootCategories(): Flow<List<FoodCategoryEntity>>

    @Query("SELECT * FROM food_category WHERE parentId = :parentId ORDER BY sortOrder ASC")
    fun observeByParent(parentId: String): Flow<List<FoodCategoryEntity>>

    @Query("SELECT * FROM food_category WHERE id = :id")
    suspend fun getById(id: String): FoodCategoryEntity?

    @Query("SELECT COUNT(*) FROM food_category")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FoodCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<FoodCategoryEntity>)

    @Query("UPDATE food_category SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    // ── 备份/恢复（v0.9.3） ──

    @Query("SELECT * FROM food_category")
    suspend fun getAll(): List<FoodCategoryEntity>

    @Query("DELETE FROM food_category")
    suspend fun deleteAll()
}
