package com.what2eat.domain.foodpool

import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.SavedOption
import com.what2eat.domain.model.SavedOptionType

/**
 * 新增/编辑表单校验与默认建议（纯 Kotlin 可单测）。
 */
object FoodOptionForm {

    /** 名称长度上限 */
    const val MAX_NAME_LENGTH = 50

    /** 名称合法化：trim，并校验长度 */
    fun normalizeName(raw: String): String = raw.trim()

    fun isValidName(name: String): Boolean {
        val n = normalizeName(name)
        return n.isNotEmpty() && n.length <= MAX_NAME_LENGTH
    }

    /**
     * 根据类型建议默认列表（仅建议，不强制）。
     */
    fun suggestCollections(type: SavedOptionType): Set<CollectionType> = when (type) {
        SavedOptionType.RESTAURANT -> setOf(CollectionType.WANT_TO_TRY)
        SavedOptionType.TAKEOUT_STORE -> setOf(CollectionType.TAKEOUT)
        SavedOptionType.HOME_MEAL -> setOf(CollectionType.HOME_COOK)
        SavedOptionType.FOOD_CATEGORY -> setOf(CollectionType.WANT_TO_TRY)
    }

    /**
     * 保存选项。
     * 名称自动 trim；若未选择任何列表，默认放入"全部"（即不写任何 collection 关系，
     * 但池页"全部"仍会显示）。
     * @param existing 已存在的选项（编辑时保留 id）；null 表示新增
     */
    fun buildOption(
        existing: SavedOption?,
        name: String,
        type: SavedOptionType,
        areaText: String?,
        priceLevel: Int?,
        estimatedMinutes: Int?,
        notes: String?,
        sourceUrl: String?,
        enabled: Boolean
    ): SavedOption {
        val now = System.currentTimeMillis()
        val normalizedName = normalizeName(name)
        return (existing ?: SavedOption(
            id = newId(),
            name = normalizedName,
            optionType = type
        )).copy(
            name = normalizedName,
            optionType = type,
            areaText = areaText?.takeIf { it.isNotBlank() },
            priceLevel = priceLevel,
            estimatedMinutes = estimatedMinutes,
            notes = notes?.takeIf { it.isNotBlank() },
            sourceUrl = sourceUrl?.takeIf { it.isNotBlank() },
            enabled = enabled,
            updatedAt = now
        )
    }

    private fun newId(): String = "saved_${System.currentTimeMillis()}"
}