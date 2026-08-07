package com.what2eat.feature.decision

import com.what2eat.domain.model.FoodCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分类搜索逻辑单元测试。
 *
 * 验证 DecisionUiState.filteredGroups 的搜索规则：
 * 1. 输入"粤菜"只显示粤菜（一级分类下只保留匹配子分类）
 * 2. 输入"火锅"匹配一级分类及其子分类
 * 3. 输入不存在关键词显示明确空状态
 * 4. 清空搜索恢复完整列表
 * 5. 去除首尾空格
 * 6. 搜索不区分大小写
 * 7. 搜索结果不修改数据库（纯内存过滤）
 */
class CategorySearchTest {

    private fun root(id: String, name: String) = FoodCategory(
        id = id, name = name, parentId = null, sortOrder = 0, enabled = true
    )

    private fun child(id: String, name: String, parentId: String) = FoodCategory(
        id = id, name = name, parentId = parentId, sortOrder = 0, enabled = true
    )

    private val allCategories = listOf(
        root("r-chinese", "中餐"),
        root("r-hotpot", "火锅"),
        root("r-western", "西餐"),
        child("c-guangdong", "粤菜", "r-chinese"),
        child("c-sichuan", "川菜", "r-chinese"),
        child("c-lamb", "涮羊肉", "r-hotpot"),
        child("c-soup", "番茄锅", "r-hotpot"),
        child("c-steak", "牛排", "r-western"),
        child("c-pizza", "披萨", "r-western")
    )

    private fun stateWithQuery(query: String): DecisionUiState {
        return DecisionUiState(
            isLoading = false,
            allCategories = allCategories,
            searchQuery = query
        )
    }

    private fun childNames(state: DecisionUiState): Set<String> {
        return state.filteredGroups.flatMap { it.children.map { c -> c.name } }.toSet()
    }

    @Test
    fun `search child name shows only that child`() {
        val state = stateWithQuery("粤菜")
        val names = childNames(state)
        assertEquals(setOf("粤菜"), names)
        // 一级分类中餐下只保留粤菜
        assertEquals(1, state.filteredGroups.size)
        assertEquals("中餐", state.filteredGroups[0].root.name)
    }

    @Test
    fun `search root name matches root and its children`() {
        val state = stateWithQuery("火锅")
        val names = childNames(state)
        // root 名称命中时保留其全部子分类
        assertEquals(setOf("涮羊肉", "番茄锅"), names)
        assertEquals(1, state.filteredGroups.size)
        assertEquals("火锅", state.filteredGroups[0].root.name)
    }

    @Test
    fun `non-existent keyword yields empty groups and empty state`() {
        val state = stateWithQuery("不存在的分类")
        assertTrue(state.filteredGroups.isEmpty())
        assertTrue(state.isSearchEmpty)
    }

    @Test
    fun `clearing search restores full list`() {
        val state = stateWithQuery("")
        val names = childNames(state)
        assertEquals(setOf("粤菜", "川菜", "涮羊肉", "番茄锅", "牛排", "披萨"), names)
        assertTrue(!state.isSearchEmpty)
    }

    @Test
    fun `leading and trailing whitespace trimmed`() {
        val state = stateWithQuery("  粤菜  ")
        val names = childNames(state)
        assertEquals(setOf("粤菜"), names)
    }

    @Test
    fun `search is case insensitive`() {
        // 英文场景（若分类含英文）
        val state = DecisionUiState(
            isLoading = false,
            allCategories = listOf(
                root("r-1", "Fast Food"),
                child("c-1", "Pizza", "r-1"),
                child("c-2", "Burger", "r-1")
            ),
            searchQuery = "pizza"
        )
        val names = childNames(state)
        assertEquals(setOf("Pizza"), names)
    }

    @Test
    fun `search does not mutate original categories`() {
        val before = allCategories.map { it.copy() }
        stateWithQuery("粤菜")
        stateWithQuery("火锅")
        stateWithQuery("")
        // 原列表未被修改（搜索为纯内存过滤）
        assertEquals(before, allCategories)
    }
}