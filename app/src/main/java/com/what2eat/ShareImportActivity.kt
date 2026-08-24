package com.what2eat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.what2eat.core.designsystem.theme.What2EatTheme
import com.what2eat.feature.shareimport.ShareImportScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * 外部分享导入入口（Stage 5）。
 *
 * 通过 ACTION_SEND 接收 text/plain 分享，解析后进入确认页。
 * 用户确认后才写入吃饭池；不直接收到即写库。
 */
@AndroidEntryPoint
class ShareImportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val rawText = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT)
        val sourcePackage = resolveSourcePackage(intent)

        setContent {
            What2EatTheme {
                ShareImportScreen(
                    rawText = rawText,
                    subject = subject,
                    sourcePackage = sourcePackage,
                    onCancel = { finish() },
                    onViewDetail = { optionId ->
                        // 打开主应用并进入详情页
                        val detail = Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .putExtra("food_pool_option_id", optionId)
                        startActivity(detail)
                        finish()
                    },
                    onDone = { finish() }
                )
            }
        }
    }

    /**
     * 解析来源包名。
     * 优先级：calling package → "source_package" extra → EXTRA_REFERRER（Uri）。
     * 部分应用分享时 calling package 可能为 null，此时退回其余来源。
     */
    private fun resolveSourcePackage(intent: Intent?): String? {
        if (intent == null) return null
        val caller = runCatching { getCallingPackage() }.getOrNull()
        if (!caller.isNullOrBlank()) return caller
        intent.getStringExtra("source_package")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        // EXTRA_REFERRER 常见形式："android-app://com.dianping.v1/xxx" 或 "http(s)://..."
        val referrer = runCatching {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_REFERRER) as? Uri
        }.getOrNull()
        if (referrer != null) {
            if (referrer.scheme.equals("android-app", ignoreCase = true)) {
                referrer.host?.takeIf { it.isNotBlank() }?.let { return it }
            }
            referrer.host?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }
}