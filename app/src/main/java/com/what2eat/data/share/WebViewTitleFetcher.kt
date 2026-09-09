package com.what2eat.data.share

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.what2eat.domain.share.ShareTextParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.8.6：WebView 兜底标题抓取（无 UI 加载，纯后台）。
 *
 * 为什么需要它：美团/点评店铺页是 SPA——<title> 由 JS 在客户端渲染，
 * 纯 HttpURLConnection 永远拿不到（空标签）；且 WebView 自带 Cookie、
 * 真实浏览器指纹与 JS 执行环境。
 *
 * v0.8.9：SPA DOM 店名采集——meishi.meituan.com 的 POI H5 页 SPA 壳
 * <title> 固定为「商家详情」，店名只渲染在 DOM 里（og:title/h1/
 * class*=shopName 节点采集，onPageFinished 后分批延迟执行）。
 *
 * v0.8.10：导航白名单 + scheme 拦截 + verify 黑名单。用户实测仍跳回美团。
 *
 * v0.8.11：**主文档代理**（根除「服务端 302 → imeituan:// 唤起」盲区）。
 * 用户设备装着美团 App，真机 WebView 带 cookie/真实指纹请求美团页面时，
 * 服务端可能 302 到 imeituan:// scheme——而 Android WebView 官方文档明确：
 * 重定向链不回调 shouldOverrideUrlLoading；shouldInterceptRequest 也不会为
 * 非 http 的 Location 发起请求。两道拦截全部落空 → WebView 直接 fire Intent
 * → 用户被拽回美团（表现为「确认页刚出现瞬间跳回」）。
 * 解法：[shouldInterceptRequest] 拦下主文档请求，由我们用
 * HttpURLConnection 代发并手动消化重定向链——遇到非 http 的 Location
 * 直接断链（返回空响应），WebView 网络栈从头到尾接触不到 scheme。
 * 子资源（JS/XHR/CSS）照常加载，DOM 采集链路不受影响。
 *
 * v0.8.12：代理的 cookie 回填（用户实测 v0.8.11 不跳了但店名拿不到）。
 * 代发主文档后 WebView 不再处理其 Set-Cookie——meishi SPA 的数据接口
 * 靠 H5guard 签名，签名依赖主文档种下的 cookie（uuid 等），cookie 丢失
 * → POI 接口全被拒 → 店名渲染不出来。每个代理响应（含 30x 跳）的
 * Set-Cookie 手动回填 CookieManager；另放行第三方 cookie（跨站 API
 * 需要）+ 采集脚本增加 JSON-LD 店名。
 *
 * v0.8.15：放行 verify.meituan.com（撤掉 v0.8.10 的黑名单）+ 分域 UA。
 * - 验证页 URL 带 adaptor=auto——真实设备（移动 IP + 系统 WebView 指纹）
 *   有机会自动通过验证跳回店铺页拿标题；v0.8.10 拉黑它等于自弃此路。
 *   放行无唤起风险：scheme 重定向在代理层已掐死，JS 唤起被导航白名单 +
 *   子资源 scheme 拦截覆盖；最坏情况是停在验证页等超时（安全失败）。
 * - UA 分域：美团域（*.meituan.com/net）追加微信标记（纯网页浏览分支）；
 *   点评域用干净系统 UA——点评域见微信 UA 会 302 到微信 OAuth 死链
 *   （open.weixin.qq.com，需真实微信环境）。
 *
 * v1.1.1：修复代理线程崩溃 + 30x 改客户端跳转。
 * - v1.1.0 在主线程取好的 proxyUserAgent 没传进调用点——IO 线程仍调
 *   view.settings.userAgentString 必抛 RuntimeException → 主文档代理全部
 *   返回空响应（页面空白、标题退化成 URL，全候选"未拿到"，日志现"拦截异常"）；
 * - 30x 不再 Java 侧内部跟随：改喂 meta-refresh 壳页让 WebView 导航到真实
 *   落地 URL（verify 页 JS 要读 location.search 里的 requestCode 才能自动
 *   通过；原方案地址栏停在跳转前 URL，自动验证必坏）。每跳仍经代理，
 *   非 http Location 断链不变；导航白名单补 dianping.com（唤起链本家）。
 *
 * v1.1.2：登录页判死路 + 死路标题快速失败。
 * - SPA 数据接口 401 会把页面跳去 passport 登录页——「美团网账号登录-手机美团
 *   官网」曾穿透护栏被当店名返回（还因返回非空短路了后续候选）；现导航层拦截
 *   登录域并直接判死路（省 2×12s 空等），ShareTextParser 护栏同步拒收登录页标题；
 * - onReceivedTitle 命中验证/登录类死路标题即 complete(null) 快速失败，不再
 *   空等 12s 超时；XHR 劫持回传响应状态码（诊断数据接口被拒原因）。
 *
 * v1.1.3：候选重排 + 升级页判死路（3 次真实分享日志实测）。
 * - m.dianping.com/shop 是唯一无登录态直出店名标题的候选（cookie=无时 yoda
 *   不拦、title 即店名），MeituanEvokeResolver 已提为首选；meishi 数据接口
 *   无 SSO 必返回 80B 错误体（SPA 随后跳登录页/升级页），垫底；
 * - meishi SPA 嫌 WebView「浏览器旧」会跳 static.meituan.net 升级页——
 *   「温馨提示」曾穿透护栏被当店名；死路域集合（原 LOGIN_HOSTS）拦下并快速
 *   失败，护栏同步拒收；
 * - XHR 劫持对数据接口的短小无店名响应回传 body 片段（诊断 401/风控错误码）。
 *
 * v1.2.0：撞 yoda 验证墙上报 URL（[VerifyWallSignal]）。实测风控对测试设备
 * 已接近 100% 拦截（cookie=无也被 302 到 verify）——纯无头路线到头。
 * 现撞墙即上报验证页 URL，ShareImport 页弹出可视化 WebView 让用户手动滑过；
 * 通过态 cookie 写入共享 CookieManager 后自动重试，代理请求带通过态即不再撞墙。
 *
 * v1.2.4：验证墙信号改报「被墙的候选页 URL」（此前报验证页 URL——requestCode
 * 是一次性 challenge，被无头链消费后用户打开会提示「请求异常，拒绝操作」）。
 * VerifyPassActivity 加载候选页 URL，经主文档代理自然 302 出新鲜 challenge；
 * 通过后落地店铺页可直接带回店名。主文档代理抽出为 [MainDocProxy] 共享。
 *
 * 完整防御矩阵：
 * 1. [proxyDocument]：主文档代发 + 手动 302 + scheme 断链（本轮核心）；
 * 2. [shouldOverrideUrlLoading] 导航白名单：只放行初始 host 与美团系域名的
 *    http(s) 导航（JS location.href / meta refresh 路径）；
 * 3. [shouldInterceptRequest] 请求黑名单：子资源里非 http(s) scheme（iframe
 *    唤起）与 verify.meituan.com（滑块页）吞空响应；
 * 4. [setDownloadListener] 空实现（封下载流的外部协议处理）。
 *
 * 唤起防护优先级高于店名获取：宁可店名拿不到（回退手动填写），
 * 绝不允许把用户拽去美团 App。
 *
 * 一切异常 → null，不阻塞不报错。
 */
