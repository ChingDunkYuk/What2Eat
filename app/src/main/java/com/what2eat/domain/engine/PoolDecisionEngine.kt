package com.what2eat.domain.engine

import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.MealMode
import com.what2eat.domain.model.SavedOptionType
import kotlin.random.Random

/**
 * 吃饭池决策引擎输入：单个候选选项。
 *
 * ViewModel 负责从 SavedOption/人物偏好组装；Engine 不依赖 Android/Room/Compose。
 */
data class PoolCandidateInput(
    val optionId: String,
    val name: String,
    val optionType: SavedOptionType,
    val collections: Set<CollectionType> = emptySet(),
    val enabled: Boolean = true,
    /** 各启用人物对此选项的偏好值（-2..2），空列表=无人评分（等同 NEUTRAL） */
    val preferenceValues: List<Int> = emptyList(),
    /** 任一启用人物 hardExcluded */
    val hardExcludedByAny: Boolean = false,
    /** 距上次被选中过去的天数；null=从未被选中 */
    val lastChosenDaysAgo: Int? = null
)

/**
 * 吃饭池决策引擎输入：本次条件。
 *
 * mealMode 为 UI 层 resolve 后的单选值（ANY=不过滤类型）。
 */
data class PoolDecisionContext(
    val mealMode: MealMode = MealMode.ANY,
    val selectedCollections: Set<CollectionType> = emptySet()
)

/** 池决策推荐原因类型（UI 生成自然中文，Engine 不硬编码文案） */
enum class PoolReasonType {
    POOL_LIKED,           // 大家喜欢的店
    POOL_NEVER_CHOSEN,    // 还没去过/没点过
    POOL_RECENTLY_CHOSEN, // 最近刚选过
    POOL_WANT_TO_TRY      // 一直在待尝试清单
}

/** 单个池候选的评分结果（含权重与原因）。 */
data class PoolRecommendationItem(
    val optionId: String,
    val name: String,
    val weight: Double,
    val matchLevel: MatchLevel,
    val reasons: List<PoolReasonType>
)

/** 池决策推荐结果。 */
data class PoolRecommendationResult(
    /** 最终推荐（加权随机选出的一个）；无候选或全部换完时为 null */
    val recommended: PoolRecommendationItem?,
    /** 所有通过硬过滤且未被拒绝的候选（含权重），已按权重降序 */
    val allSurviving: List<PoolRecommendationItem>,
    /** 是否因候选耗尽而无推荐 */
    val exhausted: Boolean
)

/**
 * 吃饭池决策引擎。
 *
 * 流程与 [DefaultDecisionEngine] 同构：硬过滤 → 评分 → 加权随机。
 * 纯 Kotlin，无 Android 依赖，可独立单元测试；
 * 支持固定 seed：相同输入 + 相同 seed 必须得到相同结果。
 */
object PoolDecisionEngine {

    private const val BASE_SCORE = 100.0

    // 偏好均值（-2..2）× 20 → -40..+40
    private const val PREFERENCE_FACTOR = 20.0

    // 新鲜度
    private const val NEVER_CHOSEN_BONUS = 15.0
    private const val RECENT_CHOSEN_DAYS = 3
    private const val RECENT_CHOSEN_PENALTY = 60.0
    private const val SOMEWHAT_RECENT_DAYS = 14
    private const val SOMEWHAT_RECENT_PENALTY = 25.0

    // 待尝试列表加成
    private const val WANT_TO_TRY_BONUS = 10.0

    /** 匹配度阈值 */
    private const val HIGH_THRESHOLD = 140.0
    private const val MEDIUM_THRESHOLD = 105.0

    /** 权重下限（保证加权随机不出现非正权重） */
    private const val MIN_WEIGHT = 1.0

    /**
     * 从吃饭池候选中按条件挑一个。
     *
     * @param rejectedIds 本轮已点击"换一个"的选项 id
     * @param seed 固定随机种子（测试用）；null = 真随机
     */
    fun recommend(
        candidates: List<PoolCandidateInput>,
        context: PoolDecisionContext,
        rejectedIds: Set<String> = emptySet(),
        seed: Long? = null
    ): PoolRecommendationResult {
        val surviving = candidates.mapNotNull { c ->
            if (hardFilter(c, context, rejectedIds)) null else score(c)
        }

        if (surviving.isEmpty()) {
            return PoolRecommendationResult(null, emptyList(), exhausted = true)
        }

        val sorted = surviving.sortedByDescending { it.weight }

        val rng = if (seed != null) Random(seed) else Random.Default
        val recommended = weightedPick(sorted, rng)

        return PoolRecommendationResult(
            recommended = recommended,
            allSurviving = sorted,
            exhausted = false
        )
    }

