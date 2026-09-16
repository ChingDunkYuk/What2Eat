package com.what2eat.data.repository

import com.what2eat.core.database.dao.TagDao
import com.what2eat.core.database.entity.TagEntity
import com.what2eat.domain.model.TagPalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.6.0 标签颜色元数据回归：复刻 SavedOptionRepositoryImpl 的
 * setTagColor（null/默认色删行，非默认 upsert）与 renameTag/deleteTag 的
 * 元数据联动语义（合并 to 色胜出 / 纯重命名颜色跟随 / 删除连带清行）。
 *
 * 沿用 TagManagementTest 惯例：JVM 单测无法驱动 Room withTransaction，
 * 以内存 Fake TagDao 忠实复刻 SQL 语义后验证业务行为。
 */
class TagColorMetadataTest {

    // ── 内存 DAO（忠实模拟 TagDao SQL 语义）──

    private class FakeTagDao(
        initial: List<TagEntity> = emptyList()
    ) : TagDao {
        private val state = MutableStateFlow(initial)

        fun current(): List<TagEntity> = state.value

        override fun observeAll(): Flow<List<TagEntity>> = state

        override suspend fun getByName(name: String): TagEntity? =
            current().firstOrNull { it.name == name }

        override suspend fun upsert(entity: TagEntity) {
            // REPLACE 语义：主键存在则整行替换
            state.value = current().filterNot { it.name == entity.name } + entity
        }

        override suspend fun deleteByName(name: String) {
            state.value = current().filterNot { it.name == name }
        }

        override suspend fun renameIfTargetAbsent(from: String, to: String) {
            state.value = current().map {
                if (it.name == from) it.copy(name = to) else it
            }
        }

        override suspend fun getAll(): List<TagEntity> = current()

        override suspend fun insertAll(entities: List<TagEntity>) {
            entities.forEach { upsert(it) }
        }

        override suspend fun deleteAll() {
            state.value = emptyList()
        }
    }

    // ── 复刻 SavedOptionRepositoryImpl 语义 ──

    /** 复刻 setTagColor：null / 默认色 → 删行回落默认；非默认 → upsert */
    private fun setTagColor(dao: FakeTagDao, name: String, colorArgb: Int?) {
        runBlocking {
            if (colorArgb == null || colorArgb == TagPalette.DEFAULT) {
                dao.deleteByName(name)
            } else {
                dao.upsert(TagEntity(name = name, colorArgb = colorArgb, createdAt = NOW))
            }
        }
    }

    /** 复刻 renameTag 的元数据联动：合并（to 有行）→ 删 from；纯重命名 → 改指 */
    private fun renameMetadata(dao: FakeTagDao, from: String, to: String) {
        runBlocking {
            if (dao.getByName(to) != null) {
                dao.deleteByName(from)
            } else {
                dao.renameIfTargetAbsent(from, to)
            }
        }
    }

    /** 复刻 observeTagColors + 调用方回落：无行 = 默认色 */
    private fun colorOf(dao: FakeTagDao, name: String): Int =
        runBlocking { dao.getByName(name)?.colorArgb ?: TagPalette.DEFAULT }

    // ── 1. setTagColor 建行与改色 ──

    @Test
    fun `setTagColor upserts non-default color`() {
        val dao = FakeTagDao()

        setTagColor(dao, "火锅", TagPalette.ORANGE)
        assertEquals(TagPalette.ORANGE, colorOf(dao, "火锅"))
        assertEquals(NOW, dao.current().single().createdAt)

        // 改色：REPLACE 语义整行替换，行数不变
        setTagColor(dao, "火锅", TagPalette.MATCHA)
        assertEquals(TagPalette.MATCHA, colorOf(dao, "火锅"))
        assertEquals(1, dao.current().size)
    }

    // ── 2. 置默认/置 null → 删行回落默认 ──

    @Test
    fun `setTagColor null or default deletes row`() {
        val dao = FakeTagDao(
            listOf(TagEntity(name = "火锅", colorArgb = TagPalette.ORANGE, createdAt = 1L))
        )

        setTagColor(dao, "火锅", TagPalette.DEFAULT)
        assertTrue(dao.current().isEmpty())
        assertEquals(TagPalette.DEFAULT, colorOf(dao, "火锅"))

        setTagColor(dao, "烧烤", TagPalette.TEAL)
        setTagColor(dao, "烧烤", null)
        assertTrue(dao.current().isEmpty())
        assertEquals(TagPalette.DEFAULT, colorOf(dao, "烧烤"))
    }

    // ── 3. 纯重命名：颜色跟随 ──

    @Test
    fun `pure rename carries color to new name`() {
        val dao = FakeTagDao(
            listOf(TagEntity(name = "火锅店", colorArgb = TagPalette.AZURE, createdAt = 1L))
        )

        renameMetadata(dao, "火锅店", "火锅")

        assertNull(dao.current().firstOrNull { it.name == "火锅店" })
        assertEquals(TagPalette.AZURE, colorOf(dao, "火锅"))
        assertEquals(1, dao.current().size)
    }

    // ── 4. 合并（to 已有行）：to 色胜出 ──

    @Test
    fun `merge keeps target color and drops source row`() {
        val dao = FakeTagDao(
            listOf(
                TagEntity(name = "火锅店", colorArgb = TagPalette.AZURE, createdAt = 1L),
                TagEntity(name = "火锅", colorArgb = TagPalette.BRICK, createdAt = 2L)
            )
        )

        renameMetadata(dao, "火锅店", "火锅")

        assertEquals(1, dao.current().size)
        assertEquals(TagPalette.BRICK, colorOf(dao, "火锅")) // to 色胜出
    }

    @Test
    fun `merge into tag without metadata row keeps source color`() {
        // to 无元数据行 = 纯重命名路径（to 标签存在但从未设色）→ from 颜色跟随
        val dao = FakeTagDao(
            listOf(TagEntity(name = "火锅店", colorArgb = TagPalette.TARO, createdAt = 1L))
        )

        renameMetadata(dao, "火锅店", "火锅")

        assertEquals(TagPalette.TARO, colorOf(dao, "火锅"))
    }

    // ── 5. deleteTag 连带删行 ──

    @Test
    fun `deleteTag removes metadata row`() {
        val dao = FakeTagDao(
            listOf(
                TagEntity(name = "火锅", colorArgb = TagPalette.ORANGE, createdAt = 1L),
                TagEntity(name = "烧烤", colorArgb = TagPalette.MATCHA, createdAt = 2L)
            )
        )

        runBlocking { dao.deleteByName("火锅") }

        assertTrue(dao.current().none { it.name == "火锅" })
        assertEquals(TagPalette.DEFAULT, colorOf(dao, "火锅")) // 回落默认
        assertEquals(TagPalette.MATCHA, colorOf(dao, "烧烤")) // 其他标签不受影响
    }

    // ── 6. 无行回落默认色 ──

    @Test
    fun `missing row falls back to default color`() {
        val dao = FakeTagDao()

        assertEquals(TagPalette.DEFAULT, colorOf(dao, "从不存在的标签"))
    }

    // ── 7. 色板约束 ──

    @Test
    fun `palette presets are unique and exclude default`() {
        assertEquals(8, TagPalette.presets.size)
        assertEquals(TagPalette.presets.size, TagPalette.presets.distinct().size)
        assertFalse(TagPalette.DEFAULT in TagPalette.presets)
    }

    private companion object {
        const val NOW = 1726000000000L
    }
}
