package com.what2eat.data.share

import com.what2eat.domain.share.HtmlTitleExtractor
import com.what2eat.domain.share.LinkTitleFetcher
import com.what2eat.domain.share.MeituanEvokeResolver
import com.what2eat.domain.share.SchemeUrlExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * 链接标题抓取（无第三方依赖）：两阶段策略（v0.8.6），移动 UA 统一（v0.8.9）。
 *
 * 阶段一 HttpURLConnection 轻量链路（省流量、快）：
 * - 原始链接：手动跟随重定向，App 唤起 scheme → 提取落地 https 页继续
 * - 唤起页标题无效（纯平台名/壳页/验证页）→ poiId 构造候选页入队
 * - v0.8.9：撤掉 v0.8.8 的桌面 UA 实验——桌面版店铺页实测已全失效
 *   （点评 301 到登录页、美团 301 到营销页），全部请求统一移动 UA
 *
 * 阶段二 WebView 兜底（阶段一全空时）：
 * - 真实浏览器内核执行 JS + Cookie——SPA 客户端渲染的唯一解法；
 * - v0.8.9：WebView 内新增 SPA DOM 店名采集（og:title/h1/class*=shopName），
 *   覆盖「壳 title 固定、店名只在 DOM」的 meishi.meituan.com POI 页；
 * - 双层拦截非 http(s) scheme（shouldOverrideUrlLoading 主导航 +
 *   shouldInterceptRequest iframe），WebViewTitleFetcher 内实现。
 *
 * 一切异常 → null（调用方静默回退手动填写，不阻塞不报错）。
 */
@Singleton
class HttpLinkTitleFetcher @Inject constructor(
    private val webViewTitleFetcher: WebViewTitleFetcher
) : LinkTitleFetcher {

    override suspend fun fetchTitle(url: String): String? = withContext(Dispatchers.IO) {
        // v1.1.0：dpurl.cn 短链是 http://，Android 9+ 默认禁 cleartext HTTP——
        // 升级为 https:// 再请求（dpurl.cn 支持 https，302 链不变）
        val startUrl = if (url.startsWith("http://", ignoreCase = true)) {
            "https://" + url.substringAfter("://")
        } else {
            url
        }
        if (startUrl != url) FetchDebugLog.add("http→https: $startUrl")
        FetchDebugLog.add("HTTP阶段开始: $startUrl")
        val (httpTitle, candidates) = resolveHttp(startUrl)
        if (httpTitle != null) {
            FetchDebugLog.add("HTTP命中: $httpTitle")
            return@withContext httpTitle
        }
        // 阶段二：WebView 兜底（阶段一全空时）
        val targets = if (candidates.isEmpty()) listOf(url) else candidates
        FetchDebugLog.add("HTTP无结果,转WebView,候选${targets.size}个")
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
                // v0.8.11 防御：URL 构造/openConnection 抛异常（畸形跳转地址等）
                // 不得冒泡——这是分享流程协程链上的一环，异常会杀掉确认页进程
                val connResult = runCatching {
                    (URL(current).openConnection() as HttpURLConnection).apply {
                        connectTimeout = TIMEOUT_MS
                        readTimeout = TIMEOUT_MS
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", MOBILE_UA)
                        setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                        setRequestProperty("Accept-Encoding", "identity")
                        setRequestProperty("Referer", "https://www.meituan.com/")
                    }
                }
                val conn = connResult.getOrNull()
                if (conn == null) {
                    FetchDebugLog.add("连接失败: ${connResult.exceptionOrNull()?.javaClass?.simpleName}: ${connResult.exceptionOrNull()?.message}")
                    break
                }
                try {
                    val code = runCatching { conn.responseCode }.getOrElse { e ->
                        FetchDebugLog.add("读响应失败: ${e.javaClass.simpleName}: ${e.message}")
                        -1
                    }
                    if (code == -1) break
                    FetchDebugLog.add("HTTP $code ${current.take(70)}")
                    when (code) {
                        in 300..399 -> {
                            val location = conn.getHeaderField("Location") ?: break
                            FetchDebugLog.add("  跳转: ${location.take(60)}")
                            current = if (location.startsWith("http://", true) || location.startsWith("https://", true)) {
                                // 相对/绝对地址基于当前 URL 解析
                                runCatching { URL(URL(current), location).toString() }.getOrNull() ?: break
                            } else {
                                // App 唤起 scheme → 提取落地 https 页，提取不到断链
                                SchemeUrlExtractor.extractLandingUrl(location) ?: break
                            }
                        }
                        HttpURLConnection.HTTP_OK -> {
                            val title = readTitle(conn)
                            if (title != null) return title to candidates
                            FetchDebugLog.add("  200但标题无效(壳/验证页)")
                            // 唤起页标题无效（纯平台名/壳页/验证页）→ 候选页入队，继续外层循环
                            if (MeituanEvokeResolver.isEvokePage(current)) {
                                MeituanEvokeResolver.extractPoiH5Urls(current).forEach {
                                    if (it !in candidates) candidates.add(it)
                                    queue.add(it)
                                }
                                FetchDebugLog.add("  唤起页,派生候选${candidates.size}个")
                            }
                            break
                        }
                        else -> break
                    }
                } finally {
                    runCatching { conn.disconnect() }
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
        // v0.8.11 防御：读流/解码异常不得冒泡（协程链上抛出会杀确认页进程）
        val bytes = runCatching {
            conn.inputStream.use { input ->
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
        }.getOrNull() ?: return null
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
