package com.what2eat.feature.history

import com.what2eat.domain.history.HistoryStats

/**
 * 历史页 UI 状态。
 * Stage 2.2 封版：最小历史展示。
 * Stage 3.2：新增"再次搜索"平台面板状态。
 * v0.8.1：新增统计卡状态；列表由扁平 items 改为按日分组 groups。
 */
data class HistoryUiState(
    val isLoading: Boolean = true,
    /** 按日分组的时间线（组内时间降序） */
    val groups: List<HistoryGroupView> = emptyList(),
    /** 统计卡数据（加载完成且有历史时非 null） */
    val stats: HistoryStats? = null,
    val isEmpty: Boolean = false,
    val showSearchPanel: Boolean = false,
    val searchQuery: String = "",
    val searchMessage: String? = null
)

/**
 * 单个日期分组（含展示标签与该日条目）。
 */
data class HistoryGroupView(
    /** 组头标签：今天 / 昨天 / M月d日 / yyyy年M月d日 */
    val label: String,
    val items: List<HistoryItem>
)

/**
 * 单条历史记录。
 * v0.8.1：新增 completedAt/rerollCount（统计与按日分组所需）；sessionId 仅作列表 key，不再展示。
 */
data class HistoryItem(
    val sessionId: String,
    val categoryName: String,
    val completedAt: Long,
    val completedAtText: String,
    val decisionModeText: String,
    val rerollCount: Int,
    val participants: List<String>
)
