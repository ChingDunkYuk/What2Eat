package com.what2eat.feature.pooldecision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.engine.PoolCandidateInput
import com.what2eat.domain.engine.PoolDecisionContext
import com.what2eat.domain.engine.PoolDecisionEngine
import com.what2eat.domain.engine.PoolRecommendationItem
import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.DecisionMode
import com.what2eat.domain.model.DecisionSession
import com.what2eat.domain.model.ImportStatus
import com.what2eat.domain.model.MealMode
import com.what2eat.domain.model.PersonOptionPreference
import com.what2eat.domain.model.PersonProfile
import com.what2eat.domain.model.SavedOption
import com.what2eat.domain.model.SessionParticipant
import com.what2eat.domain.model.SessionStatus
import com.what2eat.domain.repository.DecisionSessionRepository
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * 参与池决策的列表（多选）。
 * 刻意排除"踩雷"（不该被推荐）与"待整理"（名称不完整）。
 */
enum class PoolListFilter(val label: String, val collectionType: CollectionType) {
    FREQUENT("常吃", CollectionType.FREQUENT),
    VISITED("吃过", CollectionType.VISITED),
    WANT_TO_TRY("待尝试", CollectionType.WANT_TO_TRY),
    TAKEOUT("外卖", CollectionType.TAKEOUT),
    HOME_COOK("在家做", CollectionType.HOME_COOK)
}

/** 池决策 UI 状态 */
data class PoolDecisionUiState(
    val isLoading: Boolean = true,
    val selectedLists: Set<PoolListFilter> = PoolListFilter.entries.toSet(),
    val mealMode: MealMode = MealMode.ANY,
    /** 当前推荐 */
    val recommendation: PoolRecommendationItem? = null,
    /** 当前推荐对应的池选项（详情展示用） */
    val currentOption: SavedOption? = null,
    /** 当前推荐命中的所属列表（展示用，按 PoolListFilter 顺序） */
    val currentCollections: List<CollectionType> = emptyList(),
    val rerollCount: Int = 0,
    /** 池子本身为空（无可用启用的完整选项） */
    val poolIsEmpty: Boolean = false,
    /** 本轮候选全部换完 */
    val isExhausted: Boolean = false,
    /** 已"就吃这个"（完成态） */
    val isConfirmed: Boolean = false,
    // ── 完成态搜索承接（复用历史页面板）──
    val showSearchPanel: Boolean = false,
    val searchQuery: String = "",
    val searchMessage: String? = null
)

/**
 * 「从吃饭池决定」ViewModel。
 *
 * 轻量单页流程：观察池数据 → 组装候选 → 引擎加权随机 → 换一个（内存拒绝）→
 * "就吃这个"时一次性写入 COMPLETED 会话（不落 DRAFT，不污染首页"继续本次决定"）。
 */
