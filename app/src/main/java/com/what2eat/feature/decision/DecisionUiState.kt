package com.what2eat.feature.decision

import com.what2eat.domain.engine.MatchLevel
import com.what2eat.domain.engine.ReasonType
import com.what2eat.domain.engine.RecommendationItem
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

/** 决策流程步骤（Stage 2.1 精简流程 + Stage 2.2 最终推荐） */
enum class DecisionStep {
    CONDITIONS,      // 本次条件（参与人物+用餐方式+状态+预算+距离，合并为单页）
    HANDOFF,         // 双人交接（请将手机交给...）
    CATEGORY_SELECT, // 每人本次选择
    RESULTS,         // 候选结果
    RECOMMENDATION,  // 最终推荐（Stage 2.2）
    COMPLETED        // 决定完成页（Stage 2.2）
}

/** 一个分类分组：一级分类 + 其子分类（可能被搜索过滤） */
data class CategoryGroup(
    val root: FoodCategory,
    val children: List<FoodCategory>
)

/** 最终推荐的展示数据（含推荐原因与匹配度） */
data class RecommendationView(
    val categoryId: String,
    val categoryName: String,
    val parentCategoryName: String? = null,
    /** 匹配度标签 */
    val matchLevel: MatchLevel,
    /** 推荐原因 type 列表 */
    val reasonTypes: List<ReasonType>,
    /** 调试用权重，普通 UI 不直接展示 */
    val weight: Double
)

/** 决策流程 UI 状态 */
data class DecisionUiState(
    val step: DecisionStep = DecisionStep.CONDITIONS,
    val activeSessionId: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val message: String? = null,

    // 使用模式
    val usageMode: AppUsageMode = AppUsageMode.SINGLE,

    // 错误状态
    val errorMessage: String? = null,
    /** 本次条件页的就地提示（显示在当前条件页内，不跳转整页错误） */
    val conditionsInlineError: String? = null,
    val showExitDialog: Boolean = false,
    val showNewSessionDialog: Boolean = false,
    val showCancelDialog: Boolean = false,
    // 放弃确认框是否来自"放弃并重新开始"（true=取消后进入新流程，false=取消后退出）
    val cancelForNewSession: Boolean = false,
    // 条件页已修改但未保存时返回首页的草稿提示
    val showDiscardDialog: Boolean = false,
    // 条件页是否存在未保存修改
    val conditionsModified: Boolean = false,

    // 参与者
    val availableProfiles: List<PersonProfile> = emptyList(),
    val selectedParticipantIds: Set<String> = emptySet(),
    val currentSelectingPersonId: String? = null,
    val currentSelectingPersonIndex: Int = 0,
    val participantsCompleted: List<String> = emptyList(),

    // 条件
    val mealModes: Set<MealMode> = emptySet(),
    /** 今天的状态，默认"没什么要求"（正常流程不应为空） */
    val moodTags: Set<MoodTag> = setOf(MoodTag.NO_REQUIREMENT),
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
    val isGeneratingCandidates: Boolean = false,

    // ── Stage 2.2 最终推荐 ──
    /** 当前推荐 */
    val recommendation: RecommendationView? = null,
    /** 所有通过硬过滤且未被拒绝的候选（含权重），用于"看看其他候选" */
    val survivingCandidates: List<RecommendationItem> = emptyList(),
    /** 本轮已拒绝（换一个）的候选 id */
    val rejectedIds: Set<String> = emptySet(),
    /** 换一个次数 */
    val rerollCount: Int = 0,
    /** 是否正在计算推荐 */
    val isComputingRecommendation: Boolean = false,
    /** 是否候选耗尽（原本无候选或全部换完） */
    val recommendationExhausted: Boolean = false,
    /** 是否只剩一个候选（仅显示"只剩这个选择了"） */
    val isLastRecommendation: Boolean = false,
    /** 完成页展示的分量 */
    val completedCategory: RecommendationView? = null,

    // ── Stage 3.1 通用搜索承接 ──
    /** 完成页是否显示"去找餐厅"底部搜索面板 */
    val showSearchPanel: Boolean = false,
    /** 搜索操作的 Snackbar 提示（地图/浏览器/复制结果反馈） */
    val searchMessage: String? = null
) {
    /** 当前选择中的人物 */
    val currentSelectingPerson: PersonProfile?
        get() = availableProfiles.firstOrNull { it.id == currentSelectingPersonId }

    /** 当前主用户 */
    val primaryProfile: PersonProfile?
        get() = availableProfiles.firstOrNull { it.isPrimary }

    /** 是否有活动会话 */
    val hasActiveSession: Boolean get() = activeSessionId != null

    /** 是否有错误 */
    val hasError: Boolean get() = errorMessage != null

    /** 是否双人模式（使用模式为 COUPLE 且选择人数 > 1） */
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

    /** 搜索结果数量（命中的二级分类总数） */
    val searchResultCount: Int
        get() = filteredGroups.sumOf { it.children.size }

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