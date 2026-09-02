package com.what2eat.feature.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.history.HistoryDateGrouper
import com.what2eat.domain.history.HistoryStatEntry
import com.what2eat.domain.history.HistoryStatsCalculator
import com.what2eat.domain.model.DecisionMode
import com.what2eat.domain.model.SessionStatus
import com.what2eat.domain.repository.DecisionSessionRepository
import com.what2eat.domain.repository.FoodCategoryRepository
import com.what2eat.domain.repository.PersonProfileRepository
import com.what2eat.domain.repository.SavedOptionRepository
import com.what2eat.domain.search.PlatformSearchLauncher
import com.what2eat.domain.search.SearchLauncher
import com.what2eat.domain.search.SearchPlatform
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

    init {
        observeHistory()
    }

    // ── Stage 3.2：再次搜索 ──

    /** 打开平台承接面板，供"再次搜索"复用 */
    fun showSearchPanel(categoryName: String) {
        _uiState.value = _uiState.value.copy(
            showSearchPanel = true,
            searchQuery = categoryName.trim()
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

    private fun observeHistory() {
        viewModelScope.launch {
            combine(
                sessionRepository.observeAllSessions(),
                personProfileRepository.observeEnabled()
            ) { sessions, profiles ->
                val profileByName = profiles.associateBy { it.id }
                val completed = sessions
                    .filter {
                        it.status == SessionStatus.COMPLETED &&
                            (it.selectedCategoryId != null || it.selectedOptionId != null)
                    }
                    .sortedByDescending { it.completedAt ?: it.createdAt }

                if (completed.isEmpty()) return@combine HistoryUiState(isLoading = false, isEmpty = true)

                val items = completed.map { session ->
                    val participants = sessionRepository.getParticipants(session.id)
                        .sortedBy { it.selectionOrder }
                        .map { profileByName[it.personId]?.name ?: it.personId }
                    // 池决策显示店名；分类决策显示分类名
                    val resultName = if (session.selectedOptionId != null) {
                        val option = runCatching {
                            savedOptionRepository.getById(session.selectedOptionId)
                        }.getOrNull()
                        option?.name ?: "已删除的选项"
                    } else {
                        val category = session.selectedCategoryId
                            ?.let { runCatching { foodCategoryRepository.getById(it) }.getOrNull() }
                        category?.name ?: session.selectedCategoryId ?: "未知分类"
                    }
                    HistoryItem(
                        sessionId = session.id,
                        categoryName = resultName,
                        completedAt = session.completedAt ?: session.createdAt,
                        completedAtText = formatTime(session.completedAt ?: session.createdAt),
                        decisionModeText = modeText(session.decisionMode),
                        rerollCount = session.rerollCount,
                        participants = participants
                    )
                }

                // v0.8.1：统计 + 按日分组
                val stats = HistoryStatsCalculator.compute(
                    entries = items.map {
                        HistoryStatEntry(
                            name = it.categoryName,
                            completedAt = it.completedAt,
                            rerollCount = it.rerollCount
                        )
                    },
                    nowMillis = System.currentTimeMillis()
                )
                val groups = HistoryDateGrouper
                    .groupByDay(items, { it.completedAt })
                    .map { group ->
                        HistoryGroupView(label = dayLabel(group.epochDay), items = group.items)
                    }

                HistoryUiState(
                    isLoading = false,
                    groups = groups,
                    stats = stats,
                    isEmpty = items.isEmpty()
                )
            }
                .distinctUntilChanged()
                .collect { state ->
                    _uiState.value = state
                    Log.d(TAG, "history updated: ${state.groups.sumOf { it.items.size }} records")
                }
        }
    }

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