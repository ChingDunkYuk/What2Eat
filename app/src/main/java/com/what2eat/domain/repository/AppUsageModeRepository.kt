package com.what2eat.domain.repository

import com.what2eat.domain.model.AppUsageMode
import kotlinx.coroutines.flow.Flow

/**
 * 应用使用模式 Repository 接口。
 */
interface AppUsageModeRepository {

    /** 观察当前使用模式 */
    fun observe(): Flow<AppUsageMode>

    /** 获取当前使用模式（一次性） */
    suspend fun get(): AppUsageMode

    /** 设置使用模式 */
    suspend fun set(mode: AppUsageMode)
}
