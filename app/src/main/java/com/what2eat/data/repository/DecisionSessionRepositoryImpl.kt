package com.what2eat.data.repository

import com.what2eat.core.database.dao.DecisionSessionDao
import com.what2eat.core.database.dao.FoodCategoryDao
import com.what2eat.core.database.dao.PersonCategoryPreferenceDao
import com.what2eat.core.database.dao.SessionCategorySelectionDao
import com.what2eat.core.database.dao.SessionParticipantDao
import com.what2eat.core.database.entity.DecisionSessionEntity
import com.what2eat.core.database.entity.SessionCategorySelectionEntity
import com.what2eat.core.database.entity.SessionParticipantEntity
import com.what2eat.domain.model.BudgetLevel
import com.what2eat.domain.model.CandidateCategory
import com.what2eat.domain.model.DecisionMode
import com.what2eat.domain.model.DecisionSession
import com.what2eat.domain.model.DistanceLevel
import com.what2eat.domain.model.MealMode
import com.what2eat.domain.model.MoodTag
import com.what2eat.domain.model.SelectionType
import com.what2eat.domain.model.SessionCategorySelection
import com.what2eat.domain.model.SessionParticipant
import com.what2eat.domain.model.SessionStatus
import com.what2eat.domain.repository.DecisionSessionRepository
import com.what2eat.domain.repository.FoodCategoryRepository
import com.what2eat.domain.repository.PersonCategoryPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DecisionSessionRepositoryImpl @Inject constructor(
    private val sessionDao: DecisionSessionDao,
    private val participantDao: SessionParticipantDao,
    private val selectionDao: SessionCategorySelectionDao,
    private val foodCategoryRepository: FoodCategoryRepository,
    private val preferenceRepository: PersonCategoryPreferenceRepository
) : DecisionSessionRepository {

    // ── Session ──

    override fun observeActiveSession(): Flow<DecisionSession?> {
        return sessionDao.observeActiveSession().map { it?.toDomain() }
    }

    override fun observeAllSessions(): Flow<List<DecisionSession>> {
        return sessionDao.observeAllSessions().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getSession(id: String): DecisionSession? {
        return sessionDao.getById(id)?.toDomain()
    }

    override suspend fun createSession(session: DecisionSession): String {
        sessionDao.upsert(session.toEntity())
        return session.id
    }

    override suspend fun updateSession(session: DecisionSession) {
        sessionDao.upsert(session.toEntity())
    }

    override suspend fun cancelSession(id: String) {
        sessionDao.cancel(id, System.currentTimeMillis(), System.currentTimeMillis())
    }

    override suspend fun completeSession(id: String) {
        sessionDao.complete(id, System.currentTimeMillis(), System.currentTimeMillis())
    }

    // ── Participants ──

    override fun observeParticipants(sessionId: String): Flow<List<SessionParticipant>> {
        return participantDao.observeBySession(sessionId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getParticipants(sessionId: String): List<SessionParticipant> {
        return participantDao.getBySession(sessionId).map { it.toDomain() }
    }

    override suspend fun setParticipants(sessionId: String, participants: List<SessionParticipant>) {
        participantDao.deleteBySession(sessionId)
        participantDao.upsertAll(participants.map { it.toEntity() })
    }

    override suspend fun markParticipantCompleted(sessionId: String, personId: String) {
        participantDao.markCompleted(sessionId, personId)
    }

    // ── Category Selections ──

    override fun observeSelections(sessionId: String, personId: String): Flow<List<SessionCategorySelection>> {
        return selectionDao.observeByPerson(sessionId, personId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getSelections(sessionId: String, personId: String): List<SessionCategorySelection> {
        return selectionDao.getByPerson(sessionId, personId).map { it.toDomain() }
    }

    override suspend fun getAllSelections(sessionId: String): List<SessionCategorySelection> {
        return selectionDao.getAllBySession(sessionId).map { it.toDomain() }
    }

    override suspend fun setSelection(selection: SessionCategorySelection) {
        selectionDao.upsert(selection.toEntity())
    }

    override suspend fun deleteSelection(sessionId: String, personId: String, categoryId: String) {
        selectionDao.delete(sessionId, personId, categoryId)
    }

    // ── Candidate Generation ──

    override suspend fun generateCandidates(sessionId: String): List<CandidateCategory> {
        val participants = getParticipants(sessionId)
        val allSelections = getAllSelections(sessionId)
        val allCategories = foodCategoryRepository.observeAll().first()

        // 只考虑已启用的二级分类
        val enabledCategories = allCategories.filter { it.enabled && it.parentId != null }

        // 获取每个参与者的长期硬排除
        val hardExcludedByPerson = mutableMapOf<String, Set<String>>()
        for (participant in participants) {
            val prefs = preferenceRepository.getByPerson(participant.personId)
            hardExcludedByPerson[participant.personId] = prefs.filter { it.hardExcluded }.map { it.categoryId }.toSet()
        }

        // 按分类分组选择
        val selectionByCategory = allSelections.groupBy { it.categoryId }

        val candidates = mutableListOf<CandidateCategory>()

        for (category in enabledCategories) {
            val categoryId = category.id

            // 规则2: 不在任何参与者的长期硬排除中
            val isHardExcluded = participants.any { p ->
                hardExcludedByPerson[p.personId]?.contains(categoryId) == true
            }
            if (isHardExcluded) continue

            // 获取所有参与者对此分类的选择
            val selectionsForCategory = selectionByCategory[categoryId] ?: emptyList()
            val selectionByPerson = selectionsForCategory.associateBy { it.personId }

            // 规则3: 不在任何参与者的 NOT_TODAY 中
            val hasNotToday = participants.any { p ->
                selectionByPerson[p.personId]?.selectionType == SelectionType.NOT_TODAY
            }
            if (hasNotToday) continue

            // 规则4: 所有参与者至少标记为 ACCEPT 或 WANT
            val allHaveValidSelection = participants.all { p ->
                val sel = selectionByPerson[p.personId]
                sel?.selectionType == SelectionType.ACCEPT || sel?.selectionType == SelectionType.WANT
            }
            if (!allHaveValidSelection) continue

            // 计算统计
            val wantCount = selectionsForCategory.count { it.selectionType == SelectionType.WANT }
            val acceptCount = selectionsForCategory.count { it.selectionType == SelectionType.ACCEPT }

            // 规则5-7: 排序优先级
            val rank = when {
                wantCount == participants.size -> 0  // 双方都 WANT
                wantCount > 0 -> 1                   // 一方 WANT 一方 ACCEPT
                else -> 2                             // 双方都 ACCEPT
            }

            val parentName = allCategories.firstOrNull { it.id == category.parentId }?.name
            candidates.add(
                CandidateCategory(
                    categoryId = categoryId,
                    categoryName = category.name,
                    parentCategoryName = parentName,
                    wantCount = wantCount,
                    acceptCount = acceptCount,
                    rank = rank
                )
            )
        }

        // 按 rank 排序
        return candidates.sortedBy { it.rank }
    }

    // ── Mappers ──

    private fun DecisionSessionEntity.toDomain(): DecisionSession {
        return DecisionSession(
            id = id,
            decisionMode = DecisionMode.entries.getOrElse(decisionMode) { DecisionMode.CATEGORY_FIRST },
            status = SessionStatus.entries.getOrElse(status) { SessionStatus.DRAFT },
            startedAt = startedAt,
            completedAt = completedAt,
            mealModes = parseMealModes(mealModes),
            moodTags = parseMoodTags(moodTags),
            budgetLevel = BudgetLevel.entries.getOrElse(budgetLevel) { BudgetLevel.UNLIMITED },
            distanceLevel = DistanceLevel.entries.getOrElse(distanceLevel) { DistanceLevel.UNLIMITED },
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun DecisionSession.toEntity(): DecisionSessionEntity {
        return DecisionSessionEntity(
            id = id,
            decisionMode = decisionMode.ordinal,
            status = status.ordinal,
            startedAt = startedAt,
            completedAt = completedAt,
            mealModes = mealModes.joinToString(",") { it.ordinal.toString() },
            moodTags = moodTags.joinToString(",") { it.ordinal.toString() },
            budgetLevel = budgetLevel.ordinal,
            distanceLevel = distanceLevel.ordinal,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun parseMealModes(s: String): Set<MealMode> {
        if (s.isBlank()) return emptySet()
        return s.split(",").mapNotNull { ordinal ->
            ordinal.trim().toIntOrNull()?.let { MealMode.entries.getOrElse(it) { null } }
        }.toSet()
    }

    private fun parseMoodTags(s: String): Set<MoodTag> {
        if (s.isBlank()) return emptySet()
        return s.split(",").mapNotNull { ordinal ->
            ordinal.trim().toIntOrNull()?.let { MoodTag.entries.getOrElse(it) { null } }
        }.toSet()
    }

    private fun SessionParticipantEntity.toDomain(): SessionParticipant {
        return SessionParticipant(
            sessionId = sessionId,
            personId = personId,
            selectionOrder = selectionOrder,
            completed = completed
        )
    }

    private fun SessionParticipant.toEntity(): SessionParticipantEntity {
        return SessionParticipantEntity(
            sessionId = sessionId,
            personId = personId,
            selectionOrder = selectionOrder,
            completed = completed
        )
    }

    private fun SessionCategorySelectionEntity.toDomain(): SessionCategorySelection {
        return SessionCategorySelection(
            sessionId = sessionId,
            personId = personId,
            categoryId = categoryId,
            selectionType = SelectionType.entries.getOrElse(selectionType) { SelectionType.ACCEPT },
            updatedAt = updatedAt
        )
    }

    private fun SessionCategorySelection.toEntity(): SessionCategorySelectionEntity {
        return SessionCategorySelectionEntity(
            sessionId = sessionId,
            personId = personId,
            categoryId = categoryId,
            selectionType = selectionType.ordinal,
            updatedAt = System.currentTimeMillis()
        )
    }
}
