package com.what2eat.domain.history

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
        isPool: Boolean = false
    ): Boolean = HistoryFilter.matches(state, completedAt, participants, isPool, nowMillis)

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
}
