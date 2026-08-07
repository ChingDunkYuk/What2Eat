package com.what2eat.domain.repository

import com.what2eat.domain.model.CandidateCategory
import com.what2eat.domain.model.DecisionSession
import com.what2eat.domain.model.SessionCategorySelection
import com.what2eat.domain.model.SessionParticipant
import kotlinx.coroutines.flow.Flow

/**
 * 决策会话 Repository 接口。
 */
interface DecisionSessionRepository {

    // ── Session ──

    /** 观察当前活动会话（DRAFT 或 SELECTING） */
    fun observeActiveSession(): Flow<DecisionSession?>

    /** 观察所有会话 */
    fun observeAllSessions(): Flow<List<DecisionSession>>

    /** 根据 id 获取会话 */
    suspend fun getSession(id: String): DecisionSession?

    /** 创建新会话，返回 id */
    suspend fun createSession(session: DecisionSession): String

    /** 更新会话 */
    suspend fun updateSession(session: DecisionSession)

    /** 取消会话 */
    suspend fun cancelSession(id: String)

    /** 完成会话 */
    suspend fun completeSession(id: String)

    // ── Participants ──

    /** 观察会话参与者 */
    fun observeParticipants(sessionId: String): Flow<List<SessionParticipant>>

    /** 获取会话参与者 */
    suspend fun getParticipants(sessionId: String): List<SessionParticipant>

    /** 设置会话参与者 */
    suspend fun setParticipants(sessionId: String, participants: List<SessionParticipant>)

    /** 标记参与者完成 */
    suspend fun markParticipantCompleted(sessionId: String, personId: String)

    // ── Category Selections ──

    /** 观察某参与者的分类选择 */
    fun observeSelections(sessionId: String, personId: String): Flow<List<SessionCategorySelection>>

    /** 获取某参与者的分类选择 */
    suspend fun getSelections(sessionId: String, personId: String): List<SessionCategorySelection>

    /** 获取会话所有分类选择 */
    suspend fun getAllSelections(sessionId: String): List<SessionCategorySelection>

    /** 设置分类选择 */
    suspend fun setSelection(selection: SessionCategorySelection)

    /** 删除分类选择 */
    suspend fun deleteSelection(sessionId: String, personId: String, categoryId: String)

    // ── Candidate Generation ──

    /** 生成候选分类列表 */
    suspend fun generateCandidates(sessionId: String): List<CandidateCategory>
}
