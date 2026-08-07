package com.what2eat.domain.search

/**
 * 平台启动结果。
 *
 * 区分四种结果，UI 据此决定是否提示、提示什么：
 * - [Opened]：已直接完成（映射到结果页/通用搜索），不打扰用户；
 * - [OpenedWithCopiedQuery]：只能打开 App，关键词已复制，需提示用户粘贴搜索；
 * - [FallbackToBrowser]：已回退到浏览器搜索，需提示；
 * - [Failed]：全部失败，需提示用户复制关键词。
 *
 * [message] 为面向用户的提示文案；[Opened] 默认无提示。
 */
sealed class PlatformLaunchResult {

    abstract val message: String?

    /** 直接搜索/打开成功，不打扰用户 */
    data class Opened(override val message: String? = null) : PlatformLaunchResult()

    /** 只能打开对应 App，关键词已复制，请在应用内粘贴搜索 */
    data class OpenedWithCopiedQuery(override val message: String) : PlatformLaunchResult()

    /** App 未安装（或无法启动），已回退浏览器搜索 */
    data class FallbackToBrowser(override val message: String) : PlatformLaunchResult()

    /** 全部路径失败，需提示用户手动复制关键词 */
    data class Failed(override val message: String) : PlatformLaunchResult()
}