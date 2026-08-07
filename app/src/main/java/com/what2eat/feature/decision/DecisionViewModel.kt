package com.what2eat.feature.decision

import android.util.Log
import androidx.lifecycle.SavedStateHandle
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
    private val usageModeRepository: AppUsageModeRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "DecisionViewModel"
        private const val PRIMARY_ID = "person_primary"
    }

    /** 是否从首页"先决定吃什么"进入（true）——用于决定是否提示覆盖活动会话 */
    private val startNew = savedStateHandle.get<Boolean>("startNew") ?: false

    private val _uiState = MutableStateFlow(DecisionUiState())
    val uiState: StateFlow<DecisionUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    // ── Initialization ──

    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "loadInitialData: start, startNew=$startNew")
                val usageMode = usageModeRepository.get()
                // 单人模式：始终只使用主用户（isPrimary=true），优先用 getPrimaryProfile 明确查询
                val primary = personProfileRepository.getPrimaryProfile()
                val allEnabled = personProfileRepository.observeEnabled().first()
                val profiles = if (usageMode == AppUsageMode.SINGLE) {
                    if (primary != null) listOf(primary) else allEnabled.filter { it.isPrimary }
                } else {
                    allEnabled
                }
                val allCategories = foodCategoryRepository.observeAll().first()
                val activeSession = sessionRepository.observeActiveSession().first()

                Log.d(TAG, "loadInitialData: mode=$usageMode, profiles=${profiles.size}, activeSession=${activeSession != null}")

                if (profiles.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "未找到可用的主用户档案，请重启应用完成主用户初始化"
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

                when {
                    // 用户明确要"新决定"且存在活动会话 → 先询问是否覆盖
                    activeSession != null && startNew && _uiState.value.activeSessionId == null -> {
                        _uiState.value = _uiState.value.copy(
                            availableProfiles = profiles,
                            allCategories = allCategories,
                            usageMode = usageMode,
                            isLoading = false,
                            showNewSessionDialog = true
                        )
                    }
                    activeSession != null && !startNew -> {
                        Log.d(TAG, "loadInitialData: restoring session ${activeSession.id}")
                        restoreSession(activeSession, profiles, allCategories, usageMode)
                    }
                    activeSession != null -> {
                        // startNew=true 但对话框已处理过（避免重复）——直接恢复
                        restoreSession(activeSession, profiles, allCategories, usageMode)
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(
                            availableProfiles = profiles,
                            allCategories = allCategories,
                            usageMode = usageMode,
                            isLoading = false,
                            selectedParticipantIds = profiles.map { it.id }.toSet()
                        )
                    }
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
                else -> DecisionStep.CONDITIONS
            }

            // 指定当前选择人物
            val currentPersonId = participants.firstOrNull { !it.completed }?.personId
            var currentSelections: Map<String, SelectionType> = emptyMap()
            var currentPrefs: Map<String, PersonCategoryPreference> = emptyMap()
            if (currentPersonId != null && step == DecisionStep.CATEGORY_SELECT) {
                val selections = sessionRepository.getSelections(session.id, currentPersonId)
                currentSelections = selections.associate { it.categoryId to it.selectionType }
                currentPrefs = preferenceRepository.getByPerson(currentPersonId).associateBy { it.categoryId }
            }

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
                conditionsModified = false,
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

    // ── Conditions Step ──

    /** 双人模式切换参与者 */
    fun toggleParticipant(personId: String) {
        val current = _uiState.value.selectedParticipantIds
        val newSet = if (current.contains(personId)) {
            current - personId
        } else {
            current + personId
        }
        _uiState.value = _uiState.value.copy(
            selectedParticipantIds = newSet,
            conditionsModified = true
        )
    }

    fun toggleMealMode(mode: MealMode) {
        val current = _uiState.value.mealModes.toMutableSet()
        if (current.contains(mode)) current.remove(mode) else current.add(mode)
        val resolved = MealMode.resolve(current)
        _uiState.value = _uiState.value.copy(mealModes = resolved, conditionsModified = true)
        persistConditionsLazy()
    }

    fun toggleMoodTag(tag: MoodTag) {
        val current = _uiState.value.moodTags
        val newSet = when {
            // 选择"没什么要求"：清除其他全部状态，且恒为仅此一项（不可取消到空）
            tag == MoodTag.NO_REQUIREMENT -> setOf(MoodTag.NO_REQUIREMENT)
            // 选择其他状态：自动取消"没什么要求"，其余状态之间多选
            else -> {
                val withoutNoReq = current.filterNot { it == MoodTag.NO_REQUIREMENT }.toMutableSet()
                if (withoutNoReq.contains(tag)) withoutNoReq.remove(tag) else withoutNoReq.add(tag)
                withoutNoReq
            }
        }
        _uiState.value = _uiState.value.copy(
            moodTags = newSet,
            conditionsModified = true,
            conditionsInlineError = null
        )
        persistConditionsLazy()
    }

    fun setBudget(level: BudgetLevel) {
        _uiState.value = _uiState.value.copy(budgetLevel = level, conditionsModified = true)
        persistConditionsLazy()
    }

    fun setDistance(level: DistanceLevel) {
        _uiState.value = _uiState.value.copy(distanceLevel = level, conditionsModified = true)
        persistConditionsLazy()
    }

    /**
     * 惰性创建并保存草稿会话（首次修改条件时）。
     * 保证 DRAFT 会话存在，杀进程后可从条件页恢复。
     */
    private fun persistConditionsLazy() {
        if (_uiState.value.activeSessionId != null) return
        val st = _uiState.value
        viewModelScope.launch {
            try {
                val profiles = st.availableProfiles
                val participantIds = if (st.usageMode == AppUsageMode.SINGLE) {
                    listOfNotNull(st.primaryProfile?.id)
                } else {
                    orderedParticipantIds().ifEmpty { profiles.map { it.id } }
                }
                if (participantIds.isEmpty()) return@launch

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
                val participants = participantIds.mapIndexed { index, personId ->
                    SessionParticipant(sessionId, personId, index)
                }
                sessionRepository.setParticipants(sessionId, participants)

                val hardExcluded = mutableSetOf<String>()
                for (personId in participantIds) {
                    val prefs = preferenceRepository.getByPerson(personId)
                    hardExcluded.addAll(prefs.filter { it.hardExcluded }.map { it.categoryId })
                }

                _uiState.value = _uiState.value.copy(
                    activeSessionId = sessionId,
                    selectedParticipantIds = participantIds.toSet(),
                    hardExcludedCategoryIds = hardExcluded,
                    currentSelectingPersonId = participantIds.first(),
                    currentSelectingPersonIndex = 0
                )
                persistConditionsToSession(sessionId)
                Log.d(TAG, "persistConditionsLazy: created DRAFT $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "persistConditionsLazy: failed", e)
            }
        }
    }

    private suspend fun persistConditionsToSession(sessionId: String) {
        val st = _uiState.value
        val session = sessionRepository.getSession(sessionId) ?: return
        sessionRepository.updateSession(
            session.copy(
                mealModes = st.mealModes,
                moodTags = st.moodTags,
                budgetLevel = st.budgetLevel,
                distanceLevel = st.distanceLevel
            )
        )
    }

    /** 确认条件，进入分类选择 */
    fun confirmConditions() {
        // 用餐方式与状态必填
        if (_uiState.value.mealModes.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "请至少选择一种用餐方式")
            return
        }
        if (_uiState.value.moodTags.isEmpty()) {
            // 防御性校验：仅在本条件页就地提示，不跳转整页错误
            _uiState.value = _uiState.value.copy(conditionsInlineError = "请至少选择一种今天的状态")
            return
        }
        if (_uiState.value.selectedParticipantIds.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "请至少选择一位参与者")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSaving = true)
                val st = _uiState.value
                var sessionId = st.activeSessionId

                // 没有会话则创建
                if (sessionId == null) {
                    val participantIds = orderedParticipantIds()
                    val newId = UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()
                    sessionRepository.createSession(
                        DecisionSession(
                            id = newId, status = SessionStatus.SELECTING,
                            startedAt = now, createdAt = now, updatedAt = now
                        )
                    )
                    val participants = participantIds.mapIndexed { index, personId ->
                        SessionParticipant(newId, personId, index)
                    }
                    sessionRepository.setParticipants(newId, participants)
                    sessionId = newId
                }

                // 更新条件 + 状态 SELECTING
                val session = sessionRepository.getSession(sessionId) ?: return@launch
                sessionRepository.updateSession(
                    session.copy(
                        mealModes = st.mealModes,
                        moodTags = st.moodTags,
                        budgetLevel = st.budgetLevel,
                        distanceLevel = st.distanceLevel,
                        status = SessionStatus.SELECTING
                    )
                )

                val orderedIds = orderedParticipantIds()
                val firstPersonId = orderedIds.first()
                val isDual = orderedIds.size > 1

                _uiState.value = _uiState.value.copy(
                    activeSessionId = sessionId,
                    isSaving = false,
                    conditionsModified = false,
                    currentSelectingPersonId = firstPersonId,
                    currentSelectingPersonIndex = 0,
                    participantsCompleted = emptyList(),
                    step = DecisionStep.CATEGORY_SELECT
                )
                loadCurrentPersonSelections()
                Log.d(TAG, "confirmConditions: success, firstPerson=$firstPersonId, dual=$isDual")
            } catch (e: Exception) {
                Log.e(TAG, "confirmConditions: failed", e)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "保存条件失败: ${e.message ?: "未知错误"}"
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
                    SessionCategorySelection(sessionId, personId, categoryId, type)
                )
                val updated = _uiState.value.currentPersonSelections.toMutableMap()
                updated[categoryId] = type
                _uiState.value = _uiState.value.copy(currentPersonSelections = updated)
            } catch (e: Exception) {
                Log.e(TAG, "setCategorySelection: failed", e)
                _uiState.value = _uiState.value.copy(errorMessage = "保存选择失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    /** 清除某个分类的本轮选择（恢复"未选择"） */
    fun clearCategorySelection(categoryId: String) {
        val sessionId = _uiState.value.activeSessionId ?: return
        val personId = _uiState.value.currentSelectingPersonId ?: return
        viewModelScope.launch {
            try {
                sessionRepository.deleteSelection(sessionId, personId, categoryId)
                val updated = _uiState.value.currentPersonSelections.toMutableMap()
                updated.remove(categoryId)
                _uiState.value = _uiState.value.copy(currentPersonSelections = updated)
            } catch (e: Exception) {
                Log.e(TAG, "clearCategorySelection: failed", e)
                _uiState.value = _uiState.value.copy(errorMessage = "清除选择失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(searchQuery = "")
    }

    /** 生成候选（完成当前人物选择） */
    fun completeCurrentPersonSelection() {
        val sessionId = _uiState.value.activeSessionId ?: return
        val personId = _uiState.value.currentSelectingPersonId ?: return
        val selectedIds = orderedParticipantIds()
        val currentIndex = _uiState.value.currentSelectingPersonIndex

        val hasValidSelection = _uiState.value.currentPersonSelections.values.any {
            it == SelectionType.WANT || it == SelectionType.ACCEPT
        }
        if (!hasValidSelection) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "请至少选择一个「想吃」或「可以」的分类"
            )
            return
        }

        viewModelScope.launch {
            try {
                sessionRepository.markParticipantCompleted(sessionId, personId)

                val allSelections = _uiState.value.allPersonSelections.toMutableMap()
                allSelections[personId] = _uiState.value.currentPersonSelections

                val completed = _uiState.value.participantsCompleted + personId
                val nextIndex = currentIndex + 1

                if (nextIndex < selectedIds.size) {
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
                    _uiState.value = _uiState.value.copy(
                        participantsCompleted = completed,
                        allPersonSelections = allSelections,
                        isGeneratingCandidates = true
                    )
                    val session = sessionRepository.getSession(sessionId)
                    if (session != null) {
                        // 生成候选后会话保持 READY（Stage 2.1 不进入 COMPLETED）
                        sessionRepository.updateSession(session.copy(status = SessionStatus.READY))
                    }
                    val candidates = sessionRepository.generateCandidates(sessionId)
                    _uiState.value = _uiState.value.copy(
                        isGeneratingCandidates = false,
                        candidates = candidates,
                        step = DecisionStep.RESULTS
                    )
                    Log.d(TAG, "completeCurrentPersonSelection: all done, candidates=${candidates.size}, status=READY")
                }
            } catch (e: Exception) {
                Log.e(TAG, "completeCurrentPersonSelection: failed", e)
                _uiState.value = _uiState.value.copy(
                    isGeneratingCandidates = false,
                    errorMessage = "生成候选失败: ${e.message ?: "未知错误"}"
                )
            }
        }
    }

    // ── Candidate Reason Lines ──

    fun getCandidateReasonLines(candidate: CandidateCategory): List<String> {
        val profiles = _uiState.value.availableProfiles
        val orderedIds = orderedParticipantIds()

        if (orderedIds.size == 1) {
            val personId = orderedIds[0]
            val sel = candidate.selectionsByPerson[personId]
            val selectionLabel = when (sel) {
                SelectionType.WANT -> "想吃"
                SelectionType.ACCEPT -> "可以"
                else -> "未选择"
            }
            val lines = mutableListOf("本次选择：$selectionLabel")
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
                    SelectionType.ACCEPT -> "可以"
                    else -> "未选择"
                }
                lines.add("$name：$label")
            }
            return lines
        }
        return emptyList()
    }

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

    fun getPreviousPersonName(): String? {
        val completed = _uiState.value.participantsCompleted
        if (completed.isEmpty()) return null
        val last = completed.last()
        return _uiState.value.availableProfiles.firstOrNull { it.id == last }?.name
    }

    // ── Navigation & Dialogs ──

    /**
     * 处理返回键/回首页。
     * 条件页：无修改直接退出；有修改弹草稿对话框。
     */
    fun handleBack(onExit: () -> Unit) {
        when (_uiState.value.step) {
            DecisionStep.CONDITIONS -> {
                if (_uiState.value.conditionsModified) {
                    _uiState.value = _uiState.value.copy(showDiscardDialog = true)
                } else {
                    onExit()
                }
            }
            DecisionStep.HANDOFF,
            DecisionStep.CATEGORY_SELECT,
            DecisionStep.RESULTS -> {
                _uiState.value = _uiState.value.copy(showExitDialog = true)
            }
        }
    }

    fun hideDiscardDialog() {
        _uiState.value = _uiState.value.copy(showDiscardDialog = false)
    }

    /** 放弃修改：取消会话并退出 */
    fun confirmDiscard() {
        _uiState.value = _uiState.value.copy(showDiscardDialog = false)
        val sessionId = _uiState.value.activeSessionId
        if (sessionId != null) {
            viewModelScope.launch {
                try {
                    sessionRepository.cancelSession(sessionId)
                } catch (e: Exception) {
                    Log.e(TAG, "confirmDiscard: cancel failed", e)
                }
                resetToConditions()
            }
        } else {
            resetToConditions()
        }
    }

    fun showExitDialog() {
        _uiState.value = _uiState.value.copy(showExitDialog = true)
    }

    fun hideExitDialog() {
        _uiState.value = _uiState.value.copy(showExitDialog = false)
    }

    fun showCancelDialog() {
        _uiState.value = _uiState.value.copy(showExitDialog = false, showCancelDialog = true, cancelForNewSession = false)
    }

    fun hideCancelDialog() {
        _uiState.value = _uiState.value.copy(showCancelDialog = false, cancelForNewSession = false)
    }

    fun confirmCancelSession() {
        val forNew = _uiState.value.cancelForNewSession
        _uiState.value = _uiState.value.copy(showCancelDialog = false, cancelForNewSession = false)
        if (forNew) {
            cancelActiveAndStartNew()
        } else {
            cancelSession()
        }
    }

    fun cancelSession() {
        val sessionId = _uiState.value.activeSessionId
        if (sessionId == null) {
            resetToConditions()
            return
        }
        viewModelScope.launch {
            try {
                sessionRepository.cancelSession(sessionId)
                Log.d(TAG, "cancelSession: success")
                resetToConditions()
            } catch (e: Exception) {
                Log.e(TAG, "cancelSession: failed", e)
                _uiState.value = _uiState.value.copy(errorMessage = "取消会话失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    // 新会话意见框
    fun confirmNewSession() {
        // 第一步：提示"继续现有决定"，点击"放弃并重新开始"进入二次确认
        _uiState.value = _uiState.value.copy(
            showNewSessionDialog = false,
            showCancelDialog = true,
            cancelForNewSession = true
        )
    }

    /** "继续现有决定"：恢复活动会话，不创建新会话 */
    fun cancelNewSession() {
        _uiState.value = _uiState.value.copy(showNewSessionDialog = false)
        resumeActiveSession()
    }

    private fun resumeActiveSession() {
        viewModelScope.launch {
            try {
                val active = sessionRepository.observeActiveSession().first()
                val st = _uiState.value
                if (active != null) {
                    restoreSession(active, st.availableProfiles, st.allCategories, st.usageMode)
                } else {
                    _uiState.value = _uiState.value.copy(showNewSessionDialog = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "resumeActiveSession: failed", e)
                _uiState.value = _uiState.value.copy(
                    showNewSessionDialog = false,
                    errorMessage = "恢复会话失败: ${e.message ?: "未知错误"}"
                )
            }
        }
    }

    private fun cancelActiveAndStartNew() {
        viewModelScope.launch {
            try {
                val active = sessionRepository.observeActiveSession().first()
                if (active != null) {
                    sessionRepository.cancelSession(active.id)
                }
                val st = _uiState.value
                _uiState.value = _uiState.value.copy(
                    errorMessage = null,
                    conditionsInlineError = null,
                    activeSessionId = null,
                    mealModes = emptySet(),
                    moodTags = setOf(MoodTag.NO_REQUIREMENT),
                    budgetLevel = BudgetLevel.UNLIMITED,
                    distanceLevel = DistanceLevel.UNLIMITED,
                    conditionsModified = false,
                    selectedParticipantIds = st.availableProfiles.map { it.id }.toSet(),
                    candidates = emptyList(),
                    step = DecisionStep.CONDITIONS
                )
            } catch (e: Exception) {
                Log.e(TAG, "cancelActiveAndStartNew: failed", e)
                _uiState.value = _uiState.value.copy(errorMessage = "操作失败: ${e.message ?: "未知错误"}")
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
                val session = sessionRepository.getSession(sessionId)
                if (session != null) {
                    sessionRepository.updateSession(session.copy(status = SessionStatus.SELECTING))
                }
                val participants = selectedIds.mapIndexed { index, personId ->
                    SessionParticipant(sessionId, personId, index, completed = false)
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
            } catch (e: Exception) {
                Log.e(TAG, "goBackToFirstPersonSelection: failed", e)
                _uiState.value = _uiState.value.copy(errorMessage = "返回修改失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    fun goToResults() {
        _uiState.value = _uiState.value.copy(step = DecisionStep.RESULTS)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun loadCurrentPersonSelections() {
        val sessionId = _uiState.value.activeSessionId ?: return
        val personId = _uiState.value.currentSelectingPersonId ?: return
        viewModelScope.launch {
            try {
                val selections = sessionRepository.getSelections(sessionId, personId)
                val selectionMap = selections.associate { it.categoryId to it.selectionType }
                val prefs = preferenceRepository.getByPerson(personId).associateBy { it.categoryId }
                _uiState.value = _uiState.value.copy(
                    currentPersonSelections = selectionMap,
                    currentPersonPreferences = prefs
                )
            } catch (e: Exception) {
                Log.e(TAG, "loadCurrentPersonSelections: failed", e)
                _uiState.value = _uiState.value.copy(errorMessage = "加载选择数据失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    /**
     * 参与者确定顺序：主用户（isPrimary）始终第一，其余按档案顺序。
     * 不依赖 Set.first() 迭代顺序。
     */
    private fun orderedParticipantIds(): List<String> {
        val profiles = _uiState.value.availableProfiles
        val selected = _uiState.value.selectedParticipantIds
        val primary = profiles.firstOrNull { it.isPrimary }
        val ordered = mutableListOf<String>()
        if (primary != null && selected.contains(primary.id)) ordered.add(primary.id)
        profiles.asSequence()
            .filter { it.id != primary?.id }
            .map { it.id }
            .filter { selected.contains(it) }
            .forEach { ordered.add(it) }
        return ordered
    }

    private fun resetToConditions() {
        val profiles = _uiState.value.availableProfiles
        val mode = _uiState.value.usageMode
        val shown = if (mode == AppUsageMode.SINGLE) profiles.filter { it.isPrimary } else profiles
        _uiState.value = DecisionUiState(
            usageMode = mode,
            availableProfiles = shown,
            allCategories = _uiState.value.allCategories,
            isLoading = false,
            selectedParticipantIds = shown.map { it.id }.toSet()
        )
    }
}