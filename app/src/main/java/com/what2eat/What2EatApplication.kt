package com.what2eat

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * What2Eat Application 入口。
 *
 * 使用 @HiltAndroidApp 启用 Hilt 依赖注入。
 */
@HiltAndroidApp
class What2EatApplication : Application()
