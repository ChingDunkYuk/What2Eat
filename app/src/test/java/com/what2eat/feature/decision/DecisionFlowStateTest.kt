package com.what2eat.feature.decision

import com.what2eat.domain.model.PersonProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 决策流程状态单元测试。
 *
 * 验证：
 * 1. 单人模式主用户识别（primaryProfile 来自 isPrimary=true，不依赖列表顺序）；
 * 2. 双人模式判断（isDualMode 由参与者数量决定）；
 * 3. 搜索为空状态与结果数量。
 */
class DecisionFlowStateTest {

    private val klaus = PersonProfile(id = "person_primary", name = "Klaus", isPrimary = true, sortOrder = 0)
    private val qing = PersonProfile(id = "person_secondary", name = "晴", isPrimary = false, sortOrder = 1)

    // 顺序颠倒时，primaryProfile 仍返回 Klaus
    @Test
    fun `primaryProfile returns Klaus regardless of list order`() {
        val state = DecisionUiState(
            isLoading = false,
            availableProfiles = listOf(qing, klaus),
            selectedParticipantIds = setOf("person_primary", "person_secondary")
        )
        assertEquals("Klaus", state.primaryProfile?.name)
        assertTrue(state.primaryProfile?.isPrimary == true)
    }

    // 单人模式下主用户为 Klaus
    @Test
    fun `single mode uses primary profile`() {
        val state = DecisionUiState(
            isLoading = false,
            availableProfiles = listOf(klaus)
        )
        assertEquals("Klaus", state.primaryProfile?.name)
        assertNull(state.currentSelectingPerson)
    }

    // 双人模式由参与者数量决定
    @Test
    fun `isDualMode reflects participant count`() {
        val single = DecisionUiState(
            isLoading = false,
            availableProfiles = listOf(klaus),
            selectedParticipantIds = setOf("person_primary")
        )
        assertFalse(single.isDualMode)

        val dual = DecisionUiState(
            isLoading = false,
            availableProfiles = listOf(klaus, qing),
            selectedParticipantIds = setOf("person_primary", "person_secondary")
        )
        assertTrue(dual.isDualMode)
    }

    // 搜索空状态与结果数量
    @Test
    fun `search empty state and result count`() {
        val cats = listOf(
            com.what2eat.domain.model.FoodCategory(id = "r", name = "中餐", parentId = null),
            com.what2eat.domain.model.FoodCategory(id = "c", name = "粤菜", parentId = "r", enabled = true)
        )
        val state = DecisionUiState(
            isLoading = false,
            allCategories = cats,
            searchQuery = "不存在的分类"
        )
        assertTrue(state.isSearchEmpty)
        assertEquals(0, state.searchResultCount)

        val found = DecisionUiState(
            isLoading = false,
            allCategories = cats,
            searchQuery = "粤菜"
        )
        assertFalse(found.isSearchEmpty)
        assertEquals(1, found.searchResultCount)
    }
}