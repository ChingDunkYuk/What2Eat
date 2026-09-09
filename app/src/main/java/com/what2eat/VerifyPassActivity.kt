package com.what2eat

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.what2eat.data.share.FetchDebugLog
import com.what2eat.data.share.MainDocProxy
import com.what2eat.domain.share.ShareTextParser

/**
 * v1.2.4：yoda 验证墙人工通过页（全屏 Activity）。
 *
 * 加载「被墙的候选页 URL」（如 m.dianping.com/shop/{id}），经主文档代理自然
 * 302 出**新鲜** verify challenge——v1.2.0~v1.2.3 直接加载无头链上报的验证页
 * URL 会提示「请求异常，拒绝操作」：requestCode 是一次性 challenge，已被无头
 * 链消费。本页每次都重新走一遍跳转链，challenge 必然是新的。
 *
 * 通过后的两条收尾路径：
 * 1. 验证页回跳候选页（带通过 token）→ 候选页经代理安全加载 → onReceivedTitle
 *    拿到真实店名 → 直接带回（免重试）；
 * 2. 跳往其他美团/点评域 或 用户点「已通过」→ RESULT_OK 不带店名 → 调用方
 *    重试抓取（通过态 cookie 已种下，重试即通）。
 *
 * 安全约束（与无头抓取同级）：
 * - 主文档一律经 [MainDocProxy] 代发：服务端 302 到 imeituan:// 的唤起路径在
 *   Java 侧断链（官方「重定向不回调 shouldOverrideUrlLoading」盲区由此绕开）；
 * - 导航白名单：仅 verify.meituan.com 与候选页初始 host；登录/升级死路域拦截；
 *   scheme 与站外一律拦截；子资源非 http(s) scheme 吞空；
 * - 默认系统 UA + 默认视口（v1.2.3 教训：XWEB UA 声称会触发 yoda 反自动化
 *   破坏，滑块故意渲染残缺）。
 */
class VerifyPassActivity : ComponentActivity() {

    private var webView: WebView? = null
    private var finished = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pageUrl = intent.getStringExtra(EXTRA_PAGE_URL)
        val initialHost = pageUrl?.let { Uri.parse(it).host?.lowercase() }
        if (pageUrl.isNullOrBlank() || initialHost.isNullOrBlank()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        // v1.1.0 教训复用：UA 必须在主线程（WebView 创建线程）取出——
        // shouldInterceptRequest 运行在 IO 线程，那里调 view.settings 必崩
        var proxyUserAgent: String? = null
        val view = WebView(this).apply {
            // 与登录页同款默认环境：不改 UA、不动视口/缩放（任何「不像正常浏览器」
            // 的信号都可能让 yoda 故意把滑块渲染残缺）
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            proxyUserAgent = settings.userAgentString
            runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(this, true) }
            setDownloadListener { _, _, _, _, _ -> }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val scheme = request.url.scheme?.lowercase()
                    val host = request.url.host?.lowercase()
                    // scheme（imeituan:// 等）一律拦截
                    if (scheme != "http" && scheme != "https") return true
                    // 登录/升级死路域：拦下停留（出不了店名，用户可取消）
                    if (host in DEAD_END_HOSTS) return true
                    // verify 域内导航放行（滑块流程）；候选页 host 放行（无墙直出 /
                    // 通过后回跳落地——主文档均经代理，安全）
                    if (host == VERIFY_HOST || host == initialHost) return false
                    // 跳往其他美团/点评域 = 验证已通过的回跳（不回候选页的场景）
                    if (host != null &&
                        (host.endsWith(".meituan.com") || host.endsWith(".meituan.net") ||
                            host.endsWith(".dianping.com"))
                    ) {
                        passAndFinish(shopName = null)
                    }
                    // 站外一律拦截
                    return true
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    val scheme = request.url.scheme?.lowercase()
                    if (scheme != "http" && scheme != "https") {
                        // 子资源 scheme（iframe 唤起）：吞空，阻断发 Intent
                        return MainDocProxy.emptyResponse()
                    }
                    if (request.isForMainFrame) {
                        // 主文档代理：302 到 scheme 的唤起路径在 Java 侧断链；
                        // 30x 转 meta-refresh 逐跳导航（每跳仍经代理，且地址栏与真实
                        // 页面一致——verify 页 JS 能读到新鲜的 requestCode）
                        FetchDebugLog.add("[人工验证]主文档代理: ${request.url.toString().take(50)}")
                        return runCatching {
                            MainDocProxy.fetch(request.url.toString(), proxyUserAgent)
                        }.getOrElse { MainDocProxy.emptyResponse() }
                    }
                    // 子资源（滑块 JS/图片/验证 XHR）照常放行
                    return null
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView, title: String?) {
                    // 通过后落到店铺页：标题即店名（护栏净化；验证页/壳页标题在此
                    // 被拒，不会误判通过）——直接带回，免重试
                    val cleaned = title?.let { ShareTextParser.cleanWebTitle(it) } ?: return
                    passAndFinish(shopName = cleaned)
                }
            }
        }
        webView = view

        // 顶部条：说明 +「已通过」（验证完没自动收尾时手动判通过）+「取消」
        val title = TextView(this).apply {
            text = "完成美团滑块验证，通过后自动返回"
            textSize = 14f
            setTextColor(Color.BLACK)
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val passedBtn = Button(this).apply {
            text = "已通过"
            setOnClickListener { passAndFinish(shopName = null) }
        }
        val cancelBtn = Button(this).apply {
            text = "取消"
            setOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                title,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(passedBtn)
            addView(cancelBtn)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header)
            addView(
                view,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            )
        }
        setContentView(root)

        FetchDebugLog.add("[人工验证]打开候选页: ${pageUrl.take(55)}")
        view.loadUrl(pageUrl)
    }

    private fun passAndFinish(shopName: String?) {
        if (finished) return
        finished = true
        FetchDebugLog.add("[人工验证]通过${shopName?.let { ",店名:$it" } ?: ""}")
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_SHOP_NAME, shopName)
        )
        finish()
    }

    override fun onDestroy() {
        webView?.runCatching { stopLoading(); destroy() }
        webView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PAGE_URL = "extra_page_url"
        const val EXTRA_SHOP_NAME = "extra_shop_name"
        private const val VERIFY_HOST = "verify.meituan.com"

        /** 与 WebViewTitleFetcher 对齐的死路域（登录页/升级提示页） */
        private val DEAD_END_HOSTS = setOf(
            "passport.meituan.com", "account.meituan.com", "account.dianping.com",
            "static.meituan.net"
        )
    }
}
