package com.what2eat.feature.history

import com.what2eat.domain.history.HistoryFilterState
import com.what2eat.domain.history.MonthlyReport
import com.what2eat.domain.history.HistoryStats
import com.what2eat.domain.model.CollectionType

/**
 * 历史页 UI 状态。
 * Stage 2.2 封版：最小历史展示。
 * Stage 3.2：新增"再次搜索"平台面板状态。
 * v0.8.1：新增统计卡状态；列表由扁平 items 改为按日分组 groups。
 * v1.3.0：新增三维筛选状态（filter/personNames）与月度报告（monthlyReport）。
 */
data class HistoryUiState(
    val isLoading: Boolean = true,
    /** 按日分组的时间线（组内时间降序；已应用筛选） */
    val groups: List<HistoryGroupView> = emptyList(),
    /** 统计卡数据（加载完成且有历史时非 null；全量口径，不受筛选影响） */
    val stats: HistoryStats? = null,
    val isEmpty: Boolean = false,
    val showSearchPanel: Boolean = false,
    val searchQuery: String = "",
    val searchMessage: String? = null,
    /** v1.3.0：当前筛选（时间/人物/模式；只影响时间线） */
    val filter: HistoryFilterState = HistoryFilterState(),
    /** v1.3.0：人物筛选项（enabled 人物名） */
    val personNames: List<String> = emptyList(),
    /** v1.3.0：月度吃饭报告（全量口径；无历史时为 null） */
    val monthlyReport: MonthlyReport? = null,
    /** v1.4.0：列表筛选项（历史中出现过的所属列表，按枚举序） */
    val collectionFilters: List<CollectionType> = emptyList(),
    /** v1.4.0：标签筛选项（历史中出现过的标签名，字典序） */
    val tagFilters: List<String> = emptyList()
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
 * v0.9.0：新增 areaText——池决策记录携带店铺区域，「再次搜索」拼进关键词
 * （「区域 店名」提升找店命中率；分类决策无区域概念，为 null）。
 */
data class HistoryItem(
    val sessionId: String,
    val categoryName: String,
    val completedAt: Long,
    val completedAtText: String,
    val decisionModeText: String,
    val rerollCount: Int,
    val participants: List<String>,
    val areaText: String? = null,
    /** v1.3.0：是否池决策（模式筛选用；默认 false 兼容既有构造） */
    val isPoolDecision: Boolean = false,
    /** v1.4.0：池决策选项的所属列表（列表筛选用；分类决策为空集） */
    val collections: Set<CollectionType> = emptySet(),
    /** v1.4.0：池决策选项的标签（标签筛选用；分类决策为空集） */
    val tags: Set<String> = emptySet()
)
