package com.what2eat.domain.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * v1.3.0 月度吃饭报告回归：本月计数、Top5 排序、换一率、上月环比、空数据。
 */
class MonthlyReportCalculatorTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now(zone)
    private val nowMillis: Long = today.atTime(23, 0).atZone(zone).toInstant().toEpochMilli()

    private fun entry(name: String, date: LocalDate, reroll: Int = 0) = HistoryStatEntry(
        name = name,
        completedAt = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        rerollCount = reroll
    )

    @Test
    fun `空列表产出空报告`() {
        val report = MonthlyReportCalculator.compute(emptyList(), nowMillis)
        assertEquals(0, report.totalDecisions)
        assertTrue(report.topItems.isEmpty())
        assertEquals(0f, report.swapRate, 0.001f)
        assertNull(report.prevMonthTotal)
        assertEquals("${today.year}年${today.monthValue}月", report.monthLabel)
    }

    @Test
    fun `本月计数不含上月 月初边界算本月`() {
        val prevMonthDay = today.minusMonths(1)
        val entries = listOf(
            entry("火锅", today),
            entry("烧烤", today.withDayOfMonth(1)),   // 本月 1 日 → 本月
            entry("日料", prevMonthDay),               // 上月 → 不计入本月，计入上月
            entry("日料", prevMonthDay.plusDays(1))
        )
        val report = MonthlyReportCalculator.compute(entries, nowMillis)
        assertEquals(2, report.totalDecisions)
        assertEquals(2, report.prevMonthTotal)
    }

    @Test
    fun `无上月数据时 prevMonthTotal 为 null`() {
        val report = MonthlyReportCalculator.compute(listOf(entry("火锅", today)), nowMillis)
        assertNull(report.prevMonthTotal)
    }

    @Test
    fun `Top5 按次数降序同次按名称升序且截断`() {
        val entries = mutableListOf<HistoryStatEntry>()
        // 6 个不同名：f×6 e×5 d×4 c×3 b×2 a×1 —— 截断后不应有 a
        listOf("f" to 6, "e" to 5, "d" to 4, "c" to 3, "b" to 2, "a" to 1).forEach { (name, count) ->
            repeat(count) { entries += entry(name, today) }
        }
        // 同次数并列：bb 与 aa 各 2 次 → aa 排前（名称升序）
        repeat(2) { entries += entry("bb", today) }
        repeat(2) { entries += entry("aa", today) }

        val report = MonthlyReportCalculator.compute(entries, nowMillis)
        // 整表断言：次数降序 → 同次名称升序 → 截断到 5（b/bb/a 出局，aa 以名称升序占位）
        assertEquals(
            listOf("f" to 6, "e" to 5, "d" to 4, "c" to 3, "aa" to 2),
            report.topItems
        )
    }

    @Test
    fun `换一率按有换一个记录的会话占比`() {
        val entries = listOf(
            entry("火锅", today, reroll = 0),
            entry("烧烤", today, reroll = 2),
            entry("日料", today, reroll = 0),
            entry("面条", today, reroll = 1)
        )
        val report = MonthlyReportCalculator.compute(entries, nowMillis)
        assertEquals(0.5f, report.swapRate, 0.001f)
    }
}
