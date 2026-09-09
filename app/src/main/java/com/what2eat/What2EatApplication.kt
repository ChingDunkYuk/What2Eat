package com.what2eat

import android.app.Application
import com.what2eat.data.DefaultCategoryInitializer
import com.what2eat.data.PersonProfileInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject

/**
 * What2Eat Application 入口。
 *
 * 使用 @HiltAndroidApp 启用 Hilt 依赖注入。
 * 启动时初始化默认餐饮分类，并对主用户档执行自检。
 */
@HiltAndroidApp
class What2EatApplication : Application() {

    @Inject
    lateinit var categoryInitializer: DefaultCategoryInitializer

    @Inject
    lateinit var personProfileInitializer: PersonProfileInitializer

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        installCrashCapture()
        applicationScope.launch {
            // 先初始化餐饮分类（幂等，仅首次执行）
            categoryInitializer.initializeIfNeeded()
            // 主用户自检：保证恰好一个主用户，必要时种子化 Klaus
            personProfileInitializer.initializePrimaryIfNeeded()
        }
    }

    /**
     * v1.3.1：崩溃捕获（无 adb 排障）。
     * 全局未捕获异常先把堆栈写 filesDir/[CRASH_FILE]，再交还原默认处理器正常闪退；
     * MainActivity 下次启动检测到该文件即弹窗展示堆栈（一键复制发回定位）。
     */
    private fun installCrashCapture() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                File(filesDir, CRASH_FILE).writeText(sw.toString(), Charsets.UTF_8)
            }
            previous?.uncaughtException(thread, throwable)
                ?: kotlin.system.exitProcess(1)
        }
    }

    companion object {
        const val CRASH_FILE = "last_crash.txt"
    }
}
