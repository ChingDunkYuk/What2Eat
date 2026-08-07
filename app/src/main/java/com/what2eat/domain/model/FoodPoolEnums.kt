package com.what2eat.domain.model

/** 吃饭选项的来源平台（Stage 4：手动新增为主，来源记录预留） */
enum class SourcePlatform(val label: String) {
    MANUAL("手动添加"),
    DIANPING("大众点评"),
    MEITUAN("美团"),
    NONE("无")
}

/** 吃饭选项类型 */
enum class SavedOptionType(val label: String) {
    RESTAURANT("餐厅"),
    TAKEOUT_STORE("外卖商家"),
    HOME_MEAL("在家做"),
    FOOD_CATEGORY("餐饮类型")
}

/** 导入状态（Stage 5 Share Intent 预留） */
enum class ImportStatus {
    COMPLETE,
    NEEDS_REVIEW
}

/** 所属列表类型（一个选项可属于多个列表） */
enum class CollectionType(val label: String) {
    FREQUENT("常吃"),
    VISITED("吃过"),
    WANT_TO_TRY("待尝试"),
    TAKEOUT("外卖"),
    HOME_COOK("在家做"),
    AVOIDED("踩雷")
}

/** 具体吃饭选项偏好等级 */
enum class OptionPreferenceLevel(val label: String) {
    VERY_LIKE("很喜欢"),
    LIKE("喜欢"),
    NEUTRAL("一般"),
    DISLIKE("不喜欢")
}