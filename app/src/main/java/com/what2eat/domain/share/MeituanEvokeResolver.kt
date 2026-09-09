package com.what2eat.domain.share

import java.net.URLDecoder

/**
 * 美团/点评唤起页解析器（纯 Kotlin，可单测）。
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
 * 本解析器从唤起页 URL 提取 poiId，构造店铺页候选列表供标题抓取依次访问。
 *
 * v0.8.9：候选页改为移动版（桌面版实测已全失效，见下）。
 * v0.8.8 的桌面候选是回归错误——2026-09-03 复测：
 * - www.dianping.com/shop/{id}：301 → account.dianping.com 登录页，title="大众点评网"
 *   （该标题穿透当时的护栏被误当店名返回——用户看到名称栏填了"大众点评网"）；
 * - www.meituan.com/meishi/{id}/：301 → about.meituan.com/win-together 营销页，无标题。
 *
 * v0.8.10：候选只留美团自家 meishi POI H5（唤起防护优先于店名）：
 * meishi.meituan.com/meishi/poi/index.html?isItoH5=true&poiId={id}
 * —— biz-mrn-food-poi Web 构建（scheme 指向的 MRN 页的网页版），给浏览器用户的
 *    页面，无强制唤起逻辑；实测无反爬墙（裸 HTTP 即可拿到 shell），SPA 壳 title
 *    固定"商家详情"、店名渲染在 DOM 里 → 靠 WebViewTitleFetcher 的 DOM 采集
 *    （og:title/h1/class*=shopName）拿店名。
 * 已删除的候选（v0.8.9 曾用，用户实测仍跳回美团 App）：
 * - m.dianping.com/shop/{id}：App 引导页，JS 多重 fallback 唤起 + 302 到 scheme
 *   可绕过导航层拦截（服务端重定向不回调 shouldOverrideUrlLoading）。
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
     * 从唤起页 URL 提取店铺页候选列表（v0.8.10：仅 meishi POI H5 一项）。
     *
     * 解析：query 的 url 参数（URL 编码的 imeituan:// scheme）→ 解码 → 提取 poiId
     * → 候选页：
     * meishi.meituan.com/meishi/poi/index.html?isItoH5=true&poiId={id} —— 美团自家
     * meishi POI H5（poiId 在美团域内是权威 ID，无错店风险；无反爬墙；浏览器态
     * 页面无强制唤起）。
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
            // v1.1.3：候选重排（3 次真实分享的抓取日志实测）。按「无登录态拿店名
            // 概率」排序：
            // 1. m.dianping.com/shop/{id}：点评移动页——yoda 不拦时 200 直出，
            //    title 即「【店名】电话_地址…」，唯一实测无登录态拿到店名的候选
            //    （实测 cookie=无时成功；带登录态 cookie 反而更容易被 302 去 verify）；
            // 2. i.meituan.com/poi/{id}：经典 POI 落地页，title 即店名（但几乎
            //    必 302 verify 交互验证，无头 WebView 过不了，快速失败成本低）；
            // 3. meishi POI H5：数据接口 combinedinfos 需 SSO 登录态（无登录返回
            //    80B 错误体，SPA 随后跳登录页/升级页），仅登录用户有机会。
            return listOf(
                "https://m.dianping.com/shop/$poiId",
                "https://i.meituan.com/poi/$poiId",
                "https://meishi.meituan.com/meishi/poi/index.html?isItoH5=true&poiId=$poiId"
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
