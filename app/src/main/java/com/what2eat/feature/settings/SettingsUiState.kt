package com.what2eat.feature.settings

import com.what2eat.domain.model.AppUsageMode
import com.what2eat.domain.model.PersonProfile

/**
 * 偏好摘要数据。
 * setCount: 已设置偏好的分类数（preferenceLevel != 0）
 * excludedCount: 长期不吃的分类数（hardExcluded = true）
 */
data class PreferenceSummary(
    val setCount: Int = 0,
    val excludedCount: Int = 0
) {
    /** 是否完全没有设置任何偏好 */
    val isNotSet: Boolean get() = setCount == 0 && excludedCount == 0
}

/**
 * 设置页 UI 状态。
 */
data class SettingsUiState(
    val usageMode: AppUsageMode = AppUsageMode.SINGLE,
    val profiles: List<PersonProfile> = emptyList(),
    val primaryProfile: PersonProfile? = null,
    val secondaryProfile: PersonProfile? = null,
    val primaryPreferenceSummary: PreferenceSummary = PreferenceSummary(),
    val secondaryPreferenceSummary: PreferenceSummary = PreferenceSummary(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val message: String? = null
)
