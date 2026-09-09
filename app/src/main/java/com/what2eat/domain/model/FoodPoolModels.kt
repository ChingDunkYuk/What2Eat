package com.what2eat.domain.model

/**
 * 吃饭选项（放在"我的吃饭池"中的具体选项）。
 * 可以是一家餐厅、一家外卖店、一道在家做的菜，或一个餐饮类型。
 */
data class SavedOption(
    val id: String,
    val name: String,
    val optionType: SavedOptionType,
    val enabled: Boolean = true,
    val sourcePlatform: SourcePlatform = SourcePlatform.MANUAL,
    val sourceUrl: String? = null,
    val sourcePackage: String? = null,
    val areaText: String? = null,
    val priceLevel: Int? = null,
    val estimatedMinutes: Int? = null,
    val notes: String? = null,
    val coverUri: String? = null,
    val importStatus: ImportStatus = ImportStatus.COMPLETE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastChosenAt: Long? = null
)

/**
 * 选项与列表（collection）的关系。
 * 联合主键：savedOptionId + collectionType；一个选项可属于多个列表。
 * 删除 SavedOption 时，关联关系同步清理。
 */
data class SavedOptionCollection(
    val savedOptionId: String,
    val collectionType: CollectionType,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 选项绑定的标签。
 * 复用标签系统（tagId 指向标签表）。
 * 可用于菜系、口味、用餐方式、区域、自定义标签。
 */
data class SavedOptionTag(
    val savedOptionId: String,
    val tagId: String
)

/**
 * 标签使用统计（v0.9.2 标签管理）。
 * name 即标签字符串本体；usageCount 为挂载该标签的选项数。
 */
data class TagUsage(
    val name: String,
    val usageCount: Int
)

/**
 * 人物对具体吃饭选项的长期偏好。
 * 与 PersonCategoryPreference（对分类的偏好）是两套不同数据。
 */
data class PersonOptionPreference(
    val personId: String,
    val savedOptionId: String,
    val preferenceLevel: OptionPreferenceLevel = OptionPreferenceLevel.NEUTRAL,
    val hardExcluded: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)