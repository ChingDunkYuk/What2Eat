package com.what2eat.domain.history

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * 统计输入条目。
 *
 * name 为历史展示名（分类决策的分类名或池决策的店名，均由 feature 层解析）；
 * 本对象不依赖 Android/Room/Compose。
 */
data class HistoryStatEntry(
    val name: String,
    val completedAt: Long,
    val rerollCount: Int
)

/** 最常吃榜条目 */
data class TopFoodEntry(
    val name: String,
    val count: Int
)

/** 历史统计结果 */
data class HistoryStats(
    /** 历史总决定次数 */
    val totalDecisions: Int,
    /** 本月（自然月）决定次数 */
    val thisMonthDecisions: Int,
    /** 最常吃 Top 5：计数降序、同数按名称字典序 */
    val topFoods: List<TopFoodEntry>,
    /** 平均换一个次数（四舍五入到 1 位小数；无记录为 0.0） */
    val averageRerollCount: Double
)

/**
 * 历史统计计算器（纯 Kotlin，可独立单元测试）。
 *
 * 时区作为参数注入（默认系统时区）；测试传固定 ZoneId 保证确定性。
 */
object HistoryStatsCalculator {

    /** 最常吃榜上限 */
    private const val TOP_LIMIT = 5

    fun compute(
        entries: List<HistoryStatEntry>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): HistoryStats {
        val total = entries.size

        val nowMonth = YearMonth.from(Instant.ofEpochMilli(nowMillis).atZone(zone))
        val thisMonth = entries.count {
            YearMonth.from(Instant.ofEpochMilli(it.completedAt).atZone(zone)) == nowMonth
        }

        val averageReroll = if (entries.isEmpty()) 0.0
        else round1(entries.map { it.rerollCount }.average())

        val topFoods = entries
            .groupingBy { it.name }
            .eachCount()
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
            )
            .take(TOP_LIMIT)
            .map { TopFoodEntry(name = it.key, count = it.value) }

        return HistoryStats(
            totalDecisions = total,
            thisMonthDecisions = thisMonth,
            topFoods = topFoods,
            averageRerollCount = averageReroll
        )
    }

    /** 四舍五入到 1 位小数 */
    private fun round1(value: Double): Double =
        (value * 10).roundToInt() / 10.0
}

/**
 * 按本地日分组结果。
 *
 * epochDay = LocalDate.toEpochDay()（按传入时区换算的本地日序号）。
 */
data class HistoryDayGroup<T>(
    val epochDay: Long,
    val items: List<T>
)

/**
 * 历史按日分组器（纯 Kotlin，可独立单元测试）。
 *
 * 先按时间戳降序稳定排序，再按本地日聚合；输出组序与组内条目序均为时间降序。
 */
object HistoryDateGrouper {

    fun <T> groupByDay(
        items: List<T>,
        timestampOf: (T) -> Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<HistoryDayGroup<T>> {
        if (items.isEmpty()) return emptyList()

        val sorted = items.sortedByDescending(timestampOf)

        val groups = mutableListOf<HistoryDayGroup<T>>()
        val builder = mutableListOf<T>()
        var currentEpochDay: Long? = null

        for (item in sorted) {
            val epochDay = LocalDate
                .from(Instant.ofEpochMilli(timestampOf(item)).atZone(zone))
                .toEpochDay()
            if (currentEpochDay == null || epochDay == currentEpochDay) {
                if (currentEpochDay == null) currentEpochDay = epochDay
                builder.add(item)
            } else {
                groups.add(HistoryDayGroup(epochDay = currentEpochDay, items = builder.toList()))
                builder.clear()
                builder.add(item)
                currentEpochDay = epochDay
            }
        }
        if (builder.isNotEmpty()) {
            groups.add(HistoryDayGroup(epochDay = currentEpochDay!!, items = builder.toList()))
        }

        return groups
    }
}
