package com.what2eat.data.repository

import com.what2eat.core.database.dao.SavedOptionTagDao
import com.what2eat.core.database.dao.TagUsageProjection
import com.what2eat.core.database.entity.SavedOptionTagEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.9.2 标签管理回归：复刻 SavedOptionRepositoryImpl 的
 * renameTag（deleteMergeConflicts + renameTagRefs 事务组合）与 deleteTag 语义。
 *
 * JVM 单测无法驱动 Room withTransaction，此处以内存 DAO 忠实复刻
 * 三条 SQL 语义后验证业务行为：
 * 1. 纯重命名：from 的关联全部改指 to，条数不变；
 * 2. 重命名到已有标签（合并）：同选项「from + to 并存」去重，总数 = 并集；
 * 3. 删除：该标签关联清空，其他标签不受影响；
 * 4. 使用计数：GROUP BY 统计正确。
 */
class TagManagementTest {

    // ── 内存 DAO（忠实模拟 SQL 语义）──

    private class FakeSavedOptionTagDao(
        initial: List<SavedOptionTagEntity> = emptyList()
    ) : SavedOptionTagDao {
        private val state = MutableStateFlow(initial)

        fun current(): List<SavedOptionTagEntity> = state.value

        override fun observeAll(): Flow<List<SavedOptionTagEntity>> = state

        override fun observeByOption(optionId: String): Flow<List<SavedOptionTagEntity>> =
            MutableStateFlow(current().filter { it.savedOptionId == optionId })

        override suspend fun getByOption(optionId: String): List<SavedOptionTagEntity> =
            current().filter { it.savedOptionId == optionId }

        override fun observeTagUsage(): Flow<List<TagUsageProjection>> =
            MutableStateFlow(
                current().groupBy { it.tagId }
                    .map { (tagId, rows) -> TagUsageProjection(tagId, rows.size) }
                    .sortedWith(compareByDescending<TagUsageProjection> { it.usageCount }.thenBy { it.tagId })
            )

        override suspend fun insertAll(entities: List<SavedOptionTagEntity>) {
            // IGNORE 语义：联合主键已存在则跳过
            val existing = current().map { it.savedOptionId to it.tagId }.toSet()
            val added = entities.filter { (it.savedOptionId to it.tagId) !in existing }
            state.value = current() + added
        }

        override suspend fun deleteByOption(optionId: String) {
            state.value = current().filterNot { it.savedOptionId == optionId }
        }

        override suspend fun deleteMergeConflicts(from: String, to: String) {
            // DELETE ... WHERE tagId = :to AND savedOptionId IN (SELECT ... WHERE tagId = :from)
            val fromOptions = current().filter { it.tagId == from }.map { it.savedOptionId }.toSet()
            state.value = current().filterNot { it.tagId == to && it.savedOptionId in fromOptions }
        }

        override suspend fun renameTagRefs(from: String, to: String) {
            state.value = current().map {
                if (it.tagId == from) it.copy(tagId = to) else it
            }
        }

        override suspend fun deleteByTag(tagId: String) {
            state.value = current().filterNot { it.tagId == tagId }
        }

        // ── 备份/恢复（v0.9.3）──

        override suspend fun getAll(): List<SavedOptionTagEntity> = current()

        override suspend fun deleteAll() {
            state.value = emptyList()
        }
    }

    /** 复刻 SavedOptionRepositoryImpl.renameTag（trim 校验 + 两步组合） */
    private fun renameTag(dao: FakeSavedOptionTagDao, from: String, to: String) {
        val target = to.trim()
        if (target.isEmpty() || target == from) return
        runBlocking {
            dao.deleteMergeConflicts(from, target)
            dao.renameTagRefs(from, target)
        }
    }

    private fun tagsOf(dao: FakeSavedOptionTagDao, optionId: String): Set<String> =
        runBlocking { dao.getByOption(optionId).map { it.tagId }.toSet() }

    // ── 1. 纯重命名 ──

