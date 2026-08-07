package com.what2eat.domain.engine

import com.what2eat.domain.model.BudgetLevel
import com.what2eat.domain.model.MealMode
import com.what2eat.domain.model.MoodTag
import com.what2eat.domain.model.SelectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DecisionEngine 单元测试。
 * 覆盖 Stage 2.2 要求的 19 项重点用例。
 */
class DecisionEngineTest {

    private val engine = DefaultDecisionEngine()

    private val KLAUS = "person_primary"
    private val QING = "person_secondary"

    private fun candidate(
        id: String,
        selections: Map<String, SelectionType> = mapOf(KLAUS to SelectionType.WANT),
        priceLevel: CategoryPriceLevel = CategoryPriceLevel.MEDIUM,
        mealModes: Set<MealMode> = setOf(MealMode.DINE_OUT, MealMode.TAKEOUT, MealMode.PACK),
        attributes: Set<CategoryAttribute> = emptySet()
    ) = DecisionCandidateInput(
        categoryId = id,
        categoryName = id,
        parentCategoryName = "一类",
        priceLevel = priceLevel,
        supportedMealModes = mealModes,
        attributes = attributes,
        selectionsByPerson = selections
    )

    private fun participant(
        id: String,
        longTerm: Map<String, Int> = emptyMap(),
        hardExcluded: Set<String> = emptySet()
    ) = ParticipantPreference(
        personId = id,
        name = id,
        isPrimary = id == KLAUS,
        longTermLevelByCategory = longTerm,
        hardExcludedCategoryIds = hardExcluded
    )

    private fun context(
        mealModes: Set<MealMode> = emptySet(),
        moodTags: Set<MoodTag> = emptySet(),
        budget: BudgetLevel = BudgetLevel.UNLIMITED
    ) = DecisionContext(mealModes, moodTags, budget)

    private fun klausWith(longTerm: Map<String, Int> = emptyMap(), hardExcluded: Set<String> = emptySet()) =
        participant(KLAUS, longTerm, hardExcluded)

    private fun qingWith(longTerm: Map<String, Int> = emptyMap(), hardExcluded: Set<String> = emptySet()) =
        participant(QING, longTerm, hardExcluded)

    // 1. 一个候选
    @Test
    fun singleCandidate_returnsIt() {
        val result = engine.recommend(
            candidates = listOf(candidate("a")),
            participants = listOf(klausWith()),
            context = context(),
            history = emptyList()
        )
        assertFalse(result.exhausted)
        assertNotNull(result.recommended)
        assertEquals("a", result.recommended!!.categoryId)
        assertEquals(1, result.allSurviving.size)
    }

    // 2. 两个候选
    @Test
    fun twoCandidates_returnsOne() {
        val result = engine.recommend(
            candidates = listOf(candidate("a"), candidate("b")),
            participants = listOf(klausWith()),
            context = context(),
            history = emptyList()
        )
        assertFalse(result.exhausted)
        assertNotNull(result.recommended)
        assertTrue(result.recommended!!.categoryId in setOf("a", "b"))
        assertEquals(2, result.allSurviving.size)
    }

    // 3 + 17. 固定 seed 可复现
    @Test
    fun fixedSeed_isReproducible() {
        val candidates = listOf(
            candidate("a", attributes = setOf(CategoryAttribute.HOTPOT)),
            candidate("b", attributes = setOf(CategoryAttribute.FAST_FOOD)),
            candidate("c", attributes = setOf(CategoryAttribute.JAPANESE))
        )
        val r1 = engine.recommend(candidates, listOf(klausWith()), context(), emptyList(), seed = 42L)
        val r2 = engine.recommend(candidates, listOf(klausWith()), context(), emptyList(), seed = 42L)
        assertEquals(r1.recommended!!.categoryId, r2.recommended!!.categoryId)
        assertEquals(r1.allSurviving.map { it.weight }, r2.allSurviving.map { it.weight })
    }

