package com.what2eat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.what2eat.core.database.entity.DecisionRecommendationEntity
import kotlinx.coroutines.flow.Flow

/**
 * 决策推荐快照 DAO。
 */
@Dao
interface DecisionRecommendationDao {

    @Insert
    suspend fun insert(entity: DecisionRecommendationEntity): Long

    @Query("SELECT * FROM decision_recommendation WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observeBySession(sessionId: String): Flow<List<DecisionRecommendationEntity>>

    @Query("SELECT * FROM decision_recommendation WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getBySession(sessionId: String): List<DecisionRecommendationEntity>

    @Query("UPDATE decision_recommendation SET selected = 1 WHERE sessionId = :sessionId AND categoryId = :categoryId")
    suspend fun markSelected(sessionId: String, categoryId: String)

    @Query("UPDATE decision_recommendation SET rejected = 1 WHERE sessionId = :sessionId AND categoryId = :categoryId")
    suspend fun markRejected(sessionId: String, categoryId: String)
}