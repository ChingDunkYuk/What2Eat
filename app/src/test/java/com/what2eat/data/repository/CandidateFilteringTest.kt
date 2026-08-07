package com.what2eat.data.repository

import com.what2eat.domain.model.CandidateCategory
import com.what2eat.domain.model.FoodCategory
import com.what2eat.domain.model.PersonCategoryPreference
import com.what2eat.domain.model.SelectionType
import com.what2eat.domain.model.SessionCategorySelection
import com.what2eat.domain.model.SessionParticipant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 候选分类过滤逻辑单元测试。
 *
 * 验证 generateCandidates() 的核心过滤规则：
 * 1. 分类已启用且为二级分类
 * 2. 不属于任一参与者长期硬排除
 * 3. 不属于任一参与者本次 NOT_TODAY
 * 4. 所有参与者至少标记为 ACCEPT 或 WANT
 * 5. 排序优先级正确（双方WANT=0, 一方WANT一方ACCEPT=1, 双方ACCEPT=2）
 */
class CandidateFilteringTest {

    private val sessionId = "test-session"
    private val person1 = "person-1"
    private val person2 = "person-2"

    // 测试分类数据：2个一级分类，每个下面2个二级分类
    private val categories = listOf(
        FoodCategory(id = "root-1", name = "中餐", parentId = null),
        FoodCategory(id = "root-2", name = "西餐", parentId = null),
        FoodCategory(id = "cat-1", name = "川菜", parentId = "root-1", enabled = true),
        FoodCategory(id = "cat-2", name = "粤菜", parentId = "root-1", enabled = true),
        FoodCategory(id = "cat-3", name = "意餐", parentId = "root-2", enabled = true),
        FoodCategory(id = "cat-4", name = "法餐", parentId = "root-2", enabled = false) // 已禁用
    )

    private val participants = listOf(
        SessionParticipant(sessionId, person1, 0),
        SessionParticipant(sessionId, person2, 1)
    )

    /**
     * 复现 generateCandidates() 的核心过滤逻辑，用于隔离测试。
     */
    private fun filterCandidates(
        categories: List<FoodCategory>,
        participants: List<SessionParticipant>,
        selections: List<SessionCategorySelection>,
        hardExcludedByPerson: Map<String, Set<String>>
    ): List<CandidateCategory> {
        val enabledCategories = categories.filter { it.enabled && it.parentId != null }
        val selectionByCategory = selections.groupBy { it.categoryId }
        val candidates = mutableListOf<CandidateCategory>()

        for (category in enabledCategories) {
            val categoryId = category.id

            // 规则2: 不在任何参与者的长期硬排除中
            val isHardExcluded = participants.any { p ->
                hardExcludedByPerson[p.personId]?.contains(categoryId) == true
            }
            if (isHardExcluded) continue

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

            val wantCount = selectionsForCategory.count { it.selectionType == SelectionType.WANT }
            val acceptCount = selectionsForCategory.count { it.selectionType == SelectionType.ACCEPT }
            val rank = when {
                wantCount == participants.size -> 0
                wantCount > 0 -> 1
                else -> 2
            }

            val parentName = categories.firstOrNull { it.id == category.parentId }?.name
            candidates.add(
                CandidateCategory(
                    categoryId = categoryId,
                    categoryName = category.name,
                    parentCategoryName = parentName,
                    wantCount = wantCount,
                    acceptCount = acceptCount,
                    rank = rank,
                    selectionsByPerson = selectionByPerson.mapValues { it.value.selectionType }
                )
            )
        }

        return candidates.sortedBy { it.rank }
    }

    private fun sel(personId: String, categoryId: String, type: SelectionType) =
        SessionCategorySelection(sessionId, personId, categoryId, type)

    // ── Test Cases ──

    @Test
    fun `both persons WANT same category - rank 0`() {
        val selections = listOf(
            sel(person1, "cat-1", SelectionType.WANT),
            sel(person2, "cat-1", SelectionType.WANT)
        )
        val result = filterCandidates(categories, participants, selections, emptyMap())

        assertEquals(1, result.size)
        assertEquals("cat-1", result[0].categoryId)
        assertEquals(0, result[0].rank)
        assertEquals(2, result[0].wantCount)
    }

