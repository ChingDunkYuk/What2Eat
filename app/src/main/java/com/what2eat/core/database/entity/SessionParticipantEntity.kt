package com.what2eat.core.database.entity

import androidx.room.Entity

/**
 * 决策会话参与者 Room 实体。
 * 联合主键：sessionId + personId
 */
@Entity(
    tableName = "session_participant",
    primaryKeys = ["sessionId", "personId"]
)
data class SessionParticipantEntity(
    val sessionId: String,
    val personId: String,
    val selectionOrder: Int,
    val completed: Boolean
)
