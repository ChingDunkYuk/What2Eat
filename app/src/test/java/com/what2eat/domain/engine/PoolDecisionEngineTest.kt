package com.what2eat.domain.engine

import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.MealMode
import com.what2eat.domain.model.SavedOptionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PoolDecisionEngine 单元测试。
 * 覆盖硬过滤（停用/列表/硬排除/换一个/用餐方式）、评分（偏好/新鲜度/待尝试）、
 * 加权随机（seed 可复现、单候选、耗尽、权重下限、匹配度分档）。
 */
class PoolDecisionEngineTest {

    private fun candidate(
        id: String,
        type: SavedOptionType = SavedOptionType.RESTAURANT,
        collections: Set<CollectionType> = setOf(CollectionType.FREQUENT),
        enabled: Boolean = true,
        preferenceValues: List<Int> = emptyList(),
        hardExcludedByAny: Boolean = false,
        lastChosenDaysAgo: Int? = null
    ) = PoolCandidateInput(
        optionId = id,
        name = id,
        optionType = type,
        collections = collections,
        enabled = enabled,
        preferenceValues = preferenceValues,
        hardExcludedByAny = hardExcludedByAny,
        lastChosenDaysAgo = lastChosenDaysAgo
    )

    private fun context(
        mealMode: MealMode = MealMode.ANY,
        collections: Set<CollectionType> = setOf(CollectionType.FREQUENT)
    ) = PoolDecisionContext(mealMode, collections)

    // ── 硬过滤 ──

    // 1. 停用选项被过滤
    @Test
    fun disabledCandidate_isFiltered() {
        val result = PoolDecisionEngine.recommend(
            candidates = listOf(candidate("a", enabled = false), candidate("b")),
            context = context()
        )
        assertEquals(listOf("b"), result.allSurviving.map { it.optionId })
    }

    // 2. 不在所选列表被过滤
    @Test
    fun notInSelectedCollections_isFiltered() {
        val result = PoolDecisionEngine.recommend(
            candidates = listOf(
                candidate("a", collections = setOf(CollectionType.WANT_TO_TRY)),
                candidate("b", collections = setOf(CollectionType.FREQUENT))
            ),
            context = context(collections = setOf(CollectionType.FREQUENT))
        )
        assertEquals(listOf("b"), result.allSurviving.map { it.optionId })
    }

    // 3. 任一人物硬排除被过滤
    @Test
    fun hardExcludedByAny_isFiltered() {
        val result = PoolDecisionEngine.recommend(
            candidates = listOf(candidate("a", hardExcludedByAny = true), candidate("b")),
            context = context()
        )
        assertEquals(listOf("b"), result.allSurviving.map { it.optionId })
    }

    // 4. rejectedIds 被过滤
    @Test
    fun rejectedIds_areFiltered() {
        val result = PoolDecisionEngine.recommend(
            candidates = listOf(candidate("a"), candidate("b")),
            context = context(),
            rejectedIds = setOf("a")
        )
        assertEquals(listOf("b"), result.allSurviving.map { it.optionId })
    }

    // 5. 用餐方式 DINE_OUT 只留 RESTAURANT + FOOD_CATEGORY
    @Test
    fun dineOutMode_keepsRestaurantAndFoodCategory() {
        val result = PoolDecisionEngine.recommend(
            candidates = listOf(
                candidate("restaurant", type = SavedOptionType.RESTAURANT),
                candidate("takeout", type = SavedOptionType.TAKEOUT_STORE),
                candidate("home", type = SavedOptionType.HOME_MEAL),
                candidate("category", type = SavedOptionType.FOOD_CATEGORY)
            ),
            context = context(mealMode = MealMode.DINE_OUT)
        )
        assertEquals(setOf("restaurant", "category"), result.allSurviving.map { it.optionId }.toSet())
    }

    // 6. 用餐方式 TAKEOUT 只留 TAKEOUT_STORE + FOOD_CATEGORY
    @Test
    fun takeoutMode_keepsTakeoutStoreAndFoodCategory() {
        val result = PoolDecisionEngine.recommend(
            candidates = listOf(
                candidate("restaurant", type = SavedOptionType.RESTAURANT),
                candidate("takeout", type = SavedOptionType.TAKEOUT_STORE),
                candidate("home", type = SavedOptionType.HOME_MEAL),
                candidate("category", type = SavedOptionType.FOOD_CATEGORY)
            ),
            context = context(mealMode = MealMode.TAKEOUT)
        )
        assertEquals(setOf("takeout", "category"), result.allSurviving.map { it.optionId }.toSet())
    }

    // 7. 用餐方式 COOK_HOME 只留 HOME_MEAL + FOOD_CATEGORY
    @Test
    fun cookHomeMode_keepsHomeMealAndFoodCategory() {
        val result = PoolDecisionEngine.recommend(
            candidates = listOf(
                candidate("restaurant", type = SavedOptionType.RESTAURANT),
                candidate("takeout", type = SavedOptionType.TAKEOUT_STORE),
                candidate("home", type = SavedOptionType.HOME_MEAL),
                candidate("category", type = SavedOptionType.FOOD_CATEGORY)
            ),
            context = context(mealMode = MealMode.COOK_HOME)
        )
        assertEquals(setOf("home", "category"), result.allSurviving.map { it.optionId }.toSet())
    }

    // 8. 用餐方式 ANY 全部通过
    @Test
    fun anyMode_keepsAllTypes() {
        val result = PoolDecisionEngine.recommend(
            candidates = listOf(
                candidate("restaurant", type = SavedOptionType.RESTAURANT),
                candidate("takeout", type = SavedOptionType.TAKEOUT_STORE),
                candidate("home", type = SavedOptionType.HOME_MEAL)
            ),
            context = context(mealMode = MealMode.ANY)
        )
        assertEquals(3, result.allSurviving.size)
    }

