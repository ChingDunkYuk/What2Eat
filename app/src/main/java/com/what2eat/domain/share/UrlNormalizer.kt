package com.what2eat.domain.share

import java.util.Locale

/**
 * URL 规范化器（纯 Kotlin，可单测）。
 *
 * 处理：trim、scheme/host 小写、去除明显 tracking query、去 fragment。
 * 保留真正影响页面定位的参数，不过度清理导致链接失效。
 */
object UrlNormalizer {

    /** 从文本中提取第一个有效 http/https URL。无 URL 或非法则返回 null。 */
    fun extractFirstUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        // 匹配 http(s):// 开头，直到空白或常见中文标点
        val regex = Regex("""https?://[^\s"'<>（）()【】\[\]{}，,。；;！!？?]+""")
        val raw = regex.find(text)?.value ?: return null
        return normalize(raw)
    }

    /**
     * 规范化 URL：trim、scheme/host 小写、去除 fragment、去除明显 tracking 参数。
     * 无法解析返回 null（不抛异常）。
     */
    fun normalize(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        val uri = runCatching { UriCompat.parse(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        // scheme + host 小写重建，保留 port
        val port = uri.port.takeIf { it != -1 }?.let { ":$it" } ?: ""
        val path = uri.path ?: ""
        val query = cleanQuery(uri.encodedQuery)
        val sb = StringBuilder().append(scheme).append("://").append(host).append(port).append(path)
        if (query != null) sb.append('?').append(query)
        return sb.toString()
    }

    /** 去除明显 tracking 参数（保留页面定位参数如 id、shopid、poiid 等）。 */
    private fun cleanQuery(encodedQuery: String?): String? {
        if (encodedQuery.isNullOrBlank()) return null
        val trackingKeys = setOf(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "from", "share_source", "share_medium",
            "spm", "wb", "wfr", "weixinue", "scene", "share_token"
        )
        val kept = encodedQuery.split("&")
            .mapNotNull { pair ->
                val key = pair.substringBefore('=').lowercase(Locale.ROOT)
                if (key in trackingKeys) null else pair
            }
        return if (kept.isEmpty()) null else kept.joinToString("&")
    }
}

/** 最小 URI 解析封装，避免依赖上下文，便于 JVM 单测。 */
internal object UriCompat {
    fun parse(input: String): ParsedUri? {
        val m = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*):\/\/([^/?#]*)([^?#]*)(?:\?([^#]*))?(?:#.*)?$""").find(input)
            ?: return null
        val scheme = m.groupValues[1]
        val authority = m.groupValues[2]
        val path = m.groupValues[3]
        val query = m.groupValues[4].takeIf { it.isNotEmpty() }
        val host = authority.substringBefore(':')
        val portStr = authority.substringAfter(':', "").takeIf { it.isNotEmpty() }
        val port = portStr?.trim()?.toIntOrNull() ?: -1
        if (host.isBlank()) return null
        return ParsedUri(
            scheme = scheme,
            host = host,
            port = port,
            path = path,
            encodedQuery = query
        )
    }
}

internal data class ParsedUri(
    val scheme: String,
    val host: String,
    val port: Int,
    val path: String,
    val encodedQuery: String?
)