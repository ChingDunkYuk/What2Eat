package com.what2eat.domain.model

/**
 * 人物与餐饮分类之间的偏好关系。
 *
 * preferenceLevel:
 *   -2 非常不喜欢
 *   -1 不太喜欢
 *    0 无所谓
 *    1 喜欢
 *    2 非常喜欢
 *
 * hardExcluded = true 表示长期绝对不吃。
 * 关闭硬排除后，保留原偏好等级。
 */
data class PersonCategoryPreference(
    val personId: String,
    val categoryId: String,
    val preferenceLevel: Int = 0,
    val hardExcluded: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
