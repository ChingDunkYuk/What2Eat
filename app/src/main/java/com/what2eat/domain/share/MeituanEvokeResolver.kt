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
 * 本解析器从唤起页 URL 提取 poiId，构造美团美食 POI H5 页
 * （https://www.meituan.com/meishi/{poiId}/，title 即店名）供标题抓取二次访问。
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
     * 从唤起页 URL 提取美团美食 POI H5 页地址。
     *
     * 解析：query 的 url 参数（URL 编码的 imeituan:// scheme）→ 解码 → 提取 poiId
     * → https://www.meituan.com/meishi/{poiId}/
     *
     * @return POI H5 页 URL；非唤起页 / 无 url 参数 / 无 poiId 时返回 null
     */
    fun extractPoiH5Url(evokeUrl: String): String? {
        if (!isEvokePage(evokeUrl)) return null
        val query = evokeUrl.substringAfter('?', "")
        if (query.isEmpty()) return null
        for (pair in query.split('&')) {
            val key = pair.substringBefore('=').trim()
            if (key != "url") continue
            val encoded = pair.substringAfter('=', "")
            if (encoded.isEmpty()) continue
            val scheme = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull() ?: continue
            val poiId = extractPoiId(scheme) ?: continue
            return "https://www.meituan.com/meishi/$poiId/"
        }
        return null
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
