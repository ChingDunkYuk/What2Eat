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
 */
@Dao
interface PersonProfileDao {

    @Query("SELECT * FROM person_profile ORDER BY isPrimary DESC, createdAt ASC")
    fun observeAll(): Flow<List<PersonProfileEntity>>

    @Query("SELECT * FROM person_profile WHERE isPrimary = 1 LIMIT 1")
    fun observePrimary(): Flow<PersonProfileEntity?>

    @Query("SELECT * FROM person_profile WHERE id = :id")
    suspend fun getById(id: Long): PersonProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PersonProfileEntity): Long

    @Update
    suspend fun update(entity: PersonProfileEntity)

    @Delete
    suspend fun delete(entity: PersonProfileEntity)
}
