package com.what2eat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.what2eat.core.database.entity.SessionCategorySelectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionCategorySelectionDao {

    @Query("SELECT * FROM session_category_selection WHERE sessionId = :sessionId AND personId = :personId")
    fun observeByPerson(sessionId: String, personId: String): Flow<List<SessionCategorySelectionEntity>>

    @Query("SELECT * FROM session_category_selection WHERE sessionId = :sessionId AND personId = :personId")
    suspend fun getByPerson(sessionId: String, personId: String): List<SessionCategorySelectionEntity>

    @Query("SELECT * FROM session_category_selection WHERE sessionId = :sessionId")
    suspend fun getAllBySession(sessionId: String): List<SessionCategorySelectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SessionCategorySelectionEntity)

    @Query("DELETE FROM session_category_selection WHERE sessionId = :sessionId AND personId = :personId AND categoryId = :categoryId")
    suspend fun delete(sessionId: String, personId: String, categoryId: String)

    @Query("DELETE FROM session_category_selection WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    // ── 备份/恢复（v0.9.3） ──

    @Query("SELECT * FROM session_category_selection")
    suspend fun getAll(): List<SessionCategorySelectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SessionCategorySelectionEntity>)

    @Query("DELETE FROM session_category_selection")
    suspend fun deleteAll()
}
