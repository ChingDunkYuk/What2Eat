package com.what2eat.feature.settings

import com.what2eat.domain.model.AppUsageMode
import com.what2eat.domain.model.PersonProfile

/**
 * 设置页 UI 状态。
 * Stage 1.1: 新增使用模式和双人档案管理。
 */
data class SettingsUiState(
    val usageMode: AppUsageMode = AppUsageMode.SINGLE,
    val profiles: List<PersonProfile> = emptyList(),
    val primaryProfile: PersonProfile? = null,
    val secondaryProfile: PersonProfile? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val message: String? = null
)
