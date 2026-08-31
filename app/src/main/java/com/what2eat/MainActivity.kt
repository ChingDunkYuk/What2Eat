package com.what2eat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.what2eat.core.designsystem.theme.What2EatTheme
import com.what2eat.core.navigation.What2EatNavHost
import com.what2eat.feature.onboarding.CuteSplashScreen
import com.what2eat.feature.onboarding.OnboardingScreen
import dagger.hilt.android.AndroidEntryPoint

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

    when (launchState) {
        AppLaunchState.Loading -> CuteSplashScreen()
        AppLaunchState.NeedOnboarding -> OnboardingScreen(
            // completeOnboarding 写入 DataStore 后 launchState 自动变为 Ready
            onFinished = { }
        )
        AppLaunchState.Ready -> What2EatNavHost(initialOptionId = initialOptionId)
    }
}
