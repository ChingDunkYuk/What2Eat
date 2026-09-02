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
    /** 最终确认的分类 id（COMPLETED 时有值） */
    val selectedCategoryId: String? = null,
    /** 最终确认的吃饭池选项 id（POOL_FIRST 模式 COMPLETED 时有值） */
    val selectedOptionId: String? = null,
    /** 换一个次数 */
    val rerollCount: Int = 0,
    /** 最终推荐权重（调试用） */
    val finalWeight: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