    @Test
    fun `pure rename keeps all associations`() {
        val dao = FakeSavedOptionTagDao(
            listOf(
                SavedOptionTagEntity("shop-1", "火锅店"),
                SavedOptionTagEntity("shop-2", "火锅店"),
                SavedOptionTagEntity("shop-3", "烧烤")
            )
        )

        renameTag(dao, "火锅店", "火锅")

        assertEquals(setOf("火锅"), tagsOf(dao, "shop-1"))
        assertEquals(setOf("火锅"), tagsOf(dao, "shop-2"))
        assertEquals(setOf("烧烤"), tagsOf(dao, "shop-3"))
        assertEquals(3, dao.current().size) // 条数不变
    }

    // ── 2. 重命名到已有标签 = 合并（同选项去重） ──

    @Test
    fun `rename to existing tag merges without duplicates`() {
        val dao = FakeSavedOptionTagDao(
            listOf(
                // shop-1 同时挂「火锅店」和「火锅」→ 合并后只剩一条「火锅」
                SavedOptionTagEntity("shop-1", "火锅店"),
                SavedOptionTagEntity("shop-1", "火锅"),
                // shop-2 只挂「火锅店」
                SavedOptionTagEntity("shop-2", "火锅店"),
                // shop-3 只挂「火锅」
                SavedOptionTagEntity("shop-3", "火锅"),
                // 无关标签
                SavedOptionTagEntity("shop-3", "烧烤")
            )
        )

        renameTag(dao, "火锅店", "火锅")

        assertEquals(setOf("火锅"), tagsOf(dao, "shop-1")) // 不重复
        assertEquals(setOf("火锅"), tagsOf(dao, "shop-2"))
        assertEquals(setOf("火锅", "烧烤"), tagsOf(dao, "shop-3"))
        // 总数 = 并集：5 行 - 1 冲突 = 4
        assertEquals(4, dao.current().size)
        assertFalse(dao.current().any { it.tagId == "火锅店" })
    }

    // ── 3. 删除标签 ──

    @Test
    fun `delete tag removes only its associations`() {
        val dao = FakeSavedOptionTagDao(
            listOf(
                SavedOptionTagEntity("shop-1", "火锅"),
                SavedOptionTagEntity("shop-2", "火锅"),
                SavedOptionTagEntity("shop-1", "烧烤"),
                SavedOptionTagEntity("shop-3", "烧烤")
            )
        )

        runBlocking { dao.deleteByTag("火锅") }

        assertTrue(dao.current().none { it.tagId == "火锅" })
        assertEquals(setOf("烧烤"), tagsOf(dao, "shop-1"))
        assertEquals(setOf("烧烤"), tagsOf(dao, "shop-3"))
    }

    // ── 4. 使用计数（GROUP BY 复刻） ──

    @Test
    fun `tag usage counts per option`() {
        val dao = FakeSavedOptionTagDao(
            listOf(
                SavedOptionTagEntity("shop-1", "火锅"),
                SavedOptionTagEntity("shop-2", "火锅"),
                SavedOptionTagEntity("shop-3", "火锅"),
                SavedOptionTagEntity("shop-1", "烧烤")
            )
        )

        val usage = runBlocking { dao.observeTagUsage().first() }

        // 火锅 3 家 > 烧烤 1 家；同计数按名称升序
        assertEquals(2, usage.size)
        assertEquals("火锅", usage[0].tagId)
        assertEquals(3, usage[0].usageCount)
        assertEquals("烧烤", usage[1].tagId)
        assertEquals(1, usage[1].usageCount)
    }

    // ── 5. trim / 同名空操作（repository 语义） ──

    @Test
    fun `rename to blank or same name is no-op`() {
        val dao = FakeSavedOptionTagDao(
            listOf(SavedOptionTagEntity("shop-1", "火锅"))
        )

        renameTag(dao, "火锅", "  ")
        renameTag(dao, "火锅", "火锅")

        assertEquals(setOf("火锅"), tagsOf(dao, "shop-1"))
        assertEquals(1, dao.current().size)
    }
}