@Singleton
class WebViewTitleFetcher @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    suspend fun fetchTitle(url: String): String? =
        withContext(Dispatchers.Main) {
            // v1.1.0：http:// 链接升级 https://（Android 9+ 禁 cleartext；dpurl.cn 支持 https）
            val startUrl = if (url.startsWith("http://", ignoreCase = true)) {
                "https://" + url.substringAfter("://")
            } else {
                url
            }
            // v1.1.0：H5guard 签名偶发失败，登录态下重试提升成功率（最多 MAX_ATTEMPTS 次）
            var attempt = 0
            var result: String? = null
            while (attempt < MAX_ATTEMPTS && result == null) {
                attempt++
                result = runCatching { fetchInternal(startUrl) }.getOrNull()
                if (result == null && attempt < MAX_ATTEMPTS) {
                    runCatching { delay(RETRY_DELAY_MS) }
                }
            }
            result
        }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun fetchInternal(url: String): String? {
        val done = CompletableDeferred<String?>()
        val handler = Handler(Looper.getMainLooper())
        // onPageFinished 排出的延迟采集任务（finally 里统一清理，防止在已销毁的 WebView 上执行）
        val pendingHarvests = mutableListOf<Runnable>()
        // 导航白名单基准：初始加载页的 host（页面自身站内跳转放行，出域拦截）
        val initialHost = Uri.parse(url).host?.lowercase()
        // v0.8.15 分域 UA：美团域追加微信标记（纯网页浏览分支，不强制登录不唤起）；
        // 点评域用干净系统 UA（微信 UA 会被点评 302 到微信 OAuth 死链）。
        val meituanDomain = initialHost != null &&
            (initialHost.endsWith(".meituan.com") || initialHost.endsWith(".meituan.net") ||
                initialHost == "meituan.com" || initialHost == "meituan.net")
        // v1.1.0：UA 必须在主线程（WebView 创建线程）取出——shouldInterceptRequest
        // 运行在 IO 线程，view.settings.userAgentString 在那里调用会抛
        // RuntimeException("A WebView method was called on a thread other than...")，
        // 此前正是这个 bug 导致 proxyDocument 每次返回空响应、页面空白。
        var proxyUserAgent: String? = null
        val webView = WebView(appContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            if (meituanDomain) {
                settings.userAgentString = settings.userAgentString + WECHAT_UA_SUFFIX
            }
            proxyUserAgent = settings.userAgentString
            settings.loadsImagesAutomatically = false
            settings.blockNetworkImage = true
            // 不允许 JS 自动弹窗（window.open 自动唤起路径一并关闭）
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            // 拦截层 4：下载流兜底（不设 listener 时可能触发系统外部处理）
            setDownloadListener { _, _, _, _, _ -> }
            // v0.8.12：SPA 的数据接口可能跨站（*.meituan.com 之外），放行第三方 cookie
            runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(this, true) }
            // v1.1.0：JS 数据劫持桥——美团数据接口响应经 XHR_HOOK_SCRIPT 拦截后回调店名
            addJavascriptInterface(
                ShopNameJavascriptInterface(onShopName = { name -> done.complete(name) }),
                JS_INTERFACE_NAME
            )
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    // v1.1.2/v1.1.3：跳死路域（登录页/升级提示页）= 数据接口已 401
                    // 或 SPA 嫌浏览器旧——店名不可能再渲染出来。拦下导航并直接判死路
                    //（省 2×12s 空等；已拿到店名时 complete 是 no-op，无副作用）
                    val targetHost = request.url.host?.lowercase()
                    if (targetHost in DEAD_END_HOSTS) {
                        FetchDebugLog.add("跳死路域,判死路: ${targetHost.orEmpty().take(30)}")
                        done.complete(null)
                        return true
                    }
                    // 拦截层 2（主导航白名单）：只放行「初始 host 或美团系域名」的
                    // http(s) 导航。自定义 scheme、跨域跳转一律拦截，停留在当前页。
                    return !isNavigationAllowed(request.url, initialHost)
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    // v0.8.11 防御：本回调内的任何异常都不得冒泡（否则 WebView 崩溃）
                    return runCatching { interceptRequest(request, proxyUserAgent) }
                        .getOrElse { e ->
                            FetchDebugLog.add("拦截异常: ${e.javaClass.simpleName}: ${e.message?.take(40)}")
                            emptyResponse()
                        }
                }

                override fun onPageStarted(
                    view: WebView,
                    url: String?,
                    favicon: android.graphics.Bitmap?
                ) {
                    super.onPageStarted(view, url, favicon)
                    FetchDebugLog.add("页面开始: ${url?.take(60)}")
                    // v1.1.0：DOM 解析前注入 XHR/fetch 劫持——早于页面自身数据请求，
                    // 拦截美团数据接口 JSON 响应提取店名（绕开「页面只渲染壳」）
                    view.evaluateJavascript(XHR_HOOK_SCRIPT, null)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    // SPA 在 onLoad 后才渲染店名：分批延迟采集（慢网络/二次跳转留余量）
                    HARVEST_DELAYS_MS.forEach { d ->
                        val task = Runnable { harvestOnce(view, done) }
                        pendingHarvests.add(task)
                        handler.postDelayed(task, d)
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView, title: String?) {
                    // SPA 标题多次更新（占位 → 真实店名）：净化通过即完成
                    FetchDebugLog.add("标题: ${title?.take(40)}")
                    if (title == null) return
                    val cleaned = ShareTextParser.cleanWebTitle(title)
                    if (cleaned != null) {
                        done.complete(cleaned)
                        return
                    }
                    // v1.1.2：验证页/登录页是死路（出不了店名）——快速失败，
                    // 不再空等 12 秒超时（单候选两次重试省约 20 秒）
                    if (ShareTextParser.isDeadEndWebTitle(title)) {
                        FetchDebugLog.add("死路标题,快速失败")
                        done.complete(null)
                    }
                }
            }
        }

        val timeout = Runnable {
            Log.d(TAG, "[Fetch] 超时未拿到店名: $url")
            done.complete(null)
        }
        handler.postDelayed(timeout, TIMEOUT_MS)
        try {
            Log.d(TAG, "[Fetch] 开始抓取: $url")
            FetchDebugLog.add("抓取: $url")
            webView.loadUrl(url)
            val result = done.await()
            Log.d(TAG, "[Fetch] 抓取结果: ${result ?: "null"} (url=$url)")
            FetchDebugLog.add("结果: ${result ?: "未拿到"}")
            return result
        } finally {
            handler.removeCallbacks(timeout)
            pendingHarvests.forEach { handler.removeCallbacks(it) }
            runCatching {
                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    /**
     * 拦截层 1 + 3（v0.8.11 核心：主文档代理）。
     *
     * - 子资源：非 http(s) scheme（iframe 唤起）与 verify.meituan.com 吞空响应；
     * - 主文档：**由本方法代发请求并手动消化重定向链**——WebView 网络栈
     *   从不自己请求主文档，服务端 302 到 imeituan:// 的唤起路径在 Java 侧
     *   被掐断（重定向不触发 shouldOverrideUrlLoading 的官方盲区由此绕开）。
     *   代理请求带 WebView Cookie 与 WebView 自身 UA（指纹一致，最大化
     *   拿到真实店铺页的概率），最终 HTML 以 WebResourceResponse 喂回 WebView。
     */
    private fun interceptRequest(
        request: WebResourceRequest,
        userAgent: String?
    ): WebResourceResponse? {
        val uri = request.url
        val scheme = uri.scheme?.lowercase()
        val isHttp = scheme == "http" || scheme == "https"
        if (!isHttp) {
            // 子资源 scheme（iframe src=imeituan:// 等）：吞空，阻断发 Intent
            return emptyResponse()
        }
        if (request.isForMainFrame) {
            // 主文档代理：WebView 永不自己发主文档请求/跟重定向
            // v1.1.1：UA 用主线程预取的 proxyUserAgent——本回调在 IO 线程，
            // 调 view.settings.userAgentString 必抛 RuntimeException（v1.1.0 的 bug）
            FetchDebugLog.add("主文档代理: ${uri.toString().take(55)}")
            return MainDocProxy.fetch(uri.toString(), userAgent)
        }
        // 子资源（JS/XHR/CSS，含 verify 验证页的自动验证请求）照常放行——
        // v0.8.15：adaptor=auto 的自动验证需要这些请求跑通才可能跳回店铺页
        return null
    }

    /** 导航白名单：http(s) 且（host == 初始 host 或美团/点评系域名）。其余全部拦截。 */
    private fun isNavigationAllowed(uri: Uri, initialHost: String?): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        // v1.1.1 补 dianping.com：唤起链本在点评/美团双域间跳（w.dianping.com 唤起页、
        // m.dianping.com 店铺页、verify.meituan.com 验证页），互跳是预期链路；站外一律拦截
        return host == initialHost ||
            host.endsWith(".meituan.com") || host.endsWith(".meituan.net") ||
            host.endsWith(".dianping.com")
    }

    /** 吞成空 200 响应（阻断请求且不给网络层处理 scheme 的机会）。 */
    private fun emptyResponse(): WebResourceResponse = MainDocProxy.emptyResponse()

    /** 执行一次 DOM 采集；已完成/异常静默跳过（后续批次或超时兜底）。 */
    private fun harvestOnce(view: WebView, done: CompletableDeferred<String?>) {
        if (done.isCompleted) return
        runCatching {
            view.evaluateJavascript(HARVEST_SCRIPT) { result ->
                extractShopName(result)?.let { done.complete(it) }
            }
        }
    }

    /** evaluateJavascript 结果是 JSON 数组文本：逐个净化，取第一个有效店名。 */
    private fun extractShopName(result: String?): String? {
        if (result.isNullOrBlank() || result == "null") return null
        return runCatching {
            val candidates = JSONArray(result)
            for (i in 0 until candidates.length()) {
                ShareTextParser.cleanWebTitle(candidates.optString(i))?.let { return it }
            }
            null
        }.getOrNull()
    }

    private companion object {
        private const val TAG = "WebViewTitleFetcher"

        /** SPA 渲染 + 二次跳转需要时间，给足余量 */
        private val TIMEOUT_MS = TimeUnit.SECONDS.toMillis(12)

        /** onPageFinished 后的采集批次（毫秒）：密集覆盖 SPA 慢渲染窗口 */
        private val HARVEST_DELAYS_MS = longArrayOf(1_500, 3_000, 4_500, 6_000, 9_000)

        /**
         * 死路域（主导航跳这些域 = 不可能出店名：登录页/浏览器升级提示页）。
         * static.meituan.net 是美团静态资源域——正常店铺页只把它当 CDN 加载子资源，
         * 主文档导航过去只会是 upgrader 升级提示页（meishi SPA 嫌 WebView「浏览器旧」）。
         */
        private val DEAD_END_HOSTS = setOf(
            "passport.meituan.com", "account.meituan.com", "account.dianping.com",
            "static.meituan.net"
        )

        /** v0.8.14：追加到系统 UA 尾部的微信标记（微信内打开是美团 H5 最常见场景） */
        private const val WECHAT_UA_SUFFIX =
            " MicroMessenger/8.0.49.2600(0x28003135676C5B5B) XWEB/1200065"

        /**
         * DOM 店名采集脚本（在页面上下文执行，返回字符串数组）：
         * - og:title meta（服务端/SSR 页常用）；
         * - h1（店铺页主体标题）；
         * - JSON-LD 结构化数据的 name 字段（v0.8.12）；
         * - class 含 shopName/poiName/ShopTitle 等（驼峰与中划线变体）的节点。
         * 文本截断到 40 字以内交给净化层限长，脏文本（评分/地址尾巴）由护栏裁剪。
         */
        private const val HARVEST_SCRIPT = """
            (function() {
                var out = [];
                function push(s) {
                    s = (s || '').replace(/\s+/g, ' ').trim();
                    if (s && s.length <= 40 && out.indexOf(s) < 0) out.push(s);
                }
                var og = document.querySelector('meta[property="og:title"]');
                if (og) push(og.getAttribute('content'));
                var h1 = document.querySelector('h1');
                if (h1) push(h1.textContent);
                var ld = document.querySelector('script[type="application/ld+json"]');
                if (ld) {
                    try {
                        var d = JSON.parse(ld.textContent);
                        if (d && d.name) push(d.name);
                    } catch (e) {}
                }
                var nodes = document.querySelectorAll(
                    '[class*="shopName"],[class*="ShopName"],[class*="poiName"],[class*="PoiName"],' +
                    '[class*="ShopTitle"],[class*="shop-title"],[class*="poi-title"],[class*="PoiTitle"],' +
                    '[class*="shop-name"],[class*="poi-name"]');
                for (var i = 0; i < nodes.length && i < 8; i++) push(nodes[i].textContent);
                return out;
            })()
        """

        /** v1.1.0：JS 接口在 window 上的挂载名（与 addJavascriptInterface 第二参一致） */
        private const val JS_INTERFACE_NAME = "What2EatShopName"

        /** v1.1.0：同一候选页重试次数上限（H5guard 签名偶发失败时登录态下重试提升成功率） */
        private const val MAX_ATTEMPTS = 2

        /** v1.1.0：重试间隔（让风控冷却） */
        private const val RETRY_DELAY_MS = 2_000L

        /**
         * v1.1.0：XHR/fetch 劫持脚本（onPageStarted 注入，早于页面自身数据请求）。
         *
         * 原理：美团 POI 数据接口响应是 JSON（poiName/shopName/name 字段），
         * 页面无登录态时只渲染壳——但接口请求本身已发出，响应 JSON 在 JS 层可截获。
         * 猴子补丁 XMLHttpRequest 与 fetch，响应文本里正则匹配店名字段，
         * 命中即回调原生接口 [JS_INTERFACE_NAME].onShopName。
         *
         * 防御：任何异常 try-catch 静默，不污染页面自身逻辑（避免影响 DOM 采集降级路径）。
         */
        private const val XHR_HOOK_SCRIPT = """
            (function() {
                if (window.__what2eatHooked) return;
                window.__what2eatHooked = true;
                var FIELD_RE = /"(?:poiName|shopName|name)"\s*:\s*"([^"\\]*)"/;
                // 把数据接口 URL 也回调原生（诊断面板可见抓取链是否发出数据请求）
                function log(msg) {
                    try {
                        if (window.${JS_INTERFACE_NAME} && window.${JS_INTERFACE_NAME}.onDebug) {
                            window.${JS_INTERFACE_NAME}.onDebug(msg);
                        }
                    } catch (e) {}
                }
                function report(text, url) {
                    try {
                        if (!text || typeof text !== 'string') return;
                        var m = text.match(FIELD_RE);
                        if (m && m[1]) {
                            var v = m[1].replace(/^\s+|\s+$/g, '');
                            // 限长 2..40（代码层判断，避免正则断言边界陷阱）
                            if (v.length >= 2 && v.length <= 40 && window.${JS_INTERFACE_NAME}) {
                                window.${JS_INTERFACE_NAME}.onShopName(v);
                            }
                        } else if (text.length < 300) {
                            // v1.1.3：数据接口短小无店名响应（多为 401/风控错误码）回传诊断
                            var u2 = String(url || '');
                            if (u2.indexOf('apimeishi') >= 0 || u2.indexOf('apimobile') >= 0 ||
                                u2.indexOf('/poi') >= 0) {
                                log('body:' + text.substring(0, 80));
                            }
                        }
                    } catch (e) {}
                }
                // fetch 劫持
                try {
                    var origFetch = window.fetch;
                    if (origFetch) {
                        window.fetch = function(u) {
                            var ustr = (typeof u === 'string' ? u : (u && u.url)) || '';
                            try { log('fetch:' + ustr); } catch (e) {}
                            return origFetch.apply(this, arguments).then(function(resp) {
                                try {
                                    // v1.1.2：回传状态码（诊断数据接口是否 401/403 被拒）
                                    log('resp:' + resp.status + ' ' + String(ustr).substring(0, 50));
                                    resp.clone().text().then(function(t) { report(t, ustr); });
                                } catch (e) {}
                                return resp;
                            });
                        };
                    }
                } catch (e) {}
                // XHR 劫持
                try {
                    var origOpen = XMLHttpRequest.prototype.open;
                    var origSend = XMLHttpRequest.prototype.send;
                    XMLHttpRequest.prototype.open = function(m, u) {
                        this.__what2eatUrl = u;
                        try { log('xhr:' + u); } catch (e) {}
                        return origOpen.apply(this, arguments);
                    };
                    XMLHttpRequest.prototype.send = function() {
                        this.addEventListener('load', function() {
                            try {
                                var text = null;
                                // responseType=json 时 responseText 读取抛异常——改用 response 序列化
                                if (this.responseType === '' || this.responseType === 'text') {
                                    text = this.responseText;
                                } else if (this.responseType === 'json' && this.response) {
                                    try { text = JSON.stringify(this.response); } catch (e) {}
                                }
                                // v1.1.2：回传状态码+体长（诊断数据接口是否 401/403 被拒）
                                log('resp:' + this.status + ' len=' + (text ? text.length : -1) +
                                    ' ' + String(this.__what2eatUrl || '').substring(0, 50));
                                report(text, this.__what2eatUrl);
                            } catch (e) {}
                        });
                        return origSend.apply(this, arguments);
                    };
                } catch (e) {}
            })();
        """
    }
}

