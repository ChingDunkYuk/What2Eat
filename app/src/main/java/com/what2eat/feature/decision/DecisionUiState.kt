package com.what2eat.feature.decision

import com.what2eat.domain.model.AppUsageMode
import com.what2eat.domain.model.BudgetLevel
import com.what2eat.domain.model.CandidateCategory
import com.what2eat.domain.model.DistanceLevel
import com.what2eat.domain.model.FoodCategory
import com.what2eat.domain.model.MealMode
import com.what2eat.domain.model.MoodTag
import com.what2eat.domain.model.PersonCategoryPreference
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

/** 一个分类分组：一级分类 + 其子分类（可能被搜索过滤） */
data class CategoryGroup(
    val root: FoodCategory,
    val children: List<FoodCategory>
)

/** 决策流程 UI 状态 */
data class DecisionUiState(
    val step: DecisionStep = DecisionStep.PARTICIPANTS,
    val activeSessionId: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val message: String? = null,

    // 使用模式
    val usageMode: AppUsageMode = AppUsageMode.SINGLE,

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

    // 当前人物的长期偏好（categoryId -> preference），用于候选解释
    val currentPersonPreferences: Map<String, PersonCategoryPreference> = emptyMap(),

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

    /** 是否双人模式 */
    val isDualMode: Boolean get() = selectedParticipantIds.size > 1

    /** 当前人物的选择统计 */
    val currentWantCount: Int
        get() = currentPersonSelections.values.count { it == SelectionType.WANT }

    val currentAcceptCount: Int
        get() = currentPersonSelections.values.count { it == SelectionType.ACCEPT }

    val currentNotTodayCount: Int
        get() = currentPersonSelections.values.count { it == SelectionType.NOT_TODAY }

    /** 搜索后是否为空（搜了但无结果） */
    val isSearchEmpty: Boolean
        get() = searchQuery.trim().isNotEmpty() && filteredGroups.isEmpty()

    /**
     * 过滤后的分类分组（子分类级别过滤）。
     * - 输入"粤菜"只显示粤菜（其一级分类下只保留匹配子分类）
     * - 输入"火锅"匹配一级分类及其子分类
     * - 清空搜索恢复完整列表
     */
    val filteredGroups: List<CategoryGroup>
        get() {
            val roots = allCategories.filter { it.parentId == null }
            val query = searchQuery.trim()
            val hasQuery = query.isNotEmpty()
            return roots.mapNotNull { root ->
                val allChildren = allCategories.filter { it.parentId == root.id }
                val children = if (hasQuery) {
                    allChildren.filter { child ->
                        child.name.contains(query, ignoreCase = true) ||
                            root.name.contains(query, ignoreCase = true)
                    }
                } else {
                    allChildren
                }
                if (children.isNotEmpty()) CategoryGroup(root = root, children = children) else null
            }
        }
}