package com.what2eat.domain.share

import com.what2eat.domain.model.SourcePlatform
import java.util.Locale

/**
 * 分享来源识别（轻量，不依赖平台私有 API）。
 *
 * 依据 sourcePackage、URL host、分享文本特征识别平台。
 */
object PlatformRecognizer {

    // 已知包名 → 平台
    private val packageMap = mapOf(
        "com.dianping.v1" to SourcePlatform.DIANPING,
        "com.sankuai.meituan" to SourcePlatform.MEITUAN,
        "com.autonavi.minimap" to SourcePlatform.AMAP,
        "com.baidu.BaiduMap" to SourcePlatform.BAIDU_MAP,
        "com.android.browser" to SourcePlatform.BROWSER
    )

    // 域名 → 平台
    private val hostMap = mapOf(
        "www.dianping.com" to SourcePlatform.DIANPING,
        "m.dianping.com" to SourcePlatform.DIANPING,
        "www.meituan.com" to SourcePlatform.MEITUAN,
        "m.meituan.com" to SourcePlatform.MEITUAN,
        "www.amap.com" to SourcePlatform.AMAP,
        "uri.amap.com" to SourcePlatform.AMAP,
        "map.baidu.com" to SourcePlatform.BAIDU_MAP,
        "j.map.baidu.com" to SourcePlatform.BAIDU_MAP
    )

    /** 检测来源平台。 */
    fun detect(sourcePackage: String?, url: String?, rawText: String?): SourcePlatform {
        // 1. 包名优先
        sourcePackage?.let { pkg ->
            packageMap[pkg]?.let { return it }
        }
        // 2. URL host
        url?.let { u ->
            val host = hostOf(u)
            hostMap[host]?.let { return it }
        }
        // 3. 文本特征（兜底）
        val t = rawText.orEmpty()
        if (t.contains("大众点评") || t.contains("dianping")) return SourcePlatform.DIANPING
        if (t.contains("美团") || t.contains("meituan")) return SourcePlatform.MEITUAN
        if (t.contains("高德") || t.contains("amap")) return SourcePlatform.AMAP
        if (t.contains("百度地图") || t.contains("baidu")) return SourcePlatform.BAIDU_MAP
        // 4. 有 URL 且其它应用分享 → 浏览器
        if (!url.isNullOrBlank()) return SourcePlatform.BROWSER
        return SourcePlatform.OTHER
    }

    private fun hostOf(url: String): String? {
        val m = Regex("""^https?://([^/?#]+)""", RegexOption.IGNORE_CASE).find(url.trim())
            ?: return null
        return m.groupValues[1].substringBefore(':').lowercase(Locale.ROOT)
    }
}