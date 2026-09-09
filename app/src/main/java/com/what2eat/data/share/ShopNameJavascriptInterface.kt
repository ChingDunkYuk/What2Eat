package com.what2eat.data.share

import android.util.Log
import android.webkit.JavascriptInterface
import com.what2eat.domain.share.ShareTextParser

/**
 * v1.1.0：JS 数据劫持的原生回调接口。
 *
 * WebViewTitleFetcher 注入 XHR_HOOK_SCRIPT 后，美团数据接口的 JSON 响应在
 * JS 层被拦截，命中的店名经本接口回传原生层。
 *
 * 设计要点：
 * - 提取逻辑收敛在 [ShopNameExtractor]（纯 Kotlin object，可 JVM 单测），
 *   本类只做 JS → Kotlin 的透传 + 委托；
 * - @JavascriptInterface 方法是 R8 反射调用点，proguard-rules.pro 已加 keep。
 */
class ShopNameJavascriptInterface(
    private val onShopName: (String) -> Unit
) {

    /**
     * JS 侧回调：美团数据接口响应里提取到的店名候选。
     * 经 [ShareTextParser.cleanWebTitle] 护栏净化后才回调给抓取链
     * （营销词/验证码/壳页名在此被拦截）。
     */
    @JavascriptInterface
    fun onShopName(raw: String) {
        Log.d(TAG, "[ShopNameHook] JS 回调原始店名: $raw")
        FetchDebugLog.add("JS回调店名: $raw")
        val cleaned = ShareTextParser.cleanWebTitle(raw)
        if (cleaned == null) {
            Log.d(TAG, "[ShopNameHook] 被护栏拒绝: $raw")
            FetchDebugLog.add("护栏拒绝: $raw")
            return
        }
        Log.d(TAG, "[ShopNameHook] 护栏通过: $cleaned")
        FetchDebugLog.add("✓ 店名: $cleaned")
        onShopName(cleaned)
    }

    /** JS 侧数据接口 URL 调试回调（诊断面板展示抓取链是否发出数据请求） */
    @JavascriptInterface
    fun onDebug(msg: String) {
        Log.d(TAG, "[ShopNameHook] $msg")
        FetchDebugLog.add(msg)
    }

    private companion object {
        const val TAG = "WebViewTitleFetcher"
    }
}

/**
 * v1.1.0：抓取诊断日志收集器（无 adb 时屏幕内排障）。
 *
 * 写 App 私有文件（filesDir/fetch_debug.log），跨进程/跨时序都稳——
 * 抓取链（可能在 :shareimport 进程）写入，确认页面板读取，互不依赖内存共享。
 * 每次分享抓取前 [reset] 清空，环形截断防膨胀。
 */
object FetchDebugLog {
    // 内存缓冲：同进程读写零失败（主方案）；文件：跨进程/崩溃备份（辅方案）
    private val memoryBuffer = ArrayDeque<String>()
    // v1.1.2：XHR 状态码日志上线后条目变多，扩容防关键行被挤出环形缓冲
    private const val MAX_MEMORY = 120

    @Volatile
    private var logFile: java.io.File? = null
    private const val MAX_BYTES = 32 * 1024

    fun init(context: android.content.Context) {
        if (logFile == null) {
            logFile = java.io.File(context.filesDir, "fetch_debug.log")
        }
    }

    @Synchronized
    fun add(line: String) {
        // 内存（同进程面板一定读得到）
        memoryBuffer.addLast(line)
        while (memoryBuffer.size > MAX_MEMORY) memoryBuffer.removeFirst()
        // 文件（备份，失败静默）
        logFile?.let { f ->
            runCatching {
                f.appendText(line + "\n", Charsets.UTF_8)
                if (f.length() > MAX_BYTES) {
                    val tail = f.readText(Charsets.UTF_8).takeLast(MAX_BYTES / 2)
                    f.writeText(tail, Charsets.UTF_8)
                }
            }
        }
    }

    @Synchronized
    fun snapshot(): List<String> {
        // 优先内存（同进程最可靠）；为空才读文件（跨进程场景）
        if (memoryBuffer.isNotEmpty()) return memoryBuffer.toList()
        val f = logFile ?: return emptyList()
        return runCatching {
            if (f.exists()) f.readLines(Charsets.UTF_8) else emptyList()
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun reset() {
        memoryBuffer.clear()
        runCatching { logFile?.writeText("", Charsets.UTF_8) }
    }

    @Synchronized
    fun clear() = reset()
}

/**
 * 美团数据接口响应的店名提取（纯 Kotlin，可单测）。
 *
 * 美团 POI 数据接口 JSON 常见店名字段：poiName / shopName / name。
 * 只匹配「字段名":"值"」结构，值长度 2..40（与 cleanWebTitle 限长对齐），
 * 命中第一个有效值即返回；无匹配/非法 JSON/截断 → null（静默失败）。
 */
object ShopNameExtractor {

    /** poiName/shopName/name 字段：捕获完整引号内值（排除转义引号场景的最简实现） */
    private val FIELD_REGEX = Regex(
        """"(?:poiName|shopName|name)"\s*:\s*"([^"\\]*)""""
    )

    /**
     * 从接口 JSON 文本提取店名候选。
     * 捕获完整值后用代码限长 2..40（避免正则贪婪/断言边界陷阱）：
     * 超长（>40）或过短（<2）的值跳过；无有效字段返回 null。
     */
    fun extract(jsonText: String): String? {
        if (jsonText.isBlank()) return null
        for (match in FIELD_REGEX.findAll(jsonText)) {
            val value = match.groupValues[1].trim()
            if (value.length in 2..40) return value
        }
        return null
    }
}

/**
 * v1.2.0：yoda 验证墙信号（撞墙时记录验证页 URL，UI 层据此弹可视化 WebView
 * 让用户手动滑过；通过态 cookie 写入共享 CookieManager 后，重试抓取即不再撞墙）。
 * 只保留首次撞墙 URL（首选候选的验证页——通过它即给设备种下信任）。
 */
object VerifyWallSignal {
    @Volatile
    private var pendingUrl: String? = null

    @Synchronized
    fun report(url: String) {
        if (pendingUrl == null) pendingUrl = url
    }

    @Synchronized
    fun consume(): String? {
        val u = pendingUrl
        pendingUrl = null
        return u
    }

    @Synchronized
    fun clear() {
        pendingUrl = null
    }
}
