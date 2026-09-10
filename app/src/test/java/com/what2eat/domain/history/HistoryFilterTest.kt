package com.what2eat.domain.history

import com.what2eat.domain.model.CollectionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * v1.3.0 历史筛选回归：时间/人物/模式各维度 + 组合 + 边界。
 */
class HistoryFilterTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now(zone)
    private val nowMillis: Long = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(date: LocalDate, hour: Int = 12, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun matches(
        state: HistoryFilterState,
        completedAt: Long = nowMillis,
        participants: List<String> = listOf("klaus", "晴"),
        isPool: Boolean = false,
        collections: Set<CollectionType> = emptySet(),
        tags: Set<String> = emptySet()
    ): Boolean = HistoryFilter.matches(
        state, completedAt, participants, isPool, nowMillis, collections, tags
    )

    @Test
    fun `默认态全部通过`() {
        assertTrue(matches(HistoryFilterState()))
        assertTrue(matches(HistoryFilterState(), completedAt = 0L))
    }

    @Test
    fun `近7天含恰好7天前的边界`() {
        val state = HistoryFilterState(time = HistoryTimeFilter.LAST_7_DAYS)
        val boundary = nowMillis - 7L * 24 * 60 * 60 * 1000
        assertTrue(matches(state, completedAt = boundary))
        assertTrue(matches(state, completedAt = nowMillis))
        assertFalse(matches(state, completedAt = boundary - 1))
    }

    @Test
    fun `本月按自然月切分`() {
        val state = HistoryFilterState(time = HistoryTimeFilter.THIS_MONTH)
        val monthStart = today.withDayOfMonth(1)
        assertTrue(matches(state, completedAt = at(monthStart, hour = 0)))
        val prevMonthEnd = today.withDayOfMonth(1).minusDays(1)
        assertFalse(matches(state, completedAt = at(prevMonthEnd, hour = 23)))
    }

    @Test
    fun `人物筛选按姓名匹配`() {
        val state = HistoryFilterState(personName = "晴")
        assertTrue(matches(state, participants = listOf("klaus", "晴")))
        assertFalse(matches(state, participants = listOf("klaus")))
        assertTrue(matches(HistoryFilterState(personName = null), participants = listOf("klaus")))
    }

    @Test
    fun `模式筛选`() {
        assertTrue(matches(HistoryFilterState(mode = HistoryModeFilter.POOL), isPool = true))
        assertFalse(matches(HistoryFilterState(mode = HistoryModeFilter.POOL), isPool = false))
        assertTrue(matches(HistoryFilterState(mode = HistoryModeFilter.CATEGORY), isPool = false))
        assertFalse(matches(HistoryFilterState(mode = HistoryModeFilter.CATEGORY), isPool = true))
        assertTrue(matches(HistoryFilterState(mode = HistoryModeFilter.ALL), isPool = true))
    }

    @Test
    fun `组合筛选全部维度同时满足才通过`() {
        val state = HistoryFilterState(
            time = HistoryTimeFilter.LAST_7_DAYS,
            personName = "晴",
            mode = HistoryModeFilter.POOL
        )
        assertTrue(matches(state, participants = listOf("晴"), isPool = true))
        // 人物不符
        assertFalse(matches(state, participants = listOf("klaus"), isPool = true))
        // 模式不符
        assertFalse(matches(state, participants = listOf("晴"), isPool = false))
        // 时间不符（8 天前）
        assertFalse(
            matches(
                state,
                completedAt = nowMillis - 8L * 24 * 60 * 60 * 1000,
                participants = listOf("晴"),
                isPool = true
            )
        )
    }

    // ── v1.4.0：列表/标签维度 ──

    @Test
    fun `列表维度 null 通过 命中通过 未命中拒绝`() {
        val withCollection = setOf(CollectionType.FREQUENT, CollectionType.WANT_TO_TRY)
        assertTrue(matches(HistoryFilterState(), collections = withCollection))
        assertTrue(
            matches(
                HistoryFilterState(collectionType = CollectionType.FREQUENT),
                collections = withCollection
            )
        )
        assertFalse(
            matches(
                HistoryFilterState(collectionType = CollectionType.AVOIDED),
                collections = withCollection
            )
        )
    }

    @Test
    fun `列表维度下分类决策（空集）不匹配`() {
        assertFalse(
            matches(
                HistoryFilterState(collectionType = CollectionType.FREQUENT),
                isPool = false,
                collections = emptySet()
            )
        )
    }

    @Test
    fun `标签维度 null 通过 命中通过 未命中拒绝`() {
        val tags = setOf("火锅", "潮汕")
        assertTrue(matches(HistoryFilterState(), tags = tags))
        assertTrue(matches(HistoryFilterState(tagName = "火锅"), tags = tags))
        assertFalse(matches(HistoryFilterState(tagName = "日料"), tags = tags))
        // 空集（分类决策）不匹配
        assertFalse(matches(HistoryFilterState(tagName = "火锅"), tags = emptySet()))
    }

    @Test
    fun `列表与标签组合需同时命中`() {
        val state = HistoryFilterState(
            collectionType = CollectionType.FREQUENT,
            tagName = "火锅"
        )
        assertTrue(
            matches(
                state,
                collections = setOf(CollectionType.FREQUENT),
                tags = setOf("火锅")
            )
        )
        // 只命中列表
        assertFalse(
            matches(
                state,
                collections = setOf(CollectionType.FREQUENT),
                tags = setOf("日料")
            )
        )
        // 只命中标签
        assertFalse(
            matches(
                state,
                collections = setOf(CollectionType.AVOIDED),
                tags = setOf("火锅")
            )
        )
    }
}
