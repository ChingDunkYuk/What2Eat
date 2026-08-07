package com.what2eat.feature.history

/**
 * 历史页 UI 状态。
 * Stage 2.2 封版：最小历史展示（最终分类、完成时间、decisionMode、参与人物、sessionId）。
 * Stage 3.2：新增"再次搜索"平台面板状态。
 */
data class HistoryUiState(
    val isLoading: Boolean = true,
    val items: List<HistoryItem> = emptyList(),
    val isEmpty: Boolean = false,
    val showSearchPanel: Boolean = false,
    val searchQuery: String = "",
    val searchMessage: String? = null
)

/**
 * 单条历史记录（最小展示）。
 */
data class HistoryItem(
    val sessionId: String,
    val categoryName: String,
    val completedAtText: String,
    val decisionModeText: String,
    val participants: List<String>
)