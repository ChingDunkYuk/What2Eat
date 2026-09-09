package com.what2eat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.core.designsystem.theme.What2EatTheme
import com.what2eat.core.navigation.What2EatNavHost
import com.what2eat.feature.onboarding.CuteSplashScreen
import com.what2eat.feature.onboarding.OnboardingScreen
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

/**
 * What2Eat 单 Activity 入口。
 *
 * 使用 Jetpack Compose + Navigation Compose 管理所有页面。
 * 启动门控：可爱加载页 →（首次打开）引导改名页 → 主界面。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 由外部分享"查看详情"跳入时，携带要打开的吃饭选项 id
        val initialOptionId = intent?.getStringExtra("food_pool_option_id")
        setContent {
            What2EatTheme {
                AppLaunchGate(initialOptionId = initialOptionId)
            }
        }
    }
}

/**
 * 启动门控。
 *
 * Loading → 可爱加载页；NeedOnboarding → 引导改名页；
 * Ready → 主界面导航。引导完成后由 DataStore 流自动流转到 Ready，无需手动导航。
 */
@Composable
private fun AppLaunchGate(initialOptionId: String?) {
    val viewModel: MainViewModel = hiltViewModel()
    val launchState by viewModel.launchState.collectAsStateWithLifecycle()

    // v1.3.1：上次崩溃堆栈（若有）一次性弹窗展示
    CrashReportDialog()

    when (launchState) {
        AppLaunchState.Loading -> CuteSplashScreen()
        AppLaunchState.NeedOnboarding -> OnboardingScreen(
            // completeOnboarding 写入 DataStore 后 launchState 自动变为 Ready
            onFinished = { }
        )
        AppLaunchState.Ready -> What2EatNavHost(initialOptionId = initialOptionId)
    }
}

/**
 * v1.3.1：崩溃报告弹窗（无 adb 排障）。
 * 上次进程因未捕获异常退出时，Application 处理器已把堆栈写入
 * filesDir/[What2EatApplication.CRASH_FILE]；本次启动检测到即弹窗，
 * 「复制并关闭」复制堆栈并删除文件（只报一次，不打扰）。
 */
@Composable
private fun CrashReportDialog() {
    val context = LocalContext.current
    val crashFile = remember { File(context.filesDir, What2EatApplication.CRASH_FILE) }
    var trace by remember {
        mutableStateOf(
            runCatching {
                if (crashFile.exists()) crashFile.readText(Charsets.UTF_8).take(4000) else null
            }.getOrNull()
        )
    }
    val currentTrace = trace ?: return
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = {
            runCatching { crashFile.delete() }
            trace = null
        },
        title = { Text("上次闪退的错误信息") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "点「复制并关闭」把堆栈发给我定位：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(currentTrace, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(currentTrace))
                runCatching { crashFile.delete() }
                trace = null
            }) { Text("复制并关闭") }
        }
    )
}
