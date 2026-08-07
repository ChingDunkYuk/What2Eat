package com.what2eat.feature.decision

import com.what2eat.domain.model.BudgetLevel
import com.what2eat.domain.model.CandidateCategory
import com.what2eat.domain.model.DistanceLevel
import com.what2eat.domain.model.FoodCategory
import com.what2eat.domain.model.MealMode
import com.what2eat.domain.model.MoodTag
import com.what2eat.domain.model.PersonCategoryPreference
import com.what2eat.domain.model.PersonProfile
import com.what2eat.domain.model.SelectionType
import com.what2eat.domain.model.SessionCategorySelection

/** 决策流程步骤 */
enum class DecisionStep {
    PARTICIPANTS,    // 选择参与人物
    MEAL_MODE,       // 用餐方式
    MOOD,            // 今天的状态
    BUDGET,          // 预算
    DISTANCE,        // 距离
    HANDOFF,         // 双人交接（请将手机交给...）
    CATEGORY_SELECT, // 每人本次选择
    RESULTS          // 候选结果
}

/** 决策流程 UI 状态 */
data class DecisionUiState(
    val step: DecisionStep = DecisionStep.PARTICIPANTS,
    val activeSessionId: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val message: String? = null,

    // 参与者
    val availableProfiles: List<PersonProfile> = emptyList(),
    val selectedParticipantIds: Set<String> = emptySet(),
    val currentSelectingPersonId: String? = null,
    val currentSelectingPersonIndex: Int = 0,
    val participantsCompleted: List<String> = emptyList(),

    // 条件
    val mealModes: Set<MealMode> = emptySet(),
    val moodTags: Set<MoodTag> = emptySet(),
    val budgetLevel: BudgetLevel = BudgetLevel.UNLIMITED,
    val distanceLevel: DistanceLevel = DistanceLevel.UNLIMITED,

    // 分类选择
    val allCategories: List<FoodCategory> = emptyList(),
    val hardExcludedCategoryIds: Set<String> = emptySet(),
    val currentPersonSelections: Map<String, SelectionType> = emptyMap(),
    val allPersonSelections: Map<String, Map<String, SelectionType>> = emptyMap(),

    // 结果
    val candidates: List<CandidateCategory> = emptyList(),
    val isGeneratingCandidates: Boolean = false
) {
    /** 当前选择中的人物 */
    val currentSelectingPerson: PersonProfile?
        get() = availableProfiles.firstOrNull { it.id == currentSelectingPersonId }

    /** 是否有活动会话 */
    val hasActiveSession: Boolean get() = activeSessionId != null
}
