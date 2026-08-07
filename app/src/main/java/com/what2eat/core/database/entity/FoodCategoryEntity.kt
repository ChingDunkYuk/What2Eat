package com.what2eat.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 餐饮分类 Room 实体。
 * parentId 为 null 表示一级分类。
 */
@Entity(
    tableName = "food_category",
    indices = [Index(value = ["parentId"])]
)
data class FoodCategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val parentId: String? = null,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val isSystemPreset: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
