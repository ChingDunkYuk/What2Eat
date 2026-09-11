package com.what2eat.feature.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.history.HistoryDateGrouper
import com.what2eat.domain.history.HistoryFilter
import com.what2eat.domain.history.HistoryFilterState
import com.what2eat.domain.history.HistoryModeFilter
import com.what2eat.domain.history.HistoryStatEntry
import com.what2eat.domain.history.HistoryStatsCalculator
import com.what2eat.domain.history.HistoryTimeFilter
import com.what2eat.domain.history.MonthlyReportCalculator
import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.DecisionMode
import com.what2eat.domain.model.DecisionSession
import com.what2eat.domain.model.FoodCategory
import com.what2eat.domain.model.PersonProfile
import com.what2eat.domain.model.SavedOption
import com.what2eat.domain.model.SessionParticipant
import com.what2eat.domain.model.SessionStatus
import com.what2eat.domain.repository.DecisionSessionRepository
import com.what2eat.domain.repository.FoodCategoryRepository
import com.what2eat.domain.repository.PersonProfileRepository
import com.what2eat.domain.repository.SavedOptionRepository
import com.what2eat.domain.search.PlatformSearchLauncher
import com.what2eat.domain.search.SearchLauncher
import com.what2eat.domain.search.SearchPlatform
import com.what2eat.domain.search.SearchQueryBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 历史页 ViewModel。
 *
 * 修复（Stage 3.1 回归）：改为持续观察 `observeAllSessions()` 数据流，
 * 而非一次性 `.first()` 加载。这样无论 HistoryViewModel 是否被底部导航复用，
 * 每次进入历史页（或数据库变化）都会自动刷新，单人和双人记录都能正确显示最新结果。
 *
 * Stage 3.2：新增"再次搜索"平台承接面板（大众点评/美团/地图/浏览器/复制），
 * 复用 feature.common.PlatformSearchSheet 与 PlatformSearchLauncher。
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepository: DecisionSessionRepository,
    private val personProfileRepository: PersonProfileRepository,
    private val foodCategoryRepository: FoodCategoryRepository,
    private val savedOptionRepository: SavedOptionRepository,
    private val platformSearchLauncher: PlatformSearchLauncher,
    private val searchLauncher: SearchLauncher
) : ViewModel() {

    companion object {
        private const val TAG = "HistoryViewModel"
    }

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    // v1.3.0：历史筛选状态流。
    // 必须声明在 init { observeHistory() } 之前——Kotlin 属性按声明顺序初始化，
    // viewModelScope 是 Main.immediate，init 启动的协程构造期间即执行 combine，
    // 若 filterFlow 声明在 init 之后，combine 收集到 null 流 → NPE（v1.3.0 历史页闪退根因）。
    private val filterFlow = MutableStateFlow(HistoryFilterState())

    init {
        observeHistory()
    }

    // ── Stage 3.2：再次搜索 ──

    /** 打开平台承接面板，供"再次搜索"复用（v0.9.0：区域拼进搜索词） */
    fun showSearchPanel(categoryName: String, areaText: String? = null) {
        val query = SearchQueryBuilder.build(categoryName, areaText).query
        _uiState.value = _uiState.value.copy(
            showSearchPanel = true,
            searchQuery = query
        )
    }

    fun hideSearchPanel() {
        _uiState.value = _uiState.value.copy(showSearchPanel = false)
    }

    /** 消费（清空）搜索操作提示 */
    fun consumeSearchMessage() {
        _uiState.value = _uiState.value.copy(searchMessage = null)
    }

    /** 平台承接（大众点评/美团/地图/浏览器） */
    fun onPlatformSearch(platform: SearchPlatform) {
        val query = _uiState.value.searchQuery
        val result = platformSearchLauncher.launch(platform, query)
        _uiState.value = _uiState.value.copy(searchMessage = result.message)
    }

    /** 复制关键词 */
    fun onCopySearch() {
        val query = _uiState.value.searchQuery
        val result = searchLauncher.copyQuery(query)
        _uiState.value = _uiState.value.copy(
            searchMessage = if (result.success) "已复制：$query" else result.message
        )
    }

    // ── v1.3.0：历史筛选（时间/人物/模式） ──

    fun setTimeFilter(time: HistoryTimeFilter) {
        filterFlow.value = filterFlow.value.copy(time = time)
    }

    fun setPersonFilter(personName: String?) {
        filterFlow.value = filterFlow.value.copy(personName = personName)
    }

    fun setModeFilter(mode: HistoryModeFilter) {
        filterFlow.value = filterFlow.value.copy(mode = mode)
    }

    /** v1.4.0：所属列表筛选（null=全部） */
    fun setCollectionFilter(collectionType: CollectionType?) {
        filterFlow.value = filterFlow.value.copy(collectionType = collectionType)
    }

    /** v1.4.0：标签筛选（null=全部） */
    fun setTagFilter(tagName: String?) {
        filterFlow.value = filterFlow.value.copy(tagName = tagName)
    }

    private fun observeHistory() {
        // v1.5.0 取数重构（v0.9.1 批量化之上的第二层）：
        // v1.3.0 起筛选流与会话/人物流合并 combine，每点一次筛选 chip 都重跑
        // 5 次全量查询（participants/options/categories/collections/tags）。
        // 现拆两层——数据层各自 map + distinctUntilChanged（仅数据变化才查库/
        // 重建查找表），末层 combine(dataFlow, filterFlow) 纯内存计算，
        // 筛选切换零数据库查询。
        // participants 无独立观察流：参与者随会话写入、会话表必同步变化，
        // 故挂在 sessions 流上刷新。
        val sessionsFlow = sessionRepository.observeAllSessions()
            .map { sessions ->
                sessions to sessionRepository.getAllParticipants().groupBy { it.sessionId }
            }
            .distinctUntilChanged()
        val profilesFlow = personProfileRepository.observeEnabled().distinctUntilChanged()
        val optionsFlow = savedOptionRepository.observeAll()
            .map { list -> list.associateBy { it.id } }
            .distinctUntilChanged()
        val categoriesFlow = foodCategoryRepository.observeAll()
            .map { list -> list.associateBy { it.id } }
            .distinctUntilChanged()
        val collectionsFlow = savedOptionRepository.observeAllCollections()
            .map { list ->
                list.groupBy { it.savedOptionId }
                    .mapValues { (_, l) -> l.map { it.collectionType }.toSet() }
            }
            .distinctUntilChanged()
        val tagsFlow = savedOptionRepository.observeAllTags().distinctUntilChanged()

        val dataFlow = combine(
            combine(sessionsFlow, profilesFlow) { a, b -> a to b },
            combine(optionsFlow, categoriesFlow) { a, b -> a to b },
            combine(collectionsFlow, tagsFlow) { a, b -> a to b }
        ) { (sessionsP, profiles), (options, categories), (collections, tags) ->
            HistoryData(
                sessions = sessionsP.first,
                participantsBySession = sessionsP.second,
                profiles = profiles,
                optionById = options,
                categoryById = categories,
                collectionsByOption = collections,
                tagsByOption = tags
            )
        }

        viewModelScope.launch {
            combine(dataFlow, filterFlow) { data, filter ->
                buildState(data, filter)
            }
                .distinctUntilChanged()
                .collect { state ->
                    _uiState.value = state
                    Log.d(TAG, "history updated: ${state.groups.sumOf { it.items.size }} records")
                }
        }
    }

    /** v1.5.0：纯内存组装（数据快照 + 筛选 → UI 状态；零挂起查询） */
    private fun buildState(data: HistoryData, filter: HistoryFilterState): HistoryUiState {
        val profileByName = data.profiles.associateBy { it.id }
        val personNames = data.profiles.map { it.name }
        val completed = data.sessions
                    .filter {
                        it.status == SessionStatus.COMPLETED &&
                            (it.selectedCategoryId != null || it.selectedOptionId != null)
                    }
                    .sortedByDescending { it.completedAt ?: it.createdAt }

        if (completed.isEmpty()) return HistoryUiState(
            isLoading = false,
            isEmpty = true,
            filter = filter,
            personNames = personNames
        )

        val items = completed.map { session ->
            val participants = data.participantsBySession[session.id].orEmpty()
                .sortedBy { it.selectionOrder }
                .map { profileByName[it.personId]?.name ?: it.personId }
            // 池决策显示店名；分类决策显示分类名
            val option = session.selectedOptionId?.let { data.optionById[it] }
            val resultName = option?.name
                ?: session.selectedCategoryId?.let { data.categoryById[it] }?.name
                ?: session.selectedCategoryId
                ?: if (session.selectedOptionId != null) "已删除的选项" else "未知分类"
            HistoryItem(
                sessionId = session.id,
                categoryName = resultName,
                completedAt = session.completedAt ?: session.createdAt,
                completedAtText = formatTime(session.completedAt ?: session.createdAt),
                decisionModeText = modeText(session.decisionMode),
                rerollCount = session.rerollCount,
                participants = participants,
                // v0.9.0：池决策记录携带区域（「再次搜索」用）
                areaText = option?.areaText,
                // v1.3.0：模式筛选维度
                isPoolDecision = session.decisionMode == DecisionMode.POOL_FIRST,
                // v1.4.0：列表/标签筛选维度（分类决策为空集）
                collections = option?.let { data.collectionsByOption[it.id] } ?: emptySet(),
                tags = option?.let { data.tagsByOption[it.id] }?.toSet() ?: emptySet()
            )
        }

        val nowMillis = System.currentTimeMillis()
        // v0.8.1：统计（全量口径，不受筛选影响）+ v1.3.0 月度报告（同数据源）
        val statEntries = items.map {
            HistoryStatEntry(
                name = it.categoryName,
                completedAt = it.completedAt,
                rerollCount = it.rerollCount
            )
        }
        val stats = HistoryStatsCalculator.compute(
            entries = statEntries,
            nowMillis = nowMillis
        )
        val monthlyReport = MonthlyReportCalculator.compute(statEntries, nowMillis)

        // v1.3.0：筛选只作用于时间线（统计卡/月报保持全量口径，避免互扰）
        val filteredItems = if (filter.isDefault) {
            items
        } else {
            items.filter {
                HistoryFilter.matches(
                    state = filter,
                    completedAt = it.completedAt,
                    participantNames = it.participants,
                    isPoolDecision = it.isPoolDecision,
                    nowMillis = nowMillis,
                    collections = it.collections,
                    tags = it.tags
                )
            }
        }
        val groups = HistoryDateGrouper
            .groupByDay(filteredItems, { it.completedAt })
            .map { group ->
                HistoryGroupView(label = dayLabel(group.epochDay), items = group.items)
            }

        return HistoryUiState(
            isLoading = false,
            groups = groups,
            stats = stats,
            isEmpty = items.isEmpty(),
            filter = filter,
            personNames = personNames,
            monthlyReport = monthlyReport,
            // v1.4.0：只展示历史中真实出现的维度值（减少空选项噪声）
            collectionFilters = items.flatMap { it.collections }.distinct()
                .sortedBy { it.ordinal },
            tagFilters = items.flatMap { it.tags }.distinct().sorted()
        )
    }

    /** v1.5.0：历史页数据快照（取数与筛选分离——数据变化才查库，筛选切换零查询） */
    private data class HistoryData(
        val sessions: List<DecisionSession>,
        val participantsBySession: Map<String, List<SessionParticipant>>,
        val profiles: List<PersonProfile>,
        val optionById: Map<String, SavedOption>,
        val categoryById: Map<String, FoodCategory>,
        val collectionsByOption: Map<String, Set<CollectionType>>,
        val tagsByOption: Map<String, List<String>>
    )

    private fun modeText(mode: DecisionMode): String {
        return when (mode) {
            DecisionMode.CATEGORY_FIRST -> "先决定吃什么"
            DecisionMode.POOL_FIRST -> "从吃饭池决定"
        }
    }

    /**
     * v0.8.1：分组头标签。
     * 今天 / 昨天 / M月d日（同年）/ yyyy年M月d日（跨年）。
     */
    private fun dayLabel(epochDay: Long): String {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone).toEpochDay()
        return when (today - epochDay) {
            0L -> "今天"
            1L -> "昨天"
            else -> {
                val date = LocalDate.ofEpochDay(epochDay)
                if (date.year == LocalDate.now(zone).year) {
                    "${date.monthValue}月${date.dayOfMonth}日"
                } else {
                    "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
                }
            }
        }
    }

    private fun formatTime(millis: Long): String {
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
        } catch (e: Exception) {
            millis.toString()
        }
    }
}