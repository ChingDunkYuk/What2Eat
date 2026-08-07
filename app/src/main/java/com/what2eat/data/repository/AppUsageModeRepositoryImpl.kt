package com.what2eat.data.repository

import com.what2eat.core.datastore.AppUsageModeDataStore
import com.what2eat.domain.model.AppUsageMode
import com.what2eat.domain.repository.AppUsageModeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AppUsageModeRepository 的数据层实现。
 */
@Singleton
class AppUsageModeRepositoryImpl @Inject constructor(
    private val dataStore: AppUsageModeDataStore
) : AppUsageModeRepository {

    override fun observe(): Flow<AppUsageMode> {
        return dataStore.usageModeFlow
    }

    override suspend fun get(): AppUsageMode {
        return dataStore.usageModeFlow.first()
    }

    override suspend fun set(mode: AppUsageMode) {
        dataStore.setUsageMode(mode)
    }
}
