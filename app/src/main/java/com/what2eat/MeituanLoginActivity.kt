package com.what2eat

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * v1.1.0：美团登录页（可选，提升店名抓取成功率）。
 *
 * 原理：CookieManager 全局共享——用户在此登录美团后，登录态 cookie 自动写入
 * CookieManager；WebViewTitleFetcher 的代理请求已带 CookieManager cookie
 * （storeCookies 回填 + 请求读取），登录态自然穿透到抓取链路，无需改代理代码。
 *
 * 唤起防护维持最高优先级：仅放行 http(s) 导航，非 http(s) scheme 一律拦截
 * （登录流程可能尝试唤回美团 App，全部掐死停留在网页）。
 *
 * 不存账号密码；App 卸载 cookie 即清除。
 */
class MeituanLoginActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var loginPollTask: Runnable? = null
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 已登录则直接进入时也提示
        if (MeituanCookieStore.isLoggedIn()) {
            Toast.makeText(this, "当前已是登录状态", Toast.LENGTH_SHORT).show()
        }

        val view = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    // 只放行 http(s) 导航；非 http(s) scheme（唤回美团 App）一律拦截
                    val scheme = request.url.scheme?.lowercase()
                    return scheme != "http" && scheme != "https"
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    checkLoginAndFinish(url)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    checkLoginAndFinish(url)
                }
            }
        }
        webView = view
        setContentView(view)

        // 美团统一登录页
        view.loadUrl(MEITUAN_LOGIN_URL)
        startLoginPolling()
    }

    /** 登录态检测：URL 离开登录域 或 CookieManager 出现登录 cookie 即视为登录成功 */
    private fun checkLoginAndFinish(url: String?) {
        if (MeituanCookieStore.isLoggedIn()) {
            onLoginSuccess()
            return
        }
        // URL 已跳离 passport.meituan.com 且拿到美团主站 cookie，也视为登录成功
        val host = url?.let { Uri.parse(it).host?.lowercase() }
        if (host != null && !host.contains("passport.meituan.com") &&
            (host.endsWith(".meituan.com") || host == "meituan.com") &&
            MeituanCookieStore.isLoggedIn()
        ) {
            onLoginSuccess()
        }
    }

    /** 轮询登录态（页面内扫码/输密码登录，URL 可能不跳转，靠 cookie 变化检测） */
    private fun startLoginPolling() {
        val task = object : Runnable {
            override fun run() {
                if (MeituanCookieStore.isLoggedIn()) {
                    onLoginSuccess()
                    return
                }
                handler.postDelayed(this, LOGIN_POLL_INTERVAL_MS)
            }
        }
        loginPollTask = task
        handler.postDelayed(task, LOGIN_POLL_INTERVAL_MS)
    }

    private var loginHandled = false
    private fun onLoginSuccess() {
        if (loginHandled) return
        loginHandled = true
        Toast.makeText(this, "已登录美团通行证", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        loginPollTask?.let { handler.removeCallbacks(it) }
        webView?.runCatching { stopLoading(); destroy() }
        webView = null
        super.onDestroy()
    }

    private companion object {
        // v1.1.0：美团统一登录域是 passport.meituan.com（account.meituan.com 不存在，
        // 会报 net::ERR_NAME_NOT_RESOLVED）
        const val MEITUAN_LOGIN_URL = "https://passport.meituan.com/account/signin"
        const val LOGIN_POLL_INTERVAL_MS = 1_000L
    }
}

/**
 * v1.1.0：美团登录态 cookie 工具（全局共享 CookieManager；不存账号，只读系统 cookie）。
 *
 * v1.2.5 自查结论（抓取日志实锤）：
 * - 登录会话只落在 passport.meituan.com 域；美团 H5 各子域（meishi/apimobile/
 *   apimeishi/i.meituan）需独立 SSO 会话，App 内登录**打通不了子域**——
 *   parselogininfo 接口实测返回「用户未登陆」；
 * - 且店名抓取的主要障碍是 yoda 风控墙与 H5guard 签名（code 1600），
 *   与登录态无关。登录对店名识别的实际帮助有限，滑块验证人工通过才是主通道。
 */
object MeituanCookieStore {

    /**
     * 真实登录凭证 cookie 名（v1.2.5 修正：剔除 uuid/_hc.v——它们是美团分析 SDK
     * 对**所有访客**都种的匿名设备 ID，曾导致未登录也显示「已登录」的误判）。
     */
    private val LOGIN_COOKIE_KEYS = listOf("passport.sid", "token", "userId", "mtltoken")

    /**
     * 是否已登录美团通行证（任一真实凭证 cookie 存在）。
     * v1.2.5：检查域从 www.meituan.com 改为 passport.meituan.com——真实会话落在
     * passport 域（日志实测 passport.sid 只在 passport 请求上携带）。
     * 注意：美团 H5 各子域（meishi/apimobile 等）需独立 SSO 会话，
     * 本判定为 true 不代表子域有登录态（实测子域接口仍返回「用户未登陆」）。
     */
    fun isLoggedIn(): Boolean = runCatching {
        val cookie = CookieManager.getInstance()
            .getCookie("https://passport.meituan.com/") ?: return false
        LOGIN_COOKIE_KEYS.any { key -> cookie.contains("$key=") }
    }.getOrDefault(false)

    /** 清除登录态（退出美团登录）：清 CookieManager 全部 cookie + flush */
    fun logout() = runCatching {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()
    }
}
