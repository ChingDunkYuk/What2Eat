package com.what2eat.domain.history

import java.time.LocalDate
import java.time.ZoneId

/**
 * v1.3.0：月度吃饭报告数据。
 */
data class MonthlyReport(
    /** 如「2026年9月」 */
    val monthLabel: String,
    /** 本月决定次数 */
    val totalDecisions: Int,
    /** 本月最常吃 Top5（名称 → 次数；按次数降序、名称升序） */
    val topItems: List<Pair<String, Int>>,
    /** 本月换一率（rerollCount>0 的完成会话占比，0..1） */
    val swapRate: Float,
    /** 上月决定次数（无记录为 null，用于环比展示） */
    val prevMonthTotal: Int?
)

/**
 * v1.3.0：月度报告计算（纯 Kotlin，可 JVM 单测）。
 * 输入复用 HistoryStatEntry（与统计卡同一数据源），按本机时区切月。
 */
object MonthlyReportCalculator {

    fun compute(entries: List<HistoryStatEntry>, nowMillis: Long): MonthlyReport {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now(zone)
        val monthStart = now.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val prevMonthStart =
            now.minusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val thisMonth = entries.filter { it.completedAt >= monthStart }
        val prevMonth = entries.filter { it.completedAt in prevMonthStart until monthStart }

        val top = thisMonth
            .groupingBy { it.name }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(5)
            .map { it.key to it.value }

        return MonthlyReport(
            monthLabel = "${now.year}年${now.monthValue}月",
            totalDecisions = thisMonth.size,
            topItems = top,
            swapRate = if (thisMonth.isEmpty()) {
                0f
            } else {
                thisMonth.count { it.rerollCount > 0 }.toFloat() / thisMonth.size
            },
            prevMonthTotal = if (prevMonth.isEmpty()) null else prevMonth.size
        )
    }
}
