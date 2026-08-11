package com.what2eat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.what2eat.core.designsystem.theme.What2EatTheme
import com.what2eat.core.navigation.What2EatNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * What2Eat 单 Activity 入口。
 *
 * 使用 Jetpack Compose + Navigation Compose 管理所有页面。
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
                What2EatNavHost(initialOptionId = initialOptionId)
            }
        }
    }
}
