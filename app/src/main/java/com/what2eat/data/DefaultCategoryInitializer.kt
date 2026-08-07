package com.what2eat.data

import com.what2eat.domain.model.FoodCategory
import com.what2eat.domain.repository.FoodCategoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 默认餐饮分类初始化器。
 *
 * 首次启动时写入系统默认分类，使用固定 String ID。
 * 幂等设计：通过检查分类数量判断是否已初始化。
 */
@Singleton
class DefaultCategoryInitializer @Inject constructor(
    private val repository: FoodCategoryRepository
) {
    /**
     * 如果数据库中没有分类，则插入默认分类。
     * 安全调用，重复调用不会重复插入。
     */
    suspend fun initializeIfNeeded() {
        if (repository.isInitialized()) return

        val now = System.currentTimeMillis()
        val categories = buildDefaultCategories(now)
        repository.insertAll(categories)
    }

    private fun buildDefaultCategories(now: Long): List<FoodCategory> {
        val categories = mutableListOf<FoodCategory>()
        var sort = 0

        // ── 一级分类 ID 常量 ──
        val idChineseDining = "category_chinese_dining"
        val idHotpot = "category_hotpot"
        val idBbq = "category_bbq"
        val idNoodles = "category_noodles"
        val idRiceFastFood = "category_rice_fast_food"
        val idAsianCuisine = "category_asian_cuisine"
        val idWesternCuisine = "category_western_cuisine"
        val idLightMeal = "category_light_meal"
        val idDessertDrinks = "category_dessert_drinks"
        val idHomeCooking = "category_home_cooking"

        // ── 一级分类 ──
        val rootCategories = listOf(
            idChineseDining to "中式正餐",
            idHotpot to "火锅",
            idBbq to "烧烤与烤肉",
            idNoodles to "粉面",
            idRiceFastFood to "米饭与快餐",
            idAsianCuisine to "亚洲料理",
            idWesternCuisine to "西式料理",
            idLightMeal to "轻食",
            idDessertDrinks to "甜品与饮品",
            idHomeCooking to "在家解决"
        )

        for ((id, name) in rootCategories) {
            categories.add(
                FoodCategory(
                    id = id,
                    name = name,
                    parentId = null,
                    sortOrder = sort++,
                    enabled = true,
                    isSystemPreset = true,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        // ── 二级分类 ──

        // 中式正餐
        val chineseDiningChildren = listOf(
            "category_cantonese" to "粤菜",
            "category_chaoshan" to "潮汕菜",
            "category_sichuan" to "川菜",
            "category_hunan" to "湘菜",
            "category_northeast" to "东北菜",
            "category_hakka" to "客家菜",
            "category_hk_style" to "茶餐厅",
            "category_stir_fry" to "小炒",
            "category_roast_meat" to "烧腊"
        )
        categories.addAll(childrenOf(idChineseDining, chineseDiningChildren, sort, now).also { sort += it.size })

        // 火锅
        val hotpotChildren = listOf(
            "category_hotpot_chaoshan_beef" to "潮汕牛肉火锅",
            "category_hotpot_sichuan" to "川渝火锅",
            "category_hotpot_coconut_chicken" to "椰子鸡",
            "category_hotpot_pork_stomach" to "猪肚鸡",
            "category_hotpot_skewer" to "串串",
            "category_hotpot_grilled_fish" to "烤鱼",
            "category_hotpot_sauerkraut_fish" to "酸菜鱼"
        )
        categories.addAll(childrenOf(idHotpot, hotpotChildren, sort, now).also { sort += it.size })

        // 烧烤与烤肉
        val bbqChildren = listOf(
            "category_bbq_grilled_meat" to "烤肉",
            "category_bbq_grill" to "烧烤"
        )
        categories.addAll(childrenOf(idBbq, bbqChildren, sort, now).also { sort += it.size })

        // 粉面
        val noodleChildren = listOf(
            "category_noodle_wonton" to "云吞面",
            "category_noodle_beef" to "牛肉面",
            "category_noodle_japanese_ramen" to "日式拉面",
            "category_noodle_luosi" to "螺蛳粉",
            "category_noodle_guilin" to "桂林米粉",
            "category_noodle_malatang" to "麻辣烫"
        )
        categories.addAll(childrenOf(idNoodles, noodleChildren, sort, now).also { sort += it.size })

        // 米饭与快餐
        val riceFastFoodChildren = listOf(
            "category_rice_claypot" to "煲仔饭",
            "category_rice_fried" to "炒饭",
            "category_fast_food_hamburger" to "汉堡",
            "category_fast_food_fried_chicken" to "炸鸡",
            "category_fast_food_shaxian" to "沙县小吃",
            "category_fast_food_convenience_store" to "便利店"
        )
        categories.addAll(childrenOf(idRiceFastFood, riceFastFoodChildren, sort, now).also { sort += it.size })

        // 亚洲料理
        val asianChildren = listOf(
            "category_japanese" to "日料",
            "category_japanese_sushi" to "寿司",
            "category_korean" to "韩式料理",
            "category_thai" to "泰国菜",
            "category_vietnamese" to "越南菜"
        )
        categories.addAll(childrenOf(idAsianCuisine, asianChildren, sort, now).also { sort += it.size })

        // 西式料理
        val westernChildren = listOf(
            "category_western_pizza" to "披萨",
            "category_western_steak" to "牛排",
            "category_western_pasta" to "意面"
        )
        categories.addAll(childrenOf(idWesternCuisine, westernChildren, sort, now).also { sort += it.size })

        // 轻食
        val lightMealChildren = listOf(
            "category_light_meal_salad" to "轻食"
        )
        categories.addAll(childrenOf(idLightMeal, lightMealChildren, sort, now).also { sort += it.size })

        // 在家解决
        val homeCookingChildren = listOf(
            "category_home_cooked_noodles" to "煮面",
            "category_home_stir_fry" to "简单炒菜",
            "category_home_hotpot" to "自己煮火锅",
            "category_home_frozen" to "速冻食品",
            "category_home_clear_fridge" to "清冰箱"
        )
        categories.addAll(childrenOf(idHomeCooking, homeCookingChildren, sort, now).also { sort += it.size })

        return categories
    }

    private fun childrenOf(
        parentId: String,
        children: List<Pair<String, String>>,
        startSort: Int,
        now: Long
    ): List<FoodCategory> {
        return children.mapIndexed { index, (id, name) ->
            FoodCategory(
                id = id,
                name = name,
                parentId = parentId,
                sortOrder = startSort + index,
                enabled = true,
                isSystemPreset = true,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}