/**
 * v1.2.4：主文档代理（从 WebViewTitleFetcher 抽出的共享实现，VerifyPassActivity 复用）。
 *
 * HttpURLConnection 代发主文档一次（30x 不内部跟随——见 [redirectResponse] 说明）：
 * - 30x 且 Location 为 http(s) → 喂 meta-refresh 壳页，WebView 导航到真实落地 URL；
 * - Location 非 http(s)（imeituan:// 唤起）→ 断链（空响应）——v0.8.10 官方盲区
 *   （重定向不回调 shouldOverrideUrlLoading）的根治点；
 * - 终态响应（200/404/500…）→ 原样喂回（错误页采集不到店名自然失败）。
 *
 * 运行在 WebView 的 IO 线程（shouldInterceptRequest 回调线程），阻塞合法。
 * userAgent 必须由主线程预取传入——IO 线程调 view.settings 必抛 RuntimeException（v1.1.0 的 bug）。
 */
internal object MainDocProxy {

    private const val TAG = "WebViewTitleFetcher"

    /** 单跳网络超时 */
    private const val DOC_TIMEOUT_MS = 6_000

    /** 代理请求的兜底 UA（正常取 WebView 自身 UA） */
    private const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    fun fetch(url: String, userAgent: String?): WebResourceResponse {
        // 带 WebView 的 cookie：最大化拿到真实店铺页（含店名 DOM）的概率
        val cookie = runCatching {
            CookieManager.getInstance().getCookie(url)
        }.getOrNull()
        val connResult = runCatching {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = DOC_TIMEOUT_MS
                readTimeout = DOC_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", userAgent ?: MOBILE_UA)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
                if (!cookie.isNullOrBlank()) {
                    setRequestProperty("Cookie", cookie)
                }
            }
        }
        val conn = connResult.getOrNull()
        if (conn == null) {
            FetchDebugLog.add("代理连接失败: ${connResult.exceptionOrNull()?.javaClass?.simpleName} ${url.take(50)}")
            return emptyResponse()
        }
        try {
            val code = runCatching { conn.responseCode }.getOrElse { e ->
                FetchDebugLog.add("代理读响应失败: ${e.javaClass.simpleName} ${url.take(50)}")
                -1
            }
            if (code == -1) {
                runCatching { conn.disconnect() }
                return emptyResponse()
            }
            Log.d(TAG, "[Proxy] code=$code hasCookie=${!cookie.isNullOrBlank()} url=$url")
            // cookie 只显示 key（隐私），判断登录态是否带上
            val cookieKeys = cookie?.split(';')
                ?.mapNotNull { it.trim().substringBefore('=').takeIf { k -> k.isNotBlank() } }
                ?.take(6)?.joinToString(",") ?: ""
            FetchDebugLog.add("代理 code=$code cookie=${if (!cookie.isNullOrBlank()) "有($cookieKeys)" else "无"} ${url.take(55)}")
            if (code in 300..399) {
                // v0.8.12：重定向跳的 Set-Cookie 也要回填（风控 cookie 常种在 30x 上）
                storeCookies(url, conn)
                val location = conn.getHeaderField("Location")
                runCatching { conn.disconnect() }
                // 无 Location 的 3xx / 非 http 的 Location（唤起 scheme）→ 断链
                if (location.isNullOrBlank()) return emptyResponse()
                if (!location.startsWith("http://", true) && !location.startsWith("https://", true)) {
                    return emptyResponse()
                }
                // v1.1.1：30x 改客户端跳转（verify 页 JS 要真实 location 才能自动通过）
                val next = runCatching { URL(URL(url), location).toString() }.getOrNull()
                    ?: return emptyResponse()
                // v1.2.4：撞 yoda 验证墙 → 上报「被墙的页面 URL」（不是验证页 URL——
                // requestCode 是一次性 challenge，被本代理消费后用户打开会提示
                // 「请求异常，拒绝操作」；VerifyPassActivity 加载页面 URL 经代理
                // 自然 302 出新鲜 challenge）
                if (next.startsWith("https://verify.meituan.com/")) {
                    VerifyWallSignal.report(url)
                }
                FetchDebugLog.add("代理跳转: ${next.take(60)}")
                return redirectResponse(next)
            }
            // v0.8.12：终态响应的 Set-Cookie 回填（H5guard 签名依赖主文档种下的 cookie）
            storeCookies(url, conn)
            // 终态响应：喂给 WebView（流由 WebView 消费，不 disconnect）
            val contentType = conn.contentType ?: "text/html"
            val mime = contentType.substringBefore(';').trim().ifEmpty { "text/html" }
            val encoding = Regex("""charset=([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
                .find(contentType)?.groupValues?.get(1) ?: "utf-8"
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            return if (stream != null) {
                WebResourceResponse(mime, encoding, stream)
            } else {
                emptyResponse()
            }
        } catch (_: Exception) {
            runCatching { conn.disconnect() }
            return emptyResponse()
        }
    }

    /**
     * v1.1.1：把服务端 30x 转成客户端 meta-refresh 壳页。
     *
     * 此前代发在 Java 侧内部跟随重定向、最终 HTML 以「原始请求 URL」喂回——
     * WebView 地址栏停留在跳转前 URL：verify 验证页 JS 读 location.search
     * 拿不到 requestCode（自动验证流程直接坏掉），SPA 相对路径资源也解析到
     * 错误 base。改成喂 meta-refresh 壳页让 WebView 自己导航到真实落地 URL：
     * 每一跳仍经 shouldInterceptRequest 代发（非 http Location 依然断链，
     * 唤起防护不变），且 WebView 地址与真实页面一致（verify 自动通过、
     * SPA 路由、跳转 Referer 全部正常）。
     */
    private fun redirectResponse(targetUrl: String): WebResourceResponse {
        val attrEscaped = targetUrl
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
        val jsEscaped = targetUrl
            .replace("\\", "\\\\")
            .replace("'", "\\'")
        val html = "<!DOCTYPE html><html><head>" +
            "<meta http-equiv=\"refresh\" content=\"0;url=$attrEscaped\">" +
            "<script>location.replace('$jsEscaped');</script>" +
            "</head><body></body></html>"
        return WebResourceResponse(
            "text/html", "utf-8",
            ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
        )
    }

    /**
     * v0.8.12：把代理响应的 Set-Cookie 回填 CookieManager。
     * 主文档被代发后 WebView 不处理其 Set-Cookie，而 SPA 数据接口的
     * H5guard 签名依赖这些 cookie——丢失会导致店名渲染失败。
     * 从 shouldInterceptRequest 的 IO 线程调用，全部 runCatching 防御。
     */
    private fun storeCookies(url: String, conn: HttpURLConnection) {
        val cookies = runCatching { conn.headerFields?.get("Set-Cookie") }.getOrNull() ?: return
        for (cookie in cookies) {
            if (cookie.isBlank()) continue
            runCatching { CookieManager.getInstance().setCookie(url, cookie) }
        }
    }

    /** 吞成空 200 响应（阻断请求且不给网络层处理 scheme 的机会）。 */
    fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
}
