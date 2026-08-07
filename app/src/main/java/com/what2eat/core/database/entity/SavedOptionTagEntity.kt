package com.what2eat.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 选项-标签关系 Room 实体。
 * 复用标签系统：tagId 指向标签表。可用于菜系、口味、用餐方式、区域、自定义标签。
 */
@Entity(
    tableName = "saved_option_tag",
    primaryKeys = ["savedOptionId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = SavedOptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["savedOptionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tagId"])]
)
data class SavedOptionTagEntity(
    val savedOptionId: String,
    val tagId: String
)