package com.what2eat.domain.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * HistoryStatsCalculator / HistoryDateGrouper 单元测试。
 * 固定 Asia/Shanghai 时区保证确定性。
 */
class HistoryStatsTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    // ── compute：总次数 ──

    // 1. 总次数 = 条目数
    @Test
    fun total_isEntryCount() {
        val now = millisOf(2026, 9, 2)
        val entries = listOf(
            HistoryStatEntry("火锅", millisOf(2026, 8, 20), 0),
            HistoryStatEntry("粤菜", millisOf(2026, 9, 1), 1),
            HistoryStatEntry("火锅", millisOf(2026, 9, 2), 2)
        )
        val stats = HistoryStatsCalculator.compute(entries, now, zone)
        assertEquals(3, stats.totalDecisions)
    }

    // 2. 空列表 → 全零不崩溃
    @Test
    fun emptyEntries_allZero() {
        val now = millisOf(2026, 9, 2)
        val stats = HistoryStatsCalculator.compute(emptyList(), now, zone)
        assertEquals(0, stats.totalDecisions)
        assertEquals(0, stats.thisMonthDecisions)
        assertEquals(0.0, stats.averageRerollCount, 0.0001)
        assertTrue(stats.topFoods.isEmpty())
    }

    // ── compute：本月 ──

    // 3. 同月计入、上月不计
    @Test
    fun thisMonth_countsSameMonthOnly() {
        val now = millisOf(2026, 9, 15)
        val entries = listOf(
            HistoryStatEntry("a", millisOf(2026, 9, 1), 0),   // 本月
            HistoryStatEntry("b", millisOf(2026, 9, 30, 23, 59), 0), // 本月（月末深夜）
            HistoryStatEntry("c", millisOf(2026, 8, 31, 23, 59), 0), // 上月最后一天（本地时区）
            HistoryStatEntry("d", millisOf(2025, 9, 15), 0)   // 去年同月
        )
        val stats = HistoryStatsCalculator.compute(entries, now, zone)
        assertEquals(2, stats.thisMonthDecisions)
    }

    // 4. 跨年边界：now=1月，12月条目不计、1月计入
    @Test
    fun thisMonth_yearBoundary() {
        val now = millisOf(2026, 1, 15)
        val entries = listOf(
            HistoryStatEntry("a", millisOf(2025, 12, 31, 23, 59), 0),
            HistoryStatEntry("b", millisOf(2026, 1, 1, 0, 1), 0),
            HistoryStatEntry("c", millisOf(2026, 1, 31, 23, 59), 0)
        )
        val stats = HistoryStatsCalculator.compute(entries, now, zone)
        assertEquals(2, stats.thisMonthDecisions)
    }

    // ── compute：平均换一个 ──

    // 5. 平均值正确
    @Test
    fun averageReroll_correct() {
        val now = millisOf(2026, 9, 2)
        val entries = listOf(
            HistoryStatEntry("a", millisOf(2026, 9, 1), 0),
            HistoryStatEntry("b", millisOf(2026, 9, 1), 1),
            HistoryStatEntry("c", millisOf(2026, 9, 1), 2)
        )
        val stats = HistoryStatsCalculator.compute(entries, now, zone)
        assertEquals(1.0, stats.averageRerollCount, 0.0001)
    }

    // 6. 舍入到 1 位小数
    @Test
    fun averageReroll_roundedToOneDecimal() {
        val now = millisOf(2026, 9, 2)
        val entries = listOf(
            HistoryStatEntry("a", millisOf(2026, 9, 1), 0),
            HistoryStatEntry("b", millisOf(2026, 9, 1), 1),
            HistoryStatEntry("c", millisOf(2026, 9, 1), 1)
        )
        val stats = HistoryStatsCalculator.compute(entries, now, zone)
        // 2/3 = 0.666… → 0.7
        assertEquals(0.7, stats.averageRerollCount, 0.0001)
    }

    // ── compute：最常吃 Top ──

    // 7. 计数降序；同数按名称字典序
    @Test
    fun topFoods_sortedByCountDescThenName() {
        val now = millisOf(2026, 9, 2)
        val entries = listOf(
            HistoryStatEntry("火锅", millisOf(2026, 9, 1), 0),
            HistoryStatEntry("火锅", millisOf(2026, 9, 1), 0),
            HistoryStatEntry("火锅", millisOf(2026, 9, 1), 0),
            HistoryStatEntry("粤菜", millisOf(2026, 9, 1), 0),
            HistoryStatEntry("粤菜", millisOf(2026, 9, 1), 0),
            HistoryStatEntry("川菜", millisOf(2026, 9, 1), 0),
            HistoryStatEntry("川菜", millisOf(2026, 9, 1), 0)
        )
        val stats = HistoryStatsCalculator.compute(entries, now, zone)
        // 火锅 3 次；粤菜、川菜各 2 次 → 同数按名称代码点升序（川 U+5DDD < 粤 U+7CA4）
        assertEquals("火锅", stats.topFoods[0].name)
        assertEquals(3, stats.topFoods[0].count)
        assertEquals(2, stats.topFoods[1].count)
        assertEquals(2, stats.topFoods[2].count)
        assertTrue(stats.topFoods[1].name < stats.topFoods[2].name)
    }

    // 8. 超过 5 条截断
    @Test
    fun topFoods_limitedToFive() {
        val now = millisOf(2026, 9, 2)
        val entries = (1..8).map { i ->
            HistoryStatEntry("店$i", millisOf(2026, 9, 1), 0)
        }
        val stats = HistoryStatsCalculator.compute(entries, now, zone)
        assertEquals(5, stats.topFoods.size)
    }

    // ── groupByDay ──

    // 9. 同本地日合并（00:30 与 23:59 同组）
    @Test
    fun groupByDay_sameLocalDayMerged() {
        val morning = millisOf(2026, 9, 1, 0, 30)
        val night = millisOf(2026, 9, 1, 23, 59)
        val groups = HistoryDateGrouper.groupByDay(
            listOf("morning" to morning, "night" to night),
            { it.second },
            zone
        )
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].items.size)
    }

    // 10. 时区边界：UTC+8 下 15:59Z 与 16:00Z 分属两日
    @Test
    fun groupByDay_zoneBoundary() {
        // Asia/Shanghai = UTC+8：15:59Z = 本地 23:59（9月1日）；16:00Z = 本地 0:00（9月2日）
        val lateNightOfSep1 = ZonedDateTime.of(2026, 9, 1, 15, 59, 0, 0, ZoneId.of("UTC"))
            .toInstant().toEpochMilli()
        val earlyMorningOfSep2 = ZonedDateTime.of(2026, 9, 1, 16, 0, 0, 0, ZoneId.of("UTC"))
            .toInstant().toEpochMilli()
        val groups = HistoryDateGrouper.groupByDay(
            listOf("a" to lateNightOfSep1, "b" to earlyMorningOfSep2),
            { it.second },
            zone
        )
        assertEquals(2, groups.size)
        // 时间降序：9月2日组在前
        assertEquals("b", groups[0].items.single().first)
        assertEquals("a", groups[1].items.single().first)
    }

    // 11. 输出按时间降序（多天）
    @Test
    fun groupByDay_orderedDesc() {
        val day1 = millisOf(2026, 9, 1, 10, 0)
        val day2 = millisOf(2026, 9, 2, 10, 0)
        val day3 = millisOf(2026, 9, 3, 10, 0)
        val groups = HistoryDateGrouper.groupByDay(
            listOf("d1" to day1, "d3" to day3, "d2" to day2),
            { it.second },
            zone
        )
        assertEquals(3, groups.size)
        assertEquals(listOf("d3", "d2", "d1"), groups.map { it.items.single().first })
        // epochDay 同步降序
        assertEquals(
            listOf(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1))
                .map { it.toEpochDay() },
            groups.map { it.epochDay }
        )
    }

    // 12. 空列表 → 空结果
    @Test
    fun groupByDay_emptyList() {
        val groups = HistoryDateGrouper.groupByDay(
            emptyList<Pair<String, Long>>(),
            { it.second },
            zone
        )
        assertTrue(groups.isEmpty())
    }
}
