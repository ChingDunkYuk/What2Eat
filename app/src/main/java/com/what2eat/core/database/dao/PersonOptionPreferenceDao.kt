package com.what2eat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.what2eat.core.database.entity.PersonOptionPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonOptionPreferenceDao {

    @Query("SELECT * FROM person_option_preference WHERE savedOptionId = :optionId")
    fun observeByOption(optionId: String): Flow<List<PersonOptionPreferenceEntity>>

    @Query("SELECT * FROM person_option_preference WHERE savedOptionId = :optionId")
    suspend fun getByOption(optionId: String): List<PersonOptionPreferenceEntity>

    /** 全量选项偏好（v0.9.1：池决策 N+1 优化——一次查询内存 groupBy 替代逐选项查询） */
    @Query("SELECT * FROM person_option_preference")
    suspend fun getAll(): List<PersonOptionPreferenceEntity>

    @Query("SELECT * FROM person_option_preference WHERE personId = :personId AND savedOptionId = :optionId LIMIT 1")
    suspend fun get(personId: String, optionId: String): PersonOptionPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PersonOptionPreferenceEntity)

    @Query("DELETE FROM person_option_preference WHERE personId = :personId AND savedOptionId = :optionId")
    suspend fun delete(personId: String, optionId: String)

    // ── 备份/恢复（v0.9.3） ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PersonOptionPreferenceEntity>)

    @Query("DELETE FROM person_option_preference")
    suspend fun deleteAll()
}