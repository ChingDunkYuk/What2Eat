package com.what2eat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.what2eat.core.database.entity.DecisionSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DecisionSessionDao {

    @Query("SELECT * FROM decision_session WHERE status IN (0, 1) ORDER BY createdAt DESC LIMIT 1")
    fun observeActiveSession(): Flow<DecisionSessionEntity?>

    @Query("SELECT * FROM decision_session WHERE status IN (0, 1) ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveSession(): DecisionSessionEntity?

    @Query("SELECT * FROM decision_session ORDER BY createdAt DESC")
    fun observeAllSessions(): Flow<List<DecisionSessionEntity>>

    @Query("SELECT * FROM decision_session WHERE id = :id")
    suspend fun getById(id: String): DecisionSessionEntity?

    @Query("SELECT COUNT(*) FROM decision_session WHERE status IN (0, 1)")
    suspend fun countActiveSessions(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DecisionSessionEntity)

    @Query("UPDATE decision_session SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: Int, updatedAt: Long)

    @Query("UPDATE decision_session SET status = 4, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun cancel(id: String, completedAt: Long, updatedAt: Long)

    @Query("UPDATE decision_session SET status = 3, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun complete(id: String, completedAt: Long, updatedAt: Long)

    @Query("UPDATE decision_session SET mealModes = :mealModes, moodTags = :moodTags, budgetLevel = :budgetLevel, distanceLevel = :distanceLevel, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateConditions(id: String, mealModes: String, moodTags: String, budgetLevel: Int, distanceLevel: Int, status: Int, updatedAt: Long)

    @Query("DELETE FROM decision_session WHERE id = :id")
    suspend fun delete(id: String)
}
