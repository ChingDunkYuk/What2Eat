package com.what2eat.domain.share

import com.what2eat.domain.model.CollectionType
import com.what2eat.domain.model.SavedOptionType
import com.what2eat.domain.model.SourcePlatform

/**
 * 导入确认页默认值规则（纯 Kotlin，可单测）。
 *
 * - 大众点评/高德/百度地图 → 默认类型 RESTAURANT，默认列表 WANT_TO_TRY
 * - 美团 → 默认类型 RESTAURANT；分享文本含外卖关键字时默认"外卖商家"
 * - 纯文字/类型不确定 → 返回 null，要求用户确认
 */
object ShareImportDefaults {

    /** 外卖关键字（命中则优先默认"外卖商家"）。 */
    private val deliveryKeywords = listOf("外卖", "配送", "外送")

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

    /**
     * 结合分享文本的默认类型：
     * 分享文本含外卖/配送/外送等关键字时，默认"外卖商家"。
     */
    fun defaultType(platform: SourcePlatform, rawText: String?): SavedOptionType? {
        val base = defaultType(platform)
        if (base == SavedOptionType.RESTAURANT && !rawText.isNullOrBlank()) {
            val t = rawText.lowercase()
            if (deliveryKeywords.any { t.contains(it) }) {
                return SavedOptionType.TAKEOUT_STORE
            }
        }
        return base
    }

    fun defaultCollections(platform: SourcePlatform): Set<CollectionType> = when (platform) {
        SourcePlatform.DIANPING,
        SourcePlatform.MEITUAN,
        SourcePlatform.AMAP,
        SourcePlatform.BAIDU_MAP,
        SourcePlatform.BROWSER -> setOf(CollectionType.WANT_TO_TRY)
        else -> emptySet()
    }

    /**
     * 收件箱兜底名称：名称解析不出来时用作占位，之后在「待整理」里改名。
     *
     * v0.8.14：改为「待确认店铺·xx」中性占位。旧文案「来自xx的分享」与
     * 平台分享模板文案（ShareTextParser.isPlatformTemplate 拒绝的对象）
     * 完全相同——解析层刚把它拒掉，兜底层又原样放回，用户看到的还是
     * 模板文案，无法区分「识别失败占位」与「误识别成模板」。
     */
    fun fallbackName(platform: SourcePlatform): String = when (platform) {
        SourcePlatform.DIANPING,
        SourcePlatform.MEITUAN,
        SourcePlatform.AMAP,
        SourcePlatform.BAIDU_MAP,
        SourcePlatform.BROWSER -> "待确认店铺·${platform.label}"
        else -> "待确认的分享"
    }

    /**
     * 收件箱兜底类型：永不返回 null，保证「一键收进待整理」无需用户当场选类型。
     */
    fun inboxType(platform: SourcePlatform, rawText: String?): SavedOptionType =
        defaultType(platform, rawText) ?: SavedOptionType.RESTAURANT
}