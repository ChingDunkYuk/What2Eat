package com.what2eat.feature.preference

import com.what2eat.domain.model.FoodCategory
import com.what2eat.domain.model.PersonCategoryPreference
import com.what2eat.domain.model.PersonProfile

/**
 * 偏好筛选模式。
 */
enum class PreferenceFilter {
    ALL,           // 全部
    SET_ONLY,      // 只看已设置
    EXCLUDED_ONLY  // 只看长期不吃
}

/**
 * 单个分类的偏好显示项。
 */
data class CategoryPreferenceItem(
    val category: FoodCategory,
    val preference: PersonCategoryPreference?
) {
    /** 偏好等级，未设置默认为 0 */
    val preferenceLevel: Int get() = preference?.preferenceLevel ?: 0

    /** 是否硬排除 */
    val isHardExcluded: Boolean get() = preference?.hardExcluded ?: false

    /** 是否已设置（非默认值） */
    val isSet: Boolean get() = preference != null && (preference.preferenceLevel != 0 || preference.hardExcluded)
}

/**
 * 按一级分类分组的偏好。
 */
data class CategoryPreferenceGroup(
    val rootCategory: FoodCategory,
    val items: List<CategoryPreferenceItem>
)

/**
 * 偏好页面 UI 状态。
 */
data class PreferenceUiState(
    val person: PersonProfile? = null,
    val categoryGroups: List<CategoryPreferenceGroup> = emptyList(),
    val searchQuery: String = "",
    val filter: PreferenceFilter = PreferenceFilter.ALL,
    val isLoading: Boolean = true,
    val message: String? = null
)
