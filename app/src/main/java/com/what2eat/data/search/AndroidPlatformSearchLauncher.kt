package com.what2eat.data.search

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.what2eat.domain.search.PlatformFallbackResolver
import com.what2eat.domain.search.PlatformLaunchResult
import com.what2eat.domain.search.PlatformSearchLauncher
import com.what2eat.domain.search.SearchLauncher
import com.what2eat.domain.search.SearchPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android 实现：统一承接大众点评 / 美团 / 地图 / 浏览器 4 类平台。
 *
 * 决策逻辑委托给纯 Kotlin 的 [PlatformFallbackResolver]（可单测），
 * 本类只负责执行带副作用的系统调用（PackageManager 查询、启动 Intent、复制剪贴板）。
 *
 * 平台承接原则（与 Stage 3.2 需求一致）：
 * 1. 优先使用稳定、公开可用的搜索 Deep Link；—— 本阶段未验证大众点评/美团私有 URI Scheme，
 *    因此不硬编码，走"复制 + 打开 App"策略；
 * 2. 无法稳定直接搜索时：复制关键词 → 启动对应 App → 提示"已复制关键词，请在应用中搜索"；
 * 3. App 未安装时：尝试打开平台网页搜索（大众点评有公开网页搜索；美团无可靠网页搜索则直接回退）；
 * 4. 网页不可靠时：回退浏览器通用搜索；
 * 5. 所有失败都返回 [PlatformLaunchResult.Failed]，绝不闪退。
 *
 * 包可见性：Android 11+ 需在 Manifest 声明 <queries> 才能查询大众点评/美团包名，
 * 仅声明确有必要的平台包，不申请无关权限。
 */
@Singleton
class AndroidPlatformSearchLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val searchLauncher: SearchLauncher
) : PlatformSearchLauncher {

    override fun launch(
        platform: SearchPlatform,
        query: String
    ): PlatformLaunchResult = when (platform) {
        SearchPlatform.MAP -> mapToResult(query)
        SearchPlatform.BROWSER -> browserToResult(query)
        SearchPlatform.DIANPING -> launchTargetApp(PLATFORM_DIANPING, query)
        SearchPlatform.MEITUAN -> launchTargetApp(PLATFORM_MEITUAN, query)
    }

    // ── 大众点评 / 美团 承接 ──

    private fun launchTargetApp(config: PlatformConfig, query: String): PlatformLaunchResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return PlatformLaunchResult.Failed("搜索关键词为空")

        // 1. 应用是否已安装
        val installed = isAppInstalled(config.packageName)

        // 2. 已安装：复制关键词 + 启动 App（不指定搜索 Deep Link，未验证其稳定性）
        val launchSucceeded = if (installed) launchAppNoSearch(config.packageName) else false
        val copySucceeded = if (installed) searchLauncher.copyQuery(trimmed).success else false

        // 3. 未安装或启动失败：尝试平台网页承接
        var platformWebOpened = false
        if (!launchSucceeded) {
            val webUrl = config.webSearchUrl
            if (webUrl != null) {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(webUrl + Uri.encode(trimmed))
                ).addExternalFlags()
                if (canHandle(webIntent)) {
                    platformWebOpened = try {
                        context.startActivity(webIntent)
                        true
                    } catch (_: ActivityNotFoundException) {
                        false
                    } catch (_: SecurityException) {
                        false
                    }
                }
            }
        }

        // 4. 网页不可用：通用浏览器兜底
        var browserOpened = false
        if (!launchSucceeded && !platformWebOpened) {
            browserOpened = searchLauncher.openBrowserSearch(trimmed).success
        }

        // 决策（纯函数，可单测）
        return PlatformFallbackResolver.resolve(
            query = trimmed,
            displayName = config.displayName,
            capabilities = PlatformFallbackResolver.Capabilities(
                isInstalled = installed,
                launchSucceeded = launchSucceeded,
                copySucceeded = copySucceeded,
                platformWebOpened = platformWebOpened,
                browserOpened = browserOpened
            )
        )
    }

    /** 应用是否已安装（依赖 Manifest <queries> 声明的包可见性） */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 启动应用主页（不指定搜索页） */
    private fun launchAppNoSearch(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ── 地图 / 浏览器 复用既有 SearchLauncher ──

    private fun mapToResult(query: String): PlatformLaunchResult {
        val r = searchLauncher.openMapSearch(query)
        return if (r.success) {
            PlatformLaunchResult.Opened()
        } else {
            PlatformLaunchResult.FallbackToBrowser(r.message.ifBlank { "没有可用的地图应用，已尝试浏览器搜索" })
        }
    }

    private fun browserToResult(query: String): PlatformLaunchResult {
        val r = searchLauncher.openBrowserSearch(query)
        return if (r.success) {
            PlatformLaunchResult.Opened()
        } else {
            PlatformLaunchResult.Failed(r.message.ifBlank { "没有可用的浏览器，你可以复制关键词" })
        }
    }

    /** 是否有人能处理该 Intent（不指定具体应用，让系统解析） */
    private fun canHandle(intent: Intent): Boolean {
        return try {
            intent.resolveActivity(context.packageManager) != null
        } catch (_: Exception) {
            false
        }
    }

    private fun Intent.addExternalFlags(): Intent =
        apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    /** 平台配置 */
    private data class PlatformConfig(
        val packageName: String,
        val displayName: String,
        /** 公开网页搜索 URL 前缀；null 表示无可靠网页搜索，直接回退通用浏览器 */
        val webSearchUrl: String?
    )

    private companion object {
        /** 大众点评包名 */
        val PLATFORM_DIANPING = PlatformConfig(
            packageName = "com.dianping.v1",
            displayName = "大众点评",
            webSearchUrl = "https://www.dianping.com/search/keyword/0/"
        )

        /** 美团包名（无可靠公开网页搜索，webSearchUrl = null） */
        val PLATFORM_MEITUAN = PlatformConfig(
            packageName = "com.sankuai.meituan",
            displayName = "美团",
            webSearchUrl = null
        )
    }
}