    // 4. 权重负数修正为 1
    @Test
    fun negativeWeight_clampedToAtLeastOne() {
        val candidates = listOf(
            candidate(
                "a",
                selections = mapOf(KLAUS to SelectionType.WANT),
                attributes = setOf(CategoryAttribute.HEAVY) // LIGHT 状态扣分
            )
        )
        val result = engine.recommend(
            candidates = candidates,
            participants = listOf(klausWith(longTerm = mapOf("a" to -2))),
            context = context(moodTags = setOf(MoodTag.LIGHT), budget = BudgetLevel.UNDER_30),
            history = listOf(MealHistoryInput("a", lastEatenDaysAgo = 1)) // 昨天吃过 -80
        )
        assertNotNull(result.recommended)
        assertTrue("权重必须 >= 1，实际 ${result.recommended!!.weight}", result.recommended!!.weight >= 1.0)
    }

    // 5. 双方都 WANT
    @Test
    fun bothWant_generatesBothWantReason() {
        val result = engine.recommend(
            candidates = listOf(candidate("a", mapOf(KLAUS to SelectionType.WANT, QING to SelectionType.WANT))),
            participants = listOf(klausWith(), qingWith()),
            context = context(),
            history = emptyList()
        )
        assertTrue(result.recommended!!.reasons.any { it.type == ReasonType.BOTH_WANT })
    }

    // 6. 一方 WANT 一方 ACCEPT
    @Test
    fun oneWantOneAccept_generatesMixedReason() {
        val result = engine.recommend(
            candidates = listOf(candidate("a", mapOf(KLAUS to SelectionType.WANT, QING to SelectionType.ACCEPT))),
            participants = listOf(klausWith(), qingWith()),
            context = context(),
            history = emptyList()
        )
        assertTrue(result.recommended!!.reasons.any { it.type == ReasonType.ONE_WANT_ONE_ACCEPT })
    }

    // 7. 双方长期偏好差异：喜欢的一方权重更高
    @Test
    fun longTermPreference_affectsWeight() {
        val liked = candidate(
            "liked",
            selections = mapOf(KLAUS to SelectionType.WANT, QING to SelectionType.WANT)
        )
        val disliked = candidate(
            "disliked",
            selections = mapOf(KLAUS to SelectionType.WANT, QING to SelectionType.WANT)
        )
        val result = engine.recommend(
            candidates = listOf(liked, disliked),
            participants = listOf(
                klausWith(longTerm = mapOf("liked" to 2, "disliked" to -2)),
                qingWith(longTerm = mapOf("liked" to 1, "disliked" to -1))
            ),
            context = context(),
            history = emptyList()
        )
        val likedWeight = result.allSurviving.first { it.categoryId == "liked" }.weight
        val dislikedWeight = result.allSurviving.first { it.categoryId == "disliked" }.weight
        assertTrue("喜欢应高于不喜欢: $likedWeight vs $dislikedWeight", likedWeight > dislikedWeight)
    }

    // 8. 近期刚吃过：权重明显降低（但可换，不硬排除）
    @Test
    fun recentlyEaten_reducesWeightButNotExcludes() {
        val fresh = candidate("fresh")
        val other = candidate("other")
        val result = engine.recommend(
            candidates = listOf(fresh, other),
            participants = listOf(klausWith()),
            context = context(),
            history = listOf(MealHistoryInput("fresh", lastEatenDaysAgo = 1))
        )
        val freshWeight = result.allSurviving.first { it.categoryId == "fresh" }.weight
        val otherWeight = result.allSurviving.first { it.categoryId == "other" }.weight
        assertTrue("刚吃过权重应降低: $freshWeight vs $otherWeight", freshWeight < otherWeight)
    }

