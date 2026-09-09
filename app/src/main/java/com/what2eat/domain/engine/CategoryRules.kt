package com.what2eat.domain.engine

import com.what2eat.domain.model.BudgetLevel
import com.what2eat.domain.model.DistanceLevel
import com.what2eat.domain.model.MealMode
import com.what2eat.domain.model.MoodTag

/**
 * 分类元数据与规则表（集中维护，不允许散落在 UI）。
 *
 * Stage 2.2 使用系统内置规则表（非 AI、非真实餐厅数据）。
 * 通过分类 id 映射到价格等级、支持的用餐方式、以及用于"今天状态"匹配的属性标签。
 */
object CategoryRules {

    /** 不在规则表中的分类使用默认信息（MEDIUM 价格、支持堂食/外卖/打包、无标签） */
    private val defaultInfo = CategoryInfo()

    /** 热点火锅/聚餐类 id 前缀 */
    private val hotpotIds = setOf(
        "category_hotpot", "category_hotpot_chaoshan_beef", "category_hotpot_sichuan",
        "category_hotpot_coconut_chicken", "category_hotpot_pork_stomach", "category_hotpot_skewer",
        "category_hotpot_grilled_fish", "category_hotpot_sauerkraut_fish", "category_home_hotpot"
    )

    private val bbqIds = setOf("category_bbq", "category_bbq_grilled_meat", "category_bbq_grill")

    private val japaneseIds = setOf("category_japanese", "category_japanese_sushi")

    private val noodleIds = setOf(
        "category_noodle_wonton", "category_noodle_beef", "category_noodle_japanese_ramen",
        "category_noodle_luosi", "category_noodle_guilin", "category_noodle_malatang",
        "category_home_cooked_noodles"
    )

    private val fastFoodIds = setOf(
        "category_fast_food_hamburger", "category_fast_food_fried_chicken",
        "category_fast_food_shaxian", "category_fast_food_convenience_store",
        "category_rice_claypot", "category_rice_fried"
    )

    private val regularMealIds = setOf(
        "category_cantonese", "category_chaoshan", "category_sichuan", "category_hunan",
        "category_northeast", "category_hakka", "category_hk_style", "category_stir_fry",
        "category_roast_meat", "category_japanese", "category_japanese_sushi", "category_korean",
        "category_thai", "category_vietnamese", "category_western_steak", "category_western_pasta",
        "category_western_pizza"
    )

    private val heavyIds = setOf(
        "category_sichuan", "category_hunan", "category_northeast", "category_noodle_malatang",
        "category_noodle_luosi", "category_hotpot_grilled_fish", "category_hotpot_sauerkraut_fish",
        "category_hotpot_sichuan", "category_bbq_grill", "category_fast_food_fried_chicken"
    )

    private val lightIds = setOf(
        "category_light_meal_salad", "category_home_clear_fridge",
        "category_noodle_guilin", "category_vietnamese"
    )

    private val meatIds = setOf(
        "category_bbq_grilled_meat", "category_bbq_grill", "category_western_steak",
        "category_hotpot_chaoshan_beef", "category_hotpot_sichuan", "category_hotpot_grilled_fish",
        "category_hotpot_sauerkraut_fish", "category_roast_meat", "category_rice_claypot",
        "category_fast_food_hamburger", "category_northeast", "category_hotpot_pork_stomach"
    )

    private val smallPortionIds = setOf(
        "category_light_meal_salad", "category_fast_food_convenience_store",
        "category_fast_food_shaxian", "category_noodle_wonton", "category_home_cooked_noodles",
        "category_home_clear_fridge", "category_noodle_guilin"
    )

    private val heavyMealIds = hotpotIds + bbqIds + setOf(
        "category_sichuan", "category_hunan", "category_northeast", "category_roast_meat"
    )

