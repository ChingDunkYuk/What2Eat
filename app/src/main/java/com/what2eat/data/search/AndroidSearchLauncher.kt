package com.what2eat.data.search

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.what2eat.domain.search.LaunchResult
import com.what2eat.domain.search.SearchLauncher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android 实现：集中管理地图/浏览器 Intent 与剪贴板操作。
 *
 * - 使用 applicationContext，不持有 Activity；
 * - 不申请定位权限、不读取 GPS、不引入地图 SDK；
 * - 不指定具体地图应用/浏览器，让系统自行选择；
 * - 所有异常统一转为友好提示（LaunchResult.message），不抛技术堆栈。
 */
@Singleton
class AndroidSearchLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) : SearchLauncher {

    override fun openMapSearch(query: String): LaunchResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return failure("搜索关键词为空")

        val mapIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=" + Uri.encode(trimmed))
        ).addExternalFlags()

        if (canHandle(mapIntent)) {
            try {
                context.startActivity(mapIntent)
                return LaunchResult(true)
            } catch (_: ActivityNotFoundException) {
                // 回退浏览器
            } catch (_: SecurityException) {
                return failure("无法打开地图应用，你可以复制关键词")
            }
        }

        // 地图不可用：回退浏览器搜索
        val browser = openBrowserSearch(trimmed)
        return if (browser.success) {
            LaunchResult(false, "没有可用的地图应用，已尝试浏览器搜索")
        } else {
            failure("没有可用的地图应用，浏览器也不可用，你可以复制关键词")
        }
    }

    override fun openBrowserSearch(query: String): LaunchResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return failure("搜索关键词为空")

        val url = WEB_SEARCH_URL + Uri.encode(trimmed)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addExternalFlags()

        if (!canHandle(intent)) {
            return failure("没有可用的浏览器，你可以复制关键词")
        }
        return try {
            context.startActivity(intent)
            LaunchResult(true)
        } catch (_: ActivityNotFoundException) {
            failure("没有可用的浏览器，你可以复制关键词")
        } catch (_: SecurityException) {
            failure("无法打开浏览器，你可以复制关键词")
        }
    }

    override fun copyQuery(query: String): LaunchResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return failure("搜索关键词为空")

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return failure("剪贴板不可用")
        clipboard.setPrimaryClip(
            ClipData.newPlainText("what2eat_search", trimmed)
        )
        return LaunchResult(true)
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

    private fun failure(message: String): LaunchResult =
        LaunchResult(false, message)

    companion object {
        /** 通用 Web 搜索 URL（不绑定具体浏览器） */
        private const val WEB_SEARCH_URL = "https://www.baidu.com/s?wd="
    }
}