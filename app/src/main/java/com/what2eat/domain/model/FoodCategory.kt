package com.what2eat.domain.model

/**
 * 餐饮分类领域模型。
 * 支持一级和二级分类，parentId 为 null 表示一级分类。
 */
data class FoodCategory(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val isSystemPreset: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
