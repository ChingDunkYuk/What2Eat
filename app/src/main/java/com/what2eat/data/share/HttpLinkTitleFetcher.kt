package com.what2eat.data.share

import android.content.Context
import com.what2eat.domain.share.HtmlTitleExtractor
import com.what2eat.domain.share.LinkTitleFetcher
import com.what2eat.domain.share.MeituanEvokeResolver
import com.what2eat.domain.share.SchemeUrlExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * 链接标题抓取（无第三方依赖）：两阶段策略（v0.8.6）。
 *
 * 阶段一 HttpURLConnection 轻量链路（省流量、快）：
 * - 手动跟随重定向（覆盖 http↔https 协议切换）
 * - App 唤起 scheme（imeituan://）→ 提取落地 https 页继续
 * - 唤起页标题无效（纯平台名）→ poiId 构造候选页入队（点评 H5 优先、美团 POI 页次之）
 * - 反爬验证页标题（「身份核实」等）由 domain 层护栏拒绝
 *
 * 阶段二 WebView 兜底（阶段一全空时）：
 * - 真实浏览器内核执行 JS——美团/点评店铺页标题为 SPA 客户端渲染，
 *   纯 HTTP 抓到的 <title> 常为空，这是此前美团链接拿不到店名的关键原因；
 * - 自带 Cookie 与浏览器指纹，反爬通过率显著高于裸 HTTP；
 * - 目标依次为：阶段一派生的候选页（美团唤起链）或原链接（普通分享）。
 *
 * 一切异常 → null（调用方静默回退手动填写，不阻塞不报错）。
 */
@Singleton
class HttpLinkTitleFetcher @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val webViewTitleFetcher: WebViewTitleFetcher
) : LinkTitleFetcher {

    override suspend fun fetchTitle(url: String): String? = withContext(Dispatchers.IO) {
        val (httpTitle, candidates) = resolveHttp(url)
        if (httpTitle != null) return@withContext httpTitle
        // 阶段二：WebView 兜底（候选页优先；普通分享则用原链接）
        val targets = if (candidates.isEmpty()) listOf(url) else candidates
        targets.firstNotNullOfOrNull { target ->
            runCatching { webViewTitleFetcher.fetchTitle(target) }.getOrNull()
        }
    }

    /**
     * 阶段一：轻量 HTTP 解析。
     *
     * @return (有效标题 or null，唤起页派生的候选页列表)
     */
    private fun resolveHttp(startUrl: String): Pair<String?, List<String>> {
        val queue = ArrayDeque<String>()
        queue.add(startUrl)
        val candidates = mutableListOf<String>()
        var requests = 0

        while (queue.isNotEmpty() && requests < MAX_TOTAL_REQUESTS) {
            var current = queue.removeFirst()
            // 单条候选：跟随重定向直到拿到页面或断链
            while (requests < MAX_TOTAL_REQUESTS) {
                requests++
                val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", MOBILE_UA)
                    setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                    setRequestProperty("Accept-Encoding", "identity")
                    setRequestProperty("Referer", "https://www.meituan.com/")
                }
                try {
                    when (conn.responseCode) {
                        in 300..399 -> {
                            val location = conn.getHeaderField("Location") ?: break
                            current = if (location.startsWith("http://", true) || location.startsWith("https://", true)) {
                                // 相对/绝对地址基于当前 URL 解析
                                URL(URL(current), location).toString()
                            } else {
                                // App 唤起 scheme → 提取落地 https 页，提取不到断链
                                SchemeUrlExtractor.extractLandingUrl(location) ?: break
                            }
                        }
                        HttpURLConnection.HTTP_OK -> {
                            val title = readTitle(conn)
                            if (title != null) return title to candidates
                            // 唤起页标题无效（纯平台名/空/验证页）→ 候选页入队，继续外层循环
                            if (MeituanEvokeResolver.isEvokePage(current)) {
                                MeituanEvokeResolver.extractPoiH5Urls(current).forEach {
                                    if (it !in candidates) candidates.add(it)
                                    queue.add(it)
                                }
                            }
                            break
                        }
                        else -> break
                    }
                } finally {
                    conn.disconnect()
                }
            }
        }
        return null to candidates
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
        /** 总请求预算：短链(1) + 唤起页(1) + 候选页各自含重定向(约 2×2) + 余量 */
        const val MAX_TOTAL_REQUESTS = 8
        const val MAX_READ_BYTES = 64 * 1024
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        val TITLE_END_BYTES = "</title>".toByteArray(Charsets.US_ASCII)
    }
}
