package com.what2eat.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 人物-具体吃饭选项偏好 Room 实体。
 * 与 person_category_preference（对分类的偏好）是两套不同数据。
 */
@Entity(
    tableName = "person_option_preference",
    primaryKeys = ["personId", "savedOptionId"],
    foreignKeys = [
        ForeignKey(
            entity = SavedOptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["savedOptionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["savedOptionId"])]
)
data class PersonOptionPreferenceEntity(
    val personId: String,
    val savedOptionId: String,
    val preferenceLevel: Int,
    val hardExcluded: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)