@HiltViewModel
class PoolDecisionViewModel @Inject constructor(
    private val savedOptionRepository: SavedOptionRepository,
    private val personProfileRepository: PersonProfileRepository,
    private val sessionRepository: DecisionSessionRepository,
    private val platformSearchLauncher: PlatformSearchLauncher,
    private val searchLauncher: SearchLauncher
) : ViewModel() {

    private val _uiState = MutableStateFlow(PoolDecisionUiState())
    val uiState: StateFlow<PoolDecisionUiState> = _uiState.asStateFlow()

    // 池数据缓存（init 观察）
    private var options: List<SavedOption> = emptyList()
    private var collectionsByOption: Map<String, Set<CollectionType>> = emptyMap()
    private var enabledProfiles: List<PersonProfile> = emptyList()
    private var preferencesByOption: Map<String, List<PersonOptionPreference>> = emptyMap()

    private val selectedLists = MutableStateFlow(PoolListFilter.entries.toSet())
    private val mealMode = MutableStateFlow(MealMode.ANY)
    private val rejectedIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        observePoolData()
    }

    // ── 数据观察 ──

    private fun observePoolData() {
        viewModelScope.launch {
            combine(
                savedOptionRepository.observeAll().distinctUntilChanged(),
                savedOptionRepository.observeAllCollections().distinctUntilChanged(),
                personProfileRepository.observeEnabled().distinctUntilChanged()
            ) { opts, colls, profiles -> Triple(opts, colls, profiles) }
                .collect { (opts, colls, profiles) ->
                    options = opts
                    collectionsByOption = colls.groupBy { it.savedOptionId }
                        .mapValues { (_, list) -> list.map { it.collectionType }.toSet() }
                    enabledProfiles = profiles

                    // 确认完成后数据变化（如 lastChosenAt 更新）只刷新缓存，不重掷覆盖完成态
                    loadPreferences()
                    if (!_uiState.value.isConfirmed) {
                        roll(clearRejected = true)
                    } else {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
        }
    }

    /** 一次性加载所有启用人物对各选项的具体偏好（池规模小，进入/数据变化时执行） */
    private suspend fun loadPreferences() {
        val result = mutableMapOf<String, List<PersonOptionPreference>>()
        for (opt in options) {
            result[opt.id] = savedOptionRepository.getPreferences(opt.id)
        }
        preferencesByOption = result
    }

    // ── 候选与推荐 ──

    /** 参与决策的选项：启用 + 非"待整理"（名称不完整的条目不推荐） */
    private fun decidableOptions(): List<SavedOption> =
        options.filter { it.enabled && it.importStatus != ImportStatus.NEEDS_REVIEW }

    private fun buildCandidates(): List<PoolCandidateInput> = decidableOptions().map { opt ->
        val prefs = preferencesByOption[opt.id].orEmpty()
        PoolCandidateInput(
            optionId = opt.id,
            name = opt.name,
            optionType = opt.optionType,
            collections = collectionsByOption[opt.id].orEmpty(),
            enabled = opt.enabled,
            preferenceValues = prefs.map { it.preferenceLevel.value },
            hardExcludedByAny = prefs.any { it.hardExcluded },
            lastChosenDaysAgo = opt.lastChosenAt?.let { daysAgo(it) }
        )
    }

    private fun roll(clearRejected: Boolean) {
        if (clearRejected) rejectedIds.value = emptySet()

        val result = PoolDecisionEngine.recommend(
            candidates = buildCandidates(),
            context = PoolDecisionContext(
                mealMode = mealMode.value,
                selectedCollections = selectedLists.value.map { it.collectionType }.toSet()
            ),
            rejectedIds = rejectedIds.value
        )

        val recommended = result.recommended
        val option = recommended?.let { decidableOptions().firstOrNull { o -> o.id == it.optionId } }

        _uiState.update { state ->
            state.copy(
                isLoading = false,
                selectedLists = selectedLists.value,
                mealMode = mealMode.value,
                recommendation = recommended,
                currentOption = option,
                currentCollections = if (option != null) {
                    PoolListFilter.entries.map { it.collectionType }
                        .filter { it in (collectionsByOption[option.id].orEmpty()) }
                } else emptyList(),
                rerollCount = if (clearRejected) 0 else state.rerollCount,
                poolIsEmpty = decidableOptions().isEmpty(),
                isExhausted = result.exhausted && !decidableOptions().isEmpty()
            )
        }
    }

    private fun daysAgo(millis: Long): Int =
        ((System.currentTimeMillis() - millis) / MILLIS_PER_DAY).toInt()

    // ── 筛选交互 ──

    fun toggleList(filter: PoolListFilter) {
        selectedLists.value = if (filter in selectedLists.value) {
            selectedLists.value - filter
        } else {
            selectedLists.value + filter
        }
        roll(clearRejected = true)
    }

    fun setMealMode(mode: MealMode) {
        mealMode.value = mode
        roll(clearRejected = true)
    }

    /** 恢复默认全选列表 */
    fun resetLists() {
        selectedLists.value = PoolListFilter.entries.toSet()
        mealMode.value = MealMode.ANY
        roll(clearRejected = true)
    }

    // ── 换一个 / 确认 ──

    /** 换一个：当前推荐加入本轮拒绝列表，从剩余候选重新加权随机 */
    fun reroll() {
        val current = _uiState.value.recommendation ?: return
        rejectedIds.value = rejectedIds.value + current.optionId
        _uiState.update { it.copy(rerollCount = it.rerollCount + 1) }
        roll(clearRejected = false)
    }

    /** 重新看看全部（清空拒绝列表） */
    fun resetRejected() {
        roll(clearRejected = true)
    }

    /** "就吃这个"：更新 lastChosenAt + 单事务写入 COMPLETED 会话与参与者 */
    fun confirmPick() {
        val state = _uiState.value
        val recommendation = state.recommendation ?: return
        if (state.isConfirmed) return
        val option = state.currentOption ?: return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            savedOptionRepository.setLastChosenAt(option.id, now)

            val sessionId = UUID.randomUUID().toString()
            val participants = enabledProfiles.mapIndexed { index, profile ->
                SessionParticipant(
                    sessionId = sessionId,
                    personId = profile.id,
                    selectionOrder = index,
                    completed = true
                )
            }
            sessionRepository.completePoolDecision(
                session = DecisionSession(
                    id = sessionId,
                    decisionMode = DecisionMode.POOL_FIRST,
                    status = SessionStatus.COMPLETED,
                    startedAt = now,
                    completedAt = now,
                    mealModes = if (mealMode.value == MealMode.ANY) emptySet() else setOf(mealMode.value),
                    selectedOptionId = option.id,
                    rerollCount = state.rerollCount,
                    finalWeight = recommendation.weight,
                    createdAt = now,
                    updatedAt = now
                ),
                participants = participants
            )
            _uiState.update { it.copy(isConfirmed = true) }
        }
    }

    // ── 完成态搜索承接（复用历史页模式）──

    fun showSearchPanel() {
        val name = _uiState.value.currentOption?.name ?: return
        _uiState.update { it.copy(showSearchPanel = true, searchQuery = name.trim()) }
    }

    fun hideSearchPanel() {
        _uiState.update { it.copy(showSearchPanel = false) }
    }

    fun onPlatformSearch(platform: SearchPlatform) {
        val query = _uiState.value.searchQuery
        val result = platformSearchLauncher.launch(platform, query)
        _uiState.update { it.copy(searchMessage = result.message) }
    }

    fun onCopySearch() {
        val query = _uiState.value.searchQuery
        val result = searchLauncher.copyQuery(query)
        _uiState.update {
            it.copy(searchMessage = if (result.success) "已复制：$query" else result.message)
        }
    }

    fun consumeSearchMessage() {
        _uiState.update { it.copy(searchMessage = null) }
    }

    companion object {
        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
