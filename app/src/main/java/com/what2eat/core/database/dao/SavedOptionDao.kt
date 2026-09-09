package com.what2eat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.what2eat.core.database.entity.SavedOptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedOptionDao {

    @Query("SELECT * FROM saved_option ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedOptionEntity>>

    @Query("SELECT * FROM saved_option WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<SavedOptionEntity?>

    @Query("SELECT * FROM saved_option WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SavedOptionEntity?

    @Query("SELECT * FROM saved_option WHERE name LIKE '%' || :query || '%' OR areaText LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<SavedOptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavedOptionEntity)

    @Query("UPDATE saved_option SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE saved_option SET lastChosenAt = :chosenAt WHERE id = :id")
    suspend fun setLastChosenAt(id: String, chosenAt: Long)

    /** 物理删除（仅允许未被历史引用的选项） */
    @Query("DELETE FROM saved_option WHERE id = :id")
    suspend fun delete(id: String)

    // ── 备份/恢复（v0.9.3） ──

    @Query("SELECT * FROM saved_option")
    suspend fun getAll(): List<SavedOptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SavedOptionEntity>)

    @Query("DELETE FROM saved_option")
    suspend fun deleteAll()
}