    private val dateFriendlyIds = setOf(
        "category_japanese", "category_japanese_sushi", "category_western_steak",
        "category_western_pasta", "category_western_pizza", "category_hk_style",
        "category_cantonese", "category_chaoshan", "category_thai", "category_vietnamese",
        "category_korean", "category_dessert_drinks"
    )

    private val hotIds = hotpotIds + noodleIds + bbqIds + setOf(
        "category_stir_fry", "category_roast_meat", "category_rice_claypot",
        "category_home_stir_fry", "category_home_hotpot"
    )

    private val homeCookIds = setOf(
        "category_home_cooked_noodles", "category_home_stir_fry", "category_home_hotpot",
        "category_home_frozen", "category_home_clear_fridge"
    )

    private val lowPriceIds = setOf(
        "category_fast_food_convenience_store", "category_fast_food_shaxian",
        "category_home_cooked_noodles", "category_home_clear_fridge", "category_home_frozen",
        "category_home_stir_fry", "category_fast_food_fried_chicken", "category_noodle_luosi",
        "category_noodle_guilin", "category_noodle_wonton", "category_noodle_beef",
        "category_noodle_malatang", "category_hotpot_skewer"
    )

    private val highPriceIds = setOf(
        "category_japanese", "category_japanese_sushi", "category_western_steak",
        "category_bbq_grilled_meat", "category_hotpot_chaoshan_beef", "category_hotpot_coconut_chicken",
        "category_hotpot_pork_stomach", "category_hk_style"
    )

    /**
     * 获取分类元数据。
     */
    fun infoFor(categoryId: String): CategoryInfo {
        val attrs = mutableSetOf<CategoryAttribute>()
        if (categoryId in hotpotIds) attrs += CategoryAttribute.HOTPOT
        if (categoryId in bbqIds) attrs += CategoryAttribute.BBQ
        if (categoryId in japaneseIds) attrs += CategoryAttribute.JAPANESE
        if (categoryId == "category_western_steak") attrs += CategoryAttribute.STEAK
        if (categoryId in regularMealIds) attrs += CategoryAttribute.REGULAR_MEAL
        if (categoryId in fastFoodIds) attrs += CategoryAttribute.FAST_FOOD
        if (categoryId in noodleIds) attrs += CategoryAttribute.NOODLE
        if (categoryId == "category_fast_food_convenience_store") attrs += CategoryAttribute.CONVENIENCE
        if (categoryId in hotIds) attrs += CategoryAttribute.HOT
        if (categoryId in lightIds) attrs += CategoryAttribute.LIGHT
        if (categoryId in heavyIds) attrs += CategoryAttribute.HEAVY
        if (categoryId in meatIds) attrs += CategoryAttribute.MEAT
        if (categoryId in smallPortionIds) attrs += CategoryAttribute.SMALL_PORTION
        if (categoryId in heavyMealIds) attrs += CategoryAttribute.HEAVY_MEAL
        if (categoryId in dateFriendlyIds) attrs += CategoryAttribute.DATE_FRIENDLY
        if (categoryId == "category_dessert_drinks") attrs += CategoryAttribute.SWEET

        val mealModes = if (categoryId in homeCookIds) {
            setOf(MealMode.COOK_HOME)
        } else {
            setOf(MealMode.DINE_OUT, MealMode.TAKEOUT, MealMode.PACK)
        }

        val price = when {
            categoryId in lowPriceIds -> CategoryPriceLevel.LOW
            categoryId in highPriceIds -> CategoryPriceLevel.HIGH
            else -> CategoryPriceLevel.MEDIUM
        }

        return CategoryInfo(
            priceLevel = price,
            supportedMealModes = mealModes,
            attributes = attrs
        )
    }

