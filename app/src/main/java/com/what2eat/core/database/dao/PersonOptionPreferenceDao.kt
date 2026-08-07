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

    @Query("SELECT * FROM person_option_preference WHERE personId = :personId AND savedOptionId = :optionId LIMIT 1")
    suspend fun get(personId: String, optionId: String): PersonOptionPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PersonOptionPreferenceEntity)

    @Query("DELETE FROM person_option_preference WHERE personId = :personId AND savedOptionId = :optionId")
    suspend fun delete(personId: String, optionId: String)
}