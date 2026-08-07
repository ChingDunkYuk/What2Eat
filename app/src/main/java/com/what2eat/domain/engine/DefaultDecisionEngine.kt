package com.what2eat.domain.engine

import com.what2eat.domain.model.BudgetLevel
import com.what2eat.domain.model.MealMode
import com.what2eat.domain.model.MoodTag
import com.what2eat.domain.model.SelectionType
import kotlin.random.Random

/**
 * 默认决策引擎实现。
 *
 * 流程（严格遵循）：
 * 硬过滤 → 评分 → 公平分 → 加权随机 → 最终推荐
 *
 * 纯 Kotlin，无 Android/Room/Compose/ViewModel 依赖，可独立单元测试。
 * 支持固定 seed：相同输入 + 相同 seed 得到相同结果。
 * 不使用随机数直接从候选列表抽取；一定是先算权重再做加权随机。
 */
class DefaultDecisionEngine : DecisionEngine {

    companion object {
        private const val BASE_SCORE = 100.0
        private const val SELECT_WANT = 40
        private const val SELECT_ACCEPT = 0

        // 长期偏好加分
        private const val LONG_VERY_LIKE = 30
        private const val LONG_LIKE = 15
        private const val LONG_NEUTRAL = 0
        private const val LONG_DISLIKE = -20
        private const val LONG_VERY_DISLIKE = -40

        /** 匹配度阈值 */
        private const val HIGH_THRESHOLD = 170.0
        private const val MEDIUM_THRESHOLD = 130.0

        /** 历史"最近没吃过"阈值（天） */
        private const val RECENT_WINDOW_DAYS = 14
    }

    override fun recommend(
        candidates: List<DecisionCandidateInput>,
        participants: List<ParticipantPreference>,
        context: DecisionContext,
        history: List<MealHistoryInput>,
        rejectedIds: Set<String>,
        seed: Long?
    ): RecommendationResult {
        val historyByCategory = history.associateBy { it.categoryId }
        val participantIds = participants.map { it.personId }.toSet()
        val isDual = participantIds.size >= 2

        // ── 硬过滤 ──
        val surviving = candidates.mapNotNull { c ->
            val catHistory = historyByCategory[c.categoryId]?.lastEatenDaysAgo
            hardFilter(c, participants, context, rejectedIds)?.let {
                score(c, participants, context, catHistory, isDual)
            }
        }

        if (surviving.isEmpty()) {
            return RecommendationResult(null, emptyList(), exhausted = true)
        }

        // 按权重降序（供展示）
        val sorted = surviving.sortedByDescending { it.weight }

        // ── 加权随机（不直接取最高分）──
        val rng = if (seed != null) Random(seed) else Random.Default
        val recommended = weightedPick(sorted, rng)

        return RecommendationResult(
            recommended = recommended,
            allSurviving = sorted,
            exhausted = false
        )
    }

    // ── 硬过滤 ──

    private fun hardFilter(
        c: DecisionCandidateInput,
        participants: List<ParticipantPreference>,
        context: DecisionContext,
        rejectedIds: Set<String>
    ): DecisionCandidateInput? {
        // 1. 分类 disabled
        if (c.disabled) return null

        // 5. 本轮已点击"换一个"
        if (c.categoryId in rejectedIds) return null

        // 2/3/4. 按参与者校验
        for (p in participants) {
            // 2. 任一参与人物长期 hardExcluded
            if (c.categoryId in p.hardExcludedCategoryIds) return null
            // 3. 任一参与人物本轮 NOT_TODAY
            if (c.selectionsByPerson[p.personId] == SelectionType.NOT_TODAY) return null
            // 4. 所有参与人物必须明确标记为 WANT 或 ACCEPT
            val sel = c.selectionsByPerson[p.personId]
            if (sel != SelectionType.WANT && sel != SelectionType.ACCEPT) return null
        }

        // 6. 与当前用餐方式冲突
        if (context.mealModes.isNotEmpty()) {
            val intersects = c.supportedMealModes.any { it in context.mealModes }
            if (!intersects) return null
        }

        // 7. 预算硬超限（明确上限时，如"30元以内"）直接过滤
        if (CategoryRules.budgetScore(c.priceLevel, context.budgetLevel).hardExceed) return null

        return c
    }

    // ── 评分 ──

