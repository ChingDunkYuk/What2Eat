package com.what2eat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.what2eat.core.database.entity.SavedOptionCollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedOptionCollectionDao {

    @Query("SELECT * FROM saved_option_collection")
    fun observeAll(): Flow<List<SavedOptionCollectionEntity>>

    @Query("SELECT * FROM saved_option_collection WHERE savedOptionId = :optionId")
    fun observeByOption(optionId: String): Flow<List<SavedOptionCollectionEntity>>

    @Query("SELECT * FROM saved_option_collection WHERE savedOptionId = :optionId")
    suspend fun getByOption(optionId: String): List<SavedOptionCollectionEntity>

    @Query("SELECT savedOptionId FROM saved_option_collection WHERE collectionType = :collectionType")
    suspend fun getOptionIdsByCollection(collectionType: Int): List<String>

    @Query("SELECT savedOptionId FROM saved_option_collection WHERE collectionType = :collectionType")
    fun observeOptionIdsByCollection(collectionType: Int): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<SavedOptionCollectionEntity>)

    @Query("DELETE FROM saved_option_collection WHERE savedOptionId = :optionId")
    suspend fun deleteByOption(optionId: String)

    @Query("DELETE FROM saved_option_collection WHERE savedOptionId = :optionId AND collectionType = :collectionType")
    suspend fun delete(optionId: String, collectionType: Int)

    // ── 备份/恢复（v0.9.3） ──

    @Query("SELECT * FROM saved_option_collection")
    suspend fun getAll(): List<SavedOptionCollectionEntity>

    @Query("DELETE FROM saved_option_collection")
    suspend fun deleteAll()
}