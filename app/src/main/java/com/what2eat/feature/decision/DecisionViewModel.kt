package com.what2eat.feature.decision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.what2eat.domain.model.BudgetLevel
import com.what2eat.domain.model.CandidateCategory
import com.what2eat.domain.model.DecisionSession
import com.what2eat.domain.model.DistanceLevel
import com.what2eat.domain.model.FoodCategory
import com.what2eat.domain.model.MealMode
import com.what2eat.domain.model.MoodTag
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

    private val _uiState = MutableStateFlow(DecisionUiState())
    val uiState: StateFlow<DecisionUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val profiles = personProfileRepository.observeEnabled().first()
            val allCategories = foodCategoryRepository.observeAll().first()
            val activeSession = sessionRepository.observeActiveSession().first()

            if (activeSession != null) {
                // 恢复活动会话
                restoreSession(activeSession, profiles, allCategories)
            } else {
                // 新会话
                _uiState.value = _uiState.value.copy(
                    availableProfiles = profiles,
                    allCategories = allCategories,
                    isLoading = false,
                    // 默认选中所有已启用人物
                    selectedParticipantIds = profiles.map { it.id }.toSet()
                )
            }
        }
    }

    private suspend fun restoreSession(
        session: DecisionSession,
        profiles: List<PersonProfile>,
        allCategories: List<FoodCategory>
    ) {
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
            session.status == SessionStatus.SELECTING && participants.any { !it.completed } -> {
                val nextUncompleted = participants.firstOrNull { !it.completed }
                if (nextUncompleted != null && participants.indexOf(nextUncompleted) > 0) {
                    DecisionStep.HANDOFF
                } else {
                    DecisionStep.CATEGORY_SELECT
                }
            }
            session.mealModes.isEmpty() -> DecisionStep.MEAL_MODE
            session.moodTags.isEmpty() && session.status == SessionStatus.DRAFT -> DecisionStep.MOOD
            else -> DecisionStep.MEAL_MODE
        }

        _uiState.value = _uiState.value.copy(
            activeSessionId = session.id,
            availableProfiles = profiles,
            allCategories = allCategories,
            selectedParticipantIds = selectedIds,
            mealModes = session.mealModes,
            moodTags = session.moodTags,
            budgetLevel = session.budgetLevel,
            distanceLevel = session.distanceLevel,
            hardExcludedCategoryIds = hardExcluded,
            allPersonSelections = selectionsByPerson,
            currentSelectingPersonId = participants.firstOrNull { !it.completed }?.personId,
            participantsCompleted = participants.filter { it.completed }.map { it.personId },
            step = step,
            isLoading = false
        )
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

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)

            // 检查是否有旧活动会话
            val activeSession = sessionRepository.observeActiveSession().first()
            if (activeSession != null) {
                // 取消旧会话
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
        // 应用"都可以"冲突规则
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
        // 应用"没什么要求"互斥规则
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
        val sessionId = _uiState.value.activeSessionId ?: return
        val selectedIds = _uiState.value.selectedParticipantIds

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)

            // 更新会话条件
            val session = sessionRepository.getSession(sessionId) ?: return@launch
            sessionRepository.updateSession(
                session.copy(
                    mealModes = _uiState.value.mealModes,
                    moodTags = _uiState.value.moodTags,
                    budgetLevel = _uiState.value.budgetLevel,
                    distanceLevel = _uiState.value.distanceLevel,
                    status = SessionStatus.SELECTING
                )
            )

            // 设置第一个选择者
            val firstPersonId = selectedIds.first()
            _uiState.value = _uiState.value.copy(
                isSaving = false,
                currentSelectingPersonId = firstPersonId,
                currentSelectingPersonIndex = 0,
                participantsCompleted = emptyList(),
                step = if (selectedIds.size > 1) DecisionStep.HANDOFF else DecisionStep.CATEGORY_SELECT
            )
        }
    }

    // ── Handoff ──

    fun startHandoffSelection() {
        _uiState.value = _uiState.value.copy(step = DecisionStep.CATEGORY_SELECT)
    }

    // ── Category Selection ──

    fun setCategorySelection(categoryId: String, type: SelectionType) {
        val sessionId = _uiState.value.activeSessionId ?: return
        val personId = _uiState.value.currentSelectingPersonId ?: return

        viewModelScope.launch {
            sessionRepository.setSelection(
                SessionCategorySelection(
                    sessionId = sessionId,
                    personId = personId,
                    categoryId = categoryId,
                    selectionType = type
                )
            )

            // 更新本地状态
            val updatedSelections = _uiState.value.currentPersonSelections.toMutableMap()
            updatedSelections[categoryId] = type
            _uiState.value = _uiState.value.copy(currentPersonSelections = updatedSelections)
        }
    }

    /** 完成当前人物选择 */
    fun completeCurrentPersonSelection() {
        val sessionId = _uiState.value.activeSessionId ?: return
        val personId = _uiState.value.currentSelectingPersonId ?: return
        val selectedIds = _uiState.value.selectedParticipantIds.toList()
        val currentIndex = _uiState.value.currentSelectingPersonIndex

        viewModelScope.launch {
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
                    allPersonSelections = allSelections,
                    step = DecisionStep.HANDOFF
                )
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
            }
        }
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

    /** 取消会话 */
    fun cancelSession() {
        val sessionId = _uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            sessionRepository.cancelSession(sessionId)
            _uiState.value = DecisionUiState(
                availableProfiles = _uiState.value.availableProfiles,
                allCategories = _uiState.value.allCategories,
                isLoading = false,
                selectedParticipantIds = _uiState.value.availableProfiles.map { it.id }.toSet()
            )
        }
    }

    /** 完成会话 */
    fun completeSession() {
        val sessionId = _uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            sessionRepository.completeSession(sessionId)
            _uiState.value = DecisionUiState(
                availableProfiles = _uiState.value.availableProfiles,
                allCategories = _uiState.value.allCategories,
                isLoading = false,
                selectedParticipantIds = _uiState.value.availableProfiles.map { it.id }.toSet()
            )
        }
    }

    /** 获取当前人物已保存的选择 */
    fun loadCurrentPersonSelections() {
        val sessionId = _uiState.value.activeSessionId ?: return
        val personId = _uiState.value.currentSelectingPersonId ?: return

        viewModelScope.launch {
            val selections = sessionRepository.getSelections(sessionId, personId)
            val selectionMap = selections.associate { it.categoryId to it.selectionType }
            _uiState.value = _uiState.value.copy(currentPersonSelections = selectionMap)
        }
    }
}
