package com.what2eat.domain.search

/**
 * 搜索关键词构建结果。
 */
data class SearchQueryResult(
    /** 关键词是否有效（categoryName 必须有效） */
    val valid: Boolean,
    /** 构建后的搜索关键词（已 trim） */
    val query: String,
    /** 无效时的原因（面向用户的友好提示） */
    val reason: String? = null
)

/**
 * 搜索关键词构建器（纯 Kotlin，可独立单元测试）。
 *
 * 规则（不允许 UI 自行拼接搜索字符串）：
 * 1. categoryName 必须有效；
 * 2. 自动 trim；
 * 3. areaText 为空时：仅 categoryName，如「潮汕牛肉火锅」；
 * 4. areaText 有值时：`areaText + " " + categoryName`，如「佛山南海 潮汕牛肉火锅」。
 */
object SearchQueryBuilder {

    fun build(categoryName: String?, areaText: String?): SearchQueryResult {
        val name = categoryName?.trim().orEmpty()
        if (name.isEmpty()) {
            return SearchQueryResult(valid = false, query = "", reason = "搜索关键词不能为空")
        }
        val area = areaText?.trim().orEmpty()
        val query = if (area.isEmpty()) name else "$area $name"
        return SearchQueryResult(valid = true, query = query)
    }
}