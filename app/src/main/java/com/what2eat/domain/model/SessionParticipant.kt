package com.what2eat.domain.model

/**
 * 决策会话参与者。
 * 联合主键：sessionId + personId
 */
data class SessionParticipant(
    val sessionId: String,
    val personId: String,
    val selectionOrder: Int = 0,
    val completed: Boolean = false
)
