package com.what2eat.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 决策会话 Room 实体。
 * mealModes 和 moodTags 以逗号分隔的 ordinal 存储。
 */
@Entity(tableName = "decision_session")
data class DecisionSessionEntity(
    @PrimaryKey
    val id: String,
    val decisionMode: Int,
    val status: Int,
    val startedAt: Long,
    val completedAt: Long?,
    val mealModes: String,  // 逗号分隔的 MealMode ordinal
    val moodTags: String,   // 逗号分隔的 MoodTag ordinal
    val budgetLevel: Int,
    val distanceLevel: Int,
    val selectedCategoryId: String?,
    val rerollCount: Int,
    val finalWeight: Double,
    val createdAt: Long,
    val updatedAt: Long
)
