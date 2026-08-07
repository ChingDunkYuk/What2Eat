package com.what2eat.domain.engine

/**
 * 分类价格等级（Stage 2.2 使用分类级标签估算，不接真实餐厅价格）。
 */
enum class CategoryPriceLevel {
    LOW,
    MEDIUM,
    HIGH,
    PREMIUM
}

/**
 * 分类属性标签，用于条件（今天状态）匹配。
 * 规则集中在 CategoryRules 中维护，不允许散落在 UI。
 */
enum class CategoryAttribute {
    /** 火锅类（含火锅/串串/烤鱼/酸菜鱼等） */
    HOTPOT,
    /** 烤肉/烧烤 */
    BBQ,
    /** 日料 */
    JAPANESE,
    /** 牛排 */
    STEAK,
    /** 正餐/大菜（坐下好好吃一顿） */
    REGULAR_MEAL,
    /** 快餐（汉堡/炸鸡/沙县等） */
    FAST_FOOD,
    /** 粉面类 */
    NOODLE,
    /** 便利店 */
    CONVENIENCE,
    /** 热食 */
    HOT,
    /** 清淡 */
    LIGHT,
    /** 重口/油腻 */
    HEAVY,
    /** 肉类丰富 */
    MEAT,
    /** 小份/轻量 */
    SMALL_PORTION,
    /** 重型聚餐（火锅/自助/烧烤等易吃撑） */
    HEAVY_MEAL,
    /** 适合约会 */
    DATE_FRIENDLY,
    /** 甜品饮品 */
    SWEET
}