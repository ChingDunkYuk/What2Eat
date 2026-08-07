package com.what2eat.domain.search

/**
 * 外部搜索启动结果。
 *
 * @param success 是否成功启动了外部动作（或已成功建立可用的回退）
 * @param message 面向用户的提示文案（成功时可为空，失败/回退时提供友好提示）
 */
data class LaunchResult(
    val success: Boolean,
    val message: String = ""
)

/**
 * 将 Intent 与剪贴板操作集中管理。
 *
 * 禁止：
 * - Compose 页面直接堆大量 Intent try/catch；
 * - ViewModel 持有 Activity；
 * - 搜索失败导致应用崩溃。
 */
interface SearchLauncher {

    /**
     * 地图搜索：ACTION_VIEW + geo:。不绑定具体地图应用，让系统选择。
     * 地图不可用时回退浏览器搜索。
     */
    fun openMapSearch(query: String): LaunchResult

    /** 浏览器搜索：ACTION_VIEW + 通用 Web 搜索 URL。不绑定具体浏览器。 */
    fun openBrowserSearch(query: String): LaunchResult

    /** 复制关键词到系统剪贴板。 */
    fun copyQuery(query: String): LaunchResult
}