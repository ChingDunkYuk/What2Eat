package com.what2eat.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * 选项-列表关系 Room 实体。
 * 联合主键 savedOptionId + collectionType；一个选项可属于多个列表。
 * 删除 SavedOption 时级联清理（ForeignKey CASCADE）。
 */
@Entity(
    tableName = "saved_option_collection",
    primaryKeys = ["savedOptionId", "collectionType"],
    foreignKeys = [
        ForeignKey(
            entity = SavedOptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["savedOptionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SavedOptionCollectionEntity(
    val savedOptionId: String,
    val collectionType: Int,
    val createdAt: Long = System.currentTimeMillis()
)