    // 9. 从未吃过：加分
    @Test
    fun neverEaten_generatesReasonAndBonus() {
        val result = engine.recommend(
            candidates = listOf(candidate("a")),
            participants = listOf(klausWith()),
            context = context(),
            history = listOf(MealHistoryInput("a", lastEatenDaysAgo = null))
        )
        assertTrue(result.recommended!!.reasons.any { it.type == ReasonType.NEVER_EATEN })
    }

    // 10. hardExcluded 永不出现
    @Test
    fun hardExcluded_neverInSurviving() {
        val result = engine.recommend(
            candidates = listOf(candidate("a"), candidate("b")),
            participants = listOf(klausWith(hardExcluded = setOf("a"))),
            context = context(),
            history = emptyList()
        )
        assertFalse(result.allSurviving.any { it.categoryId == "a" })
        assertTrue(result.allSurviving.any { it.categoryId == "b" })
    }

    // 11. NOT_TODAY 永不出现
    @Test
    fun notToday_neverInSurviving() {
        val result = engine.recommend(
            candidates = listOf(
                candidate("a", mapOf(KLAUS to SelectionType.NOT_TODAY)),
                candidate("b", mapOf(KLAUS to SelectionType.WANT))
            ),
            participants = listOf(klausWith()),
            context = context(),
            history = emptyList()
        )
        assertFalse(result.allSurviving.any { it.categoryId == "a" })
        assertTrue(result.allSurviving.any { it.categoryId == "b" })
    }

    // 12. rejectedIds 永不重新出现
    @Test
    fun rejectedIds_neverReappear() {
        val result = engine.recommend(
            candidates = listOf(candidate("a"), candidate("b"), candidate("c")),
            participants = listOf(klausWith()),
            context = context(),
            history = emptyList(),
            rejectedIds = setOf("a", "b")
        )
        assertFalse(result.allSurviving.any { it.categoryId == "a" })
        assertFalse(result.allSurviving.any { it.categoryId == "b" })
        assertTrue(result.allSurviving.any { it.categoryId == "c" })
    }

    // 13. 换到最后一个：只剩 1 个候选
    @Test
    fun rerollUntilLast_keepsOneSurviving() {
        val result = engine.recommend(
            candidates = listOf(candidate("a"), candidate("b")),
            participants = listOf(klausWith()),
            context = context(),
            history = emptyList(),
            rejectedIds = setOf("a")
        )
        assertEquals(1, result.allSurviving.size)
        assertEquals("b", result.recommended!!.categoryId)
    }

    // 14. 全部换完：exhausted
    @Test
    fun rerollAll_exhausted() {
        val result = engine.recommend(
            candidates = listOf(candidate("a"), candidate("b")),
            participants = listOf(klausWith()),
            context = context(),
            history = emptyList(),
            rejectedIds = setOf("a", "b")
        )
        assertTrue(result.exhausted)
        assertNull(result.recommended)
        assertTrue(result.allSurviving.isEmpty())
    }

    // 15. 单人评分：WANT 高分于 ACCEPT
    @Test
    fun singleScoring_wantHigherThanAccept() {
        val result = engine.recommend(
            candidates = listOf(
                candidate("want", mapOf(KLAUS to SelectionType.WANT)),
                candidate("accept", mapOf(KLAUS to SelectionType.ACCEPT))
            ),
            participants = listOf(klausWith()),
            context = context(),
            history = emptyList()
        )
        val want = result.allSurviving.first { it.categoryId == "want" }.weight
        val accept = result.allSurviving.first { it.categoryId == "accept" }.weight
        assertTrue("WANT 应高于 ACCEPT: $want vs $accept", want > accept)
    }

