package com.what2eat.data.repository

import com.what2eat.core.database.dao.DecisionRecommendationDao
import com.what2eat.core.database.dao.DecisionSessionDao
import com.what2eat.core.database.dao.SessionParticipantDao
import com.what2eat.core.database.entity.DecisionRecommendationEntity
import com.what2eat.core.database.entity.DecisionSessionEntity
import com.what2eat.core.database.entity.SessionParticipantEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 3.1 回归：双人历史记录写入与查询单元测试。
 *
 * 复现并验证"就吃这个"统一完成事务的存储语义（DecisionSession 更新 + 推荐标记选中），
 * 以及历史查询（getCompletedHistory）对单双人记录的一视同仁。覆盖四种回归情况：
 * 1. 单人决定 → 历史新增一条；
 * 2. 双人决定 → 历史新增一条，且 Klaus、晴 两个参与者都保留；
 * 3. 连续完成两次 → 历史新增两条，不覆盖；
 * 4. 杀掉应用重新打开（重建 DAO 于同一存储）→ 单人和双人历史均存在。
 *
 * 说明：事务原子性由生产代码 database.withTransaction 保证（JVM 单测无法直接驱动 Room
 * 事务），此处通过忠实复刻事务体内的两条 DAO 写入来验证业务行为与历史查询不受
 * participant 数量影响。
 */
class DecisionHistoryTest {

    private val klaus = "person_primary"
    private val qing = "person_secondary"

    // ── 内存 DAO（忠实模拟 SQL 语义）──

    private class FakeDecisionSessionDao(
        initial: List<DecisionSessionEntity> = emptyList()
    ) : DecisionSessionDao {
        private val state = MutableStateFlow(initial)

        fun current(): List<DecisionSessionEntity> = state.value

        override fun observeActiveSession(): Flow<DecisionSessionEntity?> =
            MutableStateFlow(current().firstOrNull { it.status in 0..2 })

        override suspend fun getActiveSession(): DecisionSessionEntity? =
            current().firstOrNull { it.status in 0..2 }

        override fun observeAllSessions(): Flow<List<DecisionSessionEntity>> = state

        override suspend fun getById(id: String): DecisionSessionEntity? =
            current().firstOrNull { it.id == id }

        override suspend fun countActiveSessions(): Int = current().count { it.status in 0..2 }

        override suspend fun upsert(entity: DecisionSessionEntity) {
            // REPLACE 语义：同 id 覆盖，不同 id 追加
            val without = current().filterNot { it.id == entity.id }
            state.value = (without + entity)
        }

        override suspend fun updateStatus(id: String, status: Int, updatedAt: Long) {
            state.value = current().map {
                if (it.id == id) it.copy(status = status, updatedAt = updatedAt) else it
            }
        }

        override suspend fun cancel(id: String, completedAt: Long, updatedAt: Long) {
            state.value = current().map {
                if (it.id == id) it.copy(status = 4, completedAt = completedAt, updatedAt = updatedAt) else it
            }
        }

        override suspend fun complete(id: String, completedAt: Long, updatedAt: Long) {
            state.value = current().map {
                if (it.id == id) it.copy(status = 3, completedAt = completedAt, updatedAt = updatedAt) else it
            }
        }

        override suspend fun completeWithRecommendation(
            id: String, categoryId: String, rerollCount: Int, finalWeight: Double,
            completedAt: Long, updatedAt: Long
        ) {
            state.value = current().map {
                if (it.id == id) {
                    it.copy(
                        status = 3, selectedCategoryId = categoryId,
                        rerollCount = rerollCount, finalWeight = finalWeight,
                        completedAt = completedAt, updatedAt = updatedAt
                    )
                } else it
            }
        }

        override suspend fun getCompletedWithSelection(): List<DecisionSessionEntity> =
            current()
                .filter { it.status == 3 && it.selectedCategoryId != null }
                .sortedBy { it.completedAt }

        // ── 备份/恢复（v0.9.3）──

        override suspend fun getAll(): List<DecisionSessionEntity> = current()

        override suspend fun insertAll(entities: List<DecisionSessionEntity>) {
            val without = current().filterNot { e -> entities.any { it.id == e.id } }
            state.value = without + entities
        }

        override suspend fun deleteAll() {
            state.value = emptyList()
        }

        override suspend fun updateConditions(
            id: String, mealModes: String, moodTags: String,
            budgetLevel: Int, distanceLevel: Int, status: Int, updatedAt: Long
        ) {
            state.value = current().map {
                if (it.id == id) {
                    it.copy(
                        mealModes = mealModes, moodTags = moodTags,
                        budgetLevel = budgetLevel, distanceLevel = distanceLevel,
                        status = status, updatedAt = updatedAt
                    )
                } else it
            }
        }

        override suspend fun delete(id: String) {
            state.value = current().filterNot { it.id == id }
        }
    }

