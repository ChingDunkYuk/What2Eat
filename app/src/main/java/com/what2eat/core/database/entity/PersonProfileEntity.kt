package com.what2eat.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 人物档案 Room 实体。
 * 对应规划书 13.1 PersonProfile。
 */
@Entity(tableName = "person_profile")
data class PersonProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val avatar: String? = null,
    val isPrimary: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
