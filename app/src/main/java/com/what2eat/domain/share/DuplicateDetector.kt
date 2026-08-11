package com.what2eat.domain.share

import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.SavedOption

/**
 * 重复检测结果。
 */
sealed interface DuplicateCheckResult {
    /** 无重复，可保存 */
    data object NoDuplicate : DuplicateCheckResult

    /** 发现重复，附带已存在的选项与所在列表 */
    data class Duplicate(
        val existing: SavedOption,
        val collections: Set<CollectionType>
    ) : DuplicateCheckResult
}

/**
 * 重复检测（纯 Kotlin，可单测）。
 *
 * 优先级：
 * 1. canonical sourceUrl 相同
 * 2. sourcePlatform + source ID 相同（若能提取）
 * 3. 名称 + 区域完全相同
 * 4. 名称高度相似
 *
 * 第一版至少实现：URL 规范化后相同、名称完全相同。
 */
object DuplicateDetector {

    /**
     * 检测新导入是否与现有选项重复。
     *
     * @param newUrl 规范化后的 URL（可能为 null）
     * @param newName 新名称（可能为 null/空白）
     * @param existing 现有选项列表
     * @param existingCollections 选项 id → 所属列表
     */
    fun detect(
        newUrl: String?,
        newName: String?,
        existing: List<SavedOption>,
        existingCollections: Map<String, Set<CollectionType>>
    ): DuplicateCheckResult {
        val name = newName?.trim().orEmpty()

        // 1. URL 完全相同（规范化后）
        if (!newUrl.isNullOrBlank()) {
            val byUrl = existing.firstOrNull { it.sourceUrl != null && it.sourceUrl == newUrl }
            if (byUrl != null) {
                return DuplicateCheckResult.Duplicate(byUrl, existingCollections[byUrl.id].orEmpty())
            }
        }

        // 3 + 4. 名称完全相同（忽略大小写/空白）
        if (name.isNotEmpty()) {
            val byName = existing.firstOrNull {
                it.name.trim().equals(name, ignoreCase = true)
            }
            if (byName != null) {
                return DuplicateCheckResult.Duplicate(
                    byName,
                    existingCollections[byName.id].orEmpty()
                )
            }
        }

        return DuplicateCheckResult.NoDuplicate
    }
}