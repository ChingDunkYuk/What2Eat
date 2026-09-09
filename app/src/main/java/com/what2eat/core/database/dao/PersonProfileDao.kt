package com.what2eat.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.what2eat.core.database.entity.PersonProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * 人物档案 DAO。
 * Stage 1.1: id 改为 String。
 */
@Dao
interface PersonProfileDao {

    @Query("SELECT * FROM person_profile ORDER BY isPrimary DESC, sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<PersonProfileEntity>>

    @Query("SELECT * FROM person_profile WHERE isPrimary = 1 LIMIT 1")
    fun observePrimary(): Flow<PersonProfileEntity?>

    @Query("SELECT * FROM person_profile WHERE enabled = 1 ORDER BY isPrimary DESC, sortOrder ASC")
    fun observeEnabled(): Flow<List<PersonProfileEntity>>

    @Query("SELECT * FROM person_profile WHERE id = :id")
    suspend fun getById(id: String): PersonProfileEntity?

    @Query("SELECT * FROM person_profile WHERE isPrimary = 1 LIMIT 1")
    suspend fun getPrimary(): PersonProfileEntity?

    @Query("SELECT * FROM person_profile")
    suspend fun getAllNow(): List<PersonProfileEntity>

    @Query("SELECT COUNT(*) FROM person_profile WHERE isPrimary = 1")
    suspend fun countPrimary(): Int

    @Query("SELECT COUNT(*) FROM person_profile")
    suspend fun count(): Int

    @Query("UPDATE person_profile SET isPrimary = 0, updatedAt = :updatedAt")
    suspend fun clearAllPrimary(updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PersonProfileEntity)

    @Update
    suspend fun update(entity: PersonProfileEntity)

    @Delete
    suspend fun delete(entity: PersonProfileEntity)

    // ── 主用户 id 变更时的引用迁移 ──

    @Query("UPDATE person_category_preference SET personId = :newId WHERE personId = :oldId")
    suspend fun migratePreferencePersonId(oldId: String, newId: String)

    @Query("UPDATE session_participant SET personId = :newId WHERE personId = :oldId")
    suspend fun migrateParticipantPersonId(oldId: String, newId: String)

    // ── 备份/恢复（v0.9.3） ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PersonProfileEntity>)

    @Query("DELETE FROM person_profile")
    suspend fun deleteAll()
}
