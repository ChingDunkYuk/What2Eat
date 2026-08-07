package com.what2eat.domain.model

/**
 * 候选分类结果项。
 */
data class CandidateCategory(
    val categoryId: String,
    val categoryName: String,
    val parentCategoryName: String?,
    val wantCount: Int,
    val acceptCount: Int,
    /** 排序优先级：0=双方WANT, 1=一方WANT一方ACCEPT, 2=双方ACCEPT */
    val rank: Int,
    /** 每位参与者对此分类的选择类型，key=personId */
    val selectionsByPerson: Map<String, SelectionType> = emptyMap()
)
