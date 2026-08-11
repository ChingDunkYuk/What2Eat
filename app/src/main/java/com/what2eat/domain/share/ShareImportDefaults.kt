package com.what2eat.domain.share

import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.SavedOptionType
import com.what2eat.domain.model.SourcePlatform

/**
 * 导入确认页默认值规则（纯 Kotlin，可单测）。
 *
 * - 大众点评/高德/百度地图 → 默认类型 RESTAURANT，默认列表 WANT_TO_TRY
 * - 美团 → 默认类型 RESTAURANT（不强行判断外卖），默认列表 WANT_TO_TRY
 * - 纯文字/类型不确定 → 返回 null，要求用户确认
 */
object ShareImportDefaults {

    fun defaultType(platform: SourcePlatform): SavedOptionType? = when (platform) {
        SourcePlatform.DIANPING,
        SourcePlatform.MEITUAN,
        SourcePlatform.AMAP,
        SourcePlatform.BAIDU_MAP,
        SourcePlatform.BROWSER -> SavedOptionType.RESTAURANT
        SourcePlatform.MANUAL,
        SourcePlatform.NONE,
        SourcePlatform.OTHER -> null // 类型不确定，要求用户确认
    }

    fun defaultCollections(platform: SourcePlatform): Set<CollectionType> = when (platform) {
        SourcePlatform.DIANPING,
        SourcePlatform.MEITUAN,
        SourcePlatform.AMAP,
        SourcePlatform.BAIDU_MAP,
        SourcePlatform.BROWSER -> setOf(CollectionType.WANT_TO_TRY)
        else -> emptySet()
    }
}