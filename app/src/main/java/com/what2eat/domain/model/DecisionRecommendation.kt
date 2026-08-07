package com.what2eat.domain.model

/**
 * 决策推荐快照领域模型。
 * 记录某个会话中某次推荐的状态，用于恢复一致、换一个不重复、调试原因。
 */
data class DecisionRecommendation(
    val id: Long = 0,
    val sessionId: String,
    val categoryId: String,
    val rank: Int,
    val weight: Double,
    val selected: Boolean = false,
    val rejected: Boolean = false,
    val reasonKeys: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)