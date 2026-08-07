package com.what2eat.feature.decision

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.model.AppUsageMode
import com.what2eat.domain.model.BudgetLevel
import com.what2eat.domain.model.CandidateCategory
import com.what2eat.domain.model.DecisionSession
import com.what2eat.domain.model.DistanceLevel
import com.what2eat.domain.model.FoodCategory
import com.what2eat.domain.model.MealMode
import com.what2eat.domain.model.MoodTag
import com.what2eat.domain.model.PersonCategoryPreference
import com.what2eat.domain.model.PersonProfile
import com.what2eat.domain.model.SelectionType
import com.what2eat.domain.model.SessionCategorySelection
import com.what2eat.domain.model.SessionParticipant
import com.what2eat.domain.model.SessionStatus
import com.what2eat.domain.repository.AppUsageModeRepository
import com.what2eat.domain.repository.DecisionSessionRepository
import com.what2eat.domain.repository.FoodCategoryRepository
import com.what2eat.domain.repository.PersonCategoryPreferenceRepository
import com.what2eat.domain.repository.PersonProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DecisionViewModel @Inject constructor(
    private val sessionRepository: DecisionSessionRepository,
    private val personProfileRepository: PersonProfileRepository,
    private val foodCategoryRepository: FoodCategoryRepository,
    private val preferenceRepository: PersonCategoryPreferenceRepository,
    private val usageModeRepository: AppUsageModeRepository
) : ViewModel() {

    companion object {
        private const val TAG = "DecisionViewModel"
    }

    private val _uiState = MutableStateFlow(DecisionUiState())
    val uiState: StateFlow<DecisionUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    // ── Initialization ──

    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "loadInitialData: start")
                val usageMode = usageModeRepository.get()
                val allEnabledProfiles = personProfileRepository.observeEnabled().first()
                // 单人模式：始终只使用主用户（isPrimary=true），不依赖列表顺序/first()
                val profiles = if (usageMode == AppUsageMode.SINGLE) {
                    allEnabledProfiles.filter { it.isPrimary }
                } else {
                    allEnabledProfiles
                }
                val allCategories = foodCategoryRepository.observeAll().first()
                val activeSession = sessionRepository.observeActiveSession().first()

                Log.d(TAG, "loadInitialData: usageMode=$usageMode, profiles=${profiles.size}, categories=${allCategories.size}, activeSession=${activeSession != null}")

                if (profiles.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "未找到可用的主用户档案，请先在设置中创建/启用主用户"
                    )
                    return@launch
                }

                if (allCategories.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "餐饮分类数据为空，请重启应用以初始化默认分类"
                    )
                    return@launch
                }

                if (activeSession != null) {
                    Log.d(TAG, "loadInitialData: restoring session ${activeSession.id}")
                    restoreSession(activeSession, profiles, allCategories, usageMode)
                } else {
                    _uiState.value = _uiState.value.copy(
                        availableProfiles = profiles,
                        allCategories = allCategories,
                        usageMode = usageMode,
                        isLoading = false,
                        selectedParticipantIds = profiles.map { it.id }.toSet()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadInitialData: failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "初始化数据加载失败: ${e.message ?: "未知错误"}"
                )
            }
        }
    }

    private suspend fun restoreSession(
        session: DecisionSession,
        profiles: List<PersonProfile>,
        allCategories: List<FoodCategory>,
        usageMode: AppUsageMode
    ) {
        try {
            val participants = sessionRepository.getParticipants(session.id)
            val selectedIds = participants.map { it.personId }.toSet()

            // 获取硬排除
            val hardExcluded = mutableSetOf<String>()
            for (p in participants) {
                val prefs = preferenceRepository.getByPerson(p.personId)
                hardExcluded.addAll(prefs.filter { it.hardExcluded }.map { it.categoryId })
            }

            // 加载所有已有选择
            val allSelections = sessionRepository.getAllSelections(session.id)
            val selectionsByPerson = allSelections.groupBy { it.personId }
                .mapValues { (_, selections) ->
                    selections.associate { it.categoryId to it.selectionType }
                }

            // 确定当前步骤
            val step = when {
                session.status == SessionStatus.READY -> DecisionStep.RESULTS
                session.status == SessionStatus.SELECTING && participants.any { !it.completed } -> {
                    val nextUncompleted = participants.firstOrNull { !it.completed }
                    if (nextUncompleted != null && participants.indexOf(nextUncompleted) > 0) {
                        DecisionStep.HANDOFF
                    } else {
                        DecisionStep.CATEGORY_SELECT
                    }
                }
                session.mealModes.isEmpty() -> DecisionStep.MEAL_MODE
                session.moodTags.isEmpty() -> DecisionStep.MOOD
                session.status == SessionStatus.DRAFT -> DecisionStep.MEAL_MODE
                else -> DecisionStep.MEAL_MODE
            }

            // 如果在分类选择步骤，加载当前人物的选择
            val currentPersonId = participants.firstOrNull { !it.completed }?.personId
            var currentSelections: Map<String, SelectionType> = emptyMap()
            var currentPrefs: Map<String, PersonCategoryPreference> = emptyMap()
            if (currentPersonId != null && step == DecisionStep.CATEGORY_SELECT) {
                val selections = sessionRepository.getSelections(session.id, currentPersonId)
                currentSelections = selections.associate { it.categoryId to it.selectionType }
                currentPrefs = preferenceRepository.getByPerson(currentPersonId).associateBy { it.categoryId }
            }

            // 如果恢复到结果页，重新生成候选
            var candidates = emptyList<CandidateCategory>()
            if (step == DecisionStep.RESULTS) {
                candidates = sessionRepository.generateCandidates(session.id)
            }

            _uiState.value = _uiState.value.copy(
                activeSessionId = session.id,
                availableProfiles = profiles,
                allCategories = allCategories,
                usageMode = usageMode,
                selectedParticipantIds = selectedIds,
                mealModes = session.mealModes,
                moodTags = session.moodTags,
                budgetLevel = session.budgetLevel,
                distanceLevel = session.distanceLevel,
                hardExcludedCategoryIds = hardExcluded,
                allPersonSelections = selectionsByPerson,
                currentSelectingPersonId = currentPersonId,
                currentSelectingPersonIndex = participants.indexOfFirst { !it.completed }.coerceAtLeast(0),
                participantsCompleted = participants.filter { it.completed }.map { it.personId },
                currentPersonSelections = currentSelections,
                currentPersonPreferences = currentPrefs,
                candidates = candidates,
                step = step,
                isLoading = false
            )

            Log.d(TAG, "restoreSession: success, step=$step")
        } catch (e: Exception) {
            Log.e(TAG, "restoreSession: failed", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "会话恢复失败: ${e.message ?: "未知错误"}"
            )
        }
    }

    // ── Participant Selection ──

    fun toggleParticipant(personId: String) {
        val current = _uiState.value.selectedParticipantIds
        val newSet = if (current.contains(personId)) {
            current - personId
        } else {
            current + personId
        }
        _uiState.value = _uiState.value.copy(selectedParticipantIds = newSet)
    }

    fun confirmParticipantsAndStart() {
        val selectedIds = _uiState.value.selectedParticipantIds
        if (selectedIds.isEmpty()) return

        // 检查是否有旧活动会话
        if (_uiState.value.hasActiveSession) {
            _uiState.value = _uiState.value.copy(showNewSessionDialog = true)
            return
        }

        startNewSession()
    }

    fun confirmNewSession() {
        _uiState.value = _uiState.value.copy(showNewSessionDialog = false)
        startNewSession()
    }

    fun cancelNewSession() {
        _uiState.value = _uiState.value.copy(showNewSessionDialog = false)
    }

    private fun startNewSession() {
        val selectedIds = _uiState.value.selectedParticipantIds
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSaving = true)

                // 取消旧活动会话
                val activeSession = sessionRepository.observeActiveSession().first()
                if (activeSession != null) {
                    Log.d(TAG, "startNewSession: cancelling old session ${activeSession.id}")
                    sessionRepository.cancelSession(activeSession.id)
                }

                // 创建新会话
                val sessionId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val session = DecisionSession(
                    id = sessionId,
                    status = SessionStatus.DRAFT,
                    startedAt = now,
                    createdAt = now,
                    updatedAt = now
                )
                sessionRepository.createSession(session)

                // 设置参与者
                val participants = selectedIds.mapIndexed { index, personId ->
                    SessionParticipant(
                        sessionId = sessionId,
                        personId = personId,
                        selectionOrder = index
                    )
                }
                sessionRepository.setParticipants(sessionId, participants)

                // 获取硬排除
                val hardExcluded = mutableSetOf<String>()
                for (personId in selectedIds) {
                    val prefs = preferenceRepository.getByPerson(personId)
                    hardExcluded.addAll(prefs.filter { it.hardExcluded }.map { it.categoryId })
                }

                _uiState.value = _uiState.value.copy(
                    activeSessionId = sessionId,
                    hardExcludedCategoryIds = hardExcluded,
                    isSaving = false,
                    step = DecisionStep.MEAL_MODE
                )

                Log.d(TAG, "startNewSession: success, sessionId=$sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "startNewSession: failed", e)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "创建会话失败: ${e.message ?: "未知错误"}"
                )
            }
        }
    }

    // ── Meal Mode ──

    fun toggleMealMode(mode: MealMode) {
        val current = _uiState.value.mealModes.toMutableSet()
        if (current.contains(mode)) {
            current.remove(mode)
        } else {
            current.add(mode)
        }
        val resolved = MealMode.resolve(current)
        _uiState.value = _uiState.value.copy(mealModes = resolved)
    }

    fun confirmMealMode() {
        _uiState.value = _uiState.value.copy(step = DecisionStep.MOOD)
    }

    // ── Mood ──

    fun toggleMoodTag(tag: MoodTag) {
        val current = _uiState.value.moodTags.toMutableSet()
        if (current.contains(tag)) {
            current.remove(tag)
        } else {
            current.add(tag)
        }
        val resolved = MoodTag.resolve(current)
        _uiState.value = _uiState.value.copy(moodTags = resolved)
    }

    fun confirmMood() {
        _uiState.value = _uiState.value.copy(step = DecisionStep.BUDGET)
    }

    // ── Budget ──

    fun setBudget(level: BudgetLevel) {
        _uiState.value = _uiState.value.copy(budgetLevel = level)
    }

    fun confirmBudget() {
        _uiState.value = _uiState.value.copy(step = DecisionStep.DISTANCE)
    }

    // ── Distance ──

    fun setDistance(level: DistanceLevel) {
        _uiState.value = _uiState.value.copy(distanceLevel = level)
    }

    fun confirmDistanceAndSaveConditions() {
        val sessionId = _uiState.value.activeSessionId ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "会话不存在，请重新开始")
            return
        }
        val selectedIds = _uiState.value.selectedParticipantIds

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSaving = true)

                val session = sessionRepository.getSession(sessionId)
                if (session == null) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = "会话不存在，请重新开始"
                    )
                    return@launch
                }

                sessionRepository.updateSession(
                    session.copy(
                        mealModes = _uiState.value.mealModes,
                        moodTags = _uiState.value.moodTags,
                        budgetLevel = _uiState.value.budgetLevel,
                        distanceLevel = _uiState.value.distanceLevel,
                        status = SessionStatus.SELECTING
                    )
                )

                val orderedIds = orderedParticipantIds()
                val firstPersonId = orderedIds.first()
                val isDual = selectedIds.size > 1

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    currentSelectingPersonId = firstPersonId,
                    currentSelectingPersonIndex = 0,
                    participantsCompleted = emptyList(),
                    step = if (isDual) DecisionStep.HANDOFF else DecisionStep.CATEGORY_SELECT
                )

                // 单人模式直接加载第一人的选择
                if (!isDual) {
                    loadCurrentPersonSelections()
                }

                Log.d(TAG, "confirmDistanceAndSaveConditions: success, firstPerson=$firstPersonId, dual=$isDual")
            } catch (e: Exception) {
                Log.e(TAG, "confirmDistanceAndSaveConditions: failed", e)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "保存决策条件失败: ${e.message ?: "未知错误"}"
                )
            }
        }
    }

    // ── Handoff ──

    fun startHandoffSelection() {
        _uiState.value = _uiState.value.copy(step = DecisionStep.CATEGORY_SELECT)
        loadCurrentPersonSelections()
    }

    // ── Category Selection ──

    fun setCategorySelection(categoryId: String, type: SelectionType) {
        val sessionId = _uiState.value.activeSessionId ?: return
        val personId = _uiState.value.currentSelectingPersonId ?: return

        viewModelScope.launch {
            try {
                sessionRepository.setSelection(
                    SessionCategorySelection(
                        sessionId = sessionId,
                        personId = personId,
                        categoryId = categoryId,
                        selectionType = type
                    )
                )

                val updatedSelections = _uiState.value.currentPersonSelections.toMutableMap()
                updatedSelections[categoryId] = type
                _uiState.value = _uiState.value.copy(currentPersonSelections = updatedSelections)
            } catch (e: Exception) {
                Log.e(TAG, "setCategorySelection: failed", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "保存选择失败: ${e.message ?: "未知错误"}"
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    /** 完成当前人物选择 */
    fun completeCurrentPersonSelection() {
        val sessionId = _uiState.value.activeSessionId ?: return
        val personId = _uiState.value.currentSelectingPersonId ?: return
        val selectedIds = orderedParticipantIds()
        val currentIndex = _uiState.value.currentSelectingPersonIndex

        // 校验：至少选择一个 WANT 或 ACCEPT
        val hasValidSelection = _uiState.value.currentPersonSelections.values.any {
            it == SelectionType.WANT || it == SelectionType.ACCEPT
        }
        if (!hasValidSelection) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "请至少选择一个「想吃」或「可以接受」的分类"
            )
            return
        }

        viewModelScope.launch {
            try {
                // 标记完成
                sessionRepository.markParticipantCompleted(sessionId, personId)

                // 保存当前选择到 allPersonSelections
                val allSelections = _uiState.value.allPersonSelections.toMutableMap()
                allSelections[personId] = _uiState.value.currentPersonSelections

                val completed = _uiState.value.participantsCompleted + personId
                val nextIndex = currentIndex + 1

                if (nextIndex < selectedIds.size) {
                    // 下一个参与者
                    val nextPersonId = selectedIds[nextIndex]
                    _uiState.value = _uiState.value.copy(
                        participantsCompleted = completed,
                        currentSelectingPersonId = nextPersonId,
                        currentSelectingPersonIndex = nextIndex,
                        currentPersonSelections = emptyMap(),
                        searchQuery = "",
                        allPersonSelections = allSelections,
                        step = DecisionStep.HANDOFF
                    )
                    Log.d(TAG, "completeCurrentPersonSelection: next person=$nextPersonId")
                } else {
                    // 所有人完成，生成候选
                    _uiState.value = _uiState.value.copy(
                        participantsCompleted = completed,
                        allPersonSelections = allSelections,
                        isGeneratingCandidates = true
                    )

                    val session = sessionRepository.getSession(sessionId)
                    if (session != null) {
                        sessionRepository.updateSession(session.copy(status = SessionStatus.READY))
                    }

                    val candidates = sessionRepository.generateCandidates(sessionId)

                    _uiState.value = _uiState.value.copy(
                        isGeneratingCandidates = false,
                        candidates = candidates,
                        step = DecisionStep.RESULTS
                    )
                    Log.d(TAG, "completeCurrentPersonSelection: all done, candidates=${candidates.size}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "completeCurrentPersonSelection: failed", e)
                _uiState.value = _uiState.value.copy(
                    isGeneratingCandidates = false,
                    errorMessage = "完成选择失败: ${e.message ?: "未知错误"}"
                )
            }
        }
    }

    // ── Candidate Match Description ──

    /** 生成候选匹配原因的多行文本 */
    fun getCandidateReasonLines(candidate: CandidateCategory): List<String> {
        val profiles = _uiState.value.availableProfiles
        val orderedIds = orderedParticipantIds()

        if (orderedIds.size == 1) {
            val personId = orderedIds[0]
            val sel = candidate.selectionsByPerson[personId]
            val selectionLabel = when (sel) {
                SelectionType.WANT -> "想吃"
                SelectionType.ACCEPT -> "可以接受"
                else -> "未选择"
            }
            val lines = mutableListOf("本次选择：$selectionLabel")
            // 长期偏好
            val pref = _uiState.value.currentPersonPreferences[candidate.categoryId]
            val prefLabel = pref?.let { preferenceLevelLabel(it.preferenceLevel) } ?: "未设置"
            lines.add("长期偏好：$prefLabel")
            return lines
        }

        if (orderedIds.size >= 2) {
            val lines = mutableListOf<String>()
            for (personId in orderedIds) {
                val name = profiles.firstOrNull { it.id == personId }?.name ?: "用户"
                val sel = candidate.selectionsByPerson[personId]
                val label = when (sel) {
                    SelectionType.WANT -> "想吃"
                    SelectionType.ACCEPT -> "可以接受"
                    else -> "未选择"
                }
                lines.add("$name：$label")
            }
            return lines
        }

        return emptyList()
    }

    /** 偏好等级 → 展示文案 */
    fun preferenceLevelLabel(level: Int): String {
        return when (level) {
            -2 -> "非常不喜欢"
            -1 -> "不太喜欢"
            0 -> "无所谓"
            1 -> "喜欢"
            2 -> "非常喜欢"
            else -> "无所谓"
         }
    }

    /** 获取上一位完成选择的人物名称（用于交接页） */
    fun getPreviousPersonName(): String? {
        val completed = _uiState.value.participantsCompleted
        if (completed.isEmpty()) return null
        val lastCompletedId = completed.last()
        return _uiState.value.availableProfiles.firstOrNull { it.id == lastCompletedId }?.name
    }

    // ── Navigation ──

    fun goToStep(step: DecisionStep) {
        _uiState.value = _uiState.value.copy(step = step)
    }

    fun goBack() {
        val current = _uiState.value.step
        val previous = when (current) {
            DecisionStep.MEAL_MODE -> DecisionStep.PARTICIPANTS
            DecisionStep.MOOD -> DecisionStep.MEAL_MODE
            DecisionStep.BUDGET -> DecisionStep.MOOD
            DecisionStep.DISTANCE -> DecisionStep.BUDGET
            else -> null
        }
        if (previous != null) {
            _uiState.value = _uiState.value.copy(step = previous)
        }
    }

    // ── Exit & Cancel Dialogs ──

    fun showExitDialog() {
        _uiState.value = _uiState.value.copy(showExitDialog = true)
    }

    fun hideExitDialog() {
        _uiState.value = _uiState.value.copy(showExitDialog = false)
    }

    fun showCancelDialog() {
        _uiState.value = _uiState.value.copy(showExitDialog = false, showCancelDialog = true)
    }

    fun hideCancelDialog() {
        _uiState.value = _uiState.value.copy(showCancelDialog = false)
    }

    fun confirmCancelSession() {
        _uiState.value = _uiState.value.copy(showCancelDialog = false)
        cancelSession()
    }

    /** 取消会话并重置到参与者选择步骤 */
    fun cancelSession() {
        val sessionId = _uiState.value.activeSessionId ?: run {
            resetToParticipants()
            return
        }

        viewModelScope.launch {
            try {
                sessionRepository.cancelSession(sessionId)
                Log.d(TAG, "cancelSession: success")
                resetToParticipants()
            } catch (e: Exception) {
                Log.e(TAG, "cancelSession: failed", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "取消会话失败: ${e.message ?: "未知错误"}"
                )
            }
        }
    }

    /** 完成会话 */
    fun completeSession() {
        val sessionId = _uiState.value.activeSessionId ?: run {
            resetToParticipants()
            return
        }

        viewModelScope.launch {
            try {
                sessionRepository.completeSession(sessionId)
                Log.d(TAG, "completeSession: success")
                resetToParticipants()
            } catch (e: Exception) {
                Log.e(TAG, "completeSession: failed", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "完成会话失败: ${e.message ?: "未知错误"}"
                )
            }
        }
    }

    /** 从结果页返回修改选择 */
    fun goBackToFirstPersonSelection() {
        val selectedIds = orderedParticipantIds()
        val sessionId = _uiState.value.activeSessionId
        if (selectedIds.isEmpty() || sessionId == null) return

        viewModelScope.launch {
            try {
                // 重置会话状态为 SELECTING
                val session = sessionRepository.getSession(sessionId)
                if (session != null) {
                    sessionRepository.updateSession(session.copy(status = SessionStatus.SELECTING))
                }

                // 重置参与者完成状态
                val participants = selectedIds.mapIndexed { index, personId ->
                    SessionParticipant(
                        sessionId = sessionId,
                        personId = personId,
                        selectionOrder = index,
                        completed = false
                    )
                }
                sessionRepository.setParticipants(sessionId, participants)

                val firstPersonId = selectedIds.first()
                _uiState.value = _uiState.value.copy(
                    currentSelectingPersonId = firstPersonId,
                    currentSelectingPersonIndex = 0,
                    participantsCompleted = emptyList(),
                    step = DecisionStep.CATEGORY_SELECT
                )
                loadCurrentPersonSelections()
                Log.d(TAG, "goBackToFirstPersonSelection: success")
            } catch (e: Exception) {
                Log.e(TAG, "goBackToFirstPersonSelection: failed", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "返回修改失败: ${e.message ?: "未知错误"}"
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /** 获取当前人物已保存的选择及长期偏好 */
    fun loadCurrentPersonSelections() {
        val sessionId = _uiState.value.activeSessionId ?: return
        val personId = _uiState.value.currentSelectingPersonId ?: return

        viewModelScope.launch {
            try {
                val selections = sessionRepository.getSelections(sessionId, personId)
                val selectionMap = selections.associate { it.categoryId to it.selectionType }
                val prefs = preferenceRepository.getByPerson(personId)
                val prefMap = prefs.associateBy { it.categoryId }
                _uiState.value = _uiState.value.copy(
                    currentPersonSelections = selectionMap,
                    currentPersonPreferences = prefMap
                )
                Log.d(TAG, "loadCurrentPersonSelections: personId=$personId, selections=${selectionMap.size}, prefs=${prefMap.size}")
            } catch (e: Exception) {
                Log.e(TAG, "loadCurrentPersonSelections: failed", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "加载选择数据失败: ${e.message ?: "未知错误"}"
                )
            }
        }
    }

    /**
     * 参与者确定顺序列表：主用户（isPrimary）始终排在第一位，
     * 其余按 availableProfiles 已有顺序。不依赖 Set.first() 的迭代顺序。
     */
    private fun orderedParticipantIds(): List<String> {
        val profiles = _uiState.value.availableProfiles
        val selected = _uiState.value.selectedParticipantIds
        val primary = profiles.firstOrNull { it.isPrimary }
        val ordered = mutableListOf<String>()
        if (primary != null && selected.contains(primary.id)) {
            ordered.add(primary.id)
        }
        profiles.asSequence()
            .filter { it.id != primary?.id }
            .map { it.id }
            .filter { selected.contains(it) }
            .forEach { ordered.add(it) }
        return ordered
    }

    private fun resetToParticipants() {
        val profiles = _uiState.value.availableProfiles
        val mode = _uiState.value.usageMode
        val shownProfiles = if (mode == AppUsageMode.SINGLE) {
            profiles.filter { it.isPrimary }
        } else {
            profiles
        }
        _uiState.value = DecisionUiState(
            usageMode = mode,
            availableProfiles = shownProfiles,
            allCategories = _uiState.value.allCategories,
            isLoading = false,
            selectedParticipantIds = shownProfiles.map { it.id }.toSet()
        )
    }
}
