package com.what2eat.feature.home

/**
 * 首页 UI 状态。
 */
data class HomeUiState(
    val primaryUserName: String? = null,
    val isLoading: Boolean = true,
    val hasActiveSession: Boolean = false
) {
    /** 是否已设置主要用户名称 */
    val hasUserName: Boolean get() = !primaryUserName.isNullOrBlank()
}
