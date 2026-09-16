package com.what2eat.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 标签元数据 Room 实体（v1.6.0 标签颜色）。
 *
 * 懒元数据策略：只在用户显式设置过颜色时建行；
 * name 即标签字符串本体（与 saved_option_tag.tagId 同值，不设 FK——
 * 标签重命名/合并/删除由 Repository 事务内同步维护本表，见 v0.9.2 惯例）。
 */
@Entity(tableName = "tag")
data class TagEntity(
    @PrimaryKey val name: String,
    val colorArgb: Int,
    val createdAt: Long = 0L
)