    /**
     * "今天状态"匹配加分。
     * 返回 (分数, 是否有正向匹配)。NO_REQUIREMENT 恒为 0。
     */
    fun moodScore(attributes: Set<CategoryAttribute>, moodTags: Set<MoodTag>): MoodScoreResult {
        var score = 0
        var matched = false
        fun add(delta: Int, positive: Boolean) {
            score += delta
            if (positive) matched = true
        }

        for (tag in moodTags) {
            when (tag) {
                MoodTag.GOOD_MEAL -> if (attributes.any { it in setOf(
                        CategoryAttribute.HOTPOT, CategoryAttribute.BBQ,
                        CategoryAttribute.JAPANESE, CategoryAttribute.STEAK,
                        CategoryAttribute.REGULAR_MEAL
                    ) }) add(15, true)

                MoodTag.QUICK -> if (attributes.any { it in setOf(
                        CategoryAttribute.FAST_FOOD, CategoryAttribute.NOODLE,
                        CategoryAttribute.CONVENIENCE
                    ) }) add(20, true)

                MoodTag.HOT -> if (attributes.any { it in setOf(
                        CategoryAttribute.HOT, CategoryAttribute.HOTPOT,
                        CategoryAttribute.NOODLE, CategoryAttribute.BBQ
                    ) }) add(15, true)

                MoodTag.LIGHT -> {
                    if (CategoryAttribute.LIGHT in attributes) add(15, true)
                    if (CategoryAttribute.HEAVY in attributes) add(-15, false)
                }

                MoodTag.HEAVY -> {
                    if (CategoryAttribute.HEAVY in attributes) add(15, true)
                    if (CategoryAttribute.LIGHT in attributes) add(-15, false)
                }

                MoodTag.MEATY -> if (attributes.any { it in setOf(
                        CategoryAttribute.MEAT, CategoryAttribute.BBQ,
                        CategoryAttribute.STEAK, CategoryAttribute.HOTPOT
                    ) }) add(15, true)

                MoodTag.NOT_TOO_FULL -> {
                    if (attributes.any { it in setOf(
                            CategoryAttribute.SMALL_PORTION, CategoryAttribute.LIGHT
                        ) }) add(15, true)
                    if (CategoryAttribute.HEAVY_MEAL in attributes) add(-15, false)
                }

                MoodTag.DATE -> if (CategoryAttribute.DATE_FRIENDLY in attributes) add(20, true)

                MoodTag.NO_QUEUE, MoodTag.NO_REQUIREMENT -> Unit
            }
        }
        return MoodScoreResult(score, matched)
    }

    /**
     * 预算与价格匹配。
     * 返回 (分数, 是否匹配, 是否硬性超预算需过滤)。
     * UNLIMITED 返回 (0, false, false)。
     */
    fun budgetScore(priceLevel: CategoryPriceLevel, budget: BudgetLevel): BudgetScoreResult {
        return when (budget) {
            BudgetLevel.UNLIMITED -> BudgetScoreResult(0, false, false)

            // 30 元以内为明确上限：HIGH/PREMIUM 硬过滤，LOW/MEDIUM 通过
            BudgetLevel.UNDER_30 -> when (priceLevel) {
                CategoryPriceLevel.LOW, CategoryPriceLevel.MEDIUM ->
                    BudgetScoreResult(10, true, false)
                CategoryPriceLevel.HIGH, CategoryPriceLevel.PREMIUM ->
                    BudgetScoreResult(0, false, true)
            }

            // 其余档位为软评分
            BudgetLevel.RANGE_30_60 -> softBudget(priceLevel, 2)
            BudgetLevel.RANGE_60_100 -> softBudget(priceLevel, 3)
            BudgetLevel.RANGE_100_200 -> softBudget(priceLevel, 4)
            BudgetLevel.OVER_200 -> softBudget(priceLevel, 5)
        }
    }

    private fun softBudget(priceLevel: CategoryPriceLevel, budgetNum: Int): BudgetScoreResult {
        val priceNum = when (priceLevel) {
            CategoryPriceLevel.LOW -> 1
            CategoryPriceLevel.MEDIUM -> 2
            CategoryPriceLevel.HIGH -> 3
            CategoryPriceLevel.PREMIUM -> 4
        }
        return when {
            priceNum == budgetNum -> BudgetScoreResult(10, true, false)
            priceNum > budgetNum -> BudgetScoreResult(-20, false, false)
            else -> BudgetScoreResult(0, false, false)
        }
    }

