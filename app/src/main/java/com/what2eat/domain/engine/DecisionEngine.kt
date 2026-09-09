package com.what2eat.domain.engine

import com.what2eat.domain.model.BudgetLevel
import com.what2eat.domain.model.DistanceLevel
import com.what2eat.domain.model.MealMode
import com.what2eat.domain.model.MoodTag
import com.what2eat.domain.model.SelectionType

/**
 * 决策引擎输入：单个候选分类。
 * 评分所需的全部信息都在这里，Engine 不依赖 Android/Room/Compose/ViewModel。
 */
data class DecisionCandidateInput(
    val categoryId: String,
    val categoryName: String,
    val parentCategoryName: String? = null,
    val disabled: Boolean = false,
    val priceLevel: CategoryPriceLevel = CategoryPriceLevel.MEDIUM,
    val supportedMealModes: Set<MealMode> = DEFAULT_SUPPORTED_MEAL_MODES,
    val attributes: Set<CategoryAttribute> = emptySet(),
    /** 每位参与者对此分类的选择类型，key=personId */
    val selectionsByPerson: Map<String, SelectionType> = emptyMap()
) {
    companion object {
        val DEFAULT_SUPPORTED_MEAL_MODES: Set<MealMode> =
            setOf(MealMode.DINE_OUT, MealMode.TAKEOUT, MealMode.PACK)
    }
}

/**
 * 决策引擎输入：一位参与者的偏好。
 */
data class ParticipantPreference(
    val personId: String,
    val name: String,
    val isPrimary: Boolean = false,
    /** 长期偏好等级：categoryId -> -2(非常不喜欢)..2(非常喜欢) */
    val longTermLevelByCategory: Map<String, Int> = emptyMap(),
    /** 长期硬排除：categoryId 集合 */
    val hardExcludedCategoryIds: Set<String> = emptySet()
)

/**
 * 决策引擎输入：本次决定的上下文条件。
 * v0.9.0：新增 distanceLevel——此前条件页收集并持久化，但引擎零消费（假开关）。
 */
data class DecisionContext(
    val mealModes: Set<MealMode> = emptySet(),
    val moodTags: Set<MoodTag> = emptySet(),
    val budgetLevel: BudgetLevel = BudgetLevel.UNLIMITED,
    val distanceLevel: DistanceLevel = DistanceLevel.UNLIMITED
)

/**
 * 决策引擎输入：单条历史记录。
 * lastEatenDaysAgo = null 表示从未吃过。
 */
data class MealHistoryInput(
    val categoryId: String,
    val lastEatenDaysAgo: Int? = null
)

/**
 * 推荐原因类型。
 * UI 根据 textKey/type 生成自然中文，Engine 不硬编码整段文案。
 */
enum class ReasonType {
    BOTH_WANT,
    BOTH_ACCEPT,
    ONE_WANT_ONE_ACCEPT,
    LONG_TERM_LIKE,
    MATCH_MOOD,
    MATCH_MEAL_MODE,
    MATCH_BUDGET,
    NOT_EATEN_RECENTLY,
    NEVER_EATEN
}

/**
 * 一条推荐原因。
 */
data class RecommendationReason(
    val type: ReasonType,
    val textKey: String
)

enum class MatchLevel {
    HIGH,   // 很高
    MEDIUM, // 较高
    LOW     // 一般
}

/**
 * 单个候选的评分结果（含权重与原因）。
 */
data class RecommendationItem(
    val categoryId: String,
    val categoryName: String,
    val parentCategoryName: String? = null,
    val weight: Double,
    val matchLevel: MatchLevel,
    val reasons: List<RecommendationReason>
)

/**
 * 推荐结果。
 */
data class RecommendationResult(
    /** 最终推荐（加权随机选出的一个）；无候选或全部换完时为 null */
    val recommended: RecommendationItem?,
    /** 所有通过硬过滤且未被拒绝的候选（含权重），已按权重降序 */
    val allSurviving: List<RecommendationItem>,
    /** 是否因候选耗尽而无推荐 */
    val exhausted: Boolean
)

/**
 * 决策引擎接口。
 *
 * 纯 Kotlin，可独立单元测试，支持固定 seed：
 * 相同输入 + 相同 seed 必须得到相同结果。
 */
interface DecisionEngine {

    fun recommend(
        candidates: List<DecisionCandidateInput>,
        participants: List<ParticipantPreference>,
        context: DecisionContext,
        history: List<MealHistoryInput>,
        rejectedIds: Set<String> = emptySet(),
        seed: Long? = null
    ): RecommendationResult
}