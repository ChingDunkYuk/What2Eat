package com.what2eat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.what2eat.core.database.entity.SavedOptionTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedOptionTagDao {

    @Query("SELECT * FROM saved_option_tag")
    fun observeAll(): Flow<List<SavedOptionTagEntity>>

    @Query("SELECT * FROM saved_option_tag WHERE savedOptionId = :optionId")
    fun observeByOption(optionId: String): Flow<List<SavedOptionTagEntity>>

    @Query("SELECT * FROM saved_option_tag WHERE savedOptionId = :optionId")
    suspend fun getByOption(optionId: String): List<SavedOptionTagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<SavedOptionTagEntity>)

    @Query("DELETE FROM saved_option_tag WHERE savedOptionId = :optionId")
    suspend fun deleteByOption(optionId: String)
}