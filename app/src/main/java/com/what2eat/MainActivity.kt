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
        setContent {
            What2EatTheme {
                What2EatNavHost()
            }
        }
    }
}
