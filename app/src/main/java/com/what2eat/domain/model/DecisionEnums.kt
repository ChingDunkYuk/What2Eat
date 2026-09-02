package com.what2eat.domain.model

/**
 * 决策模式。
 * 存库为 ordinal：新值必须追加在末尾，保持既有 ordinal 稳定。
 */
enum class DecisionMode {
    CATEGORY_FIRST,
    /** 从吃饭池的具体选项中决定（v0.8.0，ordinal=1） */
    POOL_FIRST
}

/** 决策会话状态 */
enum class SessionStatus {
    DRAFT,
    SELECTING,
    READY,
    COMPLETED,
    CANCELLED
}

/** 本次分类选择类型 */
enum class SelectionType {
    WANT,
    ACCEPT,
    NOT_TODAY
}

/** 用餐方式 */
enum class MealMode(val label: String) {
    DINE_OUT("出去吃"),
    TAKEOUT("外卖"),
    PACK("打包"),
    COOK_HOME("在家做"),
    ANY("都可以");

    companion object {
        /** 确保"都可以"与其他方式不冲突 */
        fun resolve(selected: Set<MealMode>): Set<MealMode> {
            return if (selected.contains(ANY)) {
                setOf(ANY)
            } else {
                selected
            }
        }
    }
}

/** 今天的状态 */
enum class MoodTag(val label: String) {
    GOOD_MEAL("想吃点好的"),
    QUICK("快速解决"),
    NO_QUEUE("不想排队"),
    HOT("想吃热的"),
    LIGHT("想吃清淡"),
    HEAVY("想吃重口"),
    MEATY("想吃肉"),
    NOT_TOO_FULL("不想吃太撑"),
    DATE("适合约会"),
    NO_REQUIREMENT("没什么要求");

    companion object {
        /** "没什么要求"与其他状态互斥 */
        fun resolve(selected: Set<MoodTag>): Set<MoodTag> {
            return if (selected.contains(NO_REQUIREMENT)) {
                setOf(NO_REQUIREMENT)
            } else {
                selected
            }
        }
    }
}

/** 预算等级 */
enum class BudgetLevel(val label: String) {
    UNDER_30("30元以内"),
    RANGE_30_60("30至60元"),
    RANGE_60_100("60至100元"),
    RANGE_100_200("100至200元"),
    OVER_200("200元以上"),
    UNLIMITED("不限")
}

/** 距离等级 */
enum class DistanceLevel(val label: String) {
    NEARBY_WALK("附近步行"),
    WITHIN_10_MIN("10分钟以内"),
    WITHIN_30_MIN("30分钟以内"),
    FAR_OK("远一点也可以"),
    UNLIMITED("不限")
}