    /**
     * 用餐方式匹配。返回 (分数, 是否匹配)。
     * 候选在硬过滤已排除冲突；此处给匹配 +15。
     */
    fun mealModeScore(supportedMealModes: Set<MealMode>, contextMealModes: Set<MealMode>): MealModeScoreResult {
        if (contextMealModes.isEmpty()) return MealModeScoreResult(0, false)
        val intersects = supportedMealModes.any { it in contextMealModes }
        return if (intersects) MealModeScoreResult(15, true) else MealModeScoreResult(0, false)
    }

    /**
     * 历史防重复加分。daysAgo=null 表示从未吃过。
     */
    fun historyScore(daysAgo: Int?): Int {
        return when {
            daysAgo == null -> 15
            daysAgo <= 1 -> -80
            daysAgo <= 3 -> -60
            daysAgo <= 7 -> -35
            daysAgo <= 14 -> -15
            daysAgo <= 30 -> 0
            else -> 10
        }
    }

    /**
     * v0.9.0：距离偏好软评分（分类属性维度，无真实地理数据，与预算同模式）。
     *
     * 语义：
     * - NEARBY_WALK / WITHIN_10_MIN（就近解决）：出餐快、随处可见的形态
     *   （快餐/便利店/粉面/家常）+15；需专门赴店的重餐（火锅/烤肉/牛排/大餐）-15。
     * - WITHIN_30_MIN：轻倾向就近，同上规则但减半。
     * - FAR_OK（专程去吃）：反向——值得专程的重餐/约会向（火锅/烤肉/日料/牛排/
     *   约会友好）+15；随便哪都有的快餐/便利店 -10。
     * - UNLIMITED：0。
     */
    fun distanceScore(attributes: Set<CategoryAttribute>, distance: DistanceLevel): Int {
        val quickForms = setOf(
            CategoryAttribute.FAST_FOOD, CategoryAttribute.NOODLE,
            CategoryAttribute.CONVENIENCE, CategoryAttribute.SMALL_PORTION
        )
        val destinationForms = setOf(
            CategoryAttribute.HOTPOT, CategoryAttribute.BBQ, CategoryAttribute.STEAK,
            CategoryAttribute.DATE_FRIENDLY, CategoryAttribute.HEAVY_MEAL
        )
        val isQuick = attributes.any { it in quickForms }
        val isDestination = attributes.any { it in destinationForms }
        return when (distance) {
            DistanceLevel.UNLIMITED -> 0
            DistanceLevel.NEARBY_WALK, DistanceLevel.WITHIN_10_MIN -> when {
                isQuick -> 15
                isDestination -> -15
                else -> 0
            }
            DistanceLevel.WITHIN_30_MIN -> when {
                isQuick -> 8
                isDestination -> -8
                else -> 0
            }
            DistanceLevel.FAR_OK -> when {
                isDestination -> 15
                isQuick -> -10
                else -> 0
            }
        }
    }
}

/** 分类元数据 */
data class CategoryInfo(
    val priceLevel: CategoryPriceLevel = CategoryPriceLevel.MEDIUM,
    val supportedMealModes: Set<MealMode> =
        setOf(MealMode.DINE_OUT, MealMode.TAKEOUT, MealMode.PACK),
    val attributes: Set<CategoryAttribute> = emptySet()
)

/** 状态匹配结果 */
data class MoodScoreResult(val score: Int, val matched: Boolean)

/** 预算匹配结果 */
data class BudgetScoreResult(val score: Int, val matched: Boolean, val hardExceed: Boolean)

/** 用餐方式匹配结果 */
data class MealModeScoreResult(val score: Int, val matched: Boolean)