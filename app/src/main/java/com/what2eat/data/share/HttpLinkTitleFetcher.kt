package com.what2eat.data.share

import com.what2eat.domain.share.HtmlTitleExtractor
import com.what2eat.domain.share.LinkTitleFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * HttpURLConnection 版链接标题抓取（无第三方依赖）。
 *
 * - 手动跟随重定向（覆盖 http↔https 协议切换），上限 3 次
 * - 移动端 UA（移动版页面更轻、标题更完整）
 * - 流式读取上限 64KB 或读到 </title> 即停（美团页面体积大，无需全量下载）
 * - 一切异常 → null（调用方静默回退手动填写，不阻塞不报错）
 */
@Singleton
class HttpLinkTitleFetcher @Inject constructor() : LinkTitleFetcher {

    override suspend fun fetchTitle(url: String): String? = withContext(Dispatchers.IO) {
        runCatching { fetchInternal(url) }.getOrNull()
    }

    private fun fetchInternal(startUrl: String): String? {
        var current = startUrl
        repeat(MAX_REDIRECTS) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", MOBILE_UA)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                setRequestProperty("Accept-Encoding", "identity")
            }
            try {
                when (conn.responseCode) {
                    in 300..399 -> {
                        val location = conn.getHeaderField("Location") ?: return null
                        // 相对地址基于当前 URL 解析
                        current = URL(URL(current), location).toString()
                    }
                    HttpURLConnection.HTTP_OK -> return readTitle(conn)
                    else -> return null
                }
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    private fun readTitle(conn: HttpURLConnection): String? {
        val contentType = conn.contentType
        if (!contentType.isNullOrBlank() && !contentType.contains("html", ignoreCase = true)) {
            return null
        }
        val bytes = conn.inputStream.use { input ->
            val buffer = ByteArray(MAX_READ_BYTES)
            var len = 0
            while (len < buffer.size) {
                val n = input.read(buffer, len, buffer.size - len)
                if (n < 0) break
                len += n
                // 已读到 </title> → 提前停止，不浪费流量
                if (containsBytes(buffer, len, TITLE_END_BYTES)) break
            }
            buffer.copyOf(len)
        }
        val charset = charsetFromContentType(contentType)
            ?: charsetFromMeta(bytes)
            ?: Charsets.UTF_8
        return HtmlTitleExtractor.parseTitle(String(bytes, charset))
    }

    /** Content-Type: text/html; charset=utf-8 */
    private fun charsetFromContentType(contentType: String?): Charset? =
        contentType?.let { ct ->
            Regex("""charset=([A-Za-z0-9_-]+)""").find(ct)?.groupValues?.get(1)
                ?.let { name -> runCatching { Charset.forName(name) }.getOrNull() }
        }

    /** 前 1KB 扫 <meta charset=...>（ISO-8859-1 逐字节安全解码后再匹配） */
    private fun charsetFromMeta(bytes: ByteArray): Charset? {
        val head = String(bytes, 0, min(bytes.size, 1024), Charsets.ISO_8859_1)
        return Regex("""<meta[^>]+charset\s*=\s*["']?\s*([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
            .find(head)?.groupValues?.get(1)
            ?.let { name -> runCatching { Charset.forName(name) }.getOrNull() }
    }

    private fun containsBytes(buffer: ByteArray, len: Int, pattern: ByteArray): Boolean {
        if (len < pattern.size) return false
        outer@ for (i in 0..len - pattern.size) {
            for (j in pattern.indices) {
                if (buffer[i + j] != pattern[j]) continue@outer
            }
            return true
        }
        return false
    }

    private companion object {
        const val TIMEOUT_MS = 5_000
        const val MAX_REDIRECTS = 3
        const val MAX_READ_BYTES = 64 * 1024
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        val TITLE_END_BYTES = "</title>".toByteArray(Charsets.US_ASCII)
    }
}
