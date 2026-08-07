package com.what2eat.domain.usecase

import com.what2eat.domain.repository.DecisionSessionRepository
import javax.inject.Inject

/**
 * 完成决策的统一用例（单人与双人共用唯一入口）。
 *
 * 用户点击"就吃这个"后由 [invoke] 触发，在单个数据库事务内完成：
 * - 保存 selectedCategoryId
 * - 更新 DecisionSession = COMPLETED
 * - 保存 completedAt
 * - 标记对应推荐为选中
 * - （参与者 SessionParticipant 在会话创建时已保留，完整流程不删除）
 *
 * 单人与双人必须且只能走此用例，避免"单双人两套完成逻辑"导致历史行为不一致。
 */
class CompleteDecisionUseCase @Inject constructor(
    private val sessionRepository: DecisionSessionRepository
) {

    suspend operator fun invoke(
        sessionId: String,
        categoryId: String,
        rerollCount: Int,
        finalWeight: Double
    ) {
        sessionRepository.completeWithRecommendation(
            id = sessionId,
            categoryId = categoryId,
            rerollCount = rerollCount,
            finalWeight = finalWeight
        )
    }
}