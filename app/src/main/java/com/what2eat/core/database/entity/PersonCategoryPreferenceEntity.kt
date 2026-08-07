package com.what2eat.core.database.entity

import androidx.room.Entity

/**
 * 人物与餐饮分类之间的偏好关系 Room 实体。
 * 联合主键：personId + categoryId
 */
@Entity(
    tableName = "person_category_preference",
    primaryKeys = ["personId", "categoryId"]
)
data class PersonCategoryPreferenceEntity(
    val personId: String,
    val categoryId: String,
    val preferenceLevel: Int = 0,
    val hardExcluded: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