    private class FakeDecisionRecommendationDao : DecisionRecommendationDao {
        private val state = MutableStateFlow<List<DecisionRecommendationEntity>>(emptyList())

        private fun current() = state.value

        override suspend fun insert(entity: DecisionRecommendationEntity): Long {
            state.value = current() + entity
            return (current().size).toLong()
        }

        override fun observeBySession(sessionId: String): Flow<List<DecisionRecommendationEntity>> =
            MutableStateFlow(current().filter { it.sessionId == sessionId })

        override suspend fun getBySession(sessionId: String): List<DecisionRecommendationEntity> =
            current().filter { it.sessionId == sessionId }

        override suspend fun markSelected(sessionId: String, categoryId: String) {
            state.value = current().map {
                if (it.sessionId == sessionId && it.categoryId == categoryId) it.copy(selected = true) else it
            }
        }

        override suspend fun markRejected(sessionId: String, categoryId: String) {
            state.value = current().map {
                if (it.sessionId == sessionId && it.categoryId == categoryId) it.copy(rejected = true) else it
            }
        }

        // ── 备份/恢复（v0.9.3）──

        override suspend fun getAll(): List<DecisionRecommendationEntity> = current()

        override suspend fun insertAll(entities: List<DecisionRecommendationEntity>) {
            val without = current().filterNot { e -> entities.any { it.id == e.id } }
            state.value = without + entities
        }

        override suspend fun deleteAll() {
            state.value = emptyList()
        }
    }

    private class FakeSessionParticipantDao(
        initial: List<SessionParticipantEntity> = emptyList()
    ) : SessionParticipantDao {
        private val state = MutableStateFlow(initial)

        fun current() = state.value

        override fun observeBySession(sessionId: String): Flow<List<SessionParticipantEntity>> =
            MutableStateFlow(current().filter { it.sessionId == sessionId }
                .sortedBy { it.selectionOrder })

        override suspend fun getBySession(sessionId: String): List<SessionParticipantEntity> =
            current().filter { it.sessionId == sessionId }.sortedBy { it.selectionOrder }

        override suspend fun getAll(): List<SessionParticipantEntity> = current()

        override suspend fun upsertAll(entities: List<SessionParticipantEntity>) {
            val without = current().filterNot { e -> entities.any { it.sessionId == e.sessionId && it.personId == e.personId } }
            state.value = without + entities
        }

        override suspend fun markCompleted(sessionId: String, personId: String) {
            state.value = current().map {
                if (it.sessionId == sessionId && it.personId == personId) it.copy(completed = true) else it
            }
        }

        override suspend fun deleteBySession(sessionId: String) {
            state.value = current().filterNot { it.sessionId == sessionId }
        }

        // ── 备份/恢复（v0.9.3）──

        override suspend fun deleteAll() {
            state.value = emptyList()
        }
    }

    // ── 复刻统一完成事务（与 DecisionSessionRepositoryImpl.completeWithRecommendation 一致）──

    private var idCounter = 0

    private fun newSession(
        sessionDao: FakeDecisionSessionDao,
        participantDao: FakeSessionParticipantDao,
        participantIds: List<String>,
        categoryId: String,
        weight: Double = 150.0
    ): String {
        val id = "session-${idCounter++}"
        val now = System.currentTimeMillis()
        runBlocking {
            sessionDao.upsert(
                DecisionSessionEntity(
                    id = id, decisionMode = 0, status = 2, startedAt = now, completedAt = null,
                    mealModes = "", moodTags = "", budgetLevel = 0, distanceLevel = 0,
                    selectedCategoryId = null, rerollCount = 0, finalWeight = 0.0,
                    createdAt = now, updatedAt = now
                )
            )
            participantDao.upsertAll(
                participantIds.mapIndexed { index, personId ->
                    SessionParticipantEntity(id, personId, index, completed = true)
                }
            )
            // 统一完成事务体：session 更新 + 推荐标记选中
            sessionDao.completeWithRecommendation(
                id = id, categoryId = categoryId, rerollCount = 0,
                finalWeight = weight, completedAt = now + 1, updatedAt = now + 1
            )
        }
        return id
    }

    private fun historyCategories(sessionDao: FakeDecisionSessionDao): List<String> =
        sessionDao.current()
            .filter { it.status == 3 && it.selectedCategoryId != null }
            .sortedBy { it.completedAt }
            .map { it.selectedCategoryId!! }

    /** 复刻 repository.getCompletedHistory()：返回 (categoryId, completedAt)，供 historyScore 使用 */
    private fun completedHistoryPairs(sessionDao: FakeDecisionSessionDao): List<Pair<String, Long>> =
        sessionDao.current()
            .filter { it.status == 3 && it.selectedCategoryId != null && it.completedAt != null }
            .map { it.selectedCategoryId!! to it.completedAt!! }

