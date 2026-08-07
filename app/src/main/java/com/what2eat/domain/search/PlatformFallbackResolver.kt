package com.what2eat.domain.search

/**
 * 平台承接回退决策（纯 Kotlin，无 Android 依赖，可单测）。
 *
 * 将"大众点评/美团"等目标 App 的承接策略抽象为纯函数：
 * 给定实际上已经发生的各项能力（是否安装、是否启动成功、是否复制成功、
 * 平台网页是否已打开、浏览器是否已打开），返回最终的 [PlatformLaunchResult] 结果与提示文案。
 *
 * 承接原则（与 Stage 3.2 需求一致）：
 * - 能直接搜索/打开 App → 复制关键词 + 打开，提示用户粘贴搜索；
 * - 未安装或启动失败 → 平台网页承接；
 * - 网页不可用 → 通用浏览器兜底；
 * - 全部失败 → 提示用户手动复制。
 */
object PlatformFallbackResolver {

    /** 实际已发生的能力 */
    data class Capabilities(
        val isInstalled: Boolean,
        val launchSucceeded: Boolean,
        val copySucceeded: Boolean,
        val platformWebOpened: Boolean,
        val browserOpened: Boolean
    )

    fun resolve(
        query: String,
        displayName: String,
        capabilities: Capabilities
    ): PlatformLaunchResult {
        val c = capabilities

        // 已安装且启动成功 → 复制关键词 + 打开 App，提示粘贴搜索
        if (c.isInstalled && c.launchSucceeded) {
            val message = if (c.copySucceeded) {
                "已复制“$query”\n请在${displayName}中搜索"
            } else {
                "已打开${displayName}，请手动搜索：$query"
            }
            return PlatformLaunchResult.OpenedWithCopiedQuery(message)
        }

        // 未安装或启动失败 → 平台网页 / 通用浏览器已完成承接
        if (c.platformWebOpened || c.browserOpened) {
            return PlatformLaunchResult.FallbackToBrowser(
                "未检测到$displayName，已为你打开浏览器"
            )
        }

        // 全部失败 → 提示手动复制
        return PlatformLaunchResult.Failed(
            "未找到$displayName，且浏览器不可用，请复制关键词手动搜索"
        )
    }
}