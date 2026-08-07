package com.what2eat.feature.decision

import com.what2eat.domain.model.BudgetLevel
import com.what2eat.domain.model.CandidateCategory
import com.what2eat.domain.model.DistanceLevel
import com.what2eat.domain.model.FoodCategory
import com.what2eat.domain.model.MealMode
import com.what2eat.domain.model.MoodTag
import com.what2eat.domain.model.PersonProfile
import com.what2eat.domain.model.SelectionType

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

    // 错误状态
    val errorMessage: String? = null,
    val showExitDialog: Boolean = false,
    val showNewSessionDialog: Boolean = false,
    val showCancelDialog: Boolean = false,

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
    val searchQuery: String = "",

    // 结果
    val candidates: List<CandidateCategory> = emptyList(),
    val isGeneratingCandidates: Boolean = false
) {
    /** 当前选择中的人物 */
    val currentSelectingPerson: PersonProfile?
        get() = availableProfiles.firstOrNull { it.id == currentSelectingPersonId }

    /** 是否有活动会话 */
    val hasActiveSession: Boolean get() = activeSessionId != null

    /** 是否有错误 */
    val hasError: Boolean get() = errorMessage != null

    /** 当前人物的选择统计 */
    val currentWantCount: Int
        get() = currentPersonSelections.values.count { it == SelectionType.WANT }

    val currentAcceptCount: Int
        get() = currentPersonSelections.values.count { it == SelectionType.ACCEPT }

    val currentNotTodayCount: Int
        get() = currentPersonSelections.values.count { it == SelectionType.NOT_TODAY }

    /** 过滤后的分类列表（搜索） */
    val filteredRootCategories: List<FoodCategory>
        get() {
            val roots = allCategories.filter { it.parentId == null }
            if (searchQuery.isBlank()) return roots
            val query = searchQuery.trim()
            return roots.filter { root ->
                val children = allCategories.filter { it.parentId == root.id }
                root.name.contains(query, ignoreCase = true) ||
                    children.any { it.name.contains(query, ignoreCase = true) }
            }
        }
}
