package com.what2eat.feature.settings

/**
 * 设置页 UI 状态。
 */
data class SettingsUiState(
    val nameInput: String = "",
    val savedName: String? = null,
    val isSaving: Boolean = false,
    val showSavedMessage: Boolean = false
) {
    /** 输入是否有变化 */
    val hasChanges: Boolean
        get() = nameInput.isNotBlank() && nameInput != (savedName ?: "")
}
