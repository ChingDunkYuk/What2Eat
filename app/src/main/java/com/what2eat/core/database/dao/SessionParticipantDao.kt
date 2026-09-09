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

    /** 全量参与者（v0.9.1：历史页 N+1 优化——一次查询内存 groupBy 替代逐会话查询） */
    @Query("SELECT * FROM session_participant")
    suspend fun getAll(): List<SessionParticipantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SessionParticipantEntity>)

    @Query("UPDATE session_participant SET completed = 1 WHERE sessionId = :sessionId AND personId = :personId")
    suspend fun markCompleted(sessionId: String, personId: String)

    @Query("DELETE FROM session_participant WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    // ── 备份/恢复（v0.9.3） ──

    @Query("DELETE FROM session_participant")
    suspend fun deleteAll()
}
