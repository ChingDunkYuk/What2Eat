package com.what2eat.domain.share

import java.util.Locale

/**
 * 从 URL 提取平台级业务 ID（店铺/POI/商家 ID）（纯 Kotlin，可单测）。
 *
 * 用途：重复检测的"平台 + ID"匹配。同一店铺的不同分享链接，
 * 规范化 URL 可能不同（不同子域/query），但业务 ID 相同。
 */
object SourceIdExtractor {

    private const val DIANPING = "dianping.com"
    private const val MEITUAN = "meituan.com"
    private const val AMAP = "amap.com"
    private const val BAIDU = "baidu.com"

    /** 通用 query 参数键（多平台通用）。 */
    private val defaultIdKeys = listOf(
        "id", "shopid", "poiid", "shop_id", "poi_id",
        "restaurant_id", "merchant_id", "sid"
    )

    /** 从 URL 提取业务 ID；无法提取返回 null。 */
    fun extractId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val parsed = runCatching { UriCompat.parse(url.trim()) }.getOrNull() ?: return null
        val host = parsed.host?.lowercase(Locale.ROOT) ?: return null
        val path = parsed.path.orEmpty()
        val query = parsed.encodedQuery.orEmpty()

        return when {
            host.contains(DIANPING) -> dianpingId(path, query)
            host.contains(MEITUAN) -> meituanId(path, query)
            host.contains(AMAP) -> amapId(path, query)
            host.contains(BAIDU) -> baiduId(path, query)
            else -> genericQueryId(query)
        }
    }

    /** 大众点评：/shop/xxx、/restaurant/xxx、/poi/xxx；兜底通用 query。 */
    private fun dianpingId(path: String, query: String): String? {
        pathPatternId(path, listOf("shop", "restaurant", "poi"))?.let { return it }
        return genericQueryId(query)
    }

    /** 美团：/restaurant/xxx、/r/xxx、/shop/xxx、/poi/xxx；兜底通用 query。 */
    private fun meituanId(path: String, query: String): String? {
        pathPatternId(path, listOf("restaurant", "r", "shop", "poi", "meishi"))?.let { return it }
        return genericQueryId(query)
    }

    /** 高德：uri.amap.com/marker?...&id=xxx；短链 /s/xxx。 */
    private fun amapId(path: String, query: String): String? {
        genericQueryId(query, listOf("id", "shopid", "poiid", "uid"))?.let { return it }
        return pathPatternId(path, listOf("s", "marker", "poi"))
    }

    /** 百度地图：map.baidu.com/?...&uid=xxx；短链 j.map.baidu.com/<short>。 */
    private fun baiduId(path: String, query: String): String? {
        genericQueryId(query, listOf("uid", "id", "pid"))?.let { return it }
        return pathPatternId(path, emptyList())
    }

    /** 路径模式匹配：`<seg>/<id>`（支持 /h5/shop/123 之类前缀）。 */
    private fun pathPatternId(path: String, segments: List<String>): String? {
        val trimmed = path.trim('/')
        if (trimmed.isEmpty()) return null
        if (segments.isEmpty()) {
            // 短链：取最后一段路径作为 ID
            return trimmed.substringAfterLast('/').takeIf { it.isNotEmpty() }
        }
        for (seg in segments) {
            val escaped = Regex.escape(seg)
            val id = Regex("""(?:^|/)${escaped}/([^/]+)(?:/|$)""")
                .find(trimmed)?.groupValues?.get(1)?.trim() ?: continue
            if (id.isNotEmpty()) return id
        }
        return null
    }

    /** 从 query 中按优先级取第一个命中键的值。 */
    private fun genericQueryId(query: String, keys: List<String> = defaultIdKeys): String? {
        if (query.isBlank()) return null
        for (pair in query.split("&")) {
            val key = pair.substringBefore('=').trim().lowercase(Locale.ROOT)
            if (key in keys) {
                val value = pair.substringAfter('=', "").trim()
                if (value.isNotEmpty()) return value
            }
        }
        return null
    }
}
