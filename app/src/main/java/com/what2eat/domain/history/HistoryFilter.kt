package com.what2eat.domain.history

import com.what2eat.domain.model.CollectionType
import java.time.LocalDate
import java.time.ZoneId

/** v1.3.0：历史页时间筛选维度。 */
enum class HistoryTimeFilter(val label: String) {
    ALL("全部时间"),
    LAST_7_DAYS("近 7 天"),
    THIS_MONTH("本月")
}

/** v1.3.0：历史页模式筛选维度。 */
enum class HistoryModeFilter(val label: String) {
    ALL("全部方式"),
    CATEGORY("先决定吃什么"),
    POOL("从吃饭池决定")
}

/**
 * 历史页三维筛选状态（纯内存过滤，不加查询）。
 * personName 为 null 表示「全部人物」。
 * v1.4.0：新增所属列表/标签维度（null=全部；分类决策无此概念，置维度后不匹配）。
 */
data class HistoryFilterState(
    val time: HistoryTimeFilter = HistoryTimeFilter.ALL,
    val personName: String? = null,
    val mode: HistoryModeFilter = HistoryModeFilter.ALL,
    /** v1.4.0：所属列表维度（仅池决策选项有列表） */
    val collectionType: CollectionType? = null,
    /** v1.4.0：标签维度（仅池决策选项有标签） */
    val tagName: String? = null
) {
    /** 无任何筛选（默认态可跳过过滤循环） */
    val isDefault: Boolean
        get() = time == HistoryTimeFilter.ALL && personName == null &&
            mode == HistoryModeFilter.ALL && collectionType == null && tagName == null
}

/**
 * v1.3.0：历史筛选判定（纯 Kotlin，可 JVM 单测）。
 *
 * 边界约定：
 * - LAST_7_DAYS 含恰好 7×24h 前的记录（>= 边界即算近 7 天）；
 * - THIS_MONTH 按本机时区当月 1 日 00:00 起算；
 * - personName 匹配参与人姓名（两人家庭语义足够，HistoryItem.participants 存姓名）。
 * v1.4.0：collections/tags 为池决策选项的所属列表/标签（分类决策传空集——
 * 维度被设置时自然不匹配，语义即「选了维度只看池决策」）。
 */
object HistoryFilter {

    private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000

    fun matches(
        state: HistoryFilterState,
        completedAt: Long,
        participantNames: List<String>,
        isPoolDecision: Boolean,
        nowMillis: Long,
        collections: Set<CollectionType> = emptySet(),
        tags: Set<String> = emptySet()
    ): Boolean {
        if (!timeMatches(state.time, completedAt, nowMillis)) return false
        if (state.personName != null && state.personName !in participantNames) return false
        if (!modeMatches(state.mode, isPoolDecision)) return false
        if (state.collectionType != null && state.collectionType !in collections) return false
        if (state.tagName != null && state.tagName !in tags) return false
        return true
    }

    private fun modeMatches(mode: HistoryModeFilter, isPoolDecision: Boolean): Boolean = when (mode) {
        HistoryModeFilter.ALL -> true
        HistoryModeFilter.POOL -> isPoolDecision
        HistoryModeFilter.CATEGORY -> !isPoolDecision
    }

    private fun timeMatches(time: HistoryTimeFilter, completedAt: Long, nowMillis: Long): Boolean =
        when (time) {
            HistoryTimeFilter.ALL -> true
            HistoryTimeFilter.LAST_7_DAYS -> completedAt >= nowMillis - SEVEN_DAYS_MS
            HistoryTimeFilter.THIS_MONTH -> {
                val zone = ZoneId.systemDefault()
                val firstOfMonth = LocalDate.now(zone).withDayOfMonth(1)
                completedAt >= firstOfMonth.atStartOfDay(zone).toInstant().toEpochMilli()
            }
        }
}
