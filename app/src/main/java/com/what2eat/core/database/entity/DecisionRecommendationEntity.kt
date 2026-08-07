package com.what2eat.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 决策推荐快照 Room 实体。
 * 每个会话的每次推荐（含换一个）记录一行，用于会话恢复一致、换一个不重复、调试原因。
 */
@Entity(
    tableName = "decision_recommendation",
    indices = [Index(value = ["sessionId"])]
)
data class DecisionRecommendationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val categoryId: String,
    val rank: Int,
    val weight: Double,
    val selected: Boolean,
    val rejected: Boolean,
    /** 推荐原因 textKey，逗号分隔 */
    val reasonKeys: String,
    val createdAt: Long
)