    @Test
    fun `one WANT one ACCEPT - rank 1`() {
        val selections = listOf(
            sel(person1, "cat-1", SelectionType.WANT),
            sel(person2, "cat-1", SelectionType.ACCEPT)
        )
        val result = filterCandidates(categories, participants, selections, emptyMap())

        assertEquals(1, result.size)
        assertEquals(1, result[0].rank)
    }

    @Test
    fun `both ACCEPT - rank 2`() {
        val selections = listOf(
            sel(person1, "cat-2", SelectionType.ACCEPT),
            sel(person2, "cat-2", SelectionType.ACCEPT)
        )
        val result = filterCandidates(categories, participants, selections, emptyMap())

        assertEquals(1, result.size)
        assertEquals(2, result[0].rank)
    }

    @Test
    fun `NOT_TODAY from any person - excluded`() {
        val selections = listOf(
            sel(person1, "cat-1", SelectionType.WANT),
            sel(person2, "cat-1", SelectionType.NOT_TODAY)
        )
        val result = filterCandidates(categories, participants, selections, emptyMap())

        assertTrue("NOT_TODAY 分类应被排除", result.isEmpty())
    }

    @Test
    fun `hard excluded by any person - excluded`() {
        val selections = listOf(
            sel(person1, "cat-1", SelectionType.WANT),
            sel(person2, "cat-1", SelectionType.WANT)
        )
        val hardExcluded = mapOf(person1 to setOf("cat-1"))
        val result = filterCandidates(categories, participants, selections, hardExcluded)

        assertTrue("长期硬排除分类应被排除", result.isEmpty())
    }

    @Test
    fun `disabled category - excluded`() {
        val selections = listOf(
            sel(person1, "cat-4", SelectionType.WANT),
            sel(person2, "cat-4", SelectionType.WANT)
        )
        val result = filterCandidates(categories, participants, selections, emptyMap())

        assertTrue("已禁用分类应被排除", result.isEmpty())
    }

    @Test
    fun `missing selection from one person - excluded`() {
        // 只有 person1 有选择，person2 没有选择
        val selections = listOf(
            sel(person1, "cat-1", SelectionType.WANT)
        )
        val result = filterCandidates(categories, participants, selections, emptyMap())

        assertTrue("缺少参与者选择应被排除", result.isEmpty())
    }

    @Test
    fun `multiple candidates sorted by rank`() {
        val selections = listOf(
            // cat-1: 双方WANT (rank 0)
            sel(person1, "cat-1", SelectionType.WANT),
            sel(person2, "cat-1", SelectionType.WANT),
            // cat-2: 一方WANT一方ACCEPT (rank 1)
            sel(person1, "cat-2", SelectionType.WANT),
            sel(person2, "cat-2", SelectionType.ACCEPT),
            // cat-3: 双方ACCEPT (rank 2)
            sel(person1, "cat-3", SelectionType.ACCEPT),
            sel(person2, "cat-3", SelectionType.ACCEPT)
        )
        val result = filterCandidates(categories, participants, selections, emptyMap())

        assertEquals(3, result.size)
        assertEquals(0, result[0].rank) // cat-1
        assertEquals(1, result[1].rank) // cat-2
        assertEquals(2, result[2].rank) // cat-3
    }

    @Test
    fun `selectionsByPerson populated correctly`() {
        val selections = listOf(
            sel(person1, "cat-1", SelectionType.WANT),
            sel(person2, "cat-1", SelectionType.ACCEPT)
        )
        val result = filterCandidates(categories, participants, selections, emptyMap())

        assertEquals(1, result.size)
        assertEquals(SelectionType.WANT, result[0].selectionsByPerson[person1])
        assertEquals(SelectionType.ACCEPT, result[0].selectionsByPerson[person2])
    }

    @Test
    fun `parent category name populated`() {
        val selections = listOf(
            sel(person1, "cat-1", SelectionType.WANT),
            sel(person2, "cat-1", SelectionType.WANT)
        )
        val result = filterCandidates(categories, participants, selections, emptyMap())

        assertEquals(1, result.size)
        assertEquals("中餐", result[0].parentCategoryName)
    }
}