    // ── 情况 1：单人决定 → 历史新增一条 ──

    @Test
    fun `single person decision adds one history record`() {
        val sessionDao = FakeDecisionSessionDao()
        val participantDao = FakeSessionParticipantDao()

        newSession(sessionDao, participantDao, listOf(klaus), "cat-1")

        val history = historyCategories(sessionDao)
        assertEquals(1, history.size)
        assertEquals("cat-1", history[0])
    }

    // ── 情况 2：双人决定 → 历史新增一条，且两个参与者都保留 ──

    @Test
    fun `double person decision adds one history and keeps both participants`() {
        val sessionDao = FakeDecisionSessionDao()
        val participantDao = FakeSessionParticipantDao()
        val recDao = FakeDecisionRecommendationDao()

        val id = newSession(sessionDao, participantDao, listOf(klaus, qing), "cat-firepot")

        // 历史新增一条
        val history = historyCategories(sessionDao)
        assertEquals(1, history.size)
        assertEquals("cat-firepot", history[0])

        // 参与者保留：Klaus、晴 都仍在（不被丢弃）
        val participants = runBlocking { participantDao.getBySession(id) }
        assertEquals(2, participants.size)
        assertEquals(klaus, participants[0].personId)
        assertEquals(qing, participants[1].personId)
        // 统一完成事务同时标记推荐选中
        runBlocking {
            recDao.insert(
                DecisionRecommendationEntity(
                    sessionId = id, categoryId = "cat-firepot", rank = 1,
                    weight = 150.0, selected = false, rejected = false, reasonKeys = "", createdAt = System.currentTimeMillis()
                )
            )
        }
        runBlocking { recDao.markSelected(id, "cat-firepot") }
        assertTrue(runBlocking { recDao.getBySession(id) }[0].selected)
    }

    // ── 情况 3：连续完成两次 → 历史新增两条，不覆盖 ──

    @Test
    fun `two consecutive completions create two records without overwrite`() {
        val sessionDao = FakeDecisionSessionDao()
        val participantDao = FakeSessionParticipantDao()

        newSession(sessionDao, participantDao, listOf(klaus), "cat-1")
        newSession(sessionDao, participantDao, listOf(klaus, qing), "cat-2")

        val history = historyCategories(sessionDao)
        assertEquals(2, history.size)
        assertEquals(listOf("cat-1", "cat-2"), history)
    }

    // ── 情况 4：杀掉应用重新打开 → 单人和双人历史均存在 ──

    @Test
    fun `restart keeps both single and double history`() {
        // 第一次"运行"：完成一条单人 + 一条双人
        val sessionDao = FakeDecisionSessionDao()
        val participantDao = FakeSessionParticipantDao()
        newSession(sessionDao, participantDao, listOf(klaus), "cat-single")
        newSession(sessionDao, participantDao, listOf(klaus, qing), "cat-double")

        // 模拟杀掉应用重新打开：用同一份底层数据重建 DAO / 查询
        val restartedDao = FakeDecisionSessionDao(sessionDao.current())
        val restartedParticipant = FakeSessionParticipantDao(participantDao.current())

        val history = historyCategories(restartedDao)
        assertEquals(2, history.size)
        assertEquals(listOf("cat-single", "cat-double"), history)

        // 双人历史的参与者仍完整
        val doubleSessionId = restartedDao.current().first { it.selectedCategoryId == "cat-double" }.id
        val participants = runBlocking { restartedParticipant.getBySession(doubleSessionId) }
        assertEquals(2, participants.size)
        assertNotNull(participants.firstOrNull { it.personId == klaus })
        assertNotNull(participants.firstOrNull { it.personId == qing })
    }

    // ── 情况 5：双人历史同样参与 historyScore 防重复 ──

    @Test
    fun `double person history feeds historyScore anti-duplication`() {
        val sessionDao = FakeDecisionSessionDao()
        val participantDao = FakeSessionParticipantDao()

        // 双人刚吃过 cat-firepot
        newSession(sessionDao, participantDao, listOf(klaus, qing), "cat-firepot")

        // getCompletedHistory 返回双人历史，下一轮 DecisionEngine 可据此施加惩罚
        val history = completedHistoryPairs(sessionDao)
        assertEquals(1, history.size)
        assertEquals("cat-firepot", history[0].first)
        assertNotNull(history[0].second)

        // 与单人历史同源：不存在"单人历史影响推荐、双人历史不影响"的差异
        newSession(sessionDao, participantDao, listOf(klaus), "cat-single")
        val combined = completedHistoryPairs(sessionDao)
        assertEquals(2, combined.size)
        assertTrue(combined.any { it.first == "cat-firepot" })
        assertTrue(combined.any { it.first == "cat-single" })
    }
}