    // ── 硬过滤 ──

    /** 返回 true = 被过滤排除 */
    private fun hardFilter(
        c: PoolCandidateInput,
        context: PoolDecisionContext,
        rejectedIds: Set<String>
    ): Boolean {
        // 1. 停用
        if (!c.enabled) return true
        // 2. 任一启用人物硬排除
        if (c.hardExcludedByAny) return true
        // 3. 本轮已点击"换一个"
        if (c.optionId in rejectedIds) return true
        // 4. 不在所选列表
        if (c.collections.none { it in context.selectedCollections }) return true
        // 5. 与用餐方式类型冲突
        if (!matchesMealMode(c.optionType, context.mealMode)) return true

        return false
    }

    /** 用餐方式 → 选项类型匹配（餐饮类型恒通过） */
    private fun matchesMealMode(type: SavedOptionType, mode: MealMode): Boolean {
        if (mode == MealMode.ANY) return true
        if (type == SavedOptionType.FOOD_CATEGORY) return true
        return when (mode) {
            MealMode.DINE_OUT, MealMode.PACK -> type == SavedOptionType.RESTAURANT
            MealMode.TAKEOUT -> type == SavedOptionType.TAKEOUT_STORE
            MealMode.COOK_HOME -> type == SavedOptionType.HOME_MEAL
            else -> true
        }
    }

    // ── 评分 ──

    private fun score(c: PoolCandidateInput): PoolRecommendationItem {
        val reasons = mutableListOf<PoolReasonType>()

        // 人物偏好（均值；无人评分 = 0）
        val preferenceAvg = if (c.preferenceValues.isEmpty()) 0.0
        else c.preferenceValues.average()
        if (preferenceAvg >= 1.0) reasons.add(PoolReasonType.POOL_LIKED)

        // 新鲜度
        val freshnessScore = when {
            c.lastChosenDaysAgo == null -> {
                reasons.add(PoolReasonType.POOL_NEVER_CHOSEN)
                NEVER_CHOSEN_BONUS
            }
            c.lastChosenDaysAgo < RECENT_CHOSEN_DAYS -> {
                reasons.add(PoolReasonType.POOL_RECENTLY_CHOSEN)
                -RECENT_CHOSEN_PENALTY
            }
            c.lastChosenDaysAgo < SOMEWHAT_RECENT_DAYS -> {
                reasons.add(PoolReasonType.POOL_RECENTLY_CHOSEN)
                -SOMEWHAT_RECENT_PENALTY
            }
            else -> 0.0
        }

        // 待尝试加成
        val wantToTryScore = if (CollectionType.WANT_TO_TRY in c.collections) {
            reasons.add(PoolReasonType.POOL_WANT_TO_TRY)
            WANT_TO_TRY_BONUS
        } else 0.0

        val weight = maxOf(
            MIN_WEIGHT,
            BASE_SCORE + preferenceAvg * PREFERENCE_FACTOR + freshnessScore + wantToTryScore
        )

        return PoolRecommendationItem(
            optionId = c.optionId,
            name = c.name,
            weight = weight,
            matchLevel = matchLevel(weight),
            reasons = reasons
        )
    }

    private fun matchLevel(weight: Double): MatchLevel {
        return when {
            weight >= HIGH_THRESHOLD -> MatchLevel.HIGH
            weight >= MEDIUM_THRESHOLD -> MatchLevel.MEDIUM
            else -> MatchLevel.LOW
        }
    }

    // ── 加权随机（与 DefaultDecisionEngine 同实现）──

    private fun weightedPick(items: List<PoolRecommendationItem>, rng: Random): PoolRecommendationItem {
        if (items.size == 1) return items[0]
        val total = items.sumOf { it.weight }
        var r = rng.nextDouble() * total
        for (item in items) {
            r -= item.weight
            if (r <= 0) return item
        }
        return items.last()
    }
}
