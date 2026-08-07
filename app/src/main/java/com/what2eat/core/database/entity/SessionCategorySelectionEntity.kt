package com.what2eat.core.database.entity

import androidx.room.Entity

/**
 * 本次决策分类选择 Room 实体。
 * 联合主键：sessionId + personId + categoryId
 */
@Entity(
    tableName = "session_category_selection",
    primaryKeys = ["sessionId", "personId", "categoryId"]
)
data class SessionCategorySelectionEntity(
    val sessionId: String,
    val personId: String,
    val categoryId: String,
    val selectionType: Int,
    val updatedAt: Long
)
