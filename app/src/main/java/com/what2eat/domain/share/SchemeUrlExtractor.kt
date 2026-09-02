package com.what2eat.domain.share

import java.net.URLDecoder

/**
 * v0.8.2：App 唤起 scheme 落地页提取（纯 Kotlin，可单测）。
 *
 * 背景：美团/点评短链（tb.htuiot.com、s.waimai.meituan.com 等）重定向的 Location
 * 常是 App 唤起 scheme（如 imeituan://…），HttpURLConnection 无法打开导致标题抓取静默失败。
 *
 * 提取策略：
 * 1. scheme URL 的 query 里带落地页参数（landingUrl/url/backurl 等，值 URL 编码）→ 解码后 http(s) 开头则返回；
 * 2. scheme 形如「imeituan://域名/路径」（host 是已知平台域名）→ 转成 https://域名/路径 继续抓。
 * 都不满足返回 null（调用方静默放弃）。
 */
object SchemeUrlExtractor {

    /** scheme query 中常见的落地页参数名 */
    private val landingKeys = listOf(
        "landingUrl", "landing_url", "landingPage", "landing_page",
        "url", "backUrl", "backurl", "back_url", "redirectUrl", "redirect_url", "targetUrl"
    )

    /** 可信平台域名（scheme 直转 https 仅限这些，防任意跳转） */
    private val trustedHosts = listOf(
        "meituan.com", "dianping.com", "waimai.meituan.com",
        "h5.waimai.meituan.com", "i.waimai.meituan.com"
    )

    /**
     * 从 App 唤起 scheme URL 中提取可抓取的 https 落地页。
     *
     * @param schemeUrl 如 `imeituan://funding?url=https%3A%2F%2Fh5.waimai.meituan.com%2Fshop%2F123`
     * @return 落地页 https URL；提取不到返回 null
     */
    fun extractLandingUrl(schemeUrl: String): String? {
        if (schemeUrl.isBlank()) return null

        // 1) query 落地页参数（URL 编码值）
        val query = schemeUrl.substringAfter('?', "")
        if (query.isEmpty()) return null
        for (pair in query.split('&')) {
            val key = pair.substringBefore('=').trim()
            if (key !in landingKeys) continue
            val encoded = pair.substringAfter('=', "")
            if (encoded.isEmpty()) continue
            val decoded = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull() ?: continue
            if (decoded.startsWith("http://", ignoreCase = true) ||
                decoded.startsWith("https://", ignoreCase = true)
            ) {
                return decoded
            }
        }

        // 2) 可信平台域名的 scheme 直转：imeituan://www.meituan.com/xx → https://www.meituan.com/xx
        val m = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://([^/?#]+)([/#?]?)""").find(schemeUrl) ?: return null
        val host = m.groupValues[1].substringBefore(':').lowercase()
        val rest = schemeUrl.substringAfter("://${m.groupValues[1]}", "")
        if (trustedHosts.any { host == it || host.endsWith(".$it") }) {
            return "https://$host$rest"
        }
        return null
    }
}
