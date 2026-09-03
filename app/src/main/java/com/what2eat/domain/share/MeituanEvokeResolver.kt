package com.what2eat.domain.share

import java.net.URLDecoder

/**
 * v0.8.4：美团/点评唤起页解析器（纯 Kotlin，可单测）。
 *
 * 背景：美团分享短链（dpurl.cn 等）302 跳到 w.dianping.com/cube/evoke/xxx.html 唤起页，
 * 页面 <title> 是纯平台名（"美团"）——整条链路上唯一的店铺标识是嵌在唤起页 URL
 * query 里的 imeituan:// scheme（含 poiId）。
 *
 * 真实跳转链（2026-09 实测）：
 * http://dpurl.cn/cgDhxzyz
 *   → 302 → https://w.dianping.com/cube/evoke/meituan.html?url=imeituan%3A%2F%2Fwww.meituan.com%2Fmrn%3F...%26poiId%3D1475979044%26...
 *   → 200（title="美团"，无店名数据）
 *
 * 本解析器从唤起页 URL 提取 poiId，构造店铺 H5 页候选列表供标题抓取依次访问。
 *
 * v0.8.5：候选页升级为列表（点评 H5 店铺页优先）——美团/点评 POI 数据互通
 * （唤起页托管在 dianping.com、反爬页资源在 meituan.net，双端统一基建），poiId 通用。
 */
object MeituanEvokeResolver {

    /** 唤起页 URL 特征：*dianping.com/cube/evoke/ */
    private val evokePageRegex = Regex(
        """^https?://[^/]*dianping\.com/cube/evoke/""",
        RegexOption.IGNORE_CASE
    )

    /** 判断 URL 是否为美团/点评唤起页。 */
    fun isEvokePage(url: String): Boolean =
        url.isNotBlank() && evokePageRegex.containsMatchIn(url.trim())

    /**
     * 从唤起页 URL 提取店铺 H5 页候选列表（按优先级排序，v0.8.5）。
     *
     * 解析：query 的 url 参数（URL 编码的 imeituan:// scheme）→ 解码 → 提取 poiId
     * → 候选页：
     * 1. https://m.dianping.com/shop/{poiId} —— 点评 H5 店铺页（标题即店名）
     * 2. https://www.meituan.com/meishi/{poiId}/ —— 美团美食 POI 页（部分网络被风控）
     *
     * @return 候选 URL 列表；非唤起页 / 无 url 参数 / 无 poiId 时为空列表
     */
    fun extractPoiH5Urls(evokeUrl: String): List<String> {
        if (!isEvokePage(evokeUrl)) return emptyList()
        val query = evokeUrl.substringAfter('?', "")
        if (query.isEmpty()) return emptyList()
        for (pair in query.split('&')) {
            val key = pair.substringBefore('=').trim()
            if (key != "url") continue
            val encoded = pair.substringAfter('=', "")
            if (encoded.isEmpty()) continue
            val scheme = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull() ?: continue
            val poiId = extractPoiId(scheme) ?: continue
            return listOf(
                "https://m.dianping.com/shop/$poiId",
                "https://www.meituan.com/meishi/$poiId/"
            )
        }
        return emptyList()
    }

    /** 从 imeituan:// scheme URL 的 query 中提取 poiId 参数。 */
    internal fun extractPoiId(schemeUrl: String): String? {
        val query = schemeUrl.substringAfter('?', "")
        if (query.isEmpty()) return null
        for (pair in query.split('&')) {
            val key = pair.substringBefore('=').trim()
            if (key != "poiId") continue
            val value = pair.substringAfter('=', "")
            if (value.isNotEmpty()) return value
        }
        return null
    }
}
