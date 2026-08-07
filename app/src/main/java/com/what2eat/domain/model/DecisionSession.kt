package com.what2eat.domain.model

/**
 * 决策会话。
 */
data class DecisionSession(
    val id: String,
    val decisionMode: DecisionMode = DecisionMode.CATEGORY_FIRST,
    val status: SessionStatus = SessionStatus.DRAFT,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val mealModes: Set<MealMode> = emptySet(),
    val moodTags: Set<MoodTag> = emptySet(),
    val budgetLevel: BudgetLevel = BudgetLevel.UNLIMITED,
    val distanceLevel: DistanceLevel = DistanceLevel.UNLIMITED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
