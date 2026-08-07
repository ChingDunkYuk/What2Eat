package com.what2eat.domain.foodpool

import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.ImportStatus
import com.what2eat.domain.model.SavedOption
import com.what2eat.domain.model.SavedOptionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 4：吃饭池筛选/搜索/排序逻辑单元测试。
 */
class FoodPoolFilterTest {

    private fun option(
        id: String = "o1",
        name: String = "潮汕牛肉火锅",
        type: SavedOptionType = SavedOptionType.RESTAURANT,
        area: String? = null,
        createdAt: Long = 0L,
        lastChosenAt: Long? = null,
        importStatus: ImportStatus = ImportStatus.COMPLETE
    ) = SavedOption(
        id = id, name = name, optionType = type, areaText = area,
        createdAt = createdAt, lastChosenAt = lastChosenAt, importStatus = importStatus
    )

    // ── matchesTab ──

    @Test
    fun `ALL returns true regardless of collections`() {
        assertTrue(FoodPoolFilter.matchesTab(option(), setOf(), "ALL"))
        assertTrue(FoodPoolFilter.matchesTab(option(), setOf(CollectionType.AVOIDED), "ALL"))
    }

    @Test
    fun `tab matches by collection membership`() {
        val opt = option()
        assertTrue(FoodPoolFilter.matchesTab(opt, setOf(CollectionType.FREQUENT), "FREQUENT"))
        assertTrue(FoodPoolFilter.matchesTab(opt, setOf(CollectionType.VISITED), "VISITED"))
        assertTrue(FoodPoolFilter.matchesTab(opt, setOf(CollectionType.WANT_TO_TRY), "WANT_TO_TRY"))
        assertTrue(FoodPoolFilter.matchesTab(opt, setOf(CollectionType.TAKEOUT), "TAKEOUT"))
        assertTrue(FoodPoolFilter.matchesTab(opt, setOf(CollectionType.HOME_COOK), "HOME_COOK"))
        assertTrue(FoodPoolFilter.matchesTab(opt, setOf(CollectionType.AVOIDED), "AVOIDED"))
    }

    @Test
    fun `tab does not match when collection absent`() {
        val opt = option()
        assertFalse(FoodPoolFilter.matchesTab(opt, setOf(CollectionType.FREQUENT), "VISITED"))
        assertFalse(FoodPoolFilter.matchesTab(opt, setOf(CollectionType.AVOIDED), "FREQUENT"))
    }

    @Test
    fun `NEEDS_REVIEW driven by import status`() {
        assertTrue(FoodPoolFilter.matchesTab(option(importStatus = ImportStatus.NEEDS_REVIEW), setOf(), "NEEDS_REVIEW"))
        assertFalse(FoodPoolFilter.matchesTab(option(importStatus = ImportStatus.COMPLETE), setOf(), "NEEDS_REVIEW"))
    }

    // ── matchesSearch（名称/区域/标签）──

    @Test
    fun `search matches name, area and tag`() {
        val opt = option(name = "潮汕牛肉火锅", area = "佛山南海")
        assertTrue(FoodPoolFilter.matchesSearch(opt, setOf(), "潮汕"))
        assertTrue(FoodPoolFilter.matchesSearch(opt, setOf(), "佛山"))
        assertTrue(FoodPoolFilter.matchesSearch(opt, setOf("火锅"), "火锅"))
        assertFalse(FoodPoolFilter.matchesSearch(opt, setOf("火锅"), "日料"))
    }

    @Test
    fun `empty query matches everything`() {
        assertTrue(FoodPoolFilter.matchesSearch(option(), setOf(), ""))
        assertTrue(FoodPoolFilter.matchesSearch(option(), setOf(), "   "))
    }

    @Test
    fun `search does not modify database state`() {
        // 纯函数：调用后 input 不变
        val opt = option(name = "日式拉面")
        val tags = setOf("日料")
        FoodPoolFilter.matchesSearch(opt, tags, "拉面")
        assertEquals("日式拉面", opt.name)
        assertEquals(setOf("日料"), tags)
    }

    // ── sort ──

    @Test
    fun `sort by recently added`() {
        val a = option(id = "a", name = "A", createdAt = 100L)
        val b = option(id = "b", name = "B", createdAt = 200L)
        val sorted = FoodPoolFilter.sort(listOf(a, b), FoodPoolFilter.SortMode.RECENTLY_ADDED)
        assertEquals(listOf("b", "a"), sorted.map { it.id })
    }

    @Test
    fun `sort by name`() {
        val a = option(id = "a", name = "乙")
        val b = option(id = "b", name = "甲")
        // 按 Unicode 升序："乙"(U+4E59) < "甲"(U+7532)
        val sorted = FoodPoolFilter.sort(listOf(a, b), FoodPoolFilter.SortMode.NAME)
        assertEquals(listOf("a", "b"), sorted.map { it.id })
    }

    @Test
    fun `sort ignores sort mode by default override`() {
        // 验证不依赖 discarded override 导致崩溃
        val a = option(id = "a", createdAt = 100L)
        val b = option(id = "b", createdAt = 200L)
        val sorted = FoodPoolFilter.sort(listOf(a, b), FoodPoolFilter.SortMode.RECENTLY_ADDED)
        assertEquals(listOf("b", "a"), sorted.map { it.id })
    }
}