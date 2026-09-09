package com.what2eat.data.repository

import com.what2eat.core.database.dao.SavedOptionDao
import com.what2eat.core.database.entity.SavedOptionEntity
import com.what2eat.domain.model.ImportStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.3.0 待整理批量整理回归：复刻 SavedOptionRepositoryImpl 的
 * markCompleted / deleteByIds（空列表守卫 + DAO 批量语句）语义。
 *
 * JVM 单测无法驱动 Room withTransaction，此处以内存 DAO 忠实复刻
 * 两条 SQL 语义后验证业务行为（与 TagManagementTest 同一惯例）：
 * 1. 批量确认入库：仅选中项置 COMPLETE + updatedAt 更新，未选中项不动；
 * 2. 批量删除：仅选中项移除，未选中项保留；
 * 3. 空列表守卫：不触发任何 DAO 调用。
 */
class SavedOptionBatchOpsTest {

    // ── 内存 DAO（忠实模拟 SQL 语义 + 调用计数）──

    private class FakeSavedOptionDao(
        initial: List<SavedOptionEntity>
    ) : SavedOptionDao {
        private val state = MutableStateFlow(initial)
        var updateCalls = 0
            private set
        var deleteCalls = 0
            private set

        fun current(): List<SavedOptionEntity> = state.value

        override fun observeAll(): Flow<List<SavedOptionEntity>> = state

        override fun observeById(id: String): Flow<SavedOptionEntity?> =
            MutableStateFlow(current().firstOrNull { it.id == id })

        override suspend fun getById(id: String): SavedOptionEntity? =
            current().firstOrNull { it.id == id }

        override fun search(query: String): Flow<List<SavedOptionEntity>> =
            MutableStateFlow(current().filter { it.name.contains(query) })

        override suspend fun upsert(entity: SavedOptionEntity) {
            state.value = current().filterNot { it.id == entity.id } + entity
        }

        override suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long) {
            state.value = current().map {
                if (it.id == id) it.copy(enabled = enabled, updatedAt = updatedAt) else it
            }
        }

        override suspend fun setLastChosenAt(id: String, chosenAt: Long) {
            state.value = current().map {
                if (it.id == id) it.copy(lastChosenAt = chosenAt) else it
            }
        }

        override suspend fun delete(id: String) {
            state.value = current().filterNot { it.id == id }
        }

        override suspend fun updateImportStatus(ids: List<String>, status: Int, now: Long) {
            updateCalls++
            state.value = current().map {
                if (it.id in ids) it.copy(importStatus = status, updatedAt = now) else it
            }
        }

        override suspend fun deleteByIds(ids: List<String>) {
            deleteCalls++
            state.value = current().filterNot { it.id in ids }
        }

        override suspend fun getAll(): List<SavedOptionEntity> = current()

        override suspend fun insertAll(entities: List<SavedOptionEntity>) {
            state.value = current() + entities
        }

        override suspend fun deleteAll() {
            state.value = emptyList()
        }
    }

    /** 复刻 SavedOptionRepositoryImpl.markCompleted：空列表不调 DAO，否则置 COMPLETE + now */
    private suspend fun markCompleted(dao: FakeSavedOptionDao, ids: List<String>) {
        if (ids.isEmpty()) return
        dao.updateImportStatus(ids, ImportStatus.COMPLETE.ordinal, NOW)
    }

    /** 复刻 SavedOptionRepositoryImpl.deleteByIds：空列表不调 DAO */
    private suspend fun deleteByIds(dao: FakeSavedOptionDao, ids: List<String>) {
        if (ids.isEmpty()) return
        dao.deleteByIds(ids)
    }

    private fun entity(id: String, importStatus: Int = ImportStatus.NEEDS_REVIEW.ordinal) =
        SavedOptionEntity(
            id = id,
            name = "店$id",
            optionType = 0,
            sourcePlatform = 0,
            importStatus = importStatus,
            createdAt = 1L,
            updatedAt = 1L
        )

    @Test
    fun `批量确认入库只更新选中项 未选中项不动`() = runBlocking {
        val dao = FakeSavedOptionDao(listOf(entity("a"), entity("b"), entity("c")))

        markCompleted(dao, listOf("a", "c"))

        val byId = dao.current().associateBy { it.id }
        assertEquals(ImportStatus.COMPLETE.ordinal, byId.getValue("a").importStatus)
        assertEquals(NOW, byId.getValue("a").updatedAt)
        assertEquals(ImportStatus.COMPLETE.ordinal, byId.getValue("c").importStatus)
        // 未选中项：状态与时间都不动
        assertEquals(ImportStatus.NEEDS_REVIEW.ordinal, byId.getValue("b").importStatus)
        assertEquals(1L, byId.getValue("b").updatedAt)
        assertEquals(1, dao.updateCalls)
    }

    @Test
    fun `批量删除只移除选中项`() = runBlocking {
        val dao = FakeSavedOptionDao(listOf(entity("a"), entity("b"), entity("c")))

        deleteByIds(dao, listOf("a", "c"))

        assertEquals(listOf("b"), dao.current().map { it.id })
        assertEquals(1, dao.deleteCalls)
    }

    @Test
    fun `空列表守卫不触发任何 DAO 调用`() = runBlocking {
        val dao = FakeSavedOptionDao(listOf(entity("a")))

        markCompleted(dao, emptyList())
        deleteByIds(dao, emptyList())

        assertEquals(0, dao.updateCalls)
        assertEquals(0, dao.deleteCalls)
        // 数据未被改动
        assertEquals(ImportStatus.NEEDS_REVIEW.ordinal, dao.current().single().importStatus)
    }

    private companion object {
        const val NOW = 1_788_000_000_000L
    }
}
