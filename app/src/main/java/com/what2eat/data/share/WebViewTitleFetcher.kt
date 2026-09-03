package com.what2eat.data.share

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.what2eat.domain.share.ShareTextParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.8.6：WebView 兜底标题抓取（无 UI 加载，纯后台）。
 *
 * 为什么需要它：美团/点评店铺页是 SPA——<title> 由 JS 在客户端渲染，
 * 纯 HttpURLConnection 永远拿不到（空标签）；且 WebView 自带 Cookie、
 * 真实浏览器指纹与 JS 执行环境，反爬验证的通过率也远高于裸 HTTP。
 *
 * 流程：主线程创建 WebView（不 attach 到任何视图）→ loadUrl →
 * [WebChromeClient.onReceivedTitle] 捕获标题（SPA 会多次回调：占位标题 → 真实店名）→
 * 复用 domain 层 [ShareTextParser.cleanWebTitle] 净化（平台名/元信息/验证页护栏）→
 * 有效标题立即完成；超时返回 null（调用方静默回退手动填写）。
 *
 * 一切异常 → null，不阻塞不报错。
 */
@Singleton
class WebViewTitleFetcher @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    suspend fun fetchTitle(url: String): String? = withContext(Dispatchers.Main) {
        runCatching { fetchInternal(url) }.getOrNull()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun fetchInternal(url: String): String? {
        val done = CompletableDeferred<String?>()
        val handler = Handler(Looper.getMainLooper())
        val webView = WebView(appContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = MOBILE_UA
            // 只取标题，不加载图片（省流量、提速）
            settings.loadsImagesAutomatically = false
            settings.blockNetworkImage = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    // 拦截 App 唤起 scheme（imeituan:// 等），停留在网页侧继续等标题
                    return !request.url.toString().startsWith("http")
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView, title: String?) {
                    // SPA 标题多次更新（占位 → 真实店名）：净化通过即完成
                    val cleaned = title?.let { ShareTextParser.cleanWebTitle(it) } ?: return
                    done.complete(cleaned)
                }
            }
        }

        val timeout = Runnable { done.complete(null) }
        handler.postDelayed(timeout, TIMEOUT_MS)
        try {
            webView.loadUrl(url)
            return done.await()
        } finally {
            handler.removeCallbacks(timeout)
            runCatching {
                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    private companion object {
        /** SPA 渲染需要时间，给足余量 */
        private val TIMEOUT_MS = TimeUnit.SECONDS.toMillis(8)
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