    // 9. 候选耗尽（全部被拒）
    @Test
    fun allRejected_isExhausted() {
        val result = PoolDecisionEngine.recommend(
            candidates = listOf(candidate("a"), candidate("b")),
            context = context(),
            rejectedIds = setOf("a", "b")
        )
        assertTrue(result.exhausted)
        assertNull(result.recommended)
        assertTrue(result.allSurviving.isEmpty())
    }

    // 10. 空候选列表 → 耗尽
    @Test
    fun emptyCandidates_isExhausted() {
        val result = PoolDecisionEngine.recommend(
            candidates = emptyList(),
            context = context()
        )
        assertTrue(result.exhausted)
        assertNull(result.recommended)
    }

    // ── 评分 ──

    // 11. 偏好均值拉高权重（排序断言）
    @Test
    fun higherPreference_getsHigherWeight() {
        val result = PoolDecisionEngine.recommend(
            candidates = listOf(
                candidate("liked", preferenceValues = listOf(2, 2)),
                candidate("disliked", preferenceValues = listOf(-2, -2))
            ),
            context = context()
        )
        assertEquals("liked", result.allSurviving.first().optionId)
        assertEquals("disliked", result.allSurviving.last().optionId)
        assertTrue(result.allSurviving.first().weight > result.allSurviving.last().weight)
    }

    // 12. 最近选过（<3 天）权重低于从未选中
    @Test
    fun recentlyChosen_weightLowerThanNeverChosen() {
        val result = PoolDecisionEngine.recommend(
            candidates = listOf(
                candidate("recent", lastChosenDaysAgo = 1),
                candidate("never", lastChosenDaysAgo = null)
            ),
            context = context()
        )
        val byId = result.allSurviving.associateBy { it.optionId }
        assertTrue(byId.getValue("never").weight > byId.getValue("recent").weight)
    }

    // 13. 从未选中获得加成原因；待尝试获得加成
    @Test
    fun neverChosen_andWantToTry_bonusesApplied() {
        val result = PoolDecisionEngine.recommend(
            candidates = listOf(
                candidate(
                    "plain",
                    collections = setOf(CollectionType.FREQUENT),
                    lastChosenDaysAgo = 30
                ),
                candidate(
                    "wanted",
                    collections = setOf(CollectionType.WANT_TO_TRY),
                    lastChosenDaysAgo = null
                )
            ),
            context = context(collections = setOf(CollectionType.FREQUENT, CollectionType.WANT_TO_TRY))
        )
        val wanted = result.allSurviving.first { it.optionId == "wanted" }
        val plain = result.allSurviving.first { it.optionId == "plain" }

        // wanted: 100 + 15(从未) + 10(待尝试) = 125；plain: 100
        assertEquals(125.0, wanted.weight, 0.001)
        assertEquals(100.0, plain.weight, 0.001)
        assertTrue(PoolReasonType.POOL_NEVER_CHOSEN in wanted.reasons)
        assertTrue(PoolReasonType.POOL_WANT_TO_TRY in wanted.reasons)
    }

    // 14. 权重下限 1.0（极端负分不产生非正权重）
    @Test
    fun extremeNegative_weightFlooredAtOne() {
        val result = PoolDecisionEngine.recommend(
            candidates = listOf(
                candidate(
                    "worst",
                    preferenceValues = listOf(-2, -2),
                    lastChosenDaysAgo = 0,
                    collections = setOf(CollectionType.FREQUENT)
                )
            ),
            context = context()
        )
        // 100 - 40(偏好) - 60(刚选) = 0 → clamp 1.0
        assertEquals(1.0, result.allSurviving.first().weight, 0.001)
    }

    // 15. 匹配度分档：高偏好 + 从未 + 待尝试 = HIGH；刚吃过 + 反感 = LOW
    @Test
    fun matchLevel_bands() {
        val result = PoolDecisionEngine.recommend(
            candidates = listOf(
                candidate(
                    "high",
                    preferenceValues = listOf(2),
                    collections = setOf(CollectionType.WANT_TO_TRY),
                    lastChosenDaysAgo = null
                ),
                candidate(
                    "low",
                    preferenceValues = listOf(-2),
                    lastChosenDaysAgo = 1
                )
            ),
            context = context(collections = setOf(CollectionType.FREQUENT, CollectionType.WANT_TO_TRY))
        )
        val byId = result.allSurviving.associateBy { it.optionId }
        assertEquals(MatchLevel.HIGH, byId.getValue("high").matchLevel)
        assertEquals(MatchLevel.LOW, byId.getValue("low").matchLevel)
    }

    // ── 加权随机 ──

    // 16. 固定 seed 结果可复现
    @Test
    fun sameSeed_sameResult() {
        val candidates = (1..20).map { candidate("opt$it") }
        val r1 = PoolDecisionEngine.recommend(candidates, context(), seed = 42L)
        val r2 = PoolDecisionEngine.recommend(candidates, context(), seed = 42L)
        assertEquals(r1.recommended?.optionId, r2.recommended?.optionId)
        assertNotNull(r1.recommended)
        assertFalse(r1.exhausted)
    }

    // 17. 单候选直接返回
    @Test
    fun singleCandidate_returnsDirectly() {
        val result = PoolDecisionEngine.recommend(
            candidates = listOf(candidate("only")),
            context = context(),
            seed = 7L
        )
        assertEquals("only", result.recommended?.optionId)
        assertEquals(1, result.allSurviving.size)
    }
}
