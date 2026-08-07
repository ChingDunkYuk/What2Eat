package com.what2eat.domain.model

/**
 * 人物档案领域模型。
 *
 * 对应规划书 13.1 PersonProfile：
 * id / name / avatar / isPrimary / createdAt / updatedAt
 */
data class PersonProfile(
    val id: Long = 0L,
    val name: String,
    val avatar: String? = null,
    val isPrimary: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
