package com.what2eat.feature.home

/**
 * 首页 UI 状态。
 */
data class HomeUiState(
    val primaryUserName: String? = null,
    val isLoading: Boolean = true,
    val hasActiveSession: Boolean = false,
    /** 活动会话的候选数量（READY 会话才有，用于"继续本次决定"卡片文案） */
    val candidateCount: Int = 0,
    /** 待整理条目数量（v0.9.0：>0 时首页显示整理提醒卡） */
    val needsReviewCount: Int = 0
) {
    /** 是否已设置主要用户名称 */
    val hasUserName: Boolean get() = !primaryUserName.isNullOrBlank()
}