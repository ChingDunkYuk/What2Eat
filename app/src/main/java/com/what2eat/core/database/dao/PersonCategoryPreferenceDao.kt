package com.what2eat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.what2eat.core.database.entity.PersonCategoryPreferenceEntity
import kotlinx.coroutines.flow.Flow

/**
 * 人物餐饮偏好 DAO。
 */
@Dao
interface PersonCategoryPreferenceDao {

    @Query("SELECT * FROM person_category_preference WHERE personId = :personId")
    fun observeByPerson(personId: String): Flow<List<PersonCategoryPreferenceEntity>>

    @Query("SELECT * FROM person_category_preference WHERE personId = :personId AND categoryId = :categoryId LIMIT 1")
    fun observe(personId: String, categoryId: String): Flow<PersonCategoryPreferenceEntity?>

    @Query("SELECT * FROM person_category_preference WHERE personId = :personId")
    suspend fun getByPerson(personId: String): List<PersonCategoryPreferenceEntity>

    @Query("SELECT * FROM person_category_preference WHERE personId = :personId AND categoryId = :categoryId LIMIT 1")
    suspend fun get(personId: String, categoryId: String): PersonCategoryPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PersonCategoryPreferenceEntity)

    @Query("UPDATE person_category_preference SET preferenceLevel = :level, updatedAt = :updatedAt WHERE personId = :personId AND categoryId = :categoryId")
    suspend fun setPreferenceLevel(personId: String, categoryId: String, level: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE person_category_preference SET hardExcluded = :excluded, updatedAt = :updatedAt WHERE personId = :personId AND categoryId = :categoryId")
    suspend fun setHardExcluded(personId: String, categoryId: String, excluded: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM person_category_preference WHERE personId = :personId AND categoryId = :categoryId")
    suspend fun delete(personId: String, categoryId: String)

    // ── 备份/恢复（v0.9.3） ──

    @Query("SELECT * FROM person_category_preference")
    suspend fun getAll(): List<PersonCategoryPreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PersonCategoryPreferenceEntity>)

    @Query("DELETE FROM person_category_preference")
    suspend fun deleteAll()
}
