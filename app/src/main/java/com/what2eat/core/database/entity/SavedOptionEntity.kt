package com.what2eat.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 吃饭选项 Room 实体（吃饭池）。
 * 一个选项可同时属于多个 list（通过 SavedOptionCollection 关联）。
 */
@Entity(
    tableName = "saved_option",
    indices = [Index(value = ["name"])]
)
data class SavedOptionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val optionType: Int,
    val enabled: Boolean = true,
    val sourcePlatform: Int,
    val sourceUrl: String? = null,
    val sourcePackage: String? = null,
    val areaText: String? = null,
    val priceLevel: Int? = null,
    val estimatedMinutes: Int? = null,
    val notes: String? = null,
    val coverUri: String? = null,
    val importStatus: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastChosenAt: Long? = null
)