    // 16. 双人公平分：双方都 ACCEPT 时，较不喜欢一方仍被保护（权重不等于简单相加）
    @Test
    fun dualFairScore_usesMinAndAverage() {
        // 一方非常喜欢，另一方不喜欢：权重应明显低于"双方都喜欢"
        val result = engine.recommend(
            candidates = listOf(
                candidate("a", mapOf(KLAUS to SelectionType.WANT, QING to SelectionType.ACCEPT)),
                candidate("b", mapOf(KLAUS to SelectionType.WANT, QING to SelectionType.WANT))
            ),
            participants = listOf(klausWith(), qingWith()),
            context = context(),
            history = emptyList()
        )
        val a = result.allSurviving.first { it.categoryId == "a" }.weight
        val b = result.allSurviving.first { it.categoryId == "b" }.weight
        // "b" 双方 WANT，权重应高于"a"（一方仅 ACCEPT）
        assertTrue("双方 WANT 应更高: $a vs $b", b > a)
        // 权重不应是简单相加（100+40+100+40=280 之类），应处于 100~200 区间
        assertTrue("公平分应在合理区间: $b", b < 200.0)
    }

    // 双人：较不喜欢的一方起决定作用（min 主导）
    @Test
    fun dualFair_lessInterestedSideDominates() {
        val bothHappy = candidate("both", mapOf(KLAUS to SelectionType.WANT, QING to SelectionType.WANT))
        val oneReluctant = candidate("reluctant", mapOf(KLAUS to SelectionType.WANT, QING to SelectionType.ACCEPT))
        val result = engine.recommend(
            candidates = listOf(bothHappy, oneReluctant),
            participants = listOf(klausWith(), qingWith()),
            context = context(),
            history = emptyList()
        )
        val both = result.allSurviving.first { it.categoryId == "both" }.weight
        val rel = result.allSurviving.first { it.categoryId == "reluctant" }.weight
        assertTrue(both > rel)
    }

    // 硬过滤：用餐方式冲突
    @Test
    fun mealModeConflict_filtered() {
        val homeOnly = candidate("home", mealModes = setOf(MealMode.COOK_HOME))
        val result = engine.recommend(
            candidates = listOf(homeOnly),
            participants = listOf(klausWith()),
            context = context(mealModes = setOf(MealMode.DINE_OUT, MealMode.TAKEOUT)),
            history = emptyList()
        )
        assertTrue(result.exhausted)
        assertTrue(result.allSurviving.isEmpty())
    }

    // 状态匹配加分：MATCH_MOOD 原因出现，且匹配项权重更高
    @Test
    fun moodMatch_addsBonusAndReason() {
        val hotpot = candidate("hotpot", attributes = setOf(CategoryAttribute.HOTPOT))
        val salad = candidate("salad", attributes = setOf(CategoryAttribute.LIGHT))
        val result = engine.recommend(
            candidates = listOf(hotpot, salad),
            participants = listOf(klausWith()),
            context = context(moodTags = setOf(MoodTag.HOT)),
            history = emptyList()
        )
        // hotpot 匹配"想吃热的"应加分
        val hotpotW = result.allSurviving.first { it.categoryId == "hotpot" }.weight
        val saladW = result.allSurviving.first { it.categoryId == "salad" }.weight
        assertTrue("hotpot 应有状态加分: $hotpotW vs $saladW", hotpotW > saladW)
        // 匹配项应生成 MATCH_MOOD 原因（基于 surviving 项，避免加权随机选中其他项导致误报）
        val hotpotItem = result.allSurviving.first { it.categoryId == "hotpot" }
        assertTrue("hotpot 应含 MATCH_MOOD 原因", hotpotItem.reasons.any { it.type == ReasonType.MATCH_MOOD })
    }

    // 预算硬限制：UNDER_30 排除 HIGH/PREMIUM
    @Test
    fun hardBudget_under30_excludesPremium() {
        val premium = candidate("premium", priceLevel = CategoryPriceLevel.PREMIUM)
        val result = engine.recommend(
            candidates = listOf(premium),
            participants = listOf(klausWith()),
            context = context(budget = BudgetLevel.UNDER_30),
            history = emptyList()
        )
        assertTrue(result.exhausted)
        assertTrue(result.allSurviving.isEmpty())
    }
}