package com.what2eat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.what2eat.core.database.entity.SessionParticipantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionParticipantDao {

    @Query("SELECT * FROM session_participant WHERE sessionId = :sessionId ORDER BY selectionOrder ASC")
    fun observeBySession(sessionId: String): Flow<List<SessionParticipantEntity>>

    @Query("SELECT * FROM session_participant WHERE sessionId = :sessionId ORDER BY selectionOrder ASC")
    suspend fun getBySession(sessionId: String): List<SessionParticipantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SessionParticipantEntity>)

    @Query("UPDATE session_participant SET completed = 1 WHERE sessionId = :sessionId AND personId = :personId")
    suspend fun markCompleted(sessionId: String, personId: String)

    @Query("DELETE FROM session_participant WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}
