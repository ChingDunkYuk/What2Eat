package com.what2eat

import android.app.Application
import com.what2eat.data.DefaultCategoryInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What2Eat Application 入口。
 *
 * 使用 @HiltAndroidApp 启用 Hilt 依赖注入。
 * 启动时初始化默认餐饮分类。
 */
@HiltAndroidApp
class What2EatApplication : Application() {

    @Inject
    lateinit var categoryInitializer: DefaultCategoryInitializer

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 初始化默认餐饮分类（幂等，仅首次执行）
        applicationScope.launch {
            categoryInitializer.initializeIfNeeded()
        }
    }
}
