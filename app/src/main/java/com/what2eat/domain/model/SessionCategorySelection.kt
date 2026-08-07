package com.what2eat.domain.model

/**
 * 本次决策中某参与者对某分类的选择。
 * 联合主键：sessionId + personId + categoryId
 */
data class SessionCategorySelection(
    val sessionId: String,
    val personId: String,
    val categoryId: String,
    val selectionType: SelectionType,
    val updatedAt: Long = System.currentTimeMillis()
)