    private fun score(
        c: DecisionCandidateInput,
        participants: List<ParticipantPreference>,
        context: DecisionContext,
        lastEatenDaysAgo: Int?,
        isDual: Boolean
    ): RecommendationItem {
        val reasons = mutableListOf<RecommendationReason>()

        // 条件加分（今天状态）
        val mood = CategoryRules.moodScore(c.attributes, context.moodTags)
        val conditionScore = mood.score
        if (mood.matched) reasons.add(RecommendationReason(ReasonType.MATCH_MOOD, "match_mood"))

        // 用餐方式
        val mealMode = CategoryRules.mealModeScore(c.supportedMealModes, context.mealModes)
        val mealScore = mealMode.score
        if (mealMode.matched) reasons.add(RecommendationReason(ReasonType.MATCH_MEAL_MODE, "match_meal_mode"))

        // 预算
        val budget = CategoryRules.budgetScore(c.priceLevel, context.budgetLevel)
        val budgetScore = budget.score
        if (budget.matched) reasons.add(RecommendationReason(ReasonType.MATCH_BUDGET, "match_budget"))

        // 历史
        val historyScore = CategoryRules.historyScore(lastEatenDaysAgo)
        if (lastEatenDaysAgo == null) {
            reasons.add(RecommendationReason(ReasonType.NEVER_EATEN, "never_eaten"))
        } else if (lastEatenDaysAgo > RECENT_WINDOW_DAYS) {
            reasons.add(RecommendationReason(ReasonType.NOT_EATEN_RECENTLY, "not_eaten_recently"))
        }

        // 长期偏好
        val anyLiked = participants.any { p ->
            (p.longTermLevelByCategory[c.categoryId] ?: 0) >= 1
        }
        if (anyLiked) reasons.add(RecommendationReason(ReasonType.LONG_TERM_LIKE, "long_term_like"))

        val selectionBonus = mutableMapOf<String, Int>()
        for (p in participants) {
            val sel = c.selectionsByPerson[p.personId]
            selectionBonus[p.personId] = if (sel == SelectionType.WANT) SELECT_WANT else SELECT_ACCEPT
        }

        // 双人公平原因
        if (isDual) {
            val wantCount = c.selectionsByPerson.count { it.value == SelectionType.WANT }
            when {
                wantCount == participants.size -> reasons.add(
                    RecommendationReason(ReasonType.BOTH_WANT, "both_want")
                )
                wantCount == 0 -> reasons.add(
                    RecommendationReason(ReasonType.BOTH_ACCEPT, "both_accept")
                )
                else -> reasons.add(
                    RecommendationReason(ReasonType.ONE_WANT_ONE_ACCEPT, "one_want_one_accept")
                )
            }
        }

        val weight = if (isDual) {
            val scores = participants.map { p ->
                BASE_SCORE + selectionBonus[p.personId]!! +
                    longTermBonus(p.longTermLevelByCategory[c.categoryId] ?: 0)
            }
            val average = scores.average()
            val minScore = scores.min()
            val fairScore = minScore * 0.7 + average * 0.3
            fairScore + conditionScore + mealScore + budgetScore + historyScore
        } else {
            val p = participants.firstOrNull()
            val selection = selectionBonus[p?.personId] ?: 0
            val longTerm = longTermBonus(p?.longTermLevelByCategory?.get(c.categoryId) ?: 0)
            BASE_SCORE + selection + longTerm + conditionScore + mealScore + budgetScore + historyScore
        }

        return RecommendationItem(
            categoryId = c.categoryId,
            categoryName = c.categoryName,
            parentCategoryName = c.parentCategoryName,
            weight = maxOf(1.0, weight),
            matchLevel = matchLevel(weight),
            reasons = reasons
        )
    }

    private fun longTermBonus(level: Int): Int {
        return when (level) {
            2 -> LONG_VERY_LIKE
            1 -> LONG_LIKE
            0 -> LONG_NEUTRAL
            -1 -> LONG_DISLIKE
            else -> LONG_VERY_DISLIKE
        }
    }

    private fun matchLevel(weight: Double): MatchLevel {
        return when {
            weight >= HIGH_THRESHOLD -> MatchLevel.HIGH
            weight >= MEDIUM_THRESHOLD -> MatchLevel.MEDIUM
            else -> MatchLevel.LOW
        }
    }

    // ── 加权随机 ──

    private fun weightedPick(items: List<RecommendationItem>, rng: Random): RecommendationItem {
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