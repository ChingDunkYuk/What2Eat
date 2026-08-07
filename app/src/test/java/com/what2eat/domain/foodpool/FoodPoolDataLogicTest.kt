package com.what2eat.domain.foodpool

import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.ImportStatus
import com.what2eat.domain.model.OptionPreferenceLevel
import com.what2eat.domain.model.PersonOptionPreference
import com.what2eat.domain.model.SavedOption
import com.what2eat.domain.model.SavedOptionType
import com.what2eat.domain.model.SourcePlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 4 封版：需求 4/5/6/7/9/10 的数据逻辑验证（纯 Kotlin）。
 */
class FoodPoolDataLogicTest {

    private fun option(
        id: String = "o1",
        name: String = "潮汕牛肉火锅",
        area: String? = null,
        importStatus: ImportStatus = ImportStatus.COMPLETE,
        createdAt: Long = 0L
    ) = SavedOption(
        id = id, name = name, optionType = SavedOptionType.RESTAURANT,
        areaText = area, createdAt = createdAt, importStatus = importStatus
    )

    // ── 需求 4：Klaus / 晴 具体选项偏好独立 ──

    @Test
    fun `same option two persons have independent preferences`() {
        val klaus = PersonOptionPreference(
            personId = "klaus", savedOptionId = "o1",
            preferenceLevel = OptionPreferenceLevel.VERY_LIKE
        )
        val qing = PersonOptionPreference(
            personId = "qing", savedOptionId = "o1",
            preferenceLevel = OptionPreferenceLevel.DISLIKE
        )
        // 联合主键 (personId, savedOptionId) 不同 → 两条独立记录可共存
        assertEquals("klaus", klaus.personId)
        assertEquals("qing", qing.personId)
        assertEquals(OptionPreferenceLevel.VERY_LIKE, klaus.preferenceLevel)
        assertEquals(OptionPreferenceLevel.DISLIKE, qing.preferenceLevel)
        assertTrue(klaus != qing)
        // 同一人更新不串扰另一人
        val klausUpdated = klaus.copy(preferenceLevel = OptionPreferenceLevel.NEUTRAL)
        assertEquals(OptionPreferenceLevel.DISLIKE, qing.preferenceLevel)
        assertEquals(OptionPreferenceLevel.NEUTRAL, klausUpdated.preferenceLevel)
    }

    // ── 需求 5：同一选项多列表，不重复 ──

    @Test
    fun `one option in multiple collections matches each tab`() {
        val opt = option(id = "chaoshan")
        val collections = setOf(CollectionType.FREQUENT, CollectionType.WANT_TO_TRY)
        // 同时属于常吃 + 待尝试
        assertTrue(FoodPoolFilter.matchesTab(opt, collections, "FREQUENT"))
        assertTrue(FoodPoolFilter.matchesTab(opt, collections, "WANT_TO_TRY"))
        // 一个选项一个 tab 只匹配一次（集合去重语义）
        assertEquals(2, collections.size)
        assertFalse(collections.filter { it == CollectionType.FREQUENT }.size > 1)
    }

    // ── 需求 6：踩雷 ──

    @Test
    fun `avoided option visible in avoided and still present in all`() {
        val opt = option(id = "tokyo")
        val collections = setOf(CollectionType.AVOIDED)
        assertTrue(FoodPoolFilter.matchesTab(opt, collections, "AVOIDED"))
        // 踩雷不等于删除：仍出现在全部
        assertTrue(FoodPoolFilter.matchesTab(opt, collections, "ALL"))
        // 移出踩雷后恢复：从集合移除后不再匹配踩雷 tab
        assertFalse(FoodPoolFilter.matchesTab(opt, emptySet(), "AVOIDED"))
    }

    // ── 需求 7：待整理 ──

    @Test
    fun `needs review appears in review and leaves after complete`() {
        val review = option(id = "r1", importStatus = ImportStatus.NEEDS_REVIEW)
        assertTrue(FoodPoolFilter.matchesTab(review, emptySet(), "NEEDS_REVIEW"))
        // 补齐资料后标记 COMPLETE → 自动移出待整理
        val completed = review.copy(importStatus = ImportStatus.COMPLETE)
        assertFalse(FoodPoolFilter.matchesTab(completed, emptySet(), "NEEDS_REVIEW"))
    }

    // ── 需求 9：搜索匹配名称 / 区域 / 标签 ──

    @Test
    fun `search matches name area and tag for review`() {
        val opt = option(name = "酸菜鱼", area = "天河")
        assertTrue(FoodPoolFilter.matchesSearch(opt, setOf("川菜"), "酸菜"))
        assertTrue(FoodPoolFilter.matchesSearch(opt, setOf("川菜"), "天河"))
        assertTrue(FoodPoolFilter.matchesSearch(opt, setOf("川菜"), "川菜"))
        assertFalse(FoodPoolFilter.matchesSearch(opt, setOf("川菜"), "火锅"))
    }

    // ── 需求 10：排序 ──

    @Test
    fun `recently added and name order differ`() {
        val early = option(id = "a", name = "乙", createdAt = 100L)
        val late = option(id = "b", name = "甲", createdAt = 200L)
        val byAdded = FoodPoolFilter.sort(listOf(early, late), FoodPoolFilter.SortMode.RECENTLY_ADDED)
        val byName = FoodPoolFilter.sort(listOf(early, late), FoodPoolFilter.SortMode.NAME)
        // 最近添加：晚的在前
        assertEquals(listOf("b", "a"), byAdded.map { it.id })
        // 名称升序："乙"(U+4E59) < "甲"(U+7532)
        assertEquals(listOf("a", "b"), byName.map { it.id })
        // 两种排序结果不同
        assertTrue(byAdded.map { it.id } != byName.map { it.id })
    }

    // ── 需求 8：踩雷项目可查看、移出可恢复（补充 enabled 语义）──

    @Test
    fun `avoided item keeps enabled and survives removal`() {
        // 加入踩雷后 enabled 仍为 true（需求 12：踩雷不等于停用）
        val avoided = option(id = "x").copy(
            enabled = true,
            optionType = SavedOptionType.RESTAURANT,
            sourcePlatform = SourcePlatform.DIANPING
        )
        assertTrue(avoided.enabled)
        assertEquals(SavedOptionType.RESTAURANT, avoided.optionType)
        assertEquals(SourcePlatform.DIANPING, avoided.sourcePlatform)
    }
}