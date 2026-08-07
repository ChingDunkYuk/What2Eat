package com.what2eat.domain.foodpool

import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.ImportStatus
import com.what2eat.domain.model.SavedOption

/**
 * 池页筛选/排序/搜索逻辑（纯 Kotlin 可单测）。
 * Tab 与排序枚举定义在 feature 层（FoodPoolViewModel），此处只接收参数。
 */
object FoodPoolFilter {

    /**
     * 判断选项是否属于某 Tab。
     * [tabName] 为 feature 层枚举名（ALL/FREQUENT/VISITED/WANT_TO_TRY/TAKEOUT/HOME_COOK/AVOIDED/NEEDS_REVIEW）。
     */
    fun matchesTab(
        option: SavedOption,
        optionCollections: Set<CollectionType>,
        tabName: String
    ): Boolean = when (tabName) {
        "ALL" -> true
        "FREQUENT" -> CollectionType.FREQUENT in optionCollections
        "VISITED" -> CollectionType.VISITED in optionCollections
        "WANT_TO_TRY" -> CollectionType.WANT_TO_TRY in optionCollections
        "TAKEOUT" -> CollectionType.TAKEOUT in optionCollections
        "HOME_COOK" -> CollectionType.HOME_COOK in optionCollections
        "AVOIDED" -> CollectionType.AVOIDED in optionCollections
        "NEEDS_REVIEW" -> option.importStatus == ImportStatus.NEEDS_REVIEW
        else -> true
    }

    /** 搜索是否匹配（名称/区域/标签） */
    fun matchesSearch(
        option: SavedOption,
        optionTags: Set<String>,
        query: String
    ): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return option.name.contains(q, ignoreCase = true) ||
            option.areaText?.contains(q, ignoreCase = true) == true ||
            optionTags.any { it.contains(q, ignoreCase = true) }
    }

    /** 池页排序方式（纯 Kotlin，可单测） */
    enum class SortMode { RECENTLY_ADDED, NAME, RECENTLY_VISITED, RECENTLY_CHOSEN }

    /**
     * 对筛选后的选项排序。
     * 默认"最近添加"；"最近吃过"与"最近选择"均依据 lastChosenAt。
     */
    fun sort(options: List<SavedOption>, mode: SortMode): List<SavedOption> = when (mode) {
        SortMode.RECENTLY_ADDED -> options.sortedByDescending { it.createdAt }
        SortMode.NAME -> options.sortedBy { it.name }
        SortMode.RECENTLY_VISITED -> options.sortedByDescending { it.lastChosenAt ?: 0L }
        SortMode.RECENTLY_CHOSEN -> options.sortedByDescending { it.lastChosenAt ?: 0L }
    }
}