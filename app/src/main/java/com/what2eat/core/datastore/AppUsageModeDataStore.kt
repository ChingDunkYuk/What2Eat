package com.what2eat.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.what2eat.domain.model.AppUsageMode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.usageModeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "what2eat_settings"
)

/**
 * AppUsageMode DataStore 实现。
 * 使用 Preferences DataStore 持久化使用模式设置。
 */
@Singleton
class AppUsageModeDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val usageModeFlow: Flow<AppUsageMode> = context.usageModeDataStore.data.map { preferences ->
        val ordinal = preferences[USAGE_MODE_KEY] ?: AppUsageMode.SINGLE.ordinal
        AppUsageMode.entries.getOrElse(ordinal) { AppUsageMode.SINGLE }
    }

    suspend fun setUsageMode(mode: AppUsageMode) {
        context.usageModeDataStore.edit { preferences ->
            preferences[USAGE_MODE_KEY] = mode.ordinal
        }
    }

    companion object {
        private val USAGE_MODE_KEY = intPreferencesKey("app_usage_mode")
        private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
    }

    /** 是否已完成首次引导（改名页），null-safe 默认 false */
    val onboardingCompletedFlow: Flow<Boolean> = context.usageModeDataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED_KEY] ?: false
    }

    /** 标记首次引导完成 */
    suspend fun setOnboardingCompleted() {
        context.usageModeDataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] = true
        }
    }
}

/**
 * DataStore Hilt 模块。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideAppUsageModeDataStore(
        @ApplicationContext context: Context
    ): AppUsageModeDataStore {
        return AppUsageModeDataStore(context